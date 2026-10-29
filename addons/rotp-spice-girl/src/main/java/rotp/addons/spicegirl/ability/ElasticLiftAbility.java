package rotp.addons.spicegirl.ability;

import java.util.Comparator;
import java.util.List;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class ElasticLiftAbility
        extends StandEntityAbility {
    public static final double RANGE = 5.0D;
    public static final int MAX_TARGETS = 8;
    public static final double UPWARD_IMPULSE = 1.5D;
    public static final double MIN_FRONT_DOT = 0.886D;
    public static final int HOLD_TO_FIRE_TICKS = 20;
    public static final int COOLDOWN_TICKS = 200;

    public ElasticLiftAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ElasticLiftAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 spice_girl_bounce_up (067 InitStands:73) set no
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

    public static final class ElasticLiftAction
            extends EntityActionInstance {
        public ElasticLiftAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 standOffsetFront(): half a block ahead at the default
        // height, full mode only, turned by the view yaw
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            setStandOffset(
                    0.0D,
                    0.5D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
        }

        // 1.16 holdToFire(20, false): letting go before the charge
        // completes cancels the lift.
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
            if (ability instanceof ElasticLiftAbility lift) {
                lift.setCooldownOnUse(StandPower.get(owner));
            }
            StandUtil.playStandEntitySound(
                    stand,
                    SpiceGirlAddon.SG_ABILITY_1,
                    1.0F,
                    1.0F);
            // 1.16 .shout(SG_ABILITY2_VOICE) from the user
            sayShoutOf(ability, getPowerUser(), SpiceGirlAddon.SG_ABILITY2_VOICE);

            Vec3 look = owner.getLookAngle().normalize();
            List<Entity> targets = level().getEntities(
                    stand,
                    owner.getBoundingBox().inflate(RANGE),
                    entity -> entity != owner
                            && entity != stand
                            && entity.isAlive()
                            && !entity.isRemoved()
                            && entity.onGround()
                            && owner.distanceToSqr(entity)
                                    <= RANGE * RANGE
                            && frontDot(owner, look, entity)
                                    >= MIN_FRONT_DOT);
            targets.sort(
                    Comparator.comparingDouble(
                            owner::distanceToSqr));
            targets.stream()
                    .limit(MAX_TARGETS)
                    .forEach(target -> {
                        Vec3 movement = target.getDeltaMovement();
                        target.setDeltaMovement(
                                movement.x,
                                movement.y + UPWARD_IMPULSE,
                                movement.z);
                        target.hasImpulse = true;
                        target.fallDistance = 0.0F;
                        if (target
                                instanceof LivingEntity living) {
                            ((ServerLevel) level())
                                    .sendParticles(
                                            ParticleTypes.CLOUD,
                                            living.getX(),
                                            living.getY() + 0.1D,
                                            living.getZ(),
                                            8,
                                            0.25D,
                                            0.05D,
                                            0.25D,
                                            0.02D);
                        }
                    });
            ((ServerLevel) level()).sendParticles(
                    ParticleTypes.CLOUD,
                    owner.getX(),
                    owner.getY() + 0.1D,
                    owner.getZ(),
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }

        private static double frontDot(
                ServerPlayer owner,
                Vec3 look,
                Entity target) {
            Vec3 offset = target.position()
                    .subtract(owner.position());
            return offset.lengthSqr() > 1.0E-6D
                    ? offset.normalize().dot(look)
                    : 1.0D;
        }
    }
}
