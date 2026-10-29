package rotp.addons.cmoon.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.entity.CMoonStandEntity;

public final class CMoonHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate("heavy_punch", false);

    public CMoonHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = CMoonHeavyPunch::new;
    }

    // 1.16 StandEntityHeavyAttack.replaceAction: with the meter at 0.5 the heavy punch is always
    // Heart Inversion, so when that is refused the plain punch does not stand in for it
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (context instanceof StandPower power
                && power.getSummonedStandEntity()
                        instanceof CMoonStandEntity stand
                && stand.willHeavyPunchBeFinisher()) {
            Ability finisher = power.getMoveset()
                    .getAbility(CMoonAddon.HEART_INVERSION);
            if (finisher != null
                    && finisher.isAbilityAvailable(context)) {
                return HeartInversionAbility
                        .checkFinisherConditions(context);
            }
        }
        return base;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }

    public static class CMoonHeavyPunch extends StandEntityHeavyPunch {
        private boolean passedPunchAdmission;
        private float additionalKnockbackStrength;

        public CMoonHeavyPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void addKnockback(DamageSource source) {
            // 1.16 applies additional heavy knockback separately, not inside the native damage event.
        }

        @Override
        protected void hitEntity(ActionTarget target, Level level, StandEntity stand,
                DamageSource source, float damage, float explosionRadius) {
            Entity entity = target.getMainEntity();
            boolean previousAdmission = passedPunchAdmission;
            float previousStrength = additionalKnockbackStrength;
            passedPunchAdmission = entity != null && damage > 0 && stand.canAttackEntity(entity)
                    && (!(entity instanceof LivingEntity living) || !StandEntity.isStandAttackInvulnerable(living));
            additionalKnockbackStrength = Math.max(getAdditionalHeavyPunchKnockback(stand), 0) * 0.5F;
            boolean replacesVelocity = passedPunchAdmission && !(entity instanceof LivingEntity)
                    && additionalKnockbackStrength > 0;
            CompoundTag impactBefore = replacesVelocity ? impactState(entity, level) : null;
            try {
                super.hitEntity(target, level, stand, source, damage, explosionRadius);
                if (replacesVelocity) {
                    float yaw = knockbackYaw(stand, entity);
                    entity.setDeltaMovement(new Vec3(0, 0, additionalKnockbackStrength)
                            .yRot(-yaw * MathUtil.DEG_TO_RAD));
                    // 1.16 HeavyPunchInstance.afterAttack arms a landed hit's wall impact after this push;
                    // the base heavy armed it inside the hit with the velocity from before the push
                    if (!impactState(entity, level).equals(impactBefore)) {
                        KnockbackCollisionImpact.getHandler(entity)
                                .onPunchSetKnockbackImpact(entity.getDeltaMovement(), stand)
                                .withImpactExplosion(Math.max(explosionRadius - 0.5F, 0), null, 0);
                    }
                }
            }
            finally {
                passedPunchAdmission = previousAdmission;
                additionalKnockbackStrength = previousStrength;
            }
        }

        @Override
        protected void afterHeavyPunchHit(StandEntity stand, LivingEntity target, DamageSource source,
                float damage, boolean hurt) {
            super.afterHeavyPunchHit(stand, target, source, damage, hurt);
            if (!passedPunchAdmission || damage <= 0 || additionalKnockbackStrength <= 0) {
                return;
            }
            // Both donor pulses precede the base heavy action's wall-impact capture.
            if (hurt) {
                knockbackLiving(stand, target, additionalKnockbackStrength);
                afterSuccessfulHeavyPunchHit(stand, target, source, damage, hurt);
            }
            knockbackLiving(stand, target, additionalKnockbackStrength);
        }

        protected void afterSuccessfulHeavyPunchHit(StandEntity stand, LivingEntity target, DamageSource source,
                float damage, boolean hurt) {}

        private static void knockbackLiving(StandEntity stand, LivingEntity target, float strength) {
            float yaw = knockbackYaw(stand, target) * MathUtil.DEG_TO_RAD;
            target.knockback(strength, Mth.sin(yaw), -Mth.cos(yaw));
        }

        private static CompoundTag impactState(Entity entity, Level level) {
            KnockbackCollisionImpact impact = KnockbackCollisionImpact.getExistingHandler(entity);
            return impact != null ? impact.serializeNBT(level.registryAccess()) : new CompoundTag();
        }

        private static float knockbackYaw(StandEntity stand, Entity target) {
            Vec3 delta = target.position().subtract(stand.position());
            return (float) -Mth.atan2(delta.x, delta.z) * MathUtil.RAD_TO_DEG;
        }
    }
}
