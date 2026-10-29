package rotp.addons.stonefree.ability;

import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.util.functions.UtilFunctions;

import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.addons.stonefree.state.StoneFreeState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class StoneFreeStringAbility
        extends NoPoseStandEntityAbility {
    private static final ActionAnimIdentifier RANGED_ATTACK_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "ranged_attack", false);
    private static final int STAND_BIND_STRING_LIMIT = 100;
    private final Mode mode;

    public static AbilityType<StoneFreeStringAbility> createType(
            ResourceLocation registryKey, Mode mode) {
        return new AbilityType<>(
                registryKey,
                (abilityType, abilityId) ->
                        new StoneFreeStringAbility(
                                abilityType, abilityId, mode));
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return mode == Mode.STAND_GRAPPLE
                ? RANGED_ATTACK_ANIM
                : super.getEntityAnim(action);
    }

    public StoneFreeStringAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        this(
                abilityType,
                abilityId,
                Mode.from(abilityId.nameInMoveset()));
    }

    private StoneFreeStringAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Mode mode) {
        super(
                abilityType,
                abilityId,
                type -> new StringAction(type, mode));
        this.mode = mode;
        usageGroup = mode.usageGroup;
        if (mode.userIntegrated) {
            standAutoSummonMode(AutoSummonMode.ARMS);
        }
        configurePhases(mode);
        // 1.16 swingHand() without withUserPunch: performing a user string
        // reset the user's attack strength. The barrier performed only on a
        // block target, so only its swing (placeBarrier) resets.
        if (mode.usesUserHand() && mode != Mode.BARRIER) {
            setResetsAttackStrengthOnPerform();
        }
    }

    // 1.16 017 InitStands:157-176: the user's strings were StandActions with
    // no Stand task, and the Stand's bind and grapple set no
    // standRecoveryTicks (StandEntityAction default 0).
    private void configurePhases(Mode mode) {
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        if (mode == Mode.USER_PICK) {
            setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 5);
            cooldown(16, 100);
        }
        else if (mode == Mode.CLOSE_WOUNDS) {
            setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 10);
        }
        else if (mode == Mode.USER_WHIP) {
            cooldown(10, 20);
        }
        else if (mode == Mode.STAND_BIND
                || mode == Mode.USER_BIND
                || mode == Mode.CAPTURE) {
            setDefaultPhaseLength(ActionPhase.PERFORM, 24);
            // 1.16 cooldown(24, 100, 0.5f): Resolve halves the 100 instead of removing it.
            cooldown(24, 100, 0.5F);
        }
        // 1.16 InitStands:169-175: only the Stand's grapple, recover string
        // and remove barrier are holdType. The user's two grapples go off on
        // the press, and their string stays out by itself.
        else if (mode == Mode.STAND_GRAPPLE
                || mode == Mode.RECOVER_STRING
                || mode == Mode.REMOVE_BARRIER) {
            setButtonHoldPhase(ActionPhase.PERFORM);
        }
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        if (mode == Mode.STAND_BIND
                && performer instanceof StandEntity stand) {
            action.phasesLength.put(
                    ActionPhase.PERFORM, standBindTicks(stand));
        }
        if (mode == Mode.USER_PICK && powerUser != null) {
            float healthRatio = powerUser.getMaxHealth() > 0.0F
                    ? powerUser.getHealth()
                            / powerUser.getMaxHealth()
                    : 1.0F;
            action.phasesLength.put(
                    ActionPhase.BUTTON_CHARGE,
                    Math.max(1, Mth.ceil(5.0F * healthRatio)));
        }
    }

    // 1.16 StoneFreeStringBind.getStandActionTicks: the task's perform and
    // its string's lifespan, standPerformDuration(24) over attack speed / 8.
    public static int standBindTicks(StandEntity stand) {
        return Mth.ceil(24.0D
                / Math.max(stand.getAttackSpeed() / 8.0D, 0.125D));
    }

    /**
     * 1.16 put the string budget in the ability's own name: {@code StoneFreeUserBarrier} overrode
     * {@code getTranslatedName} to pass the placed count and the maximum ("Place a Barrier (3/25)"), and
     * {@code StoneFreeUserRemoveBarrier} passed the placed count. The port dropped both, so the only way to see
     * how close you are to the limit is to hit it and read the refusal message.
     *
     * <p>The port already holds both numbers and computes the maximum exactly as 1.16 did (resolve 4 or above
     * 100, 90 under the Mobius Strip effect, otherwise 25), and {@code StoneFreeState} is synced to the client, so this is a naming
     * change only. The wording stays the port's ("String Barrier", "Remove Barriers") rather than the donor's -
     * restoring the mechanic is the point, and round 61 learned the hard way that restoring the donor's words
     * can undo a correction the porter made deliberately.
     *
     * <p>Arguments are always supplied, including when there is no user: a template with an unfilled
     * placeholder does not throw, it prints a literal "%s".
     */
    @Override
    public Component getName(Power<?> context) {
        if (mode != Mode.BARRIER && mode != Mode.REMOVE_BARRIER) {
            return super.getName(context);
        }
        LivingEntity user = context.getUser();
        StandPower power = PowerClass.STAND.cast(context);
        int placed = user != null
                ? StoneFreeState.get(user).placedStrings()
                : 0;
        if (mode == Mode.REMOVE_BARRIER) {
            return abilityName(context, "", String.valueOf(placed));
        }
        int max = user != null
                ? StoneFreeServices.maxStrings(user, power)
                : 0;
        return abilityName(
                context, "", String.valueOf(placed), String.valueOf(max));
    }

    // 1.16 StoneFreeUserMobiusStrip.getIconTexturePath: the "_off" icon
    // while Mobius mode is off (the synced state).
    @Override
    public String getSpriteName(Power<?> context) {
        if (mode == Mode.MOBIUS && context != null) {
            LivingEntity user = context.getUser();
            if (user != null && !StoneFreeState.get(user).mobiusMode()) {
                return super.getSpriteName(context) + "_off";
            }
        }
        return super.getSpriteName(context);
    }

    // 1.16 user_string_pick is holdToFire(5, false) and set its cooldown
    // when the hold fired, so a cancelled pick leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return mode != Mode.USER_PICK
                && super.shouldSetCooldownOnKeyPress(inputMethod);
    }

    // 1.16 017 InitStands:167, :173: the user's held strings (pick, close
    // wounds) were StandActions its power performed in the tick the hold
    // fired, the n-th from the press, also where the Stand charges them here.
    @Override
    public boolean chargeFiresAsItsLengthEnds(EntityActionInstance action) {
        return mode.userIntegrated
                || super.chargeFiresAsItsLengthEnds(action);
    }

    @Override
    public HeldInput onKeyPress(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod,
            float clickHoldResolveTime,
            BufferingState bufferingState) {
        if (mode == Mode.CAPTURE) {
            StoneFreeStringPunchAbility.queueCapture(
                    this, mode.staminaCost, level, user, bufferingState);
            return null;
        }
        return super.onKeyPress(
                level, user, extraClientInput, inputMethod,
                clickHoldResolveTime, bufferingState);
    }

    // 1.16 StoneFreeStringCapture was a StandEntityActionModifier (017
    // :52-53), never queued behind the string punch, so the HUD drew it by
    // its own requirements; its press joins the running punch at once or
    // does nothing (queueCapture), never waiting in the input buffer.
    @Override
    public boolean isQueuedBehindStandTask(LivingEntity user) {
        return mode != Mode.CAPTURE
                && super.isQueuedBehindStandTask(user);
    }

    /**
     * 1.16 017 InitStands:157-176: every user string (attack, sweep, whip,
     * bind, pick, both grapples, recover, close wounds, barrier, remove
     * barrier, Mobius) was a StandAction its user's power held, never a
     * Stand task, so it went off at once beside the Stand's move. It runs
     * in the user's Stand-power slot, performed by the Stand (or the arms
     * brought out for it); the Stand's bind, grapple and capture stay its
     * own tasks.
     */
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return mode.userIntegrated;
    }

    // 1.16 StringAction.canBeUsedDuringPlayerAction (017 :62-64): the
    // strings thrown from the user's hand were refused during a continuous
    // player action (a Hamon Overdrive Beat), the other user strings not.
    @Override
    public boolean canBeUsedDuringPlayerAction(
            EntityActionInstance playerAction) {
        return !mode.isThrownByUser()
                && super.canBeUsedDuringPlayerAction(playerAction);
    }

    /**
     * 1.16 user_string_whip was the shift variation of the user string attack,
     * which only ever appeared as the punch's context replacement. So Shift with
     * the attack button whipped exactly where the punch became the user string
     * attack; elsewhere the whip's Shift+LMB bind keeps whatever the punch gives.
     * Only a player's input resolves this way: 1.16 mob combos clicked the whip
     * itself.
     */
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        if (mode == Mode.STAND_BIND && abilities != null) {
            return userBindVariation(context, abilities);
        }
        if ((mode == Mode.USER_GRAPPLE || mode == Mode.USER_GRAPPLE_ENTITY)
                && abilities != null) {
            return userGrappleVariation(context, abilities);
        }
        if (mode != Mode.USER_WHIP
                || abilities == null
                || !(context.getUser() instanceof Player)) {
            return super.replaceWithSubAbility(context, abilities);
        }
        Ability punch = context.getMoveset().getAbility("punch");
        if (punch == null) {
            return this;
        }
        Ability punchVariation =
                punch.replaceWithSubAbility(context, abilities);
        if (punchVariation instanceof StoneFreeStringAbility string
                && string.mode == Mode.USER_ATTACK) {
            return this;
        }
        return punchVariation != null ? punchVariation : punch;
    }

    // 1.16 StoneFreeStringBind.replaceAction: the user binds with its own
    // string while no Stand is summoned, the Stand is only arms, or it is
    // busy with another task.
    private Ability userBindVariation(
            Power<?> context, AvailableAbilities abilities) {
        Ability userBind = abilities.getContextVariation(
                StoneFreeAddon.USER_STRING_BIND_NAME);
        if (userBind == null) {
            return this;
        }
        StandPower power = PowerClass.STAND.cast(context);
        StandEntity stand =
                power != null ? power.getSummonedStandEntity() : null;
        return stand == null
                        || stand.isArmsOnlyMode()
                        || busyWithOtherTask(stand, this)
                ? userBind
                : this;
    }

    // 1.16 StoneFreeUserGrapple.replaceAction: a live user grapple gives
    // the recover string; a manually controlled Stand grapples itself.
    private Ability userGrappleVariation(
            Power<?> context, AvailableAbilities abilities) {
        LivingEntity user = context.getUser();
        if (user != null && hasLiveUserGrapple(user)) {
            Ability recover = abilities.getContextVariation(
                    StoneFreeAddon.USER_RECOVER_STRING_NAME);
            return recover != null ? recover : this;
        }
        StandPower power = PowerClass.STAND.cast(context);
        StandEntity stand =
                power != null ? power.getSummonedStandEntity() : null;
        if (stand != null && stand.isManuallyControlled()) {
            Ability standGrapple = abilities.getContextVariation(
                    StoneFreeAddon.GRAPPLE_ENTITY_NAME);
            return standGrapple != null ? standGrapple : this;
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    // 1.16 LivingUtilCap.grapple: a user grapple string of this user is out
    // within 96 blocks. The entity check keeps a stale saved id from
    // locking the slot on the recover string.
    private static boolean hasLiveUserGrapple(LivingEntity user) {
        return StoneFreeState.get(user).userGrapple().isPresent()
                && !user.level().getEntitiesOfClass(
                        StoneFreeStringEntity.class,
                        user.getBoundingBox().inflate(96.0D),
                        string -> string.isUserGrappleFor(
                                user.getUUID()))
                        .isEmpty();
    }

    // 1.16 LivingUtilCap.userString: one of the user's attack, sweep, whip,
    // bind or pick strings is out within 16 blocks, or its grapple string
    // within 96.
    private static boolean hasUserStringOut(LivingEntity user) {
        return hasLiveUserGrapple(user)
                || !user.level().getEntitiesOfClass(
                        StoneFreeStringEntity.class,
                        user.getBoundingBox().inflate(16.0D),
                        string -> string.isActiveUserStringFor(
                                user.getUUID()))
                        .isEmpty();
    }

    // A user string is drawn from the Stand's arms, which are also its
    // owner. The grapple's string, and the attack, bind, sweep, whip and pick
    // strings that come back (1.16 SFU* entities), outlive their press, so
    // arms brought out for a string stay while it is out; the string sends
    // them away when it goes (StoneFreeStringEntity.remove).
    @Override
    public boolean retractsStandAfterAction(
            StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        LivingEntity user =
                standPower != null ? standPower.getUser() : null;
        return user == null || !hasLiveUserGrapple(user)
                && !hasUserStringComingBack(user);
    }

    private static boolean hasUserStringComingBack(LivingEntity user) {
        return !user.level().getEntitiesOfClass(
                        StoneFreeStringEntity.class,
                        user.getBoundingBox().inflate(96.0D),
                        string -> string.isUserStringComingBackFor(
                                user.getUUID()))
                .isEmpty();
    }

    /**
     * 1.16 StoneFreeStringBind, StoneFreeExtendedPunch and StoneFreeBarrage
     * replaceAction: the Stand's current task is neither this move nor a
     * light punch or the barrage.
     */
    public static boolean busyWithOtherTask(
            StandEntity stand, Ability move) {
        EntityActionInstance current = stand.getCurStandAction();
        // isOfAbility, not identity: a client's task holds the base-moveset
        // instance of its ability, not the one its user's moveset draws
        return current != null
                && !current.isOfAbility(move)
                && !(current.ability instanceof StandEntityPunchAbility)
                && !(current.ability instanceof StoneFreeBarrageAbility);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        return checkStringConditions(context);
    }

    // 1.16 PowerBaseImpl.tickHeldAction re-ran checkRequirements on every
    // held tick: the pick and Close Wounds charges and the held Recover
    // String, Remove Barriers and Stand grapple end, nothing fired, once a
    // filled main hand, healed wounds, no string or barrier left or the
    // string limit fails them.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return checkStringConditions(context);
    }

    // The string's own conditions, on the press and on every held tick.
    private ConditionCheck checkStringConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        StandPower power = PowerClass.STAND.cast(context);
        if (user == null || power == null) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 Action.checkHeldItems (needsFreeMainHand) ran before the
        // string's own conditions.
        if (mode.usesUserHand()
                && !UtilFunctions.isHandFree(
                        user, InteractionHand.MAIN_HAND)) {
            return ConditionCheck.createNegative("hand");
        }
        StoneFreeState state = StoneFreeState.get(user);
        int placed = state.placedStrings();
        if (mode == Mode.BARRIER) {
            // 1.16 StoneFreeUserBarrier.checkSpecificConditions: 'barrier'
            // once no more may be placed (canPlaceBarrier), then 100.
            if (placed >= StoneFreeServices.maxStrings(user, power)) {
                return ConditionCheck.createNegative("barrier");
            }
            if (placed >= STAND_BIND_STRING_LIMIT) {
                return ConditionCheck.createNegative("string_limit");
            }
        }
        // 1.16 StoneFreeStringBind.checkSpecificConditions: a flat 100.
        else if (mode == Mode.STAND_BIND) {
            if (placed >= STAND_BIND_STRING_LIMIT) {
                return ConditionCheck.createNegative("string_limit");
            }
        }
        // 1.16 StringAction, StoneFreeUserCloseWounds, StoneFreeStringCapture
        // and StoneFreeGrapple: the limit of all strings, not the number of
        // barriers the user may place.
        else if ((mode.usesStringBudget || mode.isGrapple())
                && placed >= StoneFreeServices.stringLimit(user)) {
            return ConditionCheck.createNegative("string_limit");
        }
        // 1.16 StringAction.checkSpecificConditions: no second grapple
        // while one of the user's grapple strings is out; its slot is the
        // recover string then (userGrappleVariation).
        if ((mode == Mode.USER_GRAPPLE || mode == Mode.USER_GRAPPLE_ENTITY)
                && hasLiveUserGrapple(user)) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 StoneFreeUserRecoverString.checkSpecificConditions: nothing
        // to take back without a user grapple string out.
        if (mode == Mode.RECOVER_STRING && !hasLiveUserGrapple(user)) {
            return ConditionCheck.NEGATIVE;
        }
        // The same 1.16 check, for every string the user throws: none
        // while one of the user's strings is still out.
        if (mode.isThrownByUser() && hasUserStringOut(user)) {
            return ConditionCheck.NEGATIVE;
        }
        if (mode == Mode.CLOSE_WOUNDS
                && StoneFreeServices.woundLevel(user) == 0) {
            return ConditionCheck.NEGATIVE;
        }
        if (mode == Mode.REMOVE_BARRIER
                && state.placedStrings() == 0
                && state.openBarrier().isEmpty()) {
            // 1.16 StoneFreeUserRemoveBarrier warned with no_barriers.
            return ConditionCheck.createNegative("no_barriers");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 StoneFreeStringBind.standKeepsTarget: the Stand's bind kept any
    // target, and its offsets turned toward it. The user's strings were
    // StandActions without a task, and stone_free_grapple_entity was no
    // shift variation, so its standKeepsTarget kept nothing.
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return mode == Mode.STAND_BIND;
    }

    // 1.16 PowerBaseImpl.checkTarget: the Stand's bind kept any target
    // (StoneFreeStringBind.standKeepsTarget, then checkStandTarget) and the
    // barrier a block (TargetRequirement.BLOCK); stone_free_grapple_entity
    // (no shift variation) and the user's strings, StandActions with no Stand
    // task, kept none, so the Stand never turned to what they were pressed at.
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return switch (mode) {
            case STAND_BIND -> super.takesPressedTarget(standEntity, target);
            case BARRIER -> target.getType() == TargetType.BLOCK;
            default -> false;
        };
    }

    public enum Mode {
        USER_ATTACK(
                true, true, 20.0F, AbilityUsageGroup.COMBAT),
        USER_SWEEP(
                true, true, 35.0F, AbilityUsageGroup.COMBAT),
        CAPTURE(
                false, true, 75.0F, AbilityUsageGroup.COMBAT),
        USER_WHIP(
                true, true, 40.0F, AbilityUsageGroup.COMBAT),
        STAND_BIND(
                false, true, 35.0F, AbilityUsageGroup.COMBAT),
        USER_BIND(
                true, true, 35.0F, AbilityUsageGroup.COMBAT),
        USER_PICK(
                true, true, 35.0F, AbilityUsageGroup.COMBAT),
        // 1.16 gave the user's grapples no stamina cost of their own: the
        // string takes its stamina while it is out.
        USER_GRAPPLE(
                true, false, 0.0F, AbilityUsageGroup.UTILITY),
        USER_GRAPPLE_ENTITY(
                true, false, 0.0F, AbilityUsageGroup.UTILITY),
        RECOVER_STRING(
                true, false, 0.0F, AbilityUsageGroup.UTILITY),
        STAND_GRAPPLE(
                false, false, 1.0F, AbilityUsageGroup.UTILITY),
        CLOSE_WOUNDS(
                true, true, 20.0F, AbilityUsageGroup.UTILITY),
        BARRIER(
                true, true, 0.0F, AbilityUsageGroup.UTILITY),
        REMOVE_BARRIER(
                true, false, 0.0F, AbilityUsageGroup.UTILITY),
        MOBIUS(
                true, false, 0.0F, AbilityUsageGroup.UTILITY);

        private final boolean userIntegrated;
        private final boolean usesStringBudget;
        private final float staminaCost;
        private final AbilityUsageGroup usageGroup;

        Mode(
                boolean userIntegrated,
                boolean usesStringBudget,
                float staminaCost,
                AbilityUsageGroup usageGroup) {
            this.userIntegrated = userIntegrated;
            this.usesStringBudget = usesStringBudget;
            this.staminaCost = staminaCost;
            this.usageGroup = usageGroup;
        }

        // 1.16 InitStands built these user strings with needsFreeMainHand()
        // and swingHand(); recover-string, close-wounds, remove-barrier,
        // mobius and the Stand's own strings had neither.
        private boolean usesUserHand() {
            return switch (this) {
                case USER_ATTACK,
                        USER_SWEEP,
                        USER_WHIP,
                        USER_BIND,
                        USER_PICK,
                        USER_GRAPPLE,
                        USER_GRAPPLE_ENTITY,
                        BARRIER -> true;
                default -> false;
            };
        }

        // 1.16 StringAction: the strings the user throws from its hand.
        private boolean isThrownByUser() {
            return usesUserHand() && this != BARRIER;
        }

        private boolean takesBackWhileHeld() {
            return this == RECOVER_STRING || this == REMOVE_BARRIER;
        }

        // 1.16 StoneFreeUserCloseWounds, StoneFreeUserRecoverString,
        // StoneFreeUserRemoveBarrier and StoneFreeUserMobiusStrip performed
        // on their user alone: arms that left before they fire do not matter
        private boolean performsWithoutStand() {
            return this == CLOSE_WOUNDS || this == RECOVER_STRING
                    || this == REMOVE_BARRIER || this == MOBIUS;
        }

        // 1.16 InitStands:167-175: the user's StandAction holds, holdType
        // (recover string, remove barrier) and holdToFire (pick, close
        // wounds).
        private boolean isUserHold() {
            return takesBackWhileHeld()
                    || this == USER_PICK || this == CLOSE_WOUNDS;
        }

        private boolean isGrapple() {
            return this == USER_GRAPPLE
                    || this == USER_GRAPPLE_ENTITY
                    || this == STAND_GRAPPLE;
        }

        private static Mode from(String name) {
            ResourceLocation registryId =
                    ResourceLocation.tryParse(name);
            String movesetName =
                    registryId != null && name.indexOf(':') >= 0
                            ? registryId.getPath()
                            : name;
            return switch (movesetName) {
                case StoneFreeAddon.USER_STRING_ATTACK_NAME ->
                        USER_ATTACK;
                case StoneFreeAddon.USER_STRING_SWEEP_NAME ->
                        USER_SWEEP;
                case StoneFreeAddon.STRING_CAPTURE_NAME ->
                        CAPTURE;
                case StoneFreeAddon.USER_STRING_WHIP_NAME ->
                        USER_WHIP;
                case StoneFreeAddon.STRING_BIND_NAME ->
                        STAND_BIND;
                case StoneFreeAddon.USER_STRING_BIND_NAME ->
                        USER_BIND;
                case StoneFreeAddon.USER_STRING_PICK_NAME ->
                        USER_PICK;
                case StoneFreeAddon.USER_GRAPPLE_NAME ->
                        USER_GRAPPLE;
                case StoneFreeAddon.USER_GRAPPLE_ENTITY_NAME ->
                        USER_GRAPPLE_ENTITY;
                case StoneFreeAddon.USER_RECOVER_STRING_NAME ->
                        RECOVER_STRING;
                case StoneFreeAddon.GRAPPLE_ENTITY_NAME ->
                        STAND_GRAPPLE;
                case StoneFreeAddon.USER_CLOSE_WOUNDS_NAME ->
                        CLOSE_WOUNDS;
                case StoneFreeAddon.USER_BARRIER_NAME ->
                        BARRIER;
                case StoneFreeAddon.USER_REMOVE_BARRIER_NAME ->
                        REMOVE_BARRIER;
                case StoneFreeAddon.USER_MOBIUS_STRIP_NAME ->
                        MOBIUS;
                default -> throw new IllegalArgumentException(
                        "Unknown Stone Free ability: " + name);
            };
        }
    }

    public static final class StringAction
            extends EntityActionInstance {
        private final Mode mode;
        private UUID actionProjectile;
        private int heldTicks;
        private boolean grappleSpawnAttempted;
        private boolean grappleSpawned;

        public StringAction(
                EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (mode == Mode.BARRIER) {
                captureBarrierBlock();
            }
            else if (performer instanceof StoneFreeStandEntity stand) {
                keepStandAimedAtTarget(
                        captureActionTargetFromAim(stand));
            }
            aimAs = AimingEntity.STAND;
            // 1.16 stone_free_grapple_entity standOffsetFromUser(-0.5, 0.25):
            // left -0.5, forward 0.25 at the default y in full mode, turned
            // by the view yaw
            if (mode == Mode.STAND_GRAPPLE) {
                setStandOffset(
                        -0.5D,
                        0.25D,
                        StandOffsetFromUser.Rotations.HEAD,
                        false);
            }
            if (level() instanceof ServerLevel serverLevel
                    && mode == Mode.CLOSE_WOUNDS) {
                LivingEntity user = getPowerUser();
                if (user != null) {
                    playUserStandSound(serverLevel, user,
                            StandPower.get(user),
                            StoneFreeAddon.STONE_FREE_CLOSING_WOUND);
                }
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.PERFORM
                    && isGrappleMode()) {
                trySpawnGrapple();
            }
        }

        @Override
        public void actionPerformStart() {
            StoneFreeStandEntity stand = stringStand();
            if (level().isClientSide()) {
                resetClientAttackStrengthOnBarrier(stand);
                return;
            }
            if (stand == null && !mode.performsWithoutStand()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null) {
                startRecovery();
                return;
            }

            float cost = mode.staminaCost;
            if (mode == Mode.CLOSE_WOUNDS) {
                cost *= StoneFreeServices.woundLevel(user);
            }
            // 1.16 paid the cost with the result ignored; a short bar just
            // drops to 0.
            if (cost > 0.0F && mode != Mode.STAND_GRAPPLE) {
                StandAbilityStamina.consume(ability, power, cost, false);
            }
            // 1.16 swingHand: the user's arm swings as the string leaves.
            // A barrier needed a block target to go off at all.
            if (mode.usesUserHand() && mode != Mode.BARRIER) {
                user.swing(InteractionHand.MAIN_HAND, true);
            }

            switch (mode) {
                // 1.16 StoneFreeUserStringAttack and StoneFreeUserStringBind
                // shot nothing either: the SFUStringEntity went out 8 blocks
                // and back within its 16 or 24 ticks (moveBoundToOwner).
                case USER_ATTACK -> spawnShot(
                        stand,
                        StoneFreeStringEntity.Kind.USER_STRING,
                        false,
                        0.0F,
                        0.0F,
                        0.0F);
                case USER_SWEEP -> spawnShot(
                        stand,
                        StoneFreeStringEntity.Kind.USER_SWEEP,
                        false,
                        0.0F,
                        0.0F,
                        0.0F);
                case USER_WHIP -> spawnShot(
                        stand,
                        StoneFreeStringEntity.Kind.USER_WHIP,
                        false,
                        0.0F,
                        0.0F,
                        0.0F);
                // 1.16 StoneFreeStringBind.addProjectile: no shot; the
                // string moves itself out and back (moveBoundToOwner). Its
                // lifespan is set before the add, which sends the spawn.
                case STAND_BIND -> spawnShot(
                        stand,
                        StoneFreeStringEntity.Kind.STAND_STRING,
                        true,
                        0.0F,
                        0.0F,
                        0.0F,
                        string -> string.setStandBindTicks(
                                standBindTicks(stand)));
                case USER_BIND -> spawnShot(
                        stand,
                        StoneFreeStringEntity.Kind.USER_STRING,
                        true,
                        0.0F,
                        0.0F,
                        0.0F);
                case USER_PICK -> {
                    if (ability instanceof StoneFreeStringAbility pick) {
                        pick.setCooldownOnUse(power);
                    }
                    // 1.16 autoSummonStand: full Stand at 5 HP or less.
                    if (user.getHealth() <= 5.0F
                            && stand.isArmsOnlyMode()) {
                        stand.fullSummonFromArms();
                    }
                    spawnShot(
                            stand,
                            StoneFreeStringEntity.Kind.USER_SPIKE,
                            false,
                            0.0F,
                            0.0F,
                            0.0F);
                }
                case USER_GRAPPLE,
                        USER_GRAPPLE_ENTITY,
                        STAND_GRAPPLE -> {
                    if (!trySpawnGrapple()) {
                        startRecovery();
                    }
                }
                case RECOVER_STRING ->
                        StoneFreeServices.discardUserGrapples(user);
                case CLOSE_WOUNDS -> {
                    StoneFreeServices.closeWounds(user);
                    playUserStandSound((ServerLevel) level(), user,
                            power,
                            StoneFreeAddon.STONE_FREE_CLOSED_WOUND);
                }
                case BARRIER -> placeBarrier(stand, user, power);
                case REMOVE_BARRIER ->
                        StoneFreeServices.discardBarriers(user);
                case MOBIUS -> {
                    StoneFreeState state = StoneFreeState.get(user);
                    boolean enabled = !state.mobiusMode();
                    state.setMobiusMode(user, enabled);
                    if (!enabled) {
                        user.removeEffect(
                                StoneFreeAddon.MOBIUS_STRIP);
                    }
                }
            }
            if (mode == Mode.STAND_BIND || mode == Mode.USER_BIND) {
                shareBindCooldown(power);
            }
            if (mode != Mode.RECOVER_STRING
                    && mode != Mode.REMOVE_BARRIER
                    && mode != Mode.MOBIUS
                    && mode != Mode.CLOSE_WOUNDS) {
                stand.playSound(
                        StoneFreeAddon.STONE_FREE_STRING.get(),
                        1.0F,
                        1.0F);
            }
        }

        // The held Stand grapple (1.16 staminaCostTick(1.0f).holdType()); the
        // user's grapples end with their press.
        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM
                    || mode != Mode.STAND_GRAPPLE) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (!grappleSpawned && !trySpawnGrapple()) {
                startRecovery();
                return;
            }
            if (++heldTicks > 1200 || user == null) {
                startRecovery();
                return;
            }
            // 1.16 StandEntityTask paid the tick cost and kept the hold on
            // an empty bar.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    mode.staminaCost,
                    true);
        }

        // The Stone Free the string comes from: the performer, or the user's
        // summoned Stand when the user threw the string itself (onKeyPress).
        private StoneFreeStandEntity stringStand() {
            if (performer instanceof StoneFreeStandEntity stand) {
                return stand;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            return power != null
                    && power.getSummonedStandEntity()
                            instanceof StoneFreeStandEntity stand
                    ? stand : null;
        }

        // 1.16 StoneFreeStringBind and StoneFreeUserStringBind each put the
        // other bind on cooldown as well.
        private void shareBindCooldown(StandPower power) {
            Ability otherBind = power.getAbility(mode == Mode.STAND_BIND
                    ? StoneFreeAddon.USER_STRING_BIND_NAME
                    : StoneFreeAddon.STRING_BIND_NAME);
            if (otherBind != null) {
                otherBind.setCooldownOnUse(power);
            }
        }

        // 1.16 StoneFreeUserBarrier was a user action on the user's own
        // crosshair (EntityStandType.clientHitResult; its miss fallback used
        // the user's reach, so it found no other block). The Stand's aim is
        // its gaze while busy and empty right after an auto-summon. The
        // server takes the press's user aim; the client keeps the synced one.
        private void captureBarrierBlock() {
            LivingEntity user = getPowerUser();
            if (level().isClientSide() || user == null) {
                return;
            }
            ActionTarget target = captureActionTargetFromAim(user);
            if (performer instanceof StoneFreeStandEntity) {
                keepStandAimedAtTarget(target);
            }
        }

        // 1.16 StoneFreeUserBarrier (TargetRequirement.BLOCK) went off only
        // on a block, under the string limit; the client copy asks the same
        // of the synced target and state.
        private boolean barrierGoesOff(
                LivingEntity user, StandPower power) {
            ActionTarget target = getActionTargetSnapshot(level());
            return target.getType() == TargetType.BLOCK
                    && level().hasChunkAt(target.getBlockPos())
                    && StoneFreeState.get(user).placedStrings()
                            < StoneFreeServices.maxStrings(user, power);
        }

        // 1.16 Action.onPerform reset the attack strength on both sides; the
        // server's reset comes with placeBarrier, the client's comes here.
        private void resetClientAttackStrengthOnBarrier(
                StoneFreeStandEntity stand) {
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            if (mode == Mode.BARRIER
                    && stand != null
                    && power != null
                    && user instanceof Player player
                    && barrierGoesOff(player, power)) {
                player.resetAttackStrengthTicker();
            }
        }

        /**
         * 1.16 threw every string that is not held (holdToFire or holdType)
         * on the key press itself. A click that shares its key with a hold
         * (LMB punch/barrage, Shift+LMB whip) is resolved on release, so its
         * press and release reach the server together, before the perform
         * tick; ending the action there dropped the string after its cooldown
         * was set. Before that tick only a charge or a held phase ends.
         */
        @Override
        public void onButtonStopHold() {
            if (awaitsPerformTick() && !isButtonHeldPhase()) {
                return;
            }
            if (getPhase() == ActionPhase.PERFORM && getPhaseTick() < 1.0F
                    && mode.takesBackWhileHeld()) {
                holdOneTick();
                return;
            }
            startRecovery();
        }

        /**
         * 1.16 SFUBarrierEntity:145 and SFUGrapplingStringEntity:71 took the strings back on a tick that saw Remove
         * Barriers or Recover String held, and the 1.16 client ended a hold no sooner than the client tick after
         * its press (InputHandler:336, 741-760), so the briefest press was held over a tick. A release that comes
         * before the perform tick leaves a held phase of that one tick.
         */
        private void holdOneTick() {
            phasesLength.put(ActionPhase.PERFORM, 1.0F);
            setPhase(ActionPhase.PERFORM, 0);
            syncPhaseChanges();
        }

        // Still before the tick that runs actionPerformStart
        // (EntityActionInstance._onTick).
        private boolean awaitsPerformTick() {
            ActionPhase phase = getPhase();
            return phase == ActionPhase.BUTTON_CHARGE
                    || phase == ActionPhase.WINDUP
                    || phase == ActionPhase.PERFORM
                            && getPhaseTick() < 1.0F;
        }

        private boolean isButtonHeldPhase() {
            return ability instanceof StoneFreeStringAbility owner
                    && owner.isActionHeld(this);
        }

        // 1.16 SFGrapplingStringEntity lived only while the Stand's grapple
        // was the held action. SFUGrapplingStringEntity stayed out after its
        // press and ended by itself, or when Recover String was held.
        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            if (!level().isClientSide()
                    && mode == Mode.STAND_GRAPPLE
                    && actionProjectile != null) {
                LivingEntity user = getPowerUser();
                Entity entity = user != null
                        ? StoneFreeServices.findEntity(
                                user, actionProjectile)
                        : null;
                if (entity != null) {
                    entity.discard();
                }
            }
            actionProjectile = null;
            grappleSpawned = false;
            if (!level().isClientSide()
                    && newAction == null
                    && mode.userIntegrated) {
                sendIdleArmsAway();
            }
            super.onActionCleared(newAction);
        }

        // StandEntity.onActionSet sends arms-only Stands away as their own
        // action ends; a user string lives in its user's Stand-power slot,
        // so its end does it for the arms brought out to throw it.
        private void sendIdleArmsAway() {
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (power != null
                    && getPerformer() instanceof StoneFreeStandEntity arms
                    && ability instanceof StoneFreeStringAbility owner
                    && owner.retractsStandAfterAction(power, arms, this)) {
                arms.leaveAfterUserString(this);
            }
        }

        // 1.16 Action.heldAllowsOtherAction: the user's held strings let no
        // other action of the power go off (PowerBaseImpl.checkRequirements)
        // until they fired or were let go; here such a press waits in the
        // input buffer.
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            if (mode == Mode.STAND_BIND) {
                return cancelsStandBind(cancellingAbility);
            }
            return !holdsOtherPressesBack();
        }

        // 1.16 StoneFreeStringBind.isCancelable (017 :85-86): only the punch,
        // the barrage and the heavy punch (the charged heavy is the port's
        // heavy) replaced the bind; any other press waits in the input buffer.
        private static boolean cancelsStandBind(
                EntityActionType cancellingAbility) {
            return cancellingAbility instanceof StoneFreePunchAbility
                    || cancellingAbility instanceof StoneFreeBarrageAbility
                    || cancellingAbility
                            instanceof StoneFreeStringPunchAbility.HeavyPunch
                    || cancellingAbility
                            instanceof StoneFreeChargedHeavyAbility;
        }

        // 1.16 StandEntityAction.canClickDuringTask (:495-497): the Stand's
        // bind and grapple were not chainable, so their own ability pressed
        // again during them was refused.
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return mode == Mode.STAND_BIND || mode == Mode.STAND_GRAPPLE;
        }

        boolean holdsOtherPressesBack() {
            return mode.isUserHold()
                    && (isButtonHeldPhase() || awaitsPerformTick());
        }

        // 1.16 stone_free_grapple_entity (StoneFreeGrapple) was a holdType
        // Stand action; the recover string and remove barrier holds were
        // StandActions, which let no other action through.
        @Override
        public boolean isHoldTypeStandAction() {
            return mode == Mode.STAND_GRAPPLE;
        }

        private StoneFreeStringEntity spawnShot(
                StoneFreeStandEntity stand,
                StoneFreeStringEntity.Kind kind,
                boolean binding,
                float velocity,
                float yawOffset,
                float pitchOffset) {
            return spawnShot(stand, kind, binding, velocity, yawOffset,
                    pitchOffset, string -> {});
        }

        private StoneFreeStringEntity spawnShot(
                StoneFreeStandEntity stand,
                StoneFreeStringEntity.Kind kind,
                boolean binding,
                float velocity,
                float yawOffset,
                float pitchOffset,
                Consumer<StoneFreeStringEntity> beforeAdd) {
            StoneFreeStringEntity string =
                    new StoneFreeStringEntity(
                            kind, stand, level());
            string.setBinding(binding);
            // user strings launch from the user's hand and look (donor SFU*StringEntity(world, user, power))
            LivingEntity shooter =
                    kind.isUserString() && getPowerUser() != null
                            ? getPowerUser()
                            : stand;
            string.setShootingPosOf(shooter);
            if (velocity > 0.0F) {
                string.shootFromRotation(
                        shooter,
                        shooter.getXRot() + pitchOffset,
                        shooter.getYRot() + yawOffset,
                        0.0F,
                        velocity,
                        0.0F);
            }
            beforeAdd.accept(string);
            addWithStandStats(string, stand);
            if (!string.isAddedToLevel()) {
                return null;
            }
            actionProjectile = string.getUUID();
            return string;
        }

        // addProjectileWithStandStats adds only a string a Stand performs;
        // one the user threw gets the same Stand stats here. 1.16 added the
        // user's own strings (SFU* entities, the barrier's too) with
        // world.addFreshEntity, so their damage and speed factors stayed 1.
        private void addWithStandStats(
                StoneFreeStringEntity string,
                StoneFreeStandEntity stand) {
            if (string.kind().isUserString()
                    || string.kind() == StoneFreeStringEntity.Kind.USER_BARRIER) {
                if (!level().isClientSide() && !string.isAddedToLevel()) {
                    string.withStandSkin(
                            stand.getStandType(), stand.getStandSkin());
                    level().addFreshEntity(string);
                }
            }
            else if (performer instanceof StandEntity) {
                addProjectileWithStandStats(string);
            }
            else if (!level().isClientSide() && !string.isAddedToLevel()) {
                string.setDamageFactor(string.getDamageFactor()
                        * (float) stand.getAttackDamage() / 8);
                string.setSpeedFactor(string.getSpeedFactor()
                        * stand.getAttackSpeed() / 8);
                string.withStandSkin(
                        stand.getStandType(), stand.getStandSkin());
                level().addFreshEntity(string);
            }
        }

        private boolean trySpawnGrapple() {
            if (level().isClientSide()
                    || grappleSpawnAttempted
                    || !isGrappleMode()) {
                return grappleSpawned;
            }
            LivingEntity user = getPowerUser();
            StoneFreeStandEntity stand = stringStand();
            if (stand == null || user == null) {
                return false;
            }
            grappleSpawnAttempted = true;
            grappleSpawned = spawnGrapple(
                    stand,
                    user,
                    mode != Mode.USER_GRAPPLE,
                    mode != Mode.STAND_GRAPPLE);
            return grappleSpawned;
        }

        private boolean isGrappleMode() {
            return mode == Mode.USER_GRAPPLE
                    || mode == Mode.USER_GRAPPLE_ENTITY
                    || mode == Mode.STAND_GRAPPLE;
        }

        private boolean spawnGrapple(
                StoneFreeStandEntity stand,
                LivingEntity user,
                boolean entities,
                boolean userGrapple) {
            if (userGrapple) {
                StoneFreeServices.discardUserGrapples(user);
            }
            StoneFreeStringEntity string = spawnShot(
                    stand,
                    userGrapple
                            ? StoneFreeStringEntity.Kind.USER_GRAPPLE
                            : StoneFreeStringEntity.Kind.STAND_GRAPPLE,
                    entities,
                    0.0F,
                            0.0F,
                            0.0F);
            if (string == null) {
                return false;
            }
            if (userGrapple) {
                StoneFreeState.get(user)
                        .setUserGrapple(
                                user, string.getUUID());
            }
            return true;
        }

        private void placeBarrier(
                StoneFreeStandEntity stand,
                LivingEntity user,
                StandPower power) {
            if (!barrierGoesOff(user, power)) {
                startRecovery();
                return;
            }
            ActionTarget target =
                    getActionTargetSnapshot(level());
            StoneFreeState state = StoneFreeState.get(user);
            int cap = StoneFreeServices.maxStrings(user, power);
            user.swing(InteractionHand.MAIN_HAND, true);
            // The swing echo reset the server only a round trip later.
            if (user instanceof Player player) {
                player.resetAttackStrengthTicker();
            }

            BlockPos block = target.getBlockPos();
            Entity previous = state.openBarrier()
                    .map(openId -> StoneFreeServices.findEntity(user, openId))
                    .orElse(null);
            if (previous instanceof StoneFreeStringEntity barrier
                    && barrier.isBarrierFor(user.getUUID())) {
                // The donor leaves the current open string untouched when its origin is selected again.
                if (barrier.isAlive() && !barrier.isBarrierAttached()
                        && block.equals(barrier.getBarrierOrigin())) {
                    return;
                }
                if (barrier.attachBarrier(block)) {
                    state.investString(user, 1, cap);
                }
            }

            StoneFreeStringEntity tail =
                    new StoneFreeStringEntity(
                            StoneFreeStringEntity.Kind.USER_BARRIER,
                            stand,
                            level());
            tail.configureOpenBarrier(block);
            addWithStandStats(tail, stand);
            state.setOpenBarrier(user, tail.getUUID());
            playUserStandSound((ServerLevel) level(), user, power,
                    StoneFreeAddon.STONE_FREE_BARRIER_PLACED);
        }
    }

    /**
     * 1.16 MCUtil.playSound(world, null, user, ..., AMBIENT, 1, 1,
     * StandUtil::playerCanHearStands): Stand-hearers only, the user
     * included (Player#playSound skips the user).
     */
    public static void playUserStandSound(
            ServerLevel level,
            LivingEntity user,
            StandPower power,
            Holder<SoundEvent> sound) {
        StandUtil.broadcastSound(
                level,
                user.position(),
                sound,
                true,
                power,
                SoundSource.AMBIENT,
                1.0F,
                1.0F);
    }
}
