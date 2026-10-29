package rotp.addons.stand_bearing_creatures.entity.goal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.impl.powers.hamon.abilities.HamonSendoOverdriveAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import rotp.addons.stand_bearing_creatures.config.MobPowerConfig;
import rotp.addons.stand_bearing_creatures.mechanics.combo.ComboStep;
import rotp.addons.stand_bearing_creatures.mechanics.combo.PowerComboData;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

abstract class AbstractPowerMobGoal<P extends Power<P>> extends Goal {
    // Safety bound on one held key; the longest add-on charges (80-100
    // ticks) fire inside it.
    static final int MAX_HELD_TICKS = 120;
    // 1.16 050 AddonUtil.getActionTarget
    static final double LOOK_TARGET_REACH = 3.0D;

    protected final Mob mob;
    private final String heldKeyTag;
    private final Map<String, Integer> cooldownUntil =
            new HashMap<>();

    private int actionTicks;
    // The key a mob holds is let go by the state of the action it
    // started (1.16 050 StandAI held it until it fired), not after a
    // fixed tick.
    private int heldSinceTick = -1;
    private int fallbackReleaseTick = -1;
    @Nullable
    private EntityActionInstance heldAction;
    @Nullable
    private String heldAbilityName;
    @Nullable
    private ActionTarget heldLookTarget;
    @Nullable
    private EntityActionInstance lookAimedAction;
    private int comboResetTicks;
    private int blockCooldownTicks;
    private String currentCombo;
    private int currentComboStep;

    protected AbstractPowerMobGoal(Mob mob, String heldKeyTag) {
        this.mob = mob;
        this.heldKeyTag = heldKeyTag;
    }

    @Nullable
    protected abstract P power();

    @Nullable
    protected abstract PowerComboData comboData(P power);

    protected abstract int resolveLevel(P power);

    protected abstract double maximumTargetRange(P power);

    protected abstract double resourceAmount(P power);

    protected boolean runMaintenance(
            P power,
            PowerComboData data,
            Map<String, Ability> abilities,
            LivingEntity target) {
        return false;
    }

    protected void preparePower(P power) {}

    // Every 20th tick, before the pick (and while a hold waits).
    protected void beforePick(P power) {}

    protected void onTargetLost(P power) {}

    /**
     * Whether a held charge fires when the target is lost. 1.16 NonStandAI
     * called stopHeldAction(true), which fired it. StandAI called it too,
     * but unsummoned in the same tick, and the unsummon task replaced any
     * fired Stand task without Stand recovery ticks (the capsule, meteor
     * and steal holds mobs use), so a Stand's charge was dropped.
     */
    protected boolean firesHeldOnTargetLoss() {
        return true;
    }

    // 1.16 050 StandAI.tryToStartNewCombo drew from the combos of the
    // user's Resolve level (StandComboData.getAvailableCombos).
    protected List<String> comboNames(P power, PowerComboData data) {
        return data.availableCombos(resolveLevel(power));
    }

    // 1.16 050 StandAI.tryToContinueCombo reset the combo and performed a
    // random available action when the next step was not available.
    protected boolean randomPickWhenComboBreaks() {
        return true;
    }

    // 1.16 050 NonStandAI asked no stamina cost.
    protected boolean paysStaminaCost(P power, Ability ability) {
        return true;
    }

    // 1.16 050 StandAI.runAI (:111) ran its combos only with
    // useWIPComboSystem on, else a random pick.
    protected boolean combosFollowConfig() {
        return true;
    }

    @Nullable
    protected String blockAction(PowerComboData data) {
        return data.blockAction();
    }

