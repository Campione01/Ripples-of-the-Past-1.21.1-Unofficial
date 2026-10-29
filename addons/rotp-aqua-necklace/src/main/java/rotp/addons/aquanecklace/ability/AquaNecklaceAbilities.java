package rotp.addons.aquanecklace.ability;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.TrainableAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.aquanecklace.AquaNecklaceAddon;
import rotp.addons.aquanecklace.entity.AquaNecklaceEntity;
import rotp.addons.aquanecklace.entity.AquaNecklaceEntity.MaterialState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class AquaNecklaceAbilities {
    public static final float LIQUID_COST = 40.0F;
    public static final float VAPOR_COST = 80.0F;
    public static final float ENTER_COST = 40.0F;
    public static final float INSIDE_HEAVY_COST = 50.0F;
    public static final float DROWN_COST_PER_TICK = 0.25F;
    public static final float LIQUID_TRAINING_PER_TICK = 5.6E-5F;
    public static final float LIQUID_MAX_TRAINING = 1.0F;
    public static final int STATE_COOLDOWN_TICKS = 5;
    public static final int POSSESSION_COOLDOWN_TICKS = 10;

    private AquaNecklaceAbilities() {}

    public static final class ChangeToLiquidAbility
            extends NoPoseStandEntityAbility implements TrainableAbility {
        public ChangeToLiquidAbility(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(
                    abilityType,
                    abilityId,
                    ChangeToLiquidAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            partsRequired(StandPart.MAIN_BODY);
            cooldown(STATE_COOLDOWN_TICKS);
            resolveLevelToUnlock(1);
        }

        @Override
        public String getLearningAbilityName() {
            return AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME;
        }

        @Override
        public float getMaxTrainingPoints(StandPower power) {
            return LIQUID_MAX_TRAINING;
        }

        @Override
        public void onMaxTraining(StandPower power) {
            var data = power != null ? power.getCurTypeData() : null;
            if (data != null) {
                data._setSkillUnlocked(
                        AquaNecklaceAddon.CHANGE_TO_GASEOUS_NAME,
                        true, false);
            }
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            ConditionCheck base =
                    super.checkSpecificConditions(context);
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            if (!base.isPositive() || stand == null) {
                return ConditionCheck.NEGATIVE;
            }
            return ConditionCheck.POSITIVE;
        }

        @Override
        public Component getName(Power<?> context) {
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            return stand != null
                            && stand.getMaterialState()
                                    == MaterialState.LIQUID
                    ? abilityName(context, ".undo")
                    : super.getName(context);
        }

        @Override
        public String getSpriteName(Power<?> context) {
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            return stand != null
                            && stand.getMaterialState()
                                    == MaterialState.LIQUID
                    ? super.getSpriteName(context) + "_undo"
                    : super.getSpriteName(context);
        }
    }

    public static final class ChangeToLiquidAction
            extends EntityActionInstance {
        public ChangeToLiquidAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)) {
                return;
            }
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            if (stand == null) {
                return;
            }
            boolean undo = stand.getMaterialState()
                    == MaterialState.LIQUID;
            // 1.16 StandEntityTask asked getStaminaCost after standPerform
            // had set the new state, so its half price for gas to liquid
            // never applied: every change paid the full cost.
            float cost = LIQUID_COST;
            stand.setMaterialState(
                    undo
                            ? MaterialState.SOLID
                            : MaterialState.LIQUID);
            // 1.16 StandEntityTask paid the cost after the perform and
            // ignored the result: a short bar only drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(owner),
                    cost,
                    false);
        }
    }

    public static final class ChangeToGaseousAbility
            extends NoPoseStandEntityAbility {
        public ChangeToGaseousAbility(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(
                    abilityType,
                    abilityId,
                    ChangeToGaseousAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            partsRequired(StandPart.MAIN_BODY);
            cooldown(STATE_COOLDOWN_TICKS);
            noResolveUnlock();
        }

        @Override
        public ConditionCheck getResolveUnlockConditionCheck(
                Power<?> context) {
            // Both the menu and automatic Resolve unlocks must wait for liquid mastery.
            if (context instanceof StandPower power) {
                var data = power.getCurTypeData();
                if (StandPower.playerSkipsActionTraining(power.getUser())
                        || power.wasProgressionSkipped()
                        || data != null
                                && data.getAbilityLearningProgressPoints(
                                        AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME)
                                        >= LIQUID_MAX_TRAINING) {
                    return super.getResolveUnlockConditionCheck(context);
                }
            }
            return ConditionCheck.createNegative("not_unlocked");
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            ConditionCheck base =
                    super.checkSpecificConditions(context);
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            if (!base.isPositive() || stand == null) {
                return ConditionCheck.NEGATIVE;
            }
            return ConditionCheck.POSITIVE;
        }

        @Override
        public Component getName(Power<?> context) {
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            return stand != null
                            && stand.getMaterialState()
                                    == MaterialState.VAPOR
                    ? abilityName(context, ".undo")
                    : super.getName(context);
        }

        @Override
        public String getSpriteName(Power<?> context) {
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            return stand != null
                            && stand.getMaterialState()
                                    == MaterialState.VAPOR
                    ? super.getSpriteName(context) + "_undo"
                    : super.getSpriteName(context);
        }
    }

    public static final class ChangeToGaseousAction
            extends EntityActionInstance {
        public ChangeToGaseousAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)) {
                return;
            }
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            if (stand == null) {
                return;
            }
            boolean undo = stand.getMaterialState()
                    == MaterialState.VAPOR;
            // 1.16 StandEntityTask asked getStaminaCost after standPerform
            // had set the new state, so its half price for liquid to gas
            // never applied: every change paid the full cost.
            float cost = VAPOR_COST;
            stand.setMaterialState(
                    undo
                            ? MaterialState.SOLID
                            : MaterialState.VAPOR);
            // 1.16 StandEntityTask paid the cost after the perform and
            // ignored the result: a short bar only drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(owner),
                    cost,
                    false);
        }
    }

    public static final class EnterEntityAbility
            extends NoPoseStandEntityAbility {
        private static final int NORMAL_HOLD_TICKS = 20;
        private static final int SKELETON_HOLD_TICKS = 10;

        public EnterEntityAbility(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(
                    abilityType,
                    abilityId,
                    EnterEntityAction::new);
            setDefaultPhaseLength(
                    ActionPhase.BUTTON_CHARGE,
                    NORMAL_HOLD_TICKS);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            partsRequired(StandPart.MAIN_BODY);
            cooldown(POSSESSION_COOLDOWN_TICKS);
            resolveLevelToUnlock(2);
            // 1.16 getting_into_entity cancelHeldOnGettingAttacked: a hit
            // during the charge drops it.
            setCancelHeldOnGettingAttacked();
        }

        // 1.16 getting_into_entity is holdToFire(20, false) and set its
        // cooldown when the hold fired, so a cancelled hold leaves none.
        @Override
        public boolean shouldSetCooldownOnKeyPress(
                InputMethod inputMethod) {
            return false;
        }

        @Override
        public void initActionFromConfig(
                EntityActionInstance action,
                Level level,
                LivingEntity powerUser,
                LivingEntity performer) {
            super.initActionFromConfig(
                    action, level, powerUser, performer);
            // 1.16 getHoldDurationToFire read the client's crosshair target
            // as it was (InputHandler.mouseTarget), before a torch or grass
            // gave way to the Stand's ray: only a skeleton there shortened
            // the charge
            ActionTarget aimed = performer instanceof AquaNecklaceEntity stand
                    ? AquaNecklaceTargeting.standAim(stand, level)
                    : ActionTarget.EMPTY;
            Entity entity =
                    aimed.getType() == ActionTarget.TargetType.ENTITY
                            ? aimed.getMainEntity()
                            : null;
            action.phasesLength.put(
                    ActionPhase.BUTTON_CHARGE,
                    entity instanceof LivingEntity living
                                    && AquaNecklaceEntity
                                            .isSkeletonHost(living)
                            ? SKELETON_HOLD_TICKS
                            : NORMAL_HOLD_TICKS);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            ConditionCheck base =
                    super.checkSpecificConditions(context);
            if (!base.isPositive()) {
                return ConditionCheck.NEGATIVE;
            }
            return checkAimedHost(context.getUser());
        }

        // 1.16 PowerBaseImpl.tickHeldAction asked for the crosshair target
        // again on every held tick; the charge, not holdOnly, ended once it
        // left a valid one, before anything was paid.
        @Override
        protected ConditionCheck checkHeldSpecificConditions(
                EntityActionInstance action, Power<?> context) {
            ConditionCheck base =
                    super.checkHeldSpecificConditions(action, context);
            if (!base.isPositive()) {
                return base;
            }
            return checkAimedHost(context.getUser());
        }

        // 1.16 getting_into_entity had TargetRequirement.ENTITY: no entity
        // under the crosshair, one the Stand may not attack
        // (checkStandTarget) or one checkStandConditions refused was
        // refused silently; one past 8 blocks of the Stand, 4 without a
        // line of sight (Action.checkRangeAndTarget), with
        // "target_too_far". Before those checks a pressed torch or grass
        // gave way to what the Stand's collision ray met
        // (overrideVanillaMouseTarget). With Aqua Necklace away the client
        // sent its vanilla pick alone, and StandEntityAction.onClick summoned
        // the Stand at its user (AutoSummonMode FULL) before those checks: the
        // press is judged as that Stand would judge it, and the core summons
        // it.
        ConditionCheck checkAimedHost(LivingEntity user) {
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(user);
            if (!(user instanceof Player)) {
                return ConditionCheck.NEGATIVE;
            }
            ActionTarget aimed = overridePressedTarget(user, stand != null
                    ? AquaNecklaceTargeting.standAim(stand, user.level())
                    : AquaNecklaceTargeting.userPick(user, user.level()));
            Entity entity =
                    aimed.getType() == ActionTarget.TargetType.ENTITY
                            ? aimed.getMainEntity()
                            : null;
            if (entity == null) {
                return ConditionCheck.NEGATIVE;
            }
            if (!ActionTargetRange.isEntityWithinRange(
                    stand != null ? stand : user,
                    entity,
                    ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
                return ConditionCheck.createNegative("target_too_far");
            }
            if (!(entity instanceof LivingEntity target)
                    || target.is(user)
                    || target instanceof StandEntity) {
                return ConditionCheck.NEGATIVE;
            }
            if (stand == null) {
                return AquaNecklaceTargeting.userMayAttack(user, target)
                                && target.isAlive()
                                && !target.isRemoved()
                        ? ConditionCheck.POSITIVE
                        : ConditionCheck.NEGATIVE;
            }
            if (!stand.canAttack(target)
                    || stand.isPossessing()
                    || !stand.canPossessTarget(user, target)) {
                return ConditionCheck.NEGATIVE;
            }
            return ConditionCheck.POSITIVE;
        }

        // 1.16 getTargetRequirement ENTITY: the task kept the entity it was
        // pressed at, and the Stand's offsets turned toward it
        @Override
        protected boolean keepsTaskTargetType(
                StandEntity standEntity, ActionTarget target) {
            return target.getType() == ActionTarget.TargetType.ENTITY;
        }
    }

    public static final class EnterEntityAction
            extends EntityActionInstance {
        public EnterEntityAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            LivingEntity aimingEntity = getPerformer();
            if (aimingEntity != null) {
                captureActionTargetFromAim(aimingEntity);
            }
        }

        // 1.16 startedHolding gave the task the press's own target; the
        // client's later crosshair only rechecked the hold
        @Override
        protected ActionTarget pressedTaskTarget() {
            return getActionTargetSnapshot(level());
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)) {
                return;
            }
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            if (stand == null || stand.isPossessing()) {
                return;
            }

            // 1.16 standPerform entered only its task's target, which
            // StandEntityTask.keepTarget dropped on any tick it left 8 blocks
            // of the Stand (4 unseen), died or could not be attacked; with
            // none left the fire entered nothing and still paid.
            ActionTarget held = getTaskTarget(level());
            LivingEntity target =
                    held.getType() == ActionTarget.TargetType.ENTITY
                                    && held.getEntity()
                                            instanceof LivingEntity living
                            ? living
                            : null;
            StandPower power = StandPower.get(owner);
            if (target != null
                    && !stand.canPossessTarget(owner, target)) {
                return;
            }
            if (ability instanceof EnterEntityAbility enter) {
                enter.setCooldownOnUse(power);
            }
            Ability leave = power.getAbility(
                    AquaNecklaceAddon.LEAVE_ENTITY_NAME);
            if (leave != null) {
                leave.setCooldownOnUse(power);
            }

            if (target != null && stand.attachTo(target)) {
                owner.displayClientMessage(
                        Component.translatable(
                                "rotp_aqua_necklace.message.entered",
                                target.getDisplayName()),
                        false);
            }
            // 1.16 StandEntityTask paid the cost after the perform and
            // ignored the result: a short bar only drops to 0.
            StandAbilityStamina.consume(
                    ability, power, ENTER_COST, false);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }
    }

    public static final class LeaveEntityAbility
            extends NoPoseStandEntityAbility {
        public LeaveEntityAbility(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(
                    abilityType,
                    abilityId,
                    LeaveEntityAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            partsRequired(StandPart.MAIN_BODY);
            cooldown(POSSESSION_COOLDOWN_TICKS);
            resolveLevelToUnlock(2);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            ConditionCheck base =
                    super.checkSpecificConditions(context);
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            return base.isPositive()
                            && stand != null
                            && stand.isPossessing()
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.NEGATIVE;
        }
    }

    public static final class LeaveEntityAction
            extends EntityActionInstance {
        public LeaveEntityAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)) {
                return;
            }
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            if (stand != null && stand.isPossessing()) {
                stand.clearPossession();
                StandPower power = StandPower.get(owner);
                Ability enter = power != null ? power.getAbility(
                        AquaNecklaceAddon.ENTER_ENTITY_NAME) : null;
                if (enter != null) {
                    enter.setCooldownOnUse(power);
                }
                owner.displayClientMessage(
                        Component.translatable(
                                "rotp_aqua_necklace.message.left"),
                        false);
            }
        }
    }

    public static final class DrownHostAbility
            extends NoPoseStandEntityAbility {
        public DrownHostAbility(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(
                    abilityType,
                    abilityId,
                    DrownHostAction::new);
            setButtonHoldPhase(ActionPhase.PERFORM);
            setDefaultPhaseLength(ActionPhase.PERFORM, 72000);
            // 1.16 017 InitStands:119 set no standRecoveryTicks
            setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
            partsRequired(StandPart.MAIN_BODY);
            resolveLevelToUnlock(3);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            ConditionCheck base =
                    super.checkSpecificConditions(context);
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            if (!base.isPositive()
                    || stand == null
                    || !stand.canDrownPossessedTarget()) {
                return ConditionCheck.NEGATIVE;
            }
            return ConditionCheck.POSITIVE;
        }
    }

    public static final class DrownHostAction
            extends EntityActionInstance {
        public DrownHostAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)) {
                return;
            }
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            if (stand == null
                    || !stand.canDrownPossessedTarget()) {
                startRecovery();
                return;
            }
            // 1.16 AquaNecklaceGetIntoTheLungs.onTaskSet turned a non-liquid
            // Stand liquid and paid 40 with the result ignored.
            if (stand.getMaterialState() != MaterialState.LIQUID) {
                stand.setMaterialState(MaterialState.LIQUID);
                StandAbilityStamina.consume(
                        ability,
                        StandPower.get(owner),
                        LIQUID_COST,
                        false);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()) {
                return;
            }
            LivingEntity owner = getPowerUser();
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            StandPower power = owner != null
                    ? StandPower.get(owner)
                    : null;
            if (stand == null
                    || !stand.canDrownPossessedTarget()) {
                startRecovery();
                return;
            }
            // 1.16 staminaCostTick(0.25), result ignored: an empty bar
            // does not end the hold.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    DROWN_COST_PER_TICK,
                    true);
            stand.drownPossessedTargetTick();
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 AquaNecklaceGetIntoTheLungs was a holdType Stand action
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 StandEntityAction.canClickDuringTask :495-497: not chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }

    public static final class HostPressureAbility
            extends StandEntityPunchAbility {
        private static final ActionAnimIdentifier PUNCH_ANIM =
                ActionAnimIdentifier.getOrCreate(
                        "punch", false);

        public HostPressureAbility(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(
                    abilityType,
                    abilityId,
                    HostPressureAction::new);
            setDefaultPhaseLength(ActionPhase.WINDUP, 0);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
            partsRequired(StandPart.MAIN_BODY);
        }

        @Override
        protected ConditionCheck checkStandEntityConditions(
                StandPower standPower,
                StandEntity standEntity) {
            if (standEntity
                    instanceof AquaNecklaceEntity aqua
                    && aqua.isPossessing()) {
                return ConditionCheck.POSITIVE;
            }
            return super.checkStandEntityConditions(
                    standPower, standEntity);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            ConditionCheck base =
                    super.checkSpecificConditions(context);
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            if (!base.isPositive() || stand == null) {
                return ConditionCheck.NEGATIVE;
            }
            return ConditionCheck.POSITIVE;
        }

        @Override
        public Component getName(Power<?> context) {
            return showsPlainPunch(context)
                    ? abilityName(context, ".out")
                    : super.getName(context);
        }

        @Override
        public String getSpriteName(Power<?> context) {
            return showsPlainPunch(context)
                    ? super.getSpriteName(context) + "_out"
                    : super.getSpriteName(context);
        }

        // 1.16 AquaNecklacePunch.replaceAction :49-52 also gave punch_out inside a skeleton, skeleton horse,
        // wither or golem host; inside one it hit the host the same way as the other punch
        public static boolean showsPlainPunch(Power<?> context) {
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            return stand != null
                    && (!stand.isPossessing()
                            || !AquaNecklaceEntity.hasBrain(
                                    stand.getPossessedTarget()));
        }

        @Override
        public ActionAnimIdentifier getEntityAnim(
                EntityActionInstance action) {
            if (action instanceof HostPressureAction pressure
                    && !pressure.isInternalPressure()
                    && action.getPerformer() instanceof StandEntity stand) {
                HumanoidArm side = pressure.getPunchingHand() == InteractionHand.MAIN_HAND
                        ? stand.getMainArm() : stand.getMainArm().getOpposite();
                return ActionAnimIdentifier.getOrCreate(
                        side == HumanoidArm.LEFT ? "punch2" : "punch", false);
            }
            return PUNCH_ANIM;
        }
    }

    public static final class HostPressureAction
            extends StandEntityPunchAbility.StandEntityPunch {
        public HostPressureAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            if (!isInternalPressure()) {
                super.onActionSet(previousAction);
            }
        }

        @Override
        public void actionTick() {
            if (!isInternalPressure()) {
                super.actionTick();
            }
        }

        @Override
        public void actionPerformStart() {
            if (!isInternalPressure()) {
                super.actionPerformStart();
                return;
            }
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)) {
                return;
            }
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            LivingEntity target = stand != null
                    ? stand.getPossessedTarget()
                    : null;
            if (target == null) {
                return;
            }
            forceSolid(ability, stand, owner);
            float damage =
                    StandStatFormulas.getLightAttackDamage(
                            stand.getAttackDamage());
            standEntityAttack(
                    stand,
                    target,
                    AquaNecklaceEntity.insideHostSource(
                            makePunchDamageSource()),
                    damage);
        }

        private boolean isInternalPressure() {
            return performer
                            instanceof AquaNecklaceEntity aqua
                    && aqua.isPossessing();
        }
    }

    public static final class BrainAttackAbility
            extends NoPoseStandEntityAbility {
        public BrainAttackAbility(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(
                    abilityType,
                    abilityId,
                    BrainAttackAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
            partsRequired(StandPart.MAIN_BODY);
            // 1.16 017 InitStands :117-118: no Resolve gate, the heavy punch's (Host Crush's) finisher variation, so it
            // and the finisher meter open at Resolve 0
            initIsFinisher(AquaNecklaceAddon.HOST_CRUSH_NAME);
            noFinisherBarDecay = true;
        }

        // initIsFinisher marks it a sub-ability; the H4 wheel keeps the Brain Attack in its own editable slot
        @Override
        public boolean addToControlSchemeEditing() {
            return true;
        }

        // 1.16 AquaNecklaceHeavyPunch.getFinisherVariationIfPresent: the heavy punch gave its key to the Brain Attack
        // only inside a host with a brain, whatever the finisher meter
        @Override
        public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
            if (abilities != null && abilities.getContextVariation(AquaNecklaceAddon.HOST_CRUSH_NAME) == this
                    && !insideHostWithBrain(context)) {
                Ability hostCrush = context.getAbility(AquaNecklaceAddon.HOST_CRUSH_NAME);
                if (hostCrush != null) {
                    abilities.replaceOtherAbilityWith(context, AquaNecklaceAddon.HOST_CRUSH_NAME, hostCrush);
                }
            }
            return this;
        }

        private static boolean insideHostWithBrain(Power<?> context) {
            AquaNecklaceEntity stand = context != null ? AquaNecklaceAddon.activeStand(context.getUser()) : null;
            return stand != null && stand.isPossessing() && AquaNecklaceEntity.hasBrain(stand.getPossessedTarget());
        }

        @Override
        public void initActionFromConfig(EntityActionInstance action, Level level,
                LivingEntity user, LivingEntity performer) {
            super.initActionFromConfig(action, level, user, performer);
            initInsideHeavyPhases(action, performer);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            ConditionCheck base =
                    super.checkSpecificConditions(context);
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            LivingEntity target = stand != null
                    ? stand.getPossessedTarget()
                    : null;
            if (!base.isPositive()
                    || !AquaNecklaceEntity.hasBrain(target)) {
                return ConditionCheck.NEGATIVE;
            }
            return ConditionCheck.POSITIVE;
        }
    }

    public static final class HostCrushAbility
            extends NoPoseStandEntityAbility {
        public HostCrushAbility(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(
                    abilityType,
                    abilityId,
                    HostCrushAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 10);
            partsRequired(StandPart.MAIN_BODY);
            noFinisherBarDecay = true;
        }

        @Override
        public void initActionFromConfig(EntityActionInstance action, Level level,
                LivingEntity user, LivingEntity performer) {
            super.initActionFromConfig(action, level, user, performer);
            initInsideHeavyPhases(action, performer);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            ConditionCheck base =
                    super.checkSpecificConditions(context);
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            if (!base.isPositive()
                    || stand == null
                    || !stand.isPossessing()) {
                return ConditionCheck.NEGATIVE;
            }
            return ConditionCheck.POSITIVE;
        }
    }

    private static void initInsideHeavyPhases(
            EntityActionInstance action, LivingEntity performer) {
        if (performer instanceof StandEntity stand) {
            action.phasesLength.put(ActionPhase.WINDUP,
                    StandStatFormulas.getHeavyAttackWindup(
                            stand.getAttackSpeed(), stand.getFinisherMeter()));
            action.phasesLength.put(ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(), stand.getFinisherMeter()));
        }
    }

    private abstract static class InsideHostHeavyAction extends EntityActionInstance {
        @Nullable protected LivingEntity host;
        private boolean prepared;

        protected InsideHostHeavyAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(@Nullable EntityActionInstance previousAction) {
            if (!(performer instanceof AquaNecklaceEntity stand)) {
                return;
            }
            stand.alternateHands();
            if (level().isClientSide()) {
                return;
            }
            stand.setHeavyPunchFinisher();
            phasesLength.put(ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(), stand.getLastHeavyFinisherValue()));
            stand.addFinisherMeter(-0.51F, 0);
            host = stand.getPossessedTarget();
            if (host != null && getPowerUser() instanceof ServerPlayer owner) {
                forceSolid(ability, stand, owner);
                keepStandAimedAtTarget(new ActionTarget(host));
                prepared = true;
            } else {
                startRecovery();
            }
        }

        protected boolean canHitHost() {
            return prepared && host != null && host.isAlive() && !host.isRemoved()
                    && performer instanceof AquaNecklaceEntity stand
                    && stand.getPossessedTarget() == host;
        }

        protected void payHeavyCost(AquaNecklaceEntity stand) {
            StandPower power = stand.getUserPower();
            if (power != null) {
                power.consumeStamina(INSIDE_HEAVY_COST);
            }
        }
    }

    public static final class HostCrushAction
            extends InsideHostHeavyAction {
        public HostCrushAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)) {
                return;
            }
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            LivingEntity target = stand != null
                    ? host
                    : null;
            if (target == null || !canHitHost()) {
                if (stand != null) {
                    payHeavyCost(stand);
                }
                return;
            }
            float damage =
                    StandStatFormulas.getHeavyAttackDamage(
                            stand.getAttackDamage());
            standEntityAttack(
                    stand,
                    target,
                    AquaNecklaceEntity.insideHostSource(
                            makePunchDamageSource()),
                    damage);
            payHeavyCost(stand);
        }
    }

    public static final class BrainAttackAction
            extends InsideHostHeavyAction {
        public BrainAttackAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)) {
                return;
            }
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            LivingEntity target = stand != null
                    ? host
                    : null;
            if (!AquaNecklaceEntity.hasBrain(target)
                    || !canHitHost()) {
                if (stand != null) {
                    payHeavyCost(stand);
                }
                return;
            }
            float damage =
                    StandStatFormulas.getHeavyAttackDamage(
                            stand.getAttackDamage() * 0.75D)
                            * 2.0F;
            boolean hurt = standEntityAttack(
                    stand,
                    target,
                    AquaNecklaceEntity.insideHostSource(
                            makePunchDamageSource()),
                    damage);
            payHeavyCost(stand);
            if (hurt && target.isAlive() && stand.getPossessedTarget() == target) {
                target.addEffect(new MobEffectInstance(
                        ModStatusEffects.STUN, 60));
            }
        }
    }

    public static final class FluidGuardAbility
            extends NoPoseStandEntityAbility {
        public FluidGuardAbility(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(
                    abilityType,
                    abilityId,
                    FluidGuardAction::new);
            setButtonHoldPhase(ActionPhase.PERFORM);
            setDefaultPhaseLength(ActionPhase.PERFORM, 72000);
            // 1.16 AquaNecklaceBlock (StandEntityBlock) set no
            // standRecoveryTicks
            setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
            partsRequired(StandPart.MAIN_BODY);
            // Owner rule of 2026-10-15: a Stand's own guard halves its
            // user's walk speed.
            standUserWalkSpeed(0.5F);
        }

        @Override
        protected ConditionCheck checkStandEntityConditions(
                StandPower power, StandEntity stand) {
            ConditionCheck base = super.checkStandEntityConditions(power, stand);
            return base.isPositive()
                            && (stand.canStartBlocking() || stand.isStandBlocking())
                    ? base : ConditionCheck.NEGATIVE;
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            ConditionCheck base =
                    super.checkSpecificConditions(context);
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            context.getUser());
            if (!base.isPositive() || stand == null
                    || !stand.canStartBlocking() && !stand.isStandBlocking()) {
                return ConditionCheck.NEGATIVE;
            }
            return ConditionCheck.POSITIVE;
        }
    }

    public static final class FluidGuardAction
            extends EntityActionInstance {
        public FluidGuardAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)) {
                return;
            }
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(owner);
            if (stand == null) {
                startRecovery();
                return;
            }
            forceSolid(ability, stand, owner);
            stand.setFluidGuarding(true);
        }

        @Override
        public void actionPerformEnd() {
            clearFluidGuard();
        }

        @Override
        public void onButtonStopHold() {
            clearFluidGuard();
            startRecovery();
        }

        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            clearFluidGuard();
        }

        private void clearFluidGuard() {
            AquaNecklaceEntity stand =
                    AquaNecklaceAddon.activeStand(
                            getPowerUser());
            if (!level().isClientSide() && stand != null) {
                stand.setFluidGuarding(false);
            }
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 AquaNecklaceBlock extended StandEntityBlock, a holdType Stand
        // action
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 StandEntityAction.canClickDuringTask :495-497: not chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }

    // 1.16 AquaNecklacePunch, AquaNecklaceHeavyPunch and AquaNecklaceBlock
    // onTaskSet: a non-solid Stand turns solid and pays 40 with the result
    // ignored.
    private static void forceSolid(
            EntityActionType ability,
            AquaNecklaceEntity stand,
            ServerPlayer owner) {
        if (stand.getMaterialState()
                == MaterialState.SOLID) {
            return;
        }
        stand.setMaterialState(MaterialState.SOLID);
        StandAbilityStamina.consume(
                ability,
                StandPower.get(owner),
                LIQUID_COST,
                false);
    }
}
