package rotp.addons.weatherreport.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class WeatherReportFinisherAbility
        extends StandEntityHeavyPunchAbility {
    public static final float LEGACY_EXTRA_KNOCKBACK = 0.5F;
    public static final int FIRE_SECONDS = 10;

    public WeatherReportFinisherAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = WeatherReportFinisher::new;
    }

    public static final class WeatherReportFinisher
            extends StandEntityHeavyPunch {
        public WeatherReportFinisher(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected float getAdditionalHeavyPunchKnockback(
                StandEntity stand) {
            return super.getAdditionalHeavyPunchKnockback(stand)
                    + LEGACY_EXTRA_KNOCKBACK
                    + finisherValue;
        }

        @Override
        protected void afterHeavyPunchHit(
                StandEntity stand,
                LivingEntity target,
                DamageSource damageSource,
                float damageAmount,
                boolean hurt) {
            super.afterHeavyPunchHit(
                    stand, target, damageSource, damageAmount, hurt);
            target.igniteForSeconds(FIRE_SECONDS);

            if (target instanceof StandEntity targetStand
                    && targetStand.isStandBlocking()) {
                float range = Math.max(
                        (float) stand.getEffectiveRange(), 0.001F);
                float proximity = 1.0F
                        - Mth.clamp(stand.distanceTo(targetStand) / range,
                                0.0F, 1.0F);
                float breakChance = Math.max(proximity - 0.25F, 0.0F);
                if (stand.getRandom().nextFloat() < breakChance) {
                    targetStand.breakStandBlocking(
                            StandStatFormulas.getGuardBreakTicks(
                                    targetStand.getDurability()));
                }
            }
        }

        @Override
        protected void addKnockback(DamageSource damageSource) {
            super.addKnockback(damageSource);
            ((DamageSourceModified) damageSource)
                    .jojo_ripples$setStandInvulTicks(10);
        }
    }
}
