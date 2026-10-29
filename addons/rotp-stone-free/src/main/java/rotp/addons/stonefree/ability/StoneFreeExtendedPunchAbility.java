package rotp.addons.stonefree.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import net.minecraft.world.entity.LivingEntity;

public final class StoneFreeExtendedPunchAbility
        extends NoPoseStandEntityAbility {
    private static final ActionAnimIdentifier RANGED_ATTACK_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "ranged_attack", false);
    public static final float STAMINA_COST = 375.0F;
    public static final int PERFORM_TICKS = 10;

    public StoneFreeExtendedPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ExtendedPunchAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        // 1.16 stone_free_extended_punch set no standRecoveryTicks (InitStands:166)
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(10, 100);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return RANGED_ATTACK_ANIM;
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        StandPower power = PowerClass.STAND.cast(context);
        // 1.16 StoneFreeExtendedPunch.replaceAction: also while the Stand is
        // busy with another task, the user throws the pick at once.
        if (power != null
                && (power.getSummonedStandEntity() == null
                        || power.getSummonedStandEntity()
                                .isArmsOnlyMode()
                        || StoneFreeStringAbility.busyWithOtherTask(
                                power.getSummonedStandEntity(), this))
                && abilities != null) {
            Ability pick = abilities.getContextVariation(
                    StoneFreeAddon.USER_STRING_PICK_NAME);
            if (pick != null) {
                return pick;
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        StandPower power = PowerClass.STAND.cast(context);
        if (power != null
                && power.getSummonedStandEntity()
                        instanceof StoneFreeStandEntity stand
                && !stand.hasForearm()
                && !stand.hasShortForearm()) {
            return ConditionCheck.createNegative(
                    "stonefree_rightforearm");
        }
        return check;
    }

    // 1.16 StoneFreeExtendedPunch kept no target (StandEntityAction
    // standKeepsTarget false, TargetRequirement NONE): PowerBaseImpl.checkTarget
    // emptied the pressed one, so the Stand kept its user's look and the
    // forearm flew along it.
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return false;
    }

    public static final class ExtendedPunchAction
            extends EntityActionInstance {
        public ExtendedPunchAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (performer instanceof StoneFreeStandEntity stand) {
                keepStandAimedAtTarget(
                        captureActionTargetFromAim(stand));
            }
            aimAs = AimingEntity.STAND;
            // 1.16 stone_free_extended_punch standOffsetFront(): 0.5 in front
            // at the default y, turned by the view yaw; an arms-only Stone
            // Free threw the user's pick instead
            setStandOffset(
                    0.0D,
                    0.5D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(performer
                            instanceof StoneFreeStandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                startRecovery();
                return;
            }

            StoneFreeStringEntity string =
                    new StoneFreeStringEntity(
                            StoneFreeStringEntity.Kind
                                    .EXTENDED_PUNCH,
                            stand,
                            level());
            string.setShootingPosOf(stand);
            addProjectileWithStandStats(string);
            stand.setForearmState(false, true);
            stand.playSound(
                    StoneFreeAddon.STONE_FREE_STRING.get(),
                    1.0F,
                    1.0F);
            // 1.16 StandEntityTask paid after the forearm was out, with the
            // result ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }

        // 1.16 StoneFreeExtendedPunch.onTaskStopped gave the forearm back when
        // the Stand's task stopped, not when the punch was gone
        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            if (!level().isClientSide()
                    && performer instanceof StoneFreeStandEntity stand) {
                stand.setForearmState(true, false);
            }
        }
    }
}
