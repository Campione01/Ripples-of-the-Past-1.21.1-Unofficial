package rotp.addons.weatherreport.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import rotp.core.powersystem.standpower.entity.StandEntity;

public final class WeatherReportStandEntity extends StandEntity {
    private static final EntityDataAccessor<Integer> ACTIVE_ICICLE_ID =
            SynchedEntityData.defineId(
                    WeatherReportStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer>
            ACTIVE_FUGA_SUMMONER_ID = SynchedEntityData.defineId(
                    WeatherReportStandEntity.class,
                    EntityDataSerializers.INT);

    public WeatherReportStandEntity(
            EntityType<? extends WeatherReportStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTIVE_ICICLE_ID, -1);
        builder.define(ACTIVE_FUGA_SUMMONER_ID, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && showsRainDrips(level())) {
            for (int i = 0; i < 2; i++) {
                level().addParticle(
                        ParticleTypes.FALLING_WATER,
                        getRandomX(1.0D),
                        getRandomY(),
                        getRandomZ(1.0D),
                        0.0D,
                        1.0D,
                        0.0D);
            }
        }
        if (!level().isClientSide()
                && entityData.get(ACTIVE_ICICLE_ID) >= 0
                && getActiveIcicle() == null) {
            entityData.set(ACTIVE_ICICLE_ID, -1);
        }
        if (!level().isClientSide()
                && entityData.get(ACTIVE_FUGA_SUMMONER_ID) >= 0
                && getActiveFugaSummoner() == null) {
            entityData.set(ACTIVE_FUGA_SUMMONER_ID, -1);
        }
    }

    // The weather flag, as 1.16 WeatherReportEntity.tick read getLevelData().isRaining(): Level.isRaining() is the
    // rain level, which fades for about 80 ticks after the rain stops and rises for about 20 after it starts.
    public static boolean showsRainDrips(Level level) {
        return level.getLevelData().isRaining();
    }

    public boolean isIcicleArmHidden() {
        return entityData.get(ACTIVE_ICICLE_ID) >= 0;
    }

    public WeatherReportIcicleEntity getActiveIcicle() {
        int entityId = entityData.get(ACTIVE_ICICLE_ID);
        if (entityId < 0) {
            return null;
        }
        Entity entity = level().getEntity(entityId);
        if (entity instanceof WeatherReportIcicleEntity icicle
                && icicle.isAlive()
                && icicle.getOwner() == this) {
            return icicle;
        }
        return null;
    }

    public void activateIcicle(WeatherReportIcicleEntity icicle) {
        if (level().isClientSide() || icicle.getOwner() != this) {
            return;
        }
        int previousId = entityData.get(ACTIVE_ICICLE_ID);
        entityData.set(ACTIVE_ICICLE_ID, icicle.getId());
        if (previousId >= 0 && previousId != icicle.getId()) {
            Entity previous = level().getEntity(previousId);
            if (previous instanceof WeatherReportIcicleEntity
                    && previous.isAlive()) {
                previous.discard();
            }
        }
    }

    public void clearActiveIcicle(int entityId) {
        if (!level().isClientSide()
                && entityData.get(ACTIVE_ICICLE_ID) == entityId) {
            entityData.set(ACTIVE_ICICLE_ID, -1);
        }
    }

    public WeatherReportFugaSummonerEntity
            getActiveFugaSummoner() {
        return getActiveFugaSummoner(null);
    }

    private WeatherReportFugaSummonerEntity getActiveFugaSummoner(
            WeatherReportFugaSummonerEntity excluded) {
        int entityId = entityData.get(ACTIVE_FUGA_SUMMONER_ID);
        Entity entity = entityId >= 0 ? level().getEntity(entityId) : null;
        if (entity
                        instanceof WeatherReportFugaSummonerEntity
                                summoner
                && summoner != excluded
                && summoner.isAlive()
                && summoner.isOwnedBy(this)) {
            return summoner;
        }
        if (level() instanceof ServerLevel serverLevel && getUser() != null) {
            for (Entity candidate : serverLevel.getAllEntities()) {
                if (candidate instanceof WeatherReportFugaSummonerEntity summoner
                        && summoner != excluded && summoner.isAlive()
                        && summoner.isOwnedBy(this)) {
                    return summoner;
                }
            }
        }
        return null;
    }

    public void activateFugaSummoner(
            WeatherReportFugaSummonerEntity summoner) {
        if (level().isClientSide() || !summoner.isOwnedBy(this)) {
            return;
        }
        WeatherReportFugaSummonerEntity previousSummoner =
                getActiveFugaSummoner(summoner);
        entityData.set(
                ACTIVE_FUGA_SUMMONER_ID, summoner.getId());
        if (previousSummoner != null) {
            previousSummoner.discard();
        }
    }

    public void clearActiveFugaSummoner(int entityId) {
        if (!level().isClientSide()
                && entityData.get(ACTIVE_FUGA_SUMMONER_ID)
                        == entityId) {
            entityData.set(ACTIVE_FUGA_SUMMONER_ID, -1);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            WeatherReportIcicleEntity icicle = getActiveIcicle();
            if (icicle != null) {
                icicle.discard();
            }
            entityData.set(ACTIVE_ICICLE_ID, -1);
            entityData.set(ACTIVE_FUGA_SUMMONER_ID, -1);
        }
        super.remove(reason);
    }
}
