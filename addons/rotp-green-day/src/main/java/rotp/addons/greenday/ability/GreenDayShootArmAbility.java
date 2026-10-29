package rotp.addons.greenday.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.greenday.GreenDayAddon;
import rotp.addons.greenday.entity.ShootingArmEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GreenDayShootArmAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 150.0F;
    public static final int COOLDOWN_TICKS = 60;
    public static final float SPEED = 1.5F;
    public static final float INACCURACY = 0.3F;
    public static final int WINDUP_TICKS = 4;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 6;

    private final boolean right;

    public GreenDayShootArmAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            boolean right) {
        super(abilityType, abilityId, ShootArmAction::new);
        this.right = right;
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        // 1.16 gd_shot_*_arm had no autoSummonStand and StandAction.onClick changed no Stand: an arms-only Green
        // Day stays arms; with the Stand away onKeyPress has the user shoot.
        standAutoSummonMode(AutoSummonMode.DISABLED);
        // 1.16 cooldown(60, 0, 0.66666f): all 60 ticks are technical, so Resolve
        // never shortened them and the multiplier had nothing to act on.
        cooldown(COOLDOWN_TICKS, 0, 0.66666F);
        resolveLevelToUnlock(3);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        LivingEntity owner = context != null
                ? context.getUser()
                : null;
        if (!right
                && owner != null
                && (owner.isShiftKeyDown()
                        || owner.hasEffect(
                                GreenDayAddon.LEFT_ARMLESS))) {
            return abilities.getContextVariationOrDisable(
                    name(), "shot_right_arm");
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        if (!(context instanceof StandPower standPower)
                || standPower.getPowerType()
                        != GreenDayAddon.GREEN_DAY.get()) {
            return ConditionCheck.createNegative(
                    "green_day_not_active");
        }
        LivingEntity owner = standPower.getUser();
        if (owner == null
                || owner.hasEffect(
                        right
                                ? GreenDayAddon.RIGHT_ARMLESS
                                : GreenDayAddon.LEFT_ARMLESS)) {
            return ConditionCheck.createNegative(
                    right
                            ? "right_arm_already_shot"
                            : "left_arm_already_shot");
        }
        return parent;
    }

    @Override
    public boolean retractsStandAfterAction(
            StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        return false;
    }

    /**
     * 1.16 gd_shot_left_arm / gd_shot_right_arm were StandActions, held by the user's Stand power and never a Stand
     * task: the shot goes into the user's Stand-power slot, performed by the Stand while it is out, so it goes off at
     * once beside the Stand's punch, a held hold-type barrage ends into its recovery, and a held Stand charge (1.16
     * refused the shot) keeps the press in the input buffer (owner rule N2).
     */
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 summoned nothing for the shot (no autoSummonStand): with the Stand away the user shoots it; the core's
    // press would refuse it under AutoSummonMode.DISABLED.
    @Override
    public HeldInput onKeyPress(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod,
            float clickHoldResolveTime,
            BufferingState bufferingState) {
        if (!level.isClientSide()
                && StandUtil.getSummonedStand(user) == null) {
            return setOrBufferAction(
                    level, user, user, inputMethod, extraClientInput,
                    clickHoldResolveTime, bufferingState);
        }
        return super.onKeyPress(
                level, user, extraClientInput, inputMethod,
                clickHoldResolveTime, bufferingState);
    }

    // Without the Stand there is no clip to play: 1.16's StandAction took no time.
    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (!(performer instanceof StandEntity)) {
            action.phasesLength.put(ActionPhase.WINDUP, 0.0F);
            action.phasesLength.put(ActionPhase.PERFORM, 1.0F);
            action.phasesLength.put(ActionPhase.RECOVERY, 0.0F);
        }
    }

    /**
     * 1.16 gd_shot_left_arm / gd_shot_right_arm (072 InitStands :68-69) were StandActions: the press shot the arm
     * (GreenDayShotLeftArm.perform :48-62) and started no Stand task. The arm leaves as this action starts, at the
     * press; its phases only play the Stand's shot clip (feedback B2), drawn while the Stand runs no task of its own.
     */
    public static final class ShootArmAction
            extends EntityActionInstance {
        public ShootArmAction(EntityActionType ability) {
            super(ability);
        }

        // isOfAbility, not identity: a client's action holds the
        // base-moveset instance of its ability, not its user's own
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return !isOfAbility(cancellingAbility);
        }

        // 1.16 drew a Stand's pose from its task alone; the shot clip is the port's (B2), so a task the Stand
        // starts during it is drawn instead.
        @Override
        public boolean posesItsStand() {
            if (!(getPerformer() instanceof StandEntity stand)) {
                return false;
            }
            EntityActionInstance task =
                    LivingComponentAction.getCurEntityAction(stand);
            return task == null || task.isOver();
        }

        @Override
        public void onActionSet(
                EntityActionInstance prevAction) {
            if (!(level() instanceof ServerLevel serverLevel)
                    || isRestoringInputLifecycle()
                    || !(ability
                            instanceof GreenDayShootArmAbility config)) {
                return;
            }
            LivingEntity owner = getPowerUser();
            StandPower standPower =
                    owner != null ? StandPower.get(owner) : null;
            boolean right = config.right;
            if (owner == null || standPower == null) {
                return;
            }
            // 1.16 StandAction.onPerform paid staminaCost(150) before perform
            // and ignored the result; a short bar just drops to 0
            StandAbilityStamina.consume(
                    ability, standPower, STAMINA_COST, false);
            if (standPower.getPowerType()
                            != GreenDayAddon.GREEN_DAY.get()
                    || owner.hasEffect(
                            right
                                    ? GreenDayAddon.RIGHT_ARMLESS
                                    : GreenDayAddon.LEFT_ARMLESS)) {
                return;
            }

            ShootingArmEntity arm =
                    GreenDayAddon.SHOOTING_ARM.get()
                            .create(serverLevel);
            if (arm == null) {
                return;
            }
            arm.setOwner(owner);
            arm.setRight(right);
            Vec3 start = owner.getEyePosition()
                    .add(0.0D, -0.3D, 0.0D);
            arm.setPos(start.x, start.y, start.z);
            arm.shootFromRotation(owner, SPEED, INACCURACY);
            serverLevel.addFreshEntity(arm);
        }
    }
}
