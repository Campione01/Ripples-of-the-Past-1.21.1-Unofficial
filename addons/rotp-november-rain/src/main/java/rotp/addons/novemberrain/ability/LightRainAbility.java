package rotp.addons.novemberrain.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class LightRainAbility extends StandEntityAbility {
    public static final double RAIN_RADIUS = 5.0D;
    public static final float DAMAGE = 1.5F;
    public static final float STAMINA_COST = 2.0F;
    public static final int RECOVERY_TICKS = 20;
    public static final int PARTICLES_PER_TICK = 20;

    public LightRainAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, LightRainAction::new);
        partsRequired(StandPart.MAIN_BODY);
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
    }

    public static final class LightRainAction extends EntityActionInstance {
        private boolean staminaPaid;

        public LightRainAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }

            Level level = level();
            if (level.isClientSide()) {
                spawnRainParticles(level, stand);
                return;
            }

            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }

            if (!staminaPaid) {
                // 1.16 StandEntityTask paid staminaCost(2) once at perform start and ignored the result
                StandAbilityStamina.consume(
                        ability, StandPower.get(user), STAMINA_COST, false);
                staminaPaid = true;
            }

            AABB rainArea = new AABB(
                    stand.getX() - RAIN_RADIUS,
                    stand.getY() - 1.0D,
                    stand.getZ() - RAIN_RADIUS,
                    stand.getX() + RAIN_RADIUS,
                    stand.getY() + 4.0D,
                    stand.getZ() + RAIN_RADIUS);

            for (Entity entity : level.getEntities(
                    stand, rainArea, entity -> entity != user)) {
                entity.hurt(level.damageSources().drown(), DAMAGE);
            }
        }

        private static void spawnRainParticles(Level level, StandEntity stand) {
            for (int i = 0; i < PARTICLES_PER_TICK; i++) {
                double x = stand.getX()
                        + (stand.getRandom().nextDouble() - 0.5D)
                        * RAIN_RADIUS * 2.0D;
                double y = stand.getY() + 3.0D;
                double z = stand.getZ()
                        + (stand.getRandom().nextDouble() - 0.5D)
                        * RAIN_RADIUS * 2.0D;
                level.addParticle(ParticleTypes.RAIN, x, y, z, 0.0D, -0.5D, 0.0D);
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() != ActionPhase.RECOVERY) {
                startRecovery();
            }
        }

        // 1.16 light_rain was a holdType Stand action; its standRecoveryTicks(20)
        // still queues a pressed Stand action (the default canBeCancelledInto)
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }
    }
}
