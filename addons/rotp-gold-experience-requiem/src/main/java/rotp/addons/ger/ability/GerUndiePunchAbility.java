package rotp.addons.ger.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class GerUndiePunchAbility
        extends StandEntityHeavyPunchAbility {
    public static final float LEGACY_STAMINA_COST = 100.0F;
    public static final int LEGACY_COOLDOWN_TICKS = 80;
    public static final int LEGACY_PERFORM_TICKS = 10;
    public static final int LEGACY_RECOVERY_TICKS = 10;

    public GerUndiePunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = GerUndiePunch::new;
        setDefaultPhaseLength(ActionPhase.WINDUP, 10);
        setDefaultPhaseLength(
                ActionPhase.PERFORM, LEGACY_PERFORM_TICKS);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY, LEGACY_RECOVERY_TICKS);
        cooldown(LEGACY_COOLDOWN_TICKS);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return GerActionAnimations.resolve(
                getAbilityId(), super.getEntityAnim(action));
    }

    public static final class GerUndiePunch
            extends StandEntityHeavyPunch {
        public GerUndiePunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            super.actionPerformStart();
            if (!level().isClientSide()) {
                StandPower power = StandPower.get(getPowerUser());
                if (power != null) {
                    // The shared heavy action consumes 50; this restores the
                    // canonical total cost of 100.
                    power.consumeStamina(50.0F);
                }
            }
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damageAmount,
                float explosionRadius) {
            // 1.16 UndiePunch marked the target before the hit and kept the
            // mark when the target survived it.
            if (target.getMainEntity() instanceof LivingEntity living
                    && stand.getUser() instanceof LivingEntity owner
                    && living.getHealth() <= damageAmount
                    && JojoModUtil.canHarm(owner, living)) {
                GoldExperienceRequiemAddon.livingState(living)
                        .armUndie(owner.getUUID());
            }

            super.hitEntity(
                    target,
                    level,
                    stand,
                    damageSource,
                    damageAmount,
                    explosionRadius);
        }
    }
}
