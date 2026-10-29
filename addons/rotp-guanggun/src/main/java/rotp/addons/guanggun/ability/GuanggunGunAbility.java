package rotp.addons.guanggun.ability;

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

import rotp.addons.guanggun.GuanggunAddon;
import rotp.addons.guanggun.entity.GuanggunBulletEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class GuanggunGunAbility extends StandEntityAbility {
    public static final int BULLET_COUNT = 12;
    public static final float STAMINA_COST = 80.0F;
    public static final int WINDUP_TICKS = 6;
    public static final int PERFORM_TICKS = 20;
    public static final float BULLET_SPEED = 3.5F;
    public static final float BULLET_INACCURACY = 6.0F;
    public static final float DAMAGE_FACTOR = 8.0F;

    public GuanggunGunAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, GunAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.ARMS);
        resolveLevelToUnlock(1);
        standAutoSummonMode(AutoSummonMode.FULL);
    }

    public static final class GunAction extends EntityActionInstance {
        public GunAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }

            for (int i = 0; i < BULLET_COUNT; i++) {
                GuanggunBulletEntity bullet =
                        new GuanggunBulletEntity(stand, serverLevel);
                Vec3 offset = new Vec3(
                        (stand.getRandom().nextDouble() - 0.5D) * 0.8D,
                        (stand.getRandom().nextDouble() - 0.5D) * 1.2D
                                + stand.getBbHeight() * 0.5D,
                        (stand.getRandom().nextDouble() - 0.5D) * 0.8D);
                bullet.setPos(stand.position().add(offset));
                bullet.shootFromRotation(
                        stand, BULLET_SPEED, BULLET_INACCURACY);
                bullet.setDamageFactor(DAMAGE_FACTOR);
                serverLevel.addFreshEntity(bullet);
            }
            // 1.16 StandEntityTask paid staminaCost(80) after standPerform and ignored the result
            StandAbilityStamina.consume(ability, StandPower.get(user), STAMINA_COST, false);
        }
    }
}
