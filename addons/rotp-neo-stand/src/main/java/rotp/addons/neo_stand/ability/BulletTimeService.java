package rotp.addons.neo_stand.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.addons.neo_stand.NeoStandAddon;
import rotp.addons.neo_stand.network.BulletTimeSyncPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BulletTimeService {
    public static final int DURATION_TICKS = 60;
    public static final int RADIUS_BLOCKS = 12 * 16;
    public static final double DILATION_FACTOR = 0.25D;

    private static final Map<UUID, ActiveField> ACTIVE =
            new HashMap<>();
    private static volatile Predicate<UUID> clientOwnerActive =
            owner -> false;

    private BulletTimeService() {}

    public static void activate(ServerPlayer user) {
        ServerLevel level = user.serverLevel();
        ACTIVE.put(
                user.getUUID(),
                new ActiveField(
                        level.dimension(),
                        user.blockPosition(),
                        DURATION_TICKS));
        applyMarker(user, DURATION_TICKS);
        StandPower power = StandPower.get(user);
        StandEntity stand =
                power != null ? power.getSummonedStandEntity() : null;
        if (stand != null) {
            applyMarker(stand, DURATION_TICKS);
        }
        broadcast(
                level,
                user.getUUID(),
                ACTIVE.get(user.getUUID()),
                DURATION_TICKS,
                true);
    }

    public static void cancel(LivingEntity user) {
        ActiveField field = ACTIVE.remove(user.getUUID());
        user.removeEffect(NeoStandAddon.BULLET_TIME_EFFECT);
        StandPower power = StandPower.get(user);
        StandEntity stand =
                power != null ? power.getSummonedStandEntity() : null;
        if (stand != null) {
            stand.removeEffect(NeoStandAddon.BULLET_TIME_EFFECT);
        }
        MinecraftServer server = user.level().getServer();
        ServerLevel level = field != null && server != null
                ? server.getLevel(field.dimension())
                : null;
        if (field != null && level != null) {
            broadcast(
                    level,
                    user.getUUID(),
                    field,
                    0,
                    false);
        }
    }

    public static boolean isActive(LivingEntity user) {
        if (user == null) {
            return false;
        }
        ActiveField field = ACTIVE.get(user.getUUID());
        return field != null
                && field.dimension().equals(user.level().dimension())
                && field.remainingTicks() > 0;
    }

    public static void installClientOwnerActiveLookup(
            Predicate<UUID> lookup) {
        clientOwnerActive =
                Objects.requireNonNull(lookup, "lookup");
    }

    public static boolean isClientOwnerActive(UUID owner) {
        return owner != null && clientOwnerActive.test(owner);
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveField>> iterator =
                ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveField> entry = iterator.next();
            ActiveField field = entry.getValue();
            ServerLevel level = server.getLevel(field.dimension());
            ServerPlayer user = server.getPlayerList()
                    .getPlayer(entry.getKey());
            if (level == null
                    || user == null
                    || !user.isAlive()
                    || user.isRemoved()
                    || !field.dimension().equals(
                            user.level().dimension())
                    || field.remainingTicks() <= 0) {
                iterator.remove();
                if (user != null) {
                    clearMarker(user);
                }
                if (level != null) {
                    broadcast(
                            level,
                            entry.getKey(),
                            field,
                            0,
                            false);
                }
                continue;
            }
            boolean suspended = level
                    .getData(ModDataAttachmentTypes.TIME_STOP.get())
                    .isTimeStopped(
                            new ChunkPos(field.center()));
            if (suspended) {
                if (!field.suspended()) {
                    field.setSuspended(true);
                    clearMarker(user);
                    broadcast(
                            level,
                            entry.getKey(),
                            field,
                            field.remainingTicks(),
                            true);
                }
                continue;
            }
            if (field.suspended()) {
                field.setSuspended(false);
                applyMarker(user, field.remainingTicks());
                StandPower power = StandPower.get(user);
                StandEntity stand = power != null
                        ? power.getSummonedStandEntity()
                        : null;
                if (stand != null) {
                    applyMarker(stand, field.remainingTicks());
                }
                broadcast(
                        level,
                        entry.getKey(),
                        field,
                        field.remainingTicks(),
                        true);
            }
            field.tick();
            if (field.remainingTicks() <= 0) {
                iterator.remove();
                clearMarker(user);
                broadcast(
                        level,
                        entry.getKey(),
                        field,
                        0,
                        false);
            }
        }
    }

    public static void syncTo(ServerPlayer observer) {
        for (Map.Entry<UUID, ActiveField> entry : ACTIVE.entrySet()) {
            ActiveField field = entry.getValue();
            if (!field.dimension().equals(
                            observer.level().dimension())) {
                continue;
            }
            int remaining = field.remainingTicks();
            if (remaining > 0) {
                sendToObserver(
                        observer,
                        payload(
                                entry.getKey(),
                                field,
                                remaining,
                                true));
            }
        }
    }

    public static void tickEntity(Entity entity) {
        if (entity.level().isClientSide()
                || entity.getDeltaMovement().lengthSqr() < 1.0E-8D
                || isExempt(entity)) {
            return;
        }
        long gameTime = entity.level().getGameTime();
        if (gameTime % 3L != 0L) {
            return;
        }
        double radiusSqr =
                (double) RADIUS_BLOCKS * RADIUS_BLOCKS;
        for (Map.Entry<UUID, ActiveField> entry : ACTIVE.entrySet()) {
            ActiveField field = entry.getValue();
            if (!field.dimension().equals(entity.level().dimension())
                    || field.remainingTicks() <= 0
                    || field.suspended()
                    || entity.distanceToSqr(
                            field.center().getX() + 0.5D,
                            entity.getY(),
                            field.center().getZ() + 0.5D)
                            > radiusSqr) {
                continue;
            }
            entity.setDeltaMovement(
                    entity.getDeltaMovement().scale(DILATION_FACTOR));
            entity.hurtMarked = true;
            return;
        }
    }

    private static boolean isExempt(Entity entity) {
        if (entity instanceof LivingEntity living
                && living.hasEffect(
                        NeoStandAddon.BULLET_TIME_EFFECT)) {
            return true;
        }
        if (entity instanceof StandEntity stand) {
            LivingEntity user = stand.getUser();
            return user != null && isActive(user);
        }
        return ACTIVE.containsKey(entity.getUUID());
    }

    private static void applyMarker(
            LivingEntity entity, int durationTicks) {
        entity.addEffect(new MobEffectInstance(
                NeoStandAddon.BULLET_TIME_EFFECT,
                Math.max(durationTicks, 1),
                0,
                false,
                true,
                true));
    }

    private static void clearMarker(ServerPlayer user) {
        user.removeEffect(NeoStandAddon.BULLET_TIME_EFFECT);
        StandPower power = StandPower.get(user);
        StandEntity stand =
                power != null ? power.getSummonedStandEntity() : null;
        if (stand != null) {
            stand.removeEffect(NeoStandAddon.BULLET_TIME_EFFECT);
        }
    }

    private static void broadcast(
            ServerLevel level,
            UUID owner,
            ActiveField field,
            int remainingTicks,
            boolean active) {
        BulletTimeSyncPayload payload = payload(
                owner,
                field,
                remainingTicks,
                active);
        for (ServerPlayer observer : level.players()) {
            sendToObserver(observer, payload);
        }
    }

    private static void sendToObserver(
            ServerPlayer observer,
            BulletTimeSyncPayload payload) {
        if (observer.connection != null
                && observer.connection.hasChannel(
                        BulletTimeSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(observer, payload);
        }
    }

    private static BulletTimeSyncPayload payload(
            UUID owner,
            ActiveField field,
            int remainingTicks,
            boolean active) {
        return new BulletTimeSyncPayload(
                owner,
                field.dimension().location(),
                field.center(),
                RADIUS_BLOCKS,
                remainingTicks,
                (float) DILATION_FACTOR,
                active);
    }

    private static final class ActiveField {
        private final ResourceKey<Level> dimension;
        private final BlockPos center;
        private int remainingTicks;
        private boolean suspended;

        private ActiveField(
                ResourceKey<Level> dimension,
                BlockPos center,
                int remainingTicks) {
            this.dimension = dimension;
            this.center = center;
            this.remainingTicks = remainingTicks;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private BlockPos center() {
            return center;
        }

        private int remainingTicks() {
            return remainingTicks;
        }

        private boolean suspended() {
            return suspended;
        }

        private void setSuspended(boolean suspended) {
            this.suspended = suspended;
        }

        private void tick() {
            remainingTicks--;
        }
    }
}
