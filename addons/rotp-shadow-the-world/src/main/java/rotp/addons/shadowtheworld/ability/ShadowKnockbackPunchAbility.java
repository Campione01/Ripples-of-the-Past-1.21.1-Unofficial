package rotp.addons.shadowtheworld.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class ShadowKnockbackPunchAbility
        extends StandEntityHeavyPunchAbility {
    public ShadowKnockbackPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = ShadowKnockbackPunch::new;
    }

    public static final class ShadowKnockbackPunch
            extends StandEntityHeavyPunch {
        public ShadowKnockbackPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            super.actionPerformStart();
            ShadowAbilitySupport.unsummonIfUnstable(
                    getPowerUser());
        }

        @Override
        protected void addKnockback(DamageSource damageSource) {
            DamageSourceModified knockback =
                    (DamageSourceModified) damageSource;
            knockback.jojo_ripples$modifyKnockback(
                    0.75F + finisherValue, 1.0F);
            knockback.jojo_ripples$knockbackXRot(-30.0F);
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damage,
                float explosionRadius) {
            Entity entity = target.getMainEntity();
            if (entity instanceof StandEntity targetStand) {
                double range = Math.max(
                        stand.getEffectiveRange(), 1.0D);
                float proximity = 1.0F - Mth.clamp(
                        (float) (stand.distanceTo(targetStand)
                                / range),
                        0.0F,
                        1.0F);
                if (stand.getRandom().nextFloat()
                        < Math.max(
                                proximity - 0.25F, 0.0F)) {
                    targetStand.breakStandBlocking(
                            StandStatFormulas
                                    .getGuardBreakTicks(
                                            targetStand
                                                    .getDurability()));
                }
            }
            super.hitEntity(
                    target,
                    level,
                    stand,
                    damageSource,
                    damage,
                    explosionRadius);
        }
    }
}
