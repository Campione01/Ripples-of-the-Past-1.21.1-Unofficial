package rotp.addons.softandwet.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class SoftAndWetFinisherAbility
        extends StandEntityHeavyPunchAbility {
    public static final int STUN_DURATION_TICKS = 40;
    public static final int STUN_AMPLIFIER = 0;
    public static final float BASE_KNOCKBACK = 0.5F;
    public static final float KNOCKBACK_X_ROT = -10.0F;

    public SoftAndWetFinisherAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = SoftAndWetFinisher::new;
    }

    public static final class SoftAndWetFinisher
            extends StandEntityHeavyPunch {
        public SoftAndWetFinisher(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damage,
                float explosionRadius) {
            if (target.getMainEntity()
                    instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(
                        ModStatusEffects.STUN,
                        STUN_DURATION_TICKS,
                        STUN_AMPLIFIER,
                        false,
                        false,
                        false));
            }
            super.hitEntity(
                    target,
                    level,
                    stand,
                    damageSource,
                    damage,
                    explosionRadius);
        }

        @Override
        protected float getAdditionalHeavyPunchKnockback(
                StandEntity stand) {
            return BASE_KNOCKBACK + finisherValue;
        }

        @Override
        protected void addKnockback(DamageSource damageSource) {
            DamageSourceModified modified =
                    (DamageSourceModified) damageSource;
            modified.jojo_ripples$modifyKnockback(
                    getAdditionalHeavyPunchKnockback(
                            (StandEntity) performer),
                    1.0F);
            modified.jojo_ripples$knockbackXRot(
                    KNOCKBACK_X_ROT);
        }
    }
}