    @Override
    public boolean canUse() {
        P power = power();
        return mob.isAlive()
                && power != null
                && power.hasPower();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    // 1.16 GoalSelector ticked every running goal each game tick, and
    // 050 StandAI/NonStandAI counted their 20-tick pick, 60-tick combo
    // reset and 80-tick block cooldown in those ticks. 1.21.1
    // Mob.serverAiStep ticks any other goal only every second tick.
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        P power = power();
        if (power == null || !power.hasPower()) {
            releaseHeldInput();
            return;
        }

        if (isHoldingInput() && shouldReleaseHeldInput()) {
            releaseHeldInput();
        }
        expireCooldowns();
        if (blockCooldownTicks > 0) {
            blockCooldownTicks--;
        }
        if (comboResetTicks > 0) {
            comboResetTicks--;
        }
        else {
            resetCombo();
        }

        LivingEntity target = mob.getTarget();
        double maxRange = maximumTargetRange(power);
        if (target == null
                || !target.isAlive()
                || target == mob
                || mob.distanceToSqr(target) > maxRange * maxRange) {
            // 1.16 050 NonStandAI stopHeldAction(true): a held charge that
            // loses its target fires instead of dropping.
            if (firesHeldOnTargetLoss()) {
                fireHeldInput();
            }
            ManualHeldInput.releaseOnTargetLoss(
                    mob, power.getPowerClass(), firesHeldOnTargetLoss());
            releaseHeldInput();
            releaseGrabIfTargetChanged(target);
            onTargetLost(power);
            resetCombo();
            return;
        }

        aimAt(target);
        preparePower(power);
        actionTicks++;
        if (actionTicks % 20 != 0) {
            return;
        }
        beforePick(power);
        // 1.16 refused other actions while one was held, so the next pick
        // waits instead of cutting the charge short; that includes a hold
        // the Debug Stand or /rotp_mwp use_random started.
        if (isHoldingInput()
                || ManualHeldInput.isHolding(
                        mob, power.getPowerClass())) {
            return;
        }

        Map<String, Ability> abilities = pool(power);
        if (abilities.isEmpty()) {
            return;
        }

        pick(power,
                comboData(power),
                abilities,
                target,
                MobPowerConfig.USE_WIP_COMBO_SYSTEM.get());
    }

    /**
     * The moves the mob may pick, as the core checked them on the mob's aim:
     * what its look met, where 050's isActionAvailable checked every action.
     */
    Map<String, Ability> pool(P power) {
        Map<String, Ability> abilities = new HashMap<>();
        for (Ability ability
                : MobPowerUtil.availableMobAbilities(power)) {
            abilities.putIfAbsent(ability.name(), ability);
        }
        return abilities;
    }

    void expireCooldowns() {
        cooldownUntil.entrySet().removeIf(
                entry -> entry.getValue() <= mob.tickCount);
    }

    void pick(
            P power,
            @Nullable PowerComboData data,
            Map<String, Ability> abilities,
            LivingEntity target,
            boolean useWipComboSystem) {
        if (data != null
                && runMaintenance(power, data, abilities, target)) {
            return;
        }
        if (data != null
                && tryBlock(power, data, abilities, target)) {
            return;
        }

        if (data != null
                && (useWipComboSystem || !combosFollowConfig())) {
            runCombo(power, data, abilities, target);
        }
        else {
            runRandom(power, data, abilities, target);
        }
    }

    @Override
    public void stop() {
        P power = power();
        releaseHeldInput();
        if (power != null) {
            onTargetLost(power);
        }
        resetCombo();
    }

    protected final boolean performNamed(
            P power,
            Map<String, Ability> abilities,
            String name,
            @Nullable ComboStep step,
            LivingEntity target) {
        Ability ability = abilities.get(name);
        if (!isAvailable(power, ability, step, target)) {
            return false;
        }
        perform(power, ability, target);
        return true;
    }

