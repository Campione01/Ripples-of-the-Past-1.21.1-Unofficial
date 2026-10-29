package rotp.addons.madeinheaven.ability;

import rotp.core.customobjects.AfterimageEntity;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.state.TimeAccelerationService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LightSpeedDashAbility
        extends StandEntityAbility {
    // 1.16 holdType(5): the longest the dash can be held.
    public static final int PERFORM_TICKS = 5;
    public static final int COOLDOWN_TICKS = 50;
    // 1.16 cooldown(50, 50): Resolve drops this second half.
    public static final int ADDITIONAL_COOLDOWN_TICKS = 50;
    // 1.16 staminaCost(150), while checkConditions only asks for 50.
    public static final float STAMINA_COST = 150.0F;
    public static final float CONDITION_STAMINA = 50.0F;
    public static final int AFTERIMAGE_LIFESPAN = 5;
    public static final double AFTERIMAGE_DAMAGE_RADIUS = 2.0D;

    public LightSpeedDashAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                LightSpeedDashAction::new);
        // 1.16 skipped this by accident (LightSpeedDash.checkConditions
        // replaced the check); the owner kept the port's rule (2026-10-18).
        partsRequired(StandPart.LEGS);
        resolveLevelToUnlock(1);
        standAutoSummonMode(AutoSummonMode.OFF_ARM);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(
                ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(COOLDOWN_TICKS, ADDITIONAL_COOLDOWN_TICKS);
    }

    // 1.16 mih_dash was holdType(5): the whole dash is the hold, so a
    // release from any bind ends it.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.PERFORM
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until that hold ends.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    // 1.16 started a hold-only action's cooldown when the hold stopped,
    // not when the key went down (see LightSpeedDashAction.onActionCleared).
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public HeldInput onKeyPress(
            Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime,
            BufferingState bufferingState) {
        if (!level.isClientSide()) {
            StandPower power = PowerClass.STAND.get(user);
            if (power == null) {
                return null;
            }
            ConditionCheck acceleration = checkAcceleration(user);
            if (!acceleration.isPositive()) {
                ConditionCheck.sendActionFailedMessage(
                        this, acceleration, user);
                return null;
            }
            // 1.16 LightSpeedDash refused too little stamina with a plain
            // NEGATIVE, which shows no message.
            if (!meetsDashStaminaFloor(power)) {
                return null;
            }
        }
        return super.onKeyPress(level, user, extraClientInput,
                inputMethod, clickHoldResolveTime, bufferingState);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base =
                super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context.getUser();
        StandPower power = StandPower.get(user);
        ConditionCheck acceleration = checkAcceleration(user);
        if (!acceleration.isPositive()) {
            return acceleration;
        }
        return ConditionCheck.noMessage(meetsDashStaminaFloor(power));
    }

    // 1.16 LightSpeedDash.checkConditions: only a failed
    // TimeUtil.checkConditions came with the message; a phase below
    // GIVE_BUFFS was a plain NEGATIVE.
    private static ConditionCheck checkAcceleration(LivingEntity user) {
        if (!ownsAcceleration(user)) {
            return ConditionCheck.createNegative(
                    "cant_use_without_acceleration");
        }
        return ConditionCheck.noMessage(
                TimeAccelerationService.phase(user.level())
                        >= TimeAccelerationService.BUFF_PHASE);
    }

    private static boolean ownsAcceleration(LivingEntity user) {
        // 1.16 TimeUtil.checkConditions read the rule on both sides; the
        // client level mirrors it from the sign of each time packet's day time.
        return TimeAccelerationService.isActiveOwner(user)
                && (user.level().isClientSide()
                        ? user.level().getGameRules().getBoolean(
                                GameRules.RULE_DAYLIGHT)
                        : user.getServer() != null
                                && user.getServer().overworld()
                                        .getGameRules().getBoolean(
                                                GameRules.RULE_DAYLIGHT));
    }

    // 1.16 PowerBaseImpl.tickHeldAction asked checkConditions again on
    // every held tick, and a refusal ended the hold. Losing the
    // acceleration came with its message; too little stamina did not
    // (see canKeepDashing).
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkAcceleration(context.getUser());
    }

    private static boolean canKeepDashing(LivingEntity user) {
        return checkAcceleration(user).isPositive()
                && meetsDashStaminaFloor(StandPower.get(user));
    }

    private static boolean meetsDashStaminaFloor(StandPower power) {
        return power != null
                && power.getStamina() >= CONDITION_STAMINA;
    }

    public static final class LightSpeedDashAction
            extends EntityActionInstance {
        private boolean afterimageSpawned;
        private boolean staminaTaken;

        public LightSpeedDashAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 onTaskSet: the user's afterimage appears with the press,
        // however long the hold then lasts.
        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            LivingEntity user = getPowerUser();
            if (afterimageSpawned
                    || user == null
                    || level().isClientSide()) {
                return;
            }
            afterimageSpawned = true;
            level().addFreshEntity(
                    new DashAfterimage(level(), user));
        }

        @Override
        public void actionPerformStart() {
            if (!(getPerformer() instanceof StandEntity stand)
                    || getPowerUser() == null) {
                forceStop();
                return;
            }
            LivingEntity user = getPowerUser();
            if (!level().isClientSide()) {
                AfterimageEntity.addAfterimages(stand, 1, 5);
                level().playSound(
                        null,
                        stand.getX(),
                        stand.getY(),
                        stand.getZ(),
                        MadeInHeavenAddon.MIH_DASH.get(),
                        stand.getSoundSource(),
                        1.0F,
                        1.0F);
                level().playSound(
                        null,
                        user.getX(),
                        user.getY(),
                        user.getZ(),
                        MadeInHeavenAddon.MIH_DASH_USER.get(),
                        user.getSoundSource(),
                        1.0F,
                        1.0F);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()
                    || getPowerUser() == null) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (!staminaTaken) {
                staminaTaken = true;
                // 1.16 StandEntityTask took the cost on the first perform
                // tick and went on even when it could only empty the bar.
                StandAbilityStamina.consume(
                        ability,
                        StandPower.get(user),
                        STAMINA_COST,
                        false);
            }
            int multiplier =
                    TimeAccelerationService.calculatedMultiplier(
                            TimeAccelerationService.phase(
                                    user.level()));
            // 1.16 LightSpeedDash: field_76429_m = Resistance,
            // field_206827_D = Dolphin's Grace.
            user.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    10,
                    14,
                    false,
                    false));
            user.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    10,
                    multiplier * 10,
                    false,
                    false));
            user.addEffect(new MobEffectInstance(
                    MobEffects.DOLPHINS_GRACE,
                    10,
                    14,
                    false,
                    false));
            // The held check came after the Stand's tick, so a dash that
            // can no longer go on ends in the tick that paid for it.
            if (!canKeepDashing(user)) {
                stopAndSynchronize();
            }
        }

        // 1.16 stoppedHolding: letting go of the key ends the dash.
        @Override
        public void onButtonStopHold() {
            stopAndSynchronize();
        }

        // 1.16 StandEntityAction.isCancelable: a hold-only task with no
        // recovery gave way to any other Stand action at once. isOfAbility,
        // not identity: a client's action holds the base-moveset instance of
        // its ability, not its user's own.
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return !isOfAbility(cancellingAbility);
        }

        // 1.16 mih_dash was a holdType(5) Stand action
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        @Override
        public void actionPerformEnd() {
            removeEffects();
        }

        // However the dash ends (release, 5 ticks, a failed check), the
        // cooldown starts then, as 1.16 stopHeldAction did.
        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            removeEffects();
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (power != null
                    && !level().isClientSide()
                    && ability instanceof LightSpeedDashAbility dash) {
                dash.setCooldownOnUse(power);
            }
        }

        private void stopAndSynchronize() {
            removeEffects();
            forceStop();
            if (!level().isClientSide()) {
                syncPhaseChanges();
            }
        }

        private void removeEffects() {
            LivingEntity user = getPowerUser();
            if (user != null && !user.level().isClientSide()) {
                user.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                user.removeEffect(MobEffects.MOVEMENT_SPEED);
                user.removeEffect(MobEffects.DOLPHINS_GRACE);
            }
        }
    }

    // 1.16 LightSpeedDash.onTaskSet: an afterimage of the user that hurts
    // every other living entity around it on each of its ticks, the one
    // that removes it included.
    private static final class DashAfterimage
            extends AfterimageEntity {
        private final LivingEntity user;

        private DashAfterimage(Level level, LivingEntity user) {
            super(level, user, 1);
            this.user = user;
            setLifeSpan(AFTERIMAGE_LIFESPAN);
        }

        @Override
        public void tick() {
            super.tick();
            if (level().isClientSide()) {
                return;
            }
            Vec3 center = getBoundingBox().getCenter();
            AABB around = new AABB(
                    center.subtract(
                            AFTERIMAGE_DAMAGE_RADIUS,
                            AFTERIMAGE_DAMAGE_RADIUS,
                            AFTERIMAGE_DAMAGE_RADIUS),
                    center.add(
                            AFTERIMAGE_DAMAGE_RADIUS,
                            AFTERIMAGE_DAMAGE_RADIUS,
                            AFTERIMAGE_DAMAGE_RADIUS));
            for (LivingEntity target :
                    level().getEntitiesOfClass(
                            LivingEntity.class,
                            around,
                            entity -> entity != user
                                    && entity.isAlive())) {
                // 1.16: DamageSource.field_76377_j = GENERIC.
                target.hurt(
                        level().damageSources().generic(), 1.0F);
            }
        }
    }
}
