package rotp.addons.neo_stand.ability;

import java.util.Comparator;

import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModItems;
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

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CircleKnivesAbility
        extends StandEntityAbility {
    public static final int KNIFE_COUNT = 60;
    public static final float SPHERE_RADIUS = 6.0F;
    public static final float STAMINA_COST = 1500.0F;
    public static final int MAX_LIFETIME = 200;

    public CircleKnivesAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(abilityType, abilityId, CircleKnivesAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 20);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 10);
        cooldown(500, 1000);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
    }

    public static final class CircleKnivesAction
            extends EntityActionInstance {
        public CircleKnivesAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPerformer()
                            instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                forceStop();
                return;
            }

            LivingEntity target = nearestConeTarget(user);
            Vec3 targetPoint = target != null
                    ? target.position().add(
                            0.0D,
                            target.getBbHeight() * 0.5D,
                            0.0D)
                    : user.position().add(
                            user.getLookAngle().scale(10.0D));
            RandomSource random = serverLevel.getRandom();
            for (int index = 0; index < KNIFE_COUNT; index++) {
                Vec3 spawn = targetPoint.add(
                        randomSphereOffset(
                                random, SPHERE_RADIUS));
                DelayedKnife knife = new DelayedKnife(
                        serverLevel,
                        user,
                        spawn,
                        targetPoint,
                        5 + random.nextInt(5));
                serverLevel.addFreshEntity(knife);
            }
            // 1.16 StandEntityTask: the cost is paid after the knives are
            // out; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, StandPower.get(user), STAMINA_COST, false);
        }

        private LivingEntity nearestConeTarget(
                LivingEntity user) {
            Vec3 look = user.getLookAngle().normalize();
            AABB search = user.getBoundingBox().inflate(15.0D);
            double minimumDot =
                    Math.cos(Math.toRadians(12.5D));
            return level().getEntitiesOfClass(
                            LivingEntity.class,
                            search,
                            candidate -> candidate != user
                                    && candidate.isAlive()
                                    && !candidate.isAlliedTo(user)
                                    && candidate.position()
                                            .subtract(user.position())
                                            .normalize()
                                            .dot(look)
                                            >= minimumDot)
                    .stream()
                    .min(Comparator.comparingDouble(
                            user::distanceToSqr))
                    .orElse(null);
        }

        private static Vec3 randomSphereOffset(
                RandomSource random,
                double radius) {
            double azimuth =
                    Math.PI * 2.0D * random.nextDouble();
            double polar = Math.acos(
                    2.0D * random.nextDouble() - 1.0D);
            double sin = Math.sin(polar);
            return new Vec3(
                    radius * sin * Math.cos(azimuth),
                    radius * sin * Math.sin(azimuth),
                    radius * Math.cos(polar));
        }
    }

    private static final class DelayedKnife
            extends KnifeEntity {
        private final Vec3 target;
        private int delay;
        private int age;
        private boolean released;

        private DelayedKnife(
                ServerLevel level,
                LivingEntity owner,
                Vec3 spawn,
                Vec3 target,
                int delay) {
            super(
                    level,
                    owner,
                    new ItemStack(ModItems.KNIFE.get()));
            this.target = target;
            this.delay = delay;
            pickup = AbstractArrow.Pickup.DISALLOWED;
            noPhysics = true;
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
            setPos(spawn);
        }

        @Override
        public void tick() {
            super.tick();
            age++;
            if (age >= MAX_LIFETIME) {
                discard();
                return;
            }
            if (released) {
                return;
            }
            if (delay-- > 0) {
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            released = true;
            noPhysics = false;
            setNoGravity(false);
            Vec3 direction = target.subtract(position()).normalize();
            shoot(
                    direction.x,
                    direction.y,
                    direction.z,
                    1.5F,
                    0.5F);
        }
    }
}