    protected final boolean isAvailable(
            P power,
            @Nullable Ability ability,
            @Nullable ComboStep step,
            LivingEntity target) {
        if (ability == null
                || cooldownUntil.containsKey(ability.name())
                || !MobPowerUtil.isMobSafeAbility(power, ability)
                || !MobPowerUtil.grabStateMatches(mob, ability)
                || isHeldBackByStandAction(ability)
                || !paysStaminaCost(power, ability)) {
            return false;
        }
        if (step != null) {
            if (step.requiredStamina() != null
                    && resourceAmount(power)
                            < step.requiredStamina()) {
                return false;
            }
            if (!matchesDistance(
                    step.distance(),
                    mob.distanceTo(target))) {
                return false;
            }
        }
        return true;
    }

    // 1.16 050 StandAI.isActionAvailable asked StandEntityAction.checkConditions
    // before every step and pick; its checkTaskCancelling refused an action
    // the Stand's current task would not let in, so a combo step reset the
    // combo and a pick drew among the others. The core holds such a press
    // back, and a mob's press is not buffered: it would be lost.
    private boolean isHeldBackByStandAction(Ability ability) {
        return ability instanceof StandEntityAbility standAbility
                && standAbility.wouldBufferInput(mob);
    }

    private boolean tryBlock(
            P power,
            PowerComboData data,
            Map<String, Ability> abilities,
            LivingEntity target) {
        String block = blockAction(data);
        if (block == null || blockCooldownTicks > 0) {
            return false;
        }
        boolean recentlyHurt =
                mob.tickCount - mob.getLastHurtByMobTimestamp() < 20;
        boolean preemptive = mob.getRandom().nextFloat() < 0.05F;
        if ((recentlyHurt || preemptive)
                && performNamed(
                        power,
                        abilities,
                        block,
                        null,
                        target)) {
            blockCooldownTicks = 80;
            return true;
        }
        return false;
    }

    void runCombo(
            P power,
            PowerComboData data,
            Map<String, Ability> abilities,
            LivingEntity target) {
        if (currentCombo == null) {
            List<String> candidates = new ArrayList<>(
                    comboNames(power, data));
            for (int i = candidates.size() - 1; i > 0; i--) {
                int swapWith = mob.getRandom().nextInt(i + 1);
                String value = candidates.get(i);
                candidates.set(i, candidates.get(swapWith));
                candidates.set(swapWith, value);
            }
            for (String candidate : candidates) {
                List<ComboStep> steps = data.combo(candidate);
                if (steps != null
                        && !steps.isEmpty()
                        && isAvailable(
                                power,
                                abilities.get(steps.getFirst().action()),
                                steps.getFirst(),
                                target)) {
                    currentCombo = candidate;
                    currentComboStep = 0;
                    break;
                }
            }
        }

        if (currentCombo == null) {
            runRandom(power, data, abilities, target);
            return;
        }
        List<ComboStep> steps = data.combo(currentCombo);
        if (steps == null || currentComboStep >= steps.size()) {
            resetCombo();
            return;
        }
        ComboStep step = steps.get(currentComboStep);
        if (performNamed(
                power,
                abilities,
                step.action(),
                step,
                target)) {
            currentComboStep++;
            comboResetTicks = 60;
        }
        else {
            resetCombo();
            if (randomPickWhenComboBreaks()) {
                runRandom(power, data, abilities, target);
            }
        }
    }

    private void runRandom(
            P power,
            @Nullable PowerComboData data,
            Map<String, Ability> abilities,
            LivingEntity target) {
        List<Ability> candidates = abilities.values().stream()
                .filter(ability -> data == null
                        || !ability.name().equals(blockAction(data)))
                .filter(ability -> isAvailable(
                        power,
                        ability,
                        null,
                        target))
                .toList();
        if (!candidates.isEmpty()) {
            perform(
                    power,
                    candidates.get(
                            mob.getRandom().nextInt(candidates.size())),
                    target);
        }
    }

