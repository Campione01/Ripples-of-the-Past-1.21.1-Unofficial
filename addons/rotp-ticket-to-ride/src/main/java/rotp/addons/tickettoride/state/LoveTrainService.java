package rotp.addons.tickettoride.state;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.tickettoride.TicketToRideAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LoveTrainService {
    public static final int DURATION_TICKS = 12_000;
    public static final int SHARED_DURATION_TICKS = 120_000;
    public static final float INITIAL_RADIUS = 0.3F;
    public static final float MAX_RADIUS = 27.0F;
    public static final float RADIUS_PER_TICK = 0.001F;

    private LoveTrainService() {}

    public static boolean activate(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)
                || isActive(user)) {
            return false;
        }
        long now = user.level().getGameTime();
        TicketToRideAddon.loveTrainState(user).activate(
                now, now + DURATION_TICKS);
        // 1.16 LoveTrainAction.standPerform: no effect particles, the icon shown
        user.addEffect(new MobEffectInstance(
                TicketToRideAddon.LOVE_TRAIN,
                DURATION_TICKS,
                0,
                false,
                false,
                true));
        level.playSound(
                null,
                user.getX(),
                user.getY(),
                user.getZ(),
                SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS,
                1.0F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        activationParticles(user.position(), level.getRandom(), sink(level));
        return true;
    }

    public static boolean deactivate(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) {
            return false;
        }
        boolean changed = user.removeEffect(
                TicketToRideAddon.LOVE_TRAIN);
        if (user.hasData(TicketToRideAddon.LOVE_TRAIN_STATE)) {
            changed |= TicketToRideAddon.loveTrainState(user).clear();
        }
        if (changed) {
            level.playSound(
                    null,
                    user.getX(),
                    user.getY(),
                    user.getZ(),
                    SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS,
                    1.0F,
                    0.8F + level.getRandom().nextFloat() * 0.4F);
            dispelParticles(user.position(), sink(level));
        }
        return changed;
    }

    public static boolean share(
            LivingEntity user, LivingEntity target) {
        if (user.level().isClientSide()
                || target == user) {
            return false;
        }
        target.addEffect(new MobEffectInstance(
                TicketToRideAddon.SHARED_LOVE_TRAIN,
                SHARED_DURATION_TICKS,
                0,
                false,
                false,
                true));
        return true;
    }

    public static boolean removeShared(LivingEntity target) {
        return target.removeEffect(
                TicketToRideAddon.SHARED_LOVE_TRAIN);
    }

    public static boolean isActive(LivingEntity entity) {
        if (!entity.hasEffect(TicketToRideAddon.LOVE_TRAIN)) {
            return false;
        }
        if (entity.level().isClientSide()) {
            return true;
        }
        return entity.hasData(TicketToRideAddon.LOVE_TRAIN_STATE)
                && TicketToRideAddon.loveTrainState(entity)
                        .active(entity.level().getGameTime());
    }

    public static float radius(LivingEntity user) {
        LoveTrainState state =
                TicketToRideAddon.loveTrainState(user);
        long elapsed = Math.max(
                0L,
                user.level().getGameTime()
                        - state.startedAt());
        return Mth.clamp(
                INITIAL_RADIUS + elapsed * RADIUS_PER_TICK,
                INITIAL_RADIUS,
                MAX_RADIUS);
    }

    public static float visualRadius(LivingEntity user) {
        if (user.hasData(TicketToRideAddon.LOVE_TRAIN_STATE)
                && TicketToRideAddon.loveTrainState(user)
                        .startedAt() > 0L) {
            return radius(user);
        }
        MobEffectInstance effect =
                user.getEffect(TicketToRideAddon.LOVE_TRAIN);
        int elapsed = effect != null
                ? Math.max(
                        0, DURATION_TICKS - effect.getDuration())
                : 0;
        return Mth.clamp(
                INITIAL_RADIUS + elapsed * RADIUS_PER_TICK,
                INITIAL_RADIUS,
                MAX_RADIUS);
    }

    public static void tick(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)
                || !user.hasData(
                        TicketToRideAddon.LOVE_TRAIN_STATE)) {
            return;
        }
        LoveTrainState state =
                TicketToRideAddon.loveTrainState(user);
        if (!isActive(user)) {
            if (state.clear()) {
                user.removeEffect(TicketToRideAddon.LOVE_TRAIN);
            }
            return;
        }

        clearHarmfulEffects(user);
        if (user.tickCount % 5 != 0) {
            return;
        }
        float radius = radius(user);
        clearFluids(level, user, radius);
        repel(level, user, radius);
        persistentParticles(user.position(), radius,
                user.tickCount % 15 == 0, level.getRandom(), sink(level));
    }

    public static boolean isProtected(LivingEntity target) {
        return protector(target) != null;
    }

    @Nullable
    public static LivingEntity protector(LivingEntity target) {
        if (isActive(target)) {
            return target;
        }
        if (!target.hasEffect(
                TicketToRideAddon.SHARED_LOVE_TRAIN)) {
            return null;
        }
        double search = MAX_RADIUS * 1.2D;
        for (LivingEntity train : target.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        target.getBoundingBox().inflate(search),
                        LoveTrainService::isActive)) {
            double allowed = radius(train) * 1.2D;
            if (train.distanceToSqr(target)
                    <= allowed * allowed) {
                return train;
            }
        }
        return null;
    }

    private static void clearHarmfulEffects(
            LivingEntity user) {
        List<MobEffectInstance> harmful = new ArrayList<>();
        for (MobEffectInstance effect
                : user.getActiveEffects()) {
            if (effect.getEffect().value().getCategory()
                    == MobEffectCategory.HARMFUL) {
                harmful.add(effect);
            }
        }
        harmful.forEach(effect ->
                user.removeEffect(effect.getEffect()));
    }

    // 1.16 LoveTrainEventHandler.clearPartingSea: along the full look vector
    // through a point 1.5 above the feet, six columns across, water and lava
    // blocks only (a waterlogged block or a water plant stays).
    private static void clearFluids(
            ServerLevel level,
            LivingEntity user,
            float radius) {
        Vec3 look = user.getLookAngle();
        Vec3 center = user.position().add(0.0D, 1.5D, 0.0D);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        int playerY = (int) user.getY();
        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos();
        for (double distance = -radius;
                distance <= radius;
                distance += 1.0D) {
            Vec3 linePos = center.add(look.scale(distance));
            for (double width = -2.5D; width <= 2.5D; width += 1.0D) {
                Vec3 clearPos = linePos.add(right.scale(width));
                for (int y = playerY - 7; y <= playerY + 13; y++) {
                    cursor.set(
                            Mth.floor(clearPos.x),
                            y,
                            Mth.floor(clearPos.z));
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Blocks.WATER)
                            || state.is(Blocks.LAVA)) {
                        level.setBlockAndUpdate(
                                cursor,
                                Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    // 1.16 LoveTrainEventHandler.repelEntities: a living entity inside the
    // repel radius is put back onto its edge and sent outwards; Stands and
    // Love Train holders or passengers stay where they are.
    private static void repel(
            ServerLevel level,
            LivingEntity user,
            float radius) {
        double repelRadius = Math.min(
                3.0D + radius * 1.2D, MAX_RADIUS);
        AABB area = user.getBoundingBox().inflate(repelRadius);
        for (Entity entity : level.getEntities(
                user,
                area,
                entity -> !(entity instanceof StandEntity)
                        && !(entity instanceof ItemEntity)
                        && !(entity instanceof Projectile)
                        && !(entity instanceof PrimedTnt))) {
            if (!(entity instanceof LivingEntity target)
                    || target.hasEffect(TicketToRideAddon.LOVE_TRAIN)
                    || target.hasEffect(
                            TicketToRideAddon.SHARED_LOVE_TRAIN)) {
                continue;
            }
            double dx = target.getX() - user.getX();
            double dz = target.getZ() - user.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (!(distance < repelRadius) || !(distance > 0.0D)) {
                continue;
            }
            double push = repelRadius + 0.1D - distance;
            dx /= distance;
            dz /= distance;
            target.setPos(
                    target.getX() + dx * push,
                    target.getY(),
                    target.getZ() + dz * push);
            target.setDeltaMovement(
                    dx * 0.5D,
                    target.getDeltaMovement().y,
                    dz * 0.5D);
            target.hurtMarked = true;
        }
    }

    // 1.16 LoveTrainEventHandler.LoveTrainData: the column count grows to the
    // whole metres of the ring, from 3 up to 50.
    public static int columns(float radius) {
        return Mth.clamp(
                Mth.floor((float) (Math.PI * 2.0D * radius)), 3, 50);
    }

    /** Where a server particle burst goes, as ServerLevel.sendParticles takes it. */
    @FunctionalInterface
    public interface ParticleSink {
        void send(ParticleOptions type, double x, double y, double z,
                int count, double dx, double dy, double dz, double speed);
    }

    /** Where a client particle goes, as Level.addParticle takes it. */
    @FunctionalInterface
    public interface ClientParticleSink {
        void add(ParticleOptions type, double x, double y, double z,
                double vx, double vy, double vz);
    }

    private static ParticleSink sink(ServerLevel level) {
        return (type, x, y, z, count, dx, dy, dz, speed) ->
                level.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
    }

    // 1.16 LoveTrainAction.spawnFullActivationEffect: eight pillars of light
    // 1 block out, light spread on the ground, a wall of light 4 blocks out.
    public static void activationParticles(
            Vec3 center, RandomSource random, ParticleSink sink) {
        SimpleParticleType pillar =
                TicketToRideAddon.LOVE_TRAIN_LIGHT_PILLAR.get();
        SimpleParticleType shortPillar =
                TicketToRideAddon.LOVE_TRAIN_LIGHT_SHORT.get();
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            double x = center.x + Math.cos(angle);
            double z = center.z + Math.sin(angle);
            for (double y = 0.0D; y <= 3.0D; y += 0.2D) {
                sink.send(pillar, x, center.y + y, z,
                        1, 0.0D, 0.05D, 0.0D, (double) 0.3F);
            }
            if (random.nextFloat() < 0.3D) {
                sink.send(shortPillar,
                        x + (random.nextDouble() - 0.5D) * 0.3D,
                        center.y + 1.5D,
                        z + (random.nextDouble() - 0.5D) * 0.3D,
                        1, 0.0D, 0.03D, 0.0D, 0.25D);
            }
        }
        for (int layer = 0; layer < 2; layer++) {
            double radius = 1.0D + layer * 2.0D;
            int particles = 30 + layer * 20;
            for (int i = 0; i < particles; i++) {
                double angle = Math.toRadians(random.nextDouble() * 360.0D);
                double distance = random.nextDouble() * radius;
                sink.send(pillar,
                        center.x + Math.cos(angle) * distance,
                        center.y + 0.1D,
                        center.z + Math.sin(angle) * distance,
                        1, Math.cos(angle) * 0.05D, 0.02D,
                        Math.sin(angle) * 0.05D, (double) 0.4F);
            }
        }
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30);
            for (double y = 0.0D; y <= 2.0D; y += 0.3D) {
                sink.send(pillar,
                        center.x + Math.cos(angle) * 4.0D,
                        center.y + y,
                        center.z + Math.sin(angle) * 4.0D,
                        2, 0.0D, 0.1D, 0.0D, (double) 0.35F);
            }
        }
    }

    // 1.16 LoveTrainEventHandler.spawnFullParticleSystem, every 5 ticks: the
    // ring of pillars a block below the feet, four decorative walls at 0.6 r,
    // an inner ring at 1.3 r every 15 ticks, short pillars on the ring
    // (80%); the energy field was empty.
    public static void persistentParticles(
            Vec3 center,
            float radius,
            boolean innerRing,
            RandomSource random,
            ParticleSink sink) {
        SimpleParticleType pillar =
                TicketToRideAddon.LOVE_TRAIN_LIGHT_PILLAR.get();
        int columns = columns(radius);
        double minY = center.y - 1.0D;
        double maxY = center.y;
        for (int i = 0; i < columns; i += 2) {
            double angle = Math.PI * 2.0D * i / columns;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            for (double y = minY; y <= maxY; y += 2.3D) {
                sink.send(pillar, x, y, z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        double wallRadius = radius * 0.6D;
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * 2.0D * i / 4.0D;
            double x = center.x + Math.cos(angle) * wallRadius;
            double z = center.z + Math.sin(angle) * wallRadius;
            for (double y = minY; y <= maxY; y += 3.0D) {
                if (random.nextFloat() < 0.4F) {
                    sink.send(pillar, x, y, z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
        if (innerRing) {
            double innerRadius = radius * 1.3D;
            for (int i = 0; i < 3; i++) {
                double angle = Math.PI * 2.0D * i / 3.0D;
                double x = center.x + Math.cos(angle) * innerRadius;
                double z = center.z + Math.sin(angle) * innerRadius;
                for (double y = minY; y <= maxY; y += 3.0D) {
                    sink.send(pillar, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
        if (random.nextFloat() < 0.8F) {
            SimpleParticleType shortPillar =
                    TicketToRideAddon.LOVE_TRAIN_LIGHT_SHORT.get();
            for (int i = 0; i < columns; i += 2) {
                double angle = Math.PI * 2.0D * i / columns;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                double spacing =
                        2.2D * (0.8D + random.nextDouble() * 0.4D);
                for (double y = minY; y <= maxY; y += spacing) {
                    double xOffset = (random.nextDouble() - 0.5D) * 0.55D * 2.0D;
                    double zOffset = (random.nextDouble() - 0.5D) * 0.55D * 2.0D;
                    sink.send(shortPillar, x + xOffset, y, z + zOffset,
                            3, 0.1D, 0.1D, 0.1D, 0.0D);
                }
            }
        }
    }

    // 1.16 DispelLoveTrainAction.playDispelEffects
    public static void dispelParticles(Vec3 at, ParticleSink sink) {
        sink.send(TicketToRideAddon.LOVE_TRAIN_LIGHT.get(),
                at.x, at.y + 1.0D, at.z, 50, 0.5D, 0.5D, 0.5D, 0.2D);
    }

    // 1.16 LoveTrainAction.onHoldTickClientEffect, on the clients while the
    // charge is held: a spiral of light every 3 ticks, sparks every 5.
    public static void holdParticles(
            Vec3 feet,
            int ticksHeld,
            RandomSource random,
            ClientParticleSink sink) {
        SimpleParticleType light =
                TicketToRideAddon.LOVE_TRAIN_LIGHT.get();
        if (ticksHeld % 3 == 0) {
            double progress = ticksHeld / 40.0D;
            double radius = 0.8D + progress * 0.5D;
            for (int i = 0; i < 4; i++) {
                double angle = Math.toRadians(ticksHeld * 5 + i * 90);
                double verticalSpeed = 0.05D * (1.0D + progress * 0.5D);
                sink.add(light,
                        feet.x + Math.cos(angle) * radius,
                        feet.y,
                        feet.z + Math.sin(angle) * radius,
                        0.0D, verticalSpeed, 0.0D);
            }
        }
        if (ticksHeld % 5 == 0) {
            Vec3 center = feet.add(0.0D, 1.0D, 0.0D);
            for (int i = 0; i < 3; i++) {
                double angle = Math.toRadians(random.nextFloat() * 360.0F);
                double radius = random.nextDouble() * 1.5D;
                Vec3 pos = center.add(Math.cos(angle) * radius,
                        random.nextDouble() * 2.0D,
                        Math.sin(angle) * radius);
                if (random.nextBoolean()) {
                    sink.add(light, pos.x, pos.y, pos.z,
                            Math.cos(angle) * 0.1D, 0.05D, Math.sin(angle) * 0.1D);
                }
                else {
                    sink.add(TicketToRideAddon.LOVE_TRAIN_LIGHT_SHORT.get(),
                            pos.x, pos.y, pos.z,
                            Math.cos(angle) * 0.15D, 0.1D, Math.sin(angle) * 0.15D);
                }
            }
        }
    }

    public static LivingEntity resolveLiving(Entity entity) {
        return entity instanceof LivingEntity living
                ? living
                : null;
    }
}
