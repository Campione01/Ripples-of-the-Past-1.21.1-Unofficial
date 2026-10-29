package rotp.addons.spicegirl.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.spicegirl.SpiceGirlAddon;
import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import rotp.addons.spicegirl.state.SpiceGirlSoftAreaService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class SoftenAreaAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST_PER_TICK = 3.0F;
    public static final double RAYCAST_RANGE = 10.0D;
    public static final int HOLD_TO_FIRE_TICKS = 20;
    public static final int COOLDOWN_TICKS = 100;

    public SoftenAreaAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, SoftenAreaAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 spice_girl_soft_block (067 InitStands:69) set no
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

    public static final class SoftenAreaAction
            extends EntityActionInstance {
        public SoftenAreaAction(EntityActionType ability) {
            super(ability);
        }

        private boolean shoutSaid;

        // 1.16 .shout(SG_ABILITY1_VOICE): a user line said when the hold
        // starts, under the sneak rule (soft_block is no SHIFT variation).
        // The flag keeps a restored hold from saying it twice.
        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            // 1.16 standOffsetFront(): half a block ahead at the default
            // height, full mode only, turned by the view yaw
            setStandOffset(
                    0.0D,
                    0.5D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
            if (!shoutSaid && !level().isClientSide()
                    && getPowerUser() != null) {
                shoutSaid = true;
                sayShoutOf(ability, getPowerUser(), SpiceGirlAddon.SG_ABILITY1_VOICE);
            }
        }

        // 0.2.2.2 took staminaCostTick only in PERFORM, and soft_block
        // performs for one tick, so the charge itself costs nothing.
        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.BUTTON_CHARGE
                    || level().isClientSide()) {
                return;
            }
            ServerPlayer owner =
                    getPowerUser() instanceof ServerPlayer player
                            ? player
                            : null;
            if (owner == null
                    || SpiceGirlAddon.activeStand(owner)
                            != getPerformer()) {
                forceStop();
            }
        }

        // 1.16 holdToFire(20, false): letting go before the charge
        // completes cancels the softening.
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
                    || SpiceGirlAddon.activeStand(owner) != stand) {
                return;
            }
            StandPower power = StandPower.get(owner);
            // 1.16 StandEntityTask paid the tick cost with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    STAMINA_COST_PER_TICK,
                    true);
            if (ability instanceof SoftenAreaAbility area) {
                area.setCooldownOnUse(power);
            }
            HitResult hit = owner.pick(
                    RAYCAST_RANGE, 0.0F, false);
            if (!(hit instanceof BlockHitResult blockHit)
                    || hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            // 1.16 soft_block had no Stand sound; its line is the user
            // shout said when the hold started.
            SpiceGirlSoftAreaService.mark(
                    owner,
                    stand,
                    blockHit.getBlockPos());
        }
    }
}