    private void perform(
            P power,
            Ability ability,
            LivingEntity target) {
        releaseHeldInput();
        heldLookTarget = null;
        lookAimedAction = null;
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 1.16 050 performAction pressed every action at
        // AddonUtil.getActionTarget, whatever another press kept.
        ActionTarget lookTarget = aimAlong(mob, lookTarget(mob));
        int cooldown = mobCooldown(power, ability);
        // 1.16 050 StandAI.performAction clicked through PowerBaseImpl,
        // whose checkRequirements and checkTarget refused the whole action
        // on the new aim, and it set the cooldown whatever the click did.
        // The core asks the same of a player's press once the aim is stored
        // (AbilityInput.withConditionCheck, a HOLD press also continuing).
        ConditionCheck check = ability.checkConditions(power);
        if (!check.isPositive() && !check.shouldContinueHold()
                || MobPowerUtil.resumesOwnTimeStop(mob, ability)) {
            cooldownUntil.put(
                    ability.name(),
                    mob.tickCount + cooldown);
            return;
        }
        // Pressed as a player presses a HOLD bind; the input method does
        // not change what the press does on the server.
        HeldInputEntry held = AbilityInput.keyPressMob(
                ability,
                mob,
                new FriendlyByteBuf(Unpooled.EMPTY_BUFFER),
                InputMethod.HOLD);
        // 1.16 set the cooldown after every click, an attack follow-up's
        // too, which the core takes without holding a key.
        cooldownUntil.put(
                ability.name(),
                mob.tickCount + cooldown);
        if (held != null) {
            MobPowerUtil.rememberHeldKey(
                    mob,
                    heldKeyTag,
                    held.keyId);
            heldSinceTick = mob.tickCount;
            heldAbilityName = ability.name();
            heldAction = held.action instanceof EntityActionInstance action
                    ? action
                    : null;
            fallbackReleaseTick = mob.tickCount
                    + fallbackHoldTicks(ability.name());
            heldLookTarget = lookTarget;
            lookAimedAction = heldAction;
        }
    }

    // 050 StandAI/NonStandAI.performAction waited only the action's
    // getCooldownTechnical + 10, not its whole cooldown, so a pick in the
    // power's own cooldown was refused at the click; a time stop's technical
    // part is the stop's ticks (TimeStop :196-198).
    int mobCooldown(P power, Ability ability) {
        return Math.max(1, ability.getCooldownTechnical(power) + 10);
    }

