package rotp.addons.weatherreport.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class WeatherReportFugaProjectileEntity
        extends ModdedProjectileEntity {
    public static final int LIFESPAN_TICKS = 400;
    public static final float BASE_DAMAGE = 5.0F;
    public static final float MAX_HARDNESS = 0.0F;
    public static final double PUFFER_CHANCE_MIN = 0.1D;
    public static final double PUFFER_CHANCE_MAX = 0.15D;
    public static final double CLOUD_CHANCE_MIN = 0.3D;
    public static final double CLOUD_CHANCE_MAX = 0.35D;
    public static final float CLOUD_RADIUS = 2.0F;
    public static final int CLOUD_DURATION_TICKS = 100;
    public static final int CLOUD_POISON_DURATION_TICKS = 100;
    public static final int SLOWNESS_DURATION_TICKS = 120;
    public static final int POISON_DURATION_TICKS = 90;
    public static final int POISON_AMPLIFIER = 1;
    public static final float SPLASH_VOLUME = 0.25F;
    public static final float SPLASH_PITCH = 1.0F;

    private static final String COORDINATOR_TAG = "Coordinator";
    private static final EntityDataAccessor<Optional<UUID>>
            COORDINATOR_UUID = SynchedEntityData.defineId(
                    WeatherReportFugaProjectileEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    public WeatherReportFugaProjectileEntity(
            EntityType<? extends WeatherReportFugaProjectileEntity> type,
            Level level) {
        super(type, level);
    }

    public WeatherReportFugaProjectileEntity(
            @Nullable LivingEntity owner,
            WeatherReportFugaSummonerEntity coordinator,
            Level level) {
        super(WeatherReportAddon.FUGA_PROJECTILE.get(), owner, level);
        entityData.set(
                COORDINATOR_UUID,
                Optional.of(coordinator.getUUID()));
        coordinator.primeDependentLifetime(this);
    }

    @Override
    public void tick() {
        WeatherReportFugaSummonerEntity coordinator = getCoordinator();
        if (level() instanceof ServerLevel serverLevel) {
            if (entityData.get(COORDINATOR_UUID).isEmpty()
                    || !WeatherReportFugaSummonerEntity.retainDependentLifetime(
                            serverLevel, this, coordinator)
                    || coordinator != null && !coordinator.retainProjectile(this)) {
                discard();
                return;
            }
        }
        super.tick();
        if (!isRemoved() && coordinator != null) {
            coordinator.retainProjectile(this);
        }
    }

    // The core base removes a projectile whose owner is dead; the rain outlives its caster, so a caster who is
    // gone is reported as no owner and the fish falls on uncredited.
    @Override
    @Nullable
    public LivingEntity getOwner() {
        LivingEntity owner = super.getOwner();
        return owner != null && owner.isAlive() ? owner : null;
    }

    @Nullable
    public WeatherReportFugaSummonerEntity getCoordinator() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        UUID uuid = entityData.get(COORDINATOR_UUID).orElse(null);
        Entity entity = uuid != null
                ? serverLevel.getEntity(uuid)
                : null;
        return entity
                instanceof WeatherReportFugaSummonerEntity coordinator
                && coordinator.isAlive()
                        ? coordinator
                         : null;
    }

    @Nullable
    public UUID getCoordinatorUuid() {
        return entityData.get(COORDINATOR_UUID).orElse(null);
    }

    public boolean isOwnedBy(
            WeatherReportFugaSummonerEntity coordinator) {
        return coordinator != null
                && entityData.get(COORDINATOR_UUID)
                        .filter(coordinator.getUUID()::equals)
                        .isPresent();
    }

    @Override
    public int ticksLifespan() {
        return LIFESPAN_TICKS;
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return MAX_HARDNESS;
    }

    @Override
    public boolean standDamage() {
        return false;
    }

    @Override
    public boolean canHitOwner() {
        return true;
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult hitResult, boolean blockDestroyed) {
        super.afterBlockHit(hitResult, blockDestroyed);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double fishSummonChance = random.nextDouble();
        WeatherReportFugaSummonerEntity coordinator =
                getCoordinator();
        if (coordinator != null
                && fishSummonChance > PUFFER_CHANCE_MIN
                && fishSummonChance < PUFFER_CHANCE_MAX
                && coordinator.canSpawnPufferfish(serverLevel)) {
            spawnPufferfish(serverLevel, coordinator, hitResult);
        }
        if (coordinator != null
                && fishSummonChance > CLOUD_CHANCE_MIN
                && fishSummonChance < CLOUD_CHANCE_MAX
                && coordinator.canSpawnCloud(serverLevel)) {
            spawnPoisonCloud(serverLevel, coordinator, hitResult);
        }
        splash(serverLevel);
    }

    private void spawnPufferfish(
            ServerLevel level,
            WeatherReportFugaSummonerEntity coordinator,
            BlockHitResult hitResult) {
        Pufferfish pufferfish =
                EntityType.PUFFERFISH.create(level);
        if (pufferfish == null) {
            return;
        }

        CompoundTag lootData = new CompoundTag();
        lootData.putString(
                "DeathLootTable",
                BuiltInLootTables.EMPTY.location().toString());
        pufferfish.readAdditionalSaveData(lootData);
        pufferfish.setPuffState(2);
        CompoundTag data = pufferfish.getPersistentData();
        data.putBoolean(
                WeatherReportFugaSummonerEntity
                        .SUMMONED_BY_RAIN_TAG,
                true);
        data.putUUID(
                WeatherReportFugaSummonerEntity
                        .DEPENDENT_COORDINATOR_TAG,
                coordinator.getUUID());
        UUID ownerUuid = coordinator.getOwnerUuid();
        if (ownerUuid != null) {
            data.putUUID(
                    WeatherReportFugaSummonerEntity
                            .DEPENDENT_OWNER_TAG,
                    ownerUuid);
        }

        Vec3 hit = hitResult.getLocation();
        pufferfish.moveTo(
                hit.x,
                hit.y + 1.0D,
                hit.z,
                random.nextFloat() * 360.0F,
                0.0F);
        if (level.addFreshEntity(pufferfish)) {
            coordinator.registerPufferfish(pufferfish);
        }
    }

    private void spawnPoisonCloud(
            ServerLevel level,
            WeatherReportFugaSummonerEntity coordinator,
            BlockHitResult hitResult) {
        Vec3 hit = hitResult.getLocation();
        AreaEffectCloud cloud =
                new AreaEffectCloud(level, hit.x, hit.y, hit.z);
        cloud.setRadius(CLOUD_RADIUS);
        cloud.setDuration(CLOUD_DURATION_TICKS);
        cloud.addEffect(new MobEffectInstance(
                MobEffects.POISON,
                CLOUD_POISON_DURATION_TICKS));
        LivingEntity owner = getOwner();
        if (owner != null) {
            cloud.setOwner(owner);
        }
        CompoundTag data = cloud.getPersistentData();
        data.putBoolean(
                WeatherReportFugaSummonerEntity.FUGU_CLOUD_TAG,
                true);
        data.putUUID(
                WeatherReportFugaSummonerEntity
                        .DEPENDENT_COORDINATOR_TAG,
                coordinator.getUUID());
        UUID ownerUuid = coordinator.getOwnerUuid();
        if (ownerUuid != null) {
            data.putUUID(
                    WeatherReportFugaSummonerEntity
                            .DEPENDENT_OWNER_TAG,
                    ownerUuid);
        }
        if (level.addFreshEntity(cloud)) {
            coordinator.registerCloud(cloud);
        }
    }

    @Override
    protected void afterEntityHit(
            EntityHitResult hitResult, boolean entityHurt) {
        super.afterEntityHit(hitResult, entityHurt);
        if (entityHurt
                && hitResult.getEntity()
                        instanceof LivingEntity target
                && level() instanceof ServerLevel serverLevel) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    SLOWNESS_DURATION_TICKS,
                    0));
            target.addEffect(new MobEffectInstance(
                    MobEffects.POISON,
                    POISON_DURATION_TICKS,
                    POISON_AMPLIFIER));
            splash(serverLevel);
        }
    }

    private void splash(ServerLevel level) {
        level.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                WeatherReportAddon.WEATHER_REPORT_FUGU_SPLASH.get(),
                SoundSource.WEATHER,
                SPLASH_VOLUME,
                SPLASH_PITCH);

        int particleCount = 0;
        while ((double) particleCount
                < random.nextDouble() * 10.0D) {
            particleCount++;
        }
        if (particleCount <= 0) {
            return;
        }
        double rotation = getYRot() * Mth.DEG_TO_RAD;
        double offset = (getBbWidth() + 1.0F) * 0.5D;
        level.sendParticles(
                ParticleTypes.SNEEZE,
                getX() - offset * Mth.sin((float) rotation),
                getY(0.5D) - 0.1D,
                getZ() + offset * Mth.cos((float) rotation),
                particleCount,
                0.25D,
                0.1D,
                0.25D,
                0.0D);
    }

    @Override
    public void remove(RemovalReason reason) {
        WeatherReportFugaSummonerEntity coordinator =
                getCoordinator();
        if (coordinator != null) {
            if (reason.shouldDestroy()) {
                coordinator.unregisterProjectile(getUUID());
            } else {
                coordinator.retainProjectile(this);
            }
        }
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COORDINATOR_UUID, Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(COORDINATOR_UUID)
                .ifPresent(uuid -> tag.putUUID(
                        COORDINATOR_TAG, uuid));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                COORDINATOR_UUID,
                tag.hasUUID(COORDINATOR_TAG)
                        ? Optional.of(
                                tag.getUUID(COORDINATOR_TAG))
                        : Optional.empty());
    }
}
