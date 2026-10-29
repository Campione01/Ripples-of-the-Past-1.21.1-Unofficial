package rotp.addons.spicegirl.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import rotp.addons.spicegirl.SpiceGirlAddon;
import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ElasticLeapAbility
        extends StandEntityAbility {
    public static final double UPWARD_IMPULSE = 1.7D;
    public static final int HOLD_TO_FIRE_TICKS = 20;
    public static final int COOLDOWN_TICKS = 175;

    public ElasticLeapAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ElasticLeapAction::new);
        // 1.16 SHIFT variation (or its finisher): shouts on sneak too
        setPlaysVoiceLineOnSneak();
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 spice_girl_bounce_up_her (067 InitStands:74) set no
        // standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
    }

    // 1.16 holdToFire(20, false) set the cooldown when the hold fired,
    // so a cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        return context != null
                && context.getUser() != null
                && context.getUser().onGround()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    public static final class ElasticLeapAction
            extends EntityActionInstance {
        public ElasticLeapAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 standOffsetFromUser(0.0, 2.0) is (left, forward): two blocks
        // ahead at the default height, full mode only, turned by the view yaw
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            setStandOffset(
                    0.0D,
                    2.0D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
        }

        // 1.16 holdToFire(20, false): letting go before the charge
        // completes cancels the leap.
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
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)
                    || !(getPerformer()
                            instanceof SpiceGirlStandEntity stand)
                    || SpiceGirlAddon.activeStand(owner) != stand
                    || !owner.onGround()) {
                return;
            }
            if (ability instanceof ElasticLeapAbility leap) {
                leap.setCooldownOnUse(StandPower.get(owner));
            }
            Vec3 movement = owner.getDeltaMovement();
            owner.setDeltaMovement(
                    movement.x,
                    UPWARD_IMPULSE,
                    movement.z);
            owner.hurtMarked = true;
            owner.fallDistance = 0.0F;
            StandUtil.playStandEntitySound(
                    stand,
                    SpiceGirlAddon.SG_ABILITY_1,
                    1.0F,
                    1.0F);
            // 1.16 .shout(SG_ABILITY2_VOICE) from the user
            sayShoutOf(ability, getPowerUser(), SpiceGirlAddon.SG_ABILITY2_VOICE);
        }
    }
}
