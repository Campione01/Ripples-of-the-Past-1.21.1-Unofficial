package rotp.addons.cmoon.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CMoonPunchAbility
        extends StandEntityPunchAbility {
    public static final float CANONICAL_DAMAGE = 1.0F;
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate("punch", false);

    public CMoonPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CMoonPunch::new);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        if (action instanceof StandEntityPunch punch
                && action.getPerformer() instanceof StandEntity stand) {
            HumanoidArm side = punch.getPunchingHand() == InteractionHand.MAIN_HAND
                    ? stand.getMainArm() : stand.getMainArm().getOpposite();
            return ActionAnimIdentifier.getOrCreate(
                    side == HumanoidArm.LEFT ? "punch2" : "punch", false);
        }
        return ANIMATION;
    }

    public static final class CMoonPunch
            extends StandEntityPunch {
        public CMoonPunch(EntityActionType ability) {
            super(ability);
        }

        private boolean samplesLaterEntry(ActionPhase newPhase) {
            return phase != null && phase != newPhase
                    && (newPhase == ActionPhase.PERFORM || newPhase == ActionPhase.RECOVERY)
                    && performer instanceof StandEntity && !performer.level().isClientSide()
                    && getClass() == CMoonPunch.class && !isGrabVariation();
        }

        @Override
        public void setPhase(ActionPhase newPhase, int tick) {
            boolean samples = samplesLaterEntry(newPhase);
            float previousLength = samples ? phasesLength.getFloat(newPhase) : 0;
            super.setPhase(newPhase, tick);
            if (samples && Float.compare(previousLength, phasesLength.getFloat(newPhase)) != 0) {
                syncPhaseChanges();
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (samplesLaterEntry(newPhase) && performer instanceof StandEntity stand) {
                int recovery = StandStatFormulas.getLightAttackRecovery(
                        stand.getAttackSpeed(), stand.getFinisherMeter());
                phasesLength.put(newPhase, newPhase == ActionPhase.RECOVERY
                        ? recovery * (stand.isArmsOnlyMode() ? 2 : 4) : recovery);
            }
        }

        @Override
        protected void hitEntity(
                ActionTarget target, Level level, StandEntity stand) {
            Entity targetEntity = target.getMainEntity();
            if (targetEntity == null) {
                return;
            }
            DamageSource source = makePunchDamageSource();
            if (source instanceof DamageSourceModified modified) {
                modified.jojo_ripples$modifyKnockback(
                        0.0F, 1.0F);
            }
            float halfCounter = Math.max(stand.guardCounter(), 0.0F) * 0.5F;
            boolean admitted = CANONICAL_DAMAGE > 0 && stand.canAttackEntity(targetEntity)
                    && (!(targetEntity instanceof LivingEntity living)
                            || !StandEntity.isStandAttackInvulnerable(living));
            boolean hurt = standEntityAttack(
                    stand,
                    targetEntity,
                    source,
                    CANONICAL_DAMAGE);
            impactSoundDue = hurt || !(targetEntity instanceof LivingEntity);
            if (admitted) {
                if (hurt && targetEntity instanceof LivingEntity) {
                    counterKnockback(stand, targetEntity, halfCounter);
                }
                counterKnockback(stand, targetEntity, halfCounter);
            }
            stand.addFinisherMeter(0.1F);
        }

        private static void counterKnockback(
                StandEntity stand, Entity target, float strength) {
            if (strength <= 0) {
                return;
            }
            Vec3 delta = target.position().subtract(stand.position());
            float yaw = (float) -Mth.atan2(delta.x, delta.z) * Mth.RAD_TO_DEG;
            float radians = yaw * Mth.DEG_TO_RAD;
            if (target instanceof LivingEntity living) {
                living.knockback(strength, Mth.sin(radians), -Mth.cos(radians));
            }
            else {
                target.setDeltaMovement(new Vec3(0, 0, strength).yRot(-radians));
            }
        }
    }
}