    private void aimAt(LivingEntity target) {
        ActionTarget kept = keptAim();
        aimAlong(mob, kept != null ? kept : lookTarget(mob));
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    // 1.16 setHeldAction left the pressed target as the mob's mouse target,
    // which stoppedHolding read as the key was let go; the port's released
    // action performs on its next tick, so the aim stays until it is over.
    private boolean keepsLookAim() {
        if (heldLookTarget != null
                && (isHoldingInput()
                        || lookAimedAction != null
                                && !lookAimedAction.isOver())) {
            return true;
        }
        heldLookTarget = null;
        lookAimedAction = null;
        return false;
    }

    // The core keeps one aim per mob, which both goals set; 1.16 kept a
    // mouse target per power, so a press of the other power, or a manual one
    // its goal keeps, is not aimed away.
    @Nullable
    private ActionTarget keptAim() {
        if (keepsLookAim()) {
            return heldLookTarget;
        }
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof AbstractPowerMobGoal<?> other
                    && other != this
                    && other.keepsLookAim()) {
                return other.heldLookTarget;
            }
        }
        return null;
    }

    /*
     * 1.16 050 StandAI, NonStandAI and UseRandomAction checked and pressed
     * every action at AddonUtil.getActionTarget, what a 3-block ray along the
     * mob's look met first (OUTLINE blocks, pickable entities), its power's
     * one mouse target. The core keeps an aim for the mob and one for its
     * Stand, so both take it. 050 never turned the Stand; StandEntityMixin's
     * defaultRotation did, in the Stand's tick (MobStandRotation).
     */
    static ActionTarget lookTarget(LivingEntity user) {
        List<ActionTarget> met = HitResultUtil.clipMultipleTargets(
                user, LOOK_TARGET_REACH, entity -> entity != user,
                0.0D, 0.0D);
        return met.isEmpty() ? ActionTarget.EMPTY : met.get(0);
    }

    ActionTarget lookTarget() {
        return lookTarget(mob);
    }

    static ActionTarget aimAlong(LivingEntity user, ActionTarget aim) {
        LivingComponentAction.getComponent(user)
                .entityAim
                .setTarget(aim);
        StandPower standPower = StandPower.get(user);
        StandEntity stand = standPower != null
                ? standPower.getSummonedStandEntity()
                : null;
        if (stand != null) {
            LivingComponentAction.getComponent(stand)
                    .entityAim
                    .setTarget(aim.copy());
        }
        return aim;
    }

    // ManualHeldInput's press keeps its aim in the user's goal of its power,
    // which would aim it away on the next tick.
    static void keepManualPressAim(
            LivingEntity user,
            PowerClass<?> powerClass,
            ActionTarget aim,
            @Nullable EntityActionInstance action) {
        if (!(user instanceof Mob mob) || action == null) {
            return;
        }
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof AbstractPowerMobGoal<?> goal
                    && goal.power() instanceof Power<?> power
                    && power.getPowerClass() == powerClass) {
                goal.heldLookTarget = aim;
                goal.lookAimedAction = action;
            }
        }
    }

    private void releaseGrabIfTargetChanged(
            @Nullable LivingEntity target) {
        LivingEntity grabbed =
                rotp.core.subsystems.entity_grab
                        .LivingComponentGrab
                        .getEntityGrabbedBy(mob);
        if (grabbed != null && grabbed != target) {
            // No forceUnsummon: onTargetLost unsummons the Stand gently.
            MobPowerUtil.releaseInputsAndGrabs(mob);
        }
    }

    private void releaseHeldInput() {
        MobPowerUtil.releaseHeldKey(mob, heldKeyTag);
        heldSinceTick = -1;
        fallbackReleaseTick = -1;
        heldAction = null;
        heldAbilityName = null;
    }

    /**
     * 1.16 stopHeldAction(true) performed the held action at once, however
     * long it had been held. The key is still down here: the charge the
     * goal would wait out is run to its end (SYO's damage still comes from
     * the energy the real hold spent), unless its release already fires as
     * held (Sendo); the release that follows fires what fires on release. Its conditions are left to the core's
     * held-action check, as at the goal's own release of a full charge.
     */
    private void fireHeldInput() {
        fireHeldAction(heldAction);
    }

    static void fireHeldAction(@Nullable EntityActionInstance action) {
        if (action == null
                || !(action.ability instanceof EntityActionAbility ability)) {
            return;
        }
        boolean changed = false;
        // A button charge, then a windup the ability holds.
        for (int pass = 0;
                pass < 2 && completesHeldCharge(action, ability);
                pass++) {
            action.setPhase(
                    action.getPhase(),
                    Mth.ceil(action.getCurPhaseLength()));
            changed = true;
        }
        if (changed) {
            action.syncPhaseChanges();
        }
    }

    // A charge shouldReleaseHeld waits out; an open-ended hold is only
    // ever let go.
    static boolean completesHeldCharge(
            EntityActionInstance action,
            EntityActionAbility ability) {
        ActionPhase phase = action.getPhase();
        float length = action.getCurPhaseLength();
        return !action.isOver()
                && !releaseFiresAsHeld(action)
                && length <= MAX_HELD_TICKS
                && action.getPhaseTick() < length
                && (phase == ActionPhase.BUTTON_CHARGE
                        || phase == ActionPhase.WINDUP
                                && ability.isActionHeld(action));
    }

    // 1.16 HamonSendoOverdrive.stoppedHolding sent the wave on any stop,
    // its sparks sized by the ticks actually held. The port's release does
    // that from WINDUP, so it is let go as it is, not run to full.
    static boolean releaseFiresAsHeld(EntityActionInstance action) {
        return action instanceof HamonSendoOverdriveAbility
                        .SendoOverdriveInstance
                && action.getPhase() == ActionPhase.WINDUP;
    }

    private boolean isHoldingInput() {
        return heldSinceTick >= 0;
    }

    private boolean shouldReleaseHeldInput() {
        EntityActionInstance action = heldAction;
        if (action == null || heldAbilityName == null) {
            return mob.tickCount >= fallbackReleaseTick;
        }
        return shouldReleaseHeld(
                action,
                heldAbilityName,
                mob.tickCount - heldSinceTick);
    }

    /**
     * 1.16 050 StandAI.performAction held every hold-to-fire action and
     * never let go early; PowerBaseImpl.tickHeldAction fired (n, false)
     * holds at holdDurationMax. The mob keeps the key down while a
     * release would still cancel or cut the action, and lets go when the
     * action is over, when a charge that fires on release is full, or
     * when an open-ended hold has run its usual time.
     */
    static boolean shouldReleaseHeld(
            EntityActionInstance action,
            String abilityName,
            int heldTicks) {
        // canBeStoppedByOriginalHoldCancel is false in RECOVERY and for an
        // action a release does nothing to (a plain click).
        if (action.isOver()
                || heldTicks >= MAX_HELD_TICKS
                || !(action.ability instanceof EntityActionAbility ability)
                || !ability.canBeStoppedByOriginalHoldCancel(action)) {
            return true;
        }
        float phaseTick = action.getPhaseTick();
        float phaseLength = action.getCurPhaseLength();
        // Open-ended: setButtonHoldPhase makes a phase 999999 ticks long.
        if (phaseLength > MAX_HELD_TICKS) {
            return phaseTick >= holdDuration(abilityName);
        }
        return switch (action.getPhase()) {
            // A charge still counting fires by itself; one held at its
            // end (shouldHoldPhaseAtEnd) fires when the key is let go.
            case BUTTON_CHARGE, WINDUP -> phaseTick >= phaseLength;
            // A held PERFORM runs its usual time; any other runs out.
            case PERFORM -> (ability.isActionHeld(action)
                            || isHeldAbility(abilityName))
                    && phaseTick >= holdDuration(abilityName);
            default -> true;
        };
    }

    // Anything that is not an entity action keeps the old timing.
    static int fallbackHoldTicks(String abilityName) {
        return isHeldAbility(abilityName)
                ? holdDuration(abilityName)
                : 1;
    }

    @Nullable
    String currentCombo() {
        return currentCombo;
    }

    private void resetCombo() {
        currentCombo = null;
        currentComboStep = 0;
        comboResetTicks = 0;
    }

    private static boolean isHeldAbility(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("barrage")
                || lower.contains("guard")
                || lower.contains("inhale")
                || lower.contains("time_stop")
                || lower.contains("breath")
                || lower.contains("blood_drain")
                || lower.contains("absorption")
                || lower.contains("devour")
                || lower.startsWith("grab");
    }

    private static int holdDuration(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("time_stop")) {
            return 45;
        }
        if (lower.contains("barrage")
                || lower.contains("guard")
                || lower.startsWith("grab")) {
            return 20;
        }
        return 12;
    }

    private static boolean matchesDistance(
            @Nullable String condition,
            double distance) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        try {
            if (condition.startsWith(">=")) {
                return distance >= Double.parseDouble(
                        condition.substring(2));
            }
            if (condition.startsWith("<=")) {
                return distance <= Double.parseDouble(
                        condition.substring(2));
            }
            if (condition.startsWith(">")) {
                return distance > Double.parseDouble(
                        condition.substring(1));
            }
            if (condition.startsWith("<")) {
                return distance < Double.parseDouble(
                        condition.substring(1));
            }
        }
        catch (NumberFormatException ignored) {
            return false;
        }
        return true;
    }
}
