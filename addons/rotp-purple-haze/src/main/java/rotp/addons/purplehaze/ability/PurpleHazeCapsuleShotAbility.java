package rotp.addons.purplehaze.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.entity.CapsuleProjectileEntity;
import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class PurpleHazeCapsuleShotAbility
        extends StandEntityAbility {
    // 1.16 standEntity.shootProjectile(capsule, 1.0F, 0.25F).
    static final float CAPSULE_INACCURACY = 0.25F;

    private Holder<SoundEvent> userShoutSound;

    public PurpleHazeCapsuleShotAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CapsuleShotAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        // 1.16 PurpleHazeCapsuleShot is holdToFire(10, false).
        setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 10);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // no standRecoveryTicks in 1.16: nothing holds the next press back
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        standAutoSummonMode(AutoSummonMode.FULL);
    }

    // 1.16 set the cooldown when the hold fired, so a cancelled hold
    // leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    public PurpleHazeCapsuleShotAbility userShoutSound(
            Holder<SoundEvent> sound) {
        userShoutSound = sound;
        return this;
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        ConditionCheck parent = super.checkStandEntityConditions(
                standPower, standEntity);
        if (!parent.isPositive()) {
            return parent;
        }
        if (!(standEntity
                instanceof PurpleHazeEntity purpleHaze)) {
            return ConditionCheck.NEGATIVE;
        }
        if (purpleHaze.isMad()) {
            return ConditionCheck.createNegative(
                    "cant_control_stand");
        }
        return purpleHaze.getCapsuleCount() > 0
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "no_capsules_to_shot");
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide()
                && standUser != null
                && userShoutSound != null) {
            sayShout(standUser, userShoutSound);
        }
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate(
                action.getPerformer()
                                instanceof PurpleHazeEntity stand
                        && stand.getCapsuleCount() > 3
                                ? "capsule_shot_left"
                                : "capsule_shot_right",
                false);
    }

    @Override
    public boolean retractsStandAfterAction(
            StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        return false;
    }

    public static final class CapsuleShotAction
            extends EntityActionInstance {
        public CapsuleShotAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdToFire(10, false): letting go before the charge
        // completes cancels the shot.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof PurpleHazeEntity stand)) {
                return;
            }
            LivingEntity owner = getPowerUser();
            if (owner == null || !stand.useCapsule()) {
                return;
            }
            if (ability instanceof PurpleHazeCapsuleShotAbility shot) {
                shot.setCooldownOnUse(StandPower.get(owner));
            }

            CapsuleProjectileEntity capsule =
                    new CapsuleProjectileEntity(
                            owner, level());
            capsule.setShootingPosOf(stand);
            // 1.16 standEntity.shootProjectile(capsule, 1.0F, 0.25F)
            capsule.shootFromRotation(
                    stand,
                    1.0F,
                    stand.getProjectileInaccuracy(CAPSULE_INACCURACY));
            addProjectileWithStandStats(capsule);
            StandUtil.playStandEntitySound(
                    stand,
                    PurpleHazeAddon.CAPSULE_SHOT,
                    1.0F,
                    1.0F);
        }
    }
}
