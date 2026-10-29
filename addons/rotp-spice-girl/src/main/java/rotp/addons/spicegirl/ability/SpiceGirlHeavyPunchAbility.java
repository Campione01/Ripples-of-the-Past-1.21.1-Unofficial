package rotp.addons.spicegirl.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.spicegirl.SpiceGirlAddon;
import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import rotp.addons.spicegirl.state.SpiceGirlElasticService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class SpiceGirlHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    public static final float LEGACY_EXTRA_KNOCKBACK = 0.5F;
    public static final float LEGACY_UPPER_ANGLE_DEGREES = -10.0F;
    public static final int ELASTIC_DURATION_TICKS = 300;

    public SpiceGirlHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = SpiceGirlHeavyPunch::new;
        // 1.16 SHIFT variation (or its finisher): shouts on sneak too
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 spice_girl_heavy_punch .shout(SG_PUNCH_HEAVY_VOICE)
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, SpiceGirlAddon.SG_PUNCH_HEAVY_VOICE);
        }
    }

    public static class SpiceGirlHeavyPunch
            extends StandEntityHeavyPunch {
        public SpiceGirlHeavyPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void addKnockback(
                DamageSource damageSource) {
            if (damageSource
                    instanceof DamageSourceModified knockback
                    && performer instanceof StandEntity stand) {
                knockback.jojo_ripples$modifyKnockback(
                        getAdditionalHeavyPunchKnockback(stand)
                                + LEGACY_EXTRA_KNOCKBACK,
                        1.0F);
                knockback.jojo_ripples$knockbackXRot(
                        LEGACY_UPPER_ANGLE_DEGREES);
            }
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
            if (hurt
                    && !stand.level().isClientSide()
                    && stand instanceof SpiceGirlStandEntity spiceGirl
                    && stand.getUser()
                            instanceof ServerPlayer owner) {
                target.addEffect(
                        new MobEffectInstance(
                                MobEffects.LEVITATION,
                                50,
                                0,
                                false,
                                false,
                                false));
                SpiceGirlElasticService.apply(
                        owner,
                        spiceGirl,
                        target,
                        elasticDurationTicks(),
                        elasticAmplifier());
            }
        }

        protected int elasticDurationTicks() {
            return ELASTIC_DURATION_TICKS;
        }

        protected int elasticAmplifier() {
            return 0;
        }
    }
}
