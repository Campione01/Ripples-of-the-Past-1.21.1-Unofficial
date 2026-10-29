package rotp.addons.weatherreport.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.state.FuguStormTerminalData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WeatherReportFugaSummonerEntity extends Entity {
    public static final int LIFETIME_TICKS = 2400;
    public static final int RANDOM_SPAWNS_PER_TICK = 4;
    public static final int TARGET_ROLLS_PER_TICK = 2;
    public static final double TARGET_ROLL_MIN = 0.2D;
    public static final double TARGET_ROLL_MAX = 0.21D;
    public static final double HORIZONTAL_RANGE = 256.0D;
    public static final double VERTICAL_RANDOM_RANGE = 32.0D;
    public static final double FALL_SPEED = -1.5D;
    public static final int MAX_ACTIVE_PROJECTILES = 256;
    public static final int MAX_ACTIVE_PUFFERFISH = 64;
    public static final int MAX_ACTIVE_CLOUDS = 64;
    public static final int MAX_TARGET_QUERY_RESULTS = 1;

    public static final String SUMMONED_BY_RAIN_TAG =
            "SummonedByRain";
    public static final String DEPENDENT_COORDINATOR_TAG =
            "FuguRainCoordinator";
    public static final String DEPENDENT_OWNER_TAG =
            "FuguRainOwner";
    public static final String FUGU_CLOUD_TAG =
            "FuguRainCloud";
    public static final String DEPENDENT_REMAINING_TAG =
            "FuguRainRemaining";
    public static final String DEPENDENT_DEADLINE_TAG =
            "FuguRainDeadline";

    private static final String OWNER_TAG = "Owner";
    private static final String END_GAME_TIME_TAG = "EndGameTime";
    private static final String STAND_TAG = "Stand";
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    WeatherReportFugaSummonerEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> STAND_UUID =
            SynchedEntityData.defineId(
                    WeatherReportFugaSummonerEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    private final Map<UUID, Long> activeProjectiles = new HashMap<>();
    private final Map<UUID, Long> activePufferfish = new HashMap<>();
    private final Map<UUID, Long> activeClouds = new HashMap<>();
    private boolean cleaningDependents;
    // The rain ends with game time, so an unloaded storm cannot resume after its two minutes.
    private long endGameTime;

    public WeatherReportFugaSummonerEntity(
            EntityType<? extends WeatherReportFugaSummonerEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        endGameTime = level.getGameTime() + LIFETIME_TICKS;
    }

    public WeatherReportFugaSummonerEntity(
            LivingEntity owner,
            WeatherReportStandEntity stand,
            Level level) {
        this(WeatherReportAddon.FUGA_SUMMONER.get(), level);
        entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        entityData.set(STAND_UUID, Optional.of(stand.getUUID()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        pruneDependents(serverLevel);
        if (serverLevel.getGameTime() >= endGameTime
                || !serverLevel.isRaining()
                || FuguStormTerminalData.get(serverLevel)
                        .isTerminal(getUUID(), serverLevel.getGameTime())) {
            discard();
            return;
        }

        for (int i = 0;
                i < RANDOM_SPAWNS_PER_TICK
                        && activeProjectiles.size()
                                < MAX_ACTIVE_PROJECTILES;
                i++) {
            spawnRandomProjectile(serverLevel);
        }
        for (int i = 0;
                i < TARGET_ROLLS_PER_TICK
                        && activeProjectiles.size()
                                < MAX_ACTIVE_PROJECTILES;
                i++) {
            double chance = random.nextFloat();
            if (chance > TARGET_ROLL_MIN
                    && chance < TARGET_ROLL_MAX) {
                spawnTargetedProjectile(serverLevel);
            }
        }
    }

    public int remainingLifetime() {
        return (int) Mth.clamp(
                endGameTime - level().getGameTime(), 0L, (long) LIFETIME_TICKS);
    }

    public void primeDependentLifetime(Entity dependent) {
        CompoundTag data = dependent.getPersistentData();
        data.putInt(
                DEPENDENT_REMAINING_TAG, remainingLifetime());
        data.putLong(DEPENDENT_DEADLINE_TAG,
                level().getGameTime() + remainingLifetime());
    }

    public static boolean retainDependentLifetime(
            ServerLevel level, Entity dependent,
            @Nullable WeatherReportFugaSummonerEntity coordinator) {
        if (!level.isRaining()) {
            return false;
        }
        CompoundTag data = dependent.getPersistentData();
        UUID coordinatorUuid = coordinator != null ? coordinator.getUUID()
                : dependent instanceof WeatherReportFugaProjectileEntity projectile
                        ? projectile.getCoordinatorUuid()
                        : data.hasUUID(DEPENDENT_COORDINATOR_TAG)
                                ? data.getUUID(DEPENDENT_COORDINATOR_TAG) : null;
        long gameTime = level.getGameTime();
        if (coordinatorUuid == null || FuguStormTerminalData.get(level)
                .isTerminal(coordinatorUuid, gameTime)) {
            return false;
        }
        if (coordinator != null) {
            coordinator.primeDependentLifetime(dependent);
            return coordinator.remainingLifetime() > 0;
        }
        // Missing parents may use an existing deadline, never create a new one.
        return data.contains(DEPENDENT_DEADLINE_TAG, Tag.TAG_LONG)
                && gameTime < data.getLong(DEPENDENT_DEADLINE_TAG);
    }

    private void spawnRandomProjectile(ServerLevel level) {
        Vec3 offset = new Vec3(
                (random.nextDouble() * HORIZONTAL_RANGE - 0.5D)
                        * (random.nextDouble()
                                - random.nextDouble()),
                random.nextDouble() * VERTICAL_RANDOM_RANGE
                        - 0.5D,
                (random.nextDouble() * HORIZONTAL_RANGE - 0.5D)
                        * (random.nextDouble()
                                - random.nextDouble()));
        spawnProjectile(level, position().add(offset));
    }

    private void spawnTargetedProjectile(ServerLevel level) {
        ArrayList<LivingEntity> targets =
                new ArrayList<>(MAX_TARGET_QUERY_RESULTS);
        level.getEntities(
                EntityTypeTest.forClass(LivingEntity.class),
                new AABB(position(), position())
                        .inflate(HORIZONTAL_RANGE),
                target -> target.isAlive()
                        && !(target instanceof Pufferfish),
                targets,
                MAX_TARGET_QUERY_RESULTS);
        if (targets.isEmpty()) {
            return;
        }
        LivingEntity target = targets.get(0);
        spawnProjectile(
                level,
                new Vec3(
                        target.getX(),
                        getY()
                                + random.nextDouble()
                                        * VERTICAL_RANDOM_RANGE
                                - 0.5D,
                        target.getZ()));
    }

    private void spawnProjectile(ServerLevel level, Vec3 position) {
        if (!level.hasChunkAt(
                net.minecraft.core.BlockPos.containing(position))) {
            return;
        }
        // 1.16 rained without asking about its caster; a caster who is gone is only no longer credited.
        LivingEntity owner = getOwnerUser(level);
        WeatherReportFugaProjectileEntity projectile =
                new WeatherReportFugaProjectileEntity(
                        owner != null && owner.isAlive() ? owner : null,
                        this, level);
        projectile.setPos(position);
        projectile.setDeltaMovement(0.0D, FALL_SPEED, 0.0D);
        if (level.addFreshEntity(projectile)) {
            activeProjectiles.put(
                    projectile.getUUID(), projectile.chunkPosition().toLong());
        }
    }

    private void pruneDependents(ServerLevel level) {
        pruneTracked(level, activeProjectiles,
                entity -> entity instanceof WeatherReportFugaProjectileEntity
                        projectile && projectile.isOwnedBy(this));
        pruneTracked(level, activePufferfish,
                entity -> entity instanceof Pufferfish pufferfish
                        && ownsPufferfish(pufferfish));
        pruneTracked(level, activeClouds,
                entity -> entity instanceof AreaEffectCloud cloud
                        && ownsCloud(cloud));
    }

    private static void pruneTracked(
            ServerLevel level, Map<UUID, Long> tracked,
            Predicate<Entity> valid) {
        tracked.entrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) {
                long chunk = entry.getValue();
                return level.areEntitiesLoaded(chunk)
                        && level.isPositionEntityTicking(new ChunkPos(chunk)
                                .getMiddleBlockPosition(level.getMinBuildHeight()));
            }
            if (!entity.isAlive() || !valid.test(entity)) {
                return true;
            }
            entry.setValue(entity.chunkPosition().toLong());
            return false;
        });
    }

    @Nullable
    public LivingEntity getOwnerUser(ServerLevel level) {
        UUID uuid = entityData.get(OWNER_UUID).orElse(null);
        Entity entity = uuid != null ? level.getEntity(uuid) : null;
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    public WeatherReportStandEntity getStand(ServerLevel level) {
        UUID uuid = entityData.get(STAND_UUID).orElse(null);
        Entity entity = uuid != null ? level.getEntity(uuid) : null;
        return entity instanceof WeatherReportStandEntity stand
                ? stand
                : null;
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public boolean isOwnedBy(WeatherReportStandEntity stand) {
        return stand != null && stand.getUser() != null
                && entityData.get(OWNER_UUID)
                        .filter(stand.getUser().getUUID()::equals)
                        .isPresent();
    }

    public boolean retainProjectile(WeatherReportFugaProjectileEntity projectile) {
        if (!activeProjectiles.containsKey(projectile.getUUID())) {
            pruneDependents((ServerLevel) level());
        }
        return projectile.isOwnedBy(this)
                && retainTracked(activeProjectiles, projectile,
                        MAX_ACTIVE_PROJECTILES);
    }

    public void unregisterProjectile(UUID uuid) {
        activeProjectiles.remove(uuid);
    }

    public boolean canSpawnPufferfish(ServerLevel level) {
        pruneDependents(level);
        return activePufferfish.size() < MAX_ACTIVE_PUFFERFISH;
    }

    public void registerPufferfish(Pufferfish pufferfish) {
        activePufferfish.put(
                pufferfish.getUUID(), pufferfish.chunkPosition().toLong());
        primeDependentLifetime(pufferfish);
    }

    public boolean retainPufferfish(
            ServerLevel level, Pufferfish pufferfish) {
        pruneDependents(level);
        return retainTracked(
                activePufferfish, pufferfish, MAX_ACTIVE_PUFFERFISH);
    }

    public boolean ownsPufferfish(Pufferfish pufferfish) {
        CompoundTag data = pufferfish.getPersistentData();
        return data.getBoolean(SUMMONED_BY_RAIN_TAG)
                && data.hasUUID(DEPENDENT_COORDINATOR_TAG)
                && getUUID().equals(
                        data.getUUID(DEPENDENT_COORDINATOR_TAG));
    }

    public boolean canSpawnCloud(ServerLevel level) {
        pruneDependents(level);
        return activeClouds.size() < MAX_ACTIVE_CLOUDS;
    }

    public void registerCloud(AreaEffectCloud cloud) {
        activeClouds.put(cloud.getUUID(), cloud.chunkPosition().toLong());
        primeDependentLifetime(cloud);
    }

    public boolean retainCloud(
            ServerLevel level, AreaEffectCloud cloud) {
        pruneDependents(level);
        return retainTracked(activeClouds, cloud, MAX_ACTIVE_CLOUDS);
    }

    public boolean ownsCloud(AreaEffectCloud cloud) {
        CompoundTag data = cloud.getPersistentData();
        return data.getBoolean(FUGU_CLOUD_TAG)
                && data.hasUUID(DEPENDENT_COORDINATOR_TAG)
                && getUUID().equals(
                        data.getUUID(DEPENDENT_COORDINATOR_TAG));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()
                && !cleaningDependents) {
            cleaningDependents = true;
            if (level() instanceof ServerLevel serverLevel) {
                FuguStormTerminalData.get(serverLevel).markTerminal(
                        getUUID(), serverLevel.getGameTime());
                discardLoadedDependents(serverLevel);
                WeatherReportStandEntity stand = getStand(serverLevel);
                if (stand != null) {
                    stand.clearActiveFugaSummoner(getId());
                }
            }
        }
        super.remove(reason);
    }

    private void discardLoadedDependents(ServerLevel level) {
        Set<UUID> dependents = new HashSet<>(activeProjectiles.keySet());
        dependents.addAll(activePufferfish.keySet());
        dependents.addAll(activeClouds.keySet());
        for (UUID uuid : dependents) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                entity.discard();
            }
        }
        activeProjectiles.clear();
        activePufferfish.clear();
        activeClouds.clear();
    }

    private static boolean retainTracked(
            Map<UUID, Long> tracked, Entity entity, int limit) {
        UUID uuid = entity.getUUID();
        if (!tracked.containsKey(uuid) && tracked.size() >= limit) {
            return false;
        }
        tracked.put(uuid, entity.chunkPosition().toLong());
        return true;
    }

    private static ListTag saveTracked(Map<UUID, Long> tracked) {
        ListTag list = new ListTag();
        tracked.forEach((uuid, chunk) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", uuid);
            entry.putLong("Chunk", chunk);
            list.add(entry);
        });
        return list;
    }

    private static void loadTracked(
            CompoundTag tag, String key, Map<UUID, Long> tracked, int limit) {
        tracked.clear();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), limit); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("UUID") && entry.contains("Chunk", Tag.TAG_LONG)) {
                tracked.put(entry.getUUID("UUID"), entry.getLong("Chunk"));
            }
        }
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(STAND_UUID, Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong(END_GAME_TIME_TAG, endGameTime);
        entityData.get(OWNER_UUID)
                .ifPresent(uuid -> tag.putUUID(OWNER_TAG, uuid));
        entityData.get(STAND_UUID)
                .ifPresent(uuid -> tag.putUUID(STAND_TAG, uuid));
        tag.put("ActiveProjectiles", saveTracked(activeProjectiles));
        tag.put("ActivePufferfish", saveTracked(activePufferfish));
        tag.put("ActiveClouds", saveTracked(activeClouds));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        long latestEnd = level().getGameTime() + LIFETIME_TICKS;
        // Saves from before the game-time end carry only the storm's age.
        endGameTime = tag.contains(END_GAME_TIME_TAG, Tag.TAG_LONG)
                ? Math.min(tag.getLong(END_GAME_TIME_TAG), latestEnd)
                : latestEnd - Mth.clamp(tag.getInt("Age"), 0, LIFETIME_TICKS);
        cleaningDependents = false;
        entityData.set(
                OWNER_UUID,
                tag.hasUUID(OWNER_TAG)
                        ? Optional.of(tag.getUUID(OWNER_TAG))
                        : Optional.empty());
        entityData.set(
                STAND_UUID,
                tag.hasUUID(STAND_TAG)
                        ? Optional.of(tag.getUUID(STAND_TAG))
                        : Optional.empty());
        loadTracked(tag, "ActiveProjectiles", activeProjectiles,
                MAX_ACTIVE_PROJECTILES);
        loadTracked(tag, "ActivePufferfish", activePufferfish,
                MAX_ACTIVE_PUFFERFISH);
        loadTracked(tag, "ActiveClouds", activeClouds, MAX_ACTIVE_CLOUDS);
    }
}
