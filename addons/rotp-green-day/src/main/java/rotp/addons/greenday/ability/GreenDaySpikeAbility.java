package rotp.addons.greenday.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class GreenDaySpikeAbility
        extends StandEntityHeavyPunchAbility {
    // 1.16 GreenDaySpike: addKnockback(0.5 + atk / 16 * finisher)
    public static final float LEGACY_BASE_KNOCKBACK = 0.5F;
    public static final float LEGACY_FINISHER_KNOCKBACK_DIVISOR = 16.0F;
    // knockbackXRot(+60): positive pitch drives the target down
    public static final float LEGACY_SPIKE_ANGLE_DEGREES = 60.0F;
    // disableBlocking(1.0F)
    public static final float LEGACY_SHIELD_DISABLE_CHANCE = 1.0F;

    public GreenDaySpikeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = GreenDaySpike::new;
    }

    public static final class GreenDaySpike
            extends StandEntityHeavyPunch {
        public GreenDaySpike(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void addKnockback(
                DamageSource damageSource) {
            DamageSourceModified knockback =
                    (DamageSourceModified) damageSource;
            StandEntity stand = (StandEntity) performer;
            float spikeKnockback = LEGACY_BASE_KNOCKBACK
                    + (float) stand.getAttackDamage()
                            / LEGACY_FINISHER_KNOCKBACK_DIVISOR
                            * stand.getLastHeavyFinisherValue();
            knockback.jojo_ripples$modifyKnockback(
                    spikeKnockback,
                    1.0F);
            knockback.jojo_ripples$knockbackXRot(
                    LEGACY_SPIKE_ANGLE_DEGREES);
        }

        @Override
        protected void afterHeavyPunchHit(
                StandEntity stand,
                LivingEntity target,
                DamageSource damageSource,
                float damageAmount,
                boolean hurt) {
            super.afterHeavyPunchHit(
                    stand,
                    target,
                    damageSource,
                    damageAmount,
                    hurt);
            if (hurt && target instanceof StandEntity targetStand) {
                targetStand.breakStandBlocking(
                        StandStatFormulas.getGuardBreakTicks(
                                targetStand.getDurability()));
            }
            disableShieldOnHit(
                    target, hurt, LEGACY_SHIELD_DISABLE_CHANCE);
        }
    }
}
