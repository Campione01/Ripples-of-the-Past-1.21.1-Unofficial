package rotp.addons.wonderofu.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.WonderOfUGameEvents;
import rotp.addons.wonderofu.entity.CarCalamityEntity.CarRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class WonderOfUStandEntity extends StandEntity {
    public static final int CAR_SPAWN_COOLDOWN_TICKS = 120;

    private static final EntityDataAccessor<Boolean> PASSIVE_ENABLED =
            SynchedEntityData.defineId(
                    WonderOfUStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ACTIVE_ENABLED =
            SynchedEntityData.defineId(
                    WonderOfUStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CAR_ENABLED =
            SynchedEntityData.defineId(
                    WonderOfUStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>>
            CURRENT_CAR_UUID = SynchedEntityData.defineId(
                    WonderOfUStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private CarCalamityEntity activeCar;
    private long lastCarSpawnGameTime =
            -CAR_SPAWN_COOLDOWN_TICKS;

    public WonderOfUStandEntity(
            EntityType<? extends WonderOfUStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PASSIVE_ENABLED, false);
        builder.define(ACTIVE_ENABLED, false);
        builder.define(CAR_ENABLED, false);
        builder.define(CURRENT_CAR_UUID, Optional.empty());
    }

    public boolean isPassiveEnabled() {
        return entityData.get(PASSIVE_ENABLED);
    }

    public void setPassiveEnabled(boolean enabled) {
        if (!level().isClientSide() && isAlive() && !isRemoved()) {
            if (enabled) {
                entityData.set(ACTIVE_ENABLED, false);
            }
            entityData.set(PASSIVE_ENABLED, enabled);
        }
    }

    public boolean isActiveEnabled() {
        return entityData.get(ACTIVE_ENABLED);
    }

    public void setActiveEnabled(boolean enabled) {
        if (!level().isClientSide() && isAlive() && !isRemoved()) {
            if (enabled) {
                entityData.set(PASSIVE_ENABLED, false);
            }
            entityData.set(ACTIVE_ENABLED, enabled);
        }
    }

    public boolean isCarEnabled() {
        return entityData.get(CAR_ENABLED);
    }

    public void setCarEnabled(boolean enabled) {
        if (level().isClientSide() || !isAlive() || isRemoved()) {
            return;
        }
        entityData.set(CAR_ENABLED, enabled);
    }

    public UUID getCurrentCarUuid() {
        return entityData.get(CURRENT_CAR_UUID).orElse(null);
    }

    public boolean interceptAttackWithCar(LivingEntity attacker) {
        if (!(level() instanceof ServerLevel serverLevel)
                || attacker == null
                || attacker.level() != serverLevel
                || !attacker.isAlive()
                || attacker.isRemoved()
                || !isCarEnabled()) {
            return false;
        }

        LivingEntity user = getUser();
        StandPower power = user != null ? StandPower.get(user) : null;
        LivingEntity relationTarget =
                WonderOfUGameEvents.resolveLiving(attacker);
        if (user == null
                || !user.isAlive()
                || user.isRemoved()
                || power == null
                || !power.hasPower()
                || power.getPowerType()
                        != WonderOfUAddon.WONDER_OF_YOU.get()
                || power.getSummonedStandEntity() != this
                || !power.isAbilityUnlocked(
                        "calamity_car_attack")
                || !WonderOfUGameEvents.canAffect(
                        user, relationTarget, this)) {
            return false;
        }

        CarCalamityEntity current = resolveCurrentCar(serverLevel);
        if (current != null && current.retarget(attacker)) {
            activeCar = current;
            return true;
        }

        long gameTime = serverLevel.getGameTime();
        if (gameTime - lastCarSpawnGameTime
                < CAR_SPAWN_COOLDOWN_TICKS) {
            return true;
        }

        double spawnX = attacker.getX();
        double spawnY = attacker.getY() + 7.0D;
        double spawnZ = attacker.getZ();
        BlockPos spawnPos =
                BlockPos.containing(spawnX, spawnY, spawnZ);
        if (!serverLevel.isInWorldBounds(spawnPos)
                || !serverLevel.hasChunkAt(spawnPos)) {
            return true;
        }

        CarCalamityEntity car =
                WonderOfUAddon.CAR_CALAMITY_ENTITY.get()
                        .create(serverLevel);
        if (car == null) {
            return true;
        }
        car.moveTo(
                spawnX,
                spawnY,
                spawnZ,
                attacker.getYRot(),
                0.0F);
        car.initializeAuthority(
                user,
                this,
                attacker,
                gameTime + CarCalamityEntity.LIFETIME_TICKS);
        if (!serverLevel.addFreshEntity(car)) {
            return true;
        }

        CarCalamityEntity previous = activeCar;
        entityData.set(
                CURRENT_CAR_UUID,
                Optional.of(car.getUUID()));
        activeCar = car;
        lastCarSpawnGameTime = gameTime;
        if (previous != null
                && previous != car
                && previous.isAlive()
                && !previous.isRemoved()) {
            previous.removeWithReason(CarRemovalReason.REPLACED);
        }
        return true;
    }

    public void onCarRemoved(CarCalamityEntity car) {
        if (level().isClientSide() || car == null) {
            return;
        }
        if (car == activeCar) {
            activeCar = null;
        }
        if (car.getUUID().equals(getCurrentCarUuid())) {
            entityData.set(CURRENT_CAR_UUID, Optional.empty());
        }
    }

    public void bindExplosionHazard(Entity hazard) {
        LivingEntity user = getUser();
        if (level().isClientSide()
                || hazard == null
                || user == null
                || hazard.level() != level()) {
            return;
        }
        CalamityHazardAuthority.bind(hazard, this, user);
    }

    public void onExplosionHazardAdded(Entity hazard) {
        if (!level().isClientSide()
                && CalamityHazardAuthority.isBound(hazard)
                && !CalamityHazardAuthority.isAuthorized(hazard)) {
            hazard.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!hasOwnerAuthority()) {
            clearOwnedState(CarRemovalReason.POWER_CHANGED);
            return;
        }
        if (activeCar != null && activeCar.isRemoved()) {
            activeCar = null;
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            clearOwnedState(CarRemovalReason.STAND_REMOVED);
        }
        super.remove(reason);
    }

    public void clearOwnedState(CarRemovalReason reason) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        entityData.set(PASSIVE_ENABLED, false);
        entityData.set(ACTIVE_ENABLED, false);
        entityData.set(CAR_ENABLED, false);
        releaseCurrentCar();
        CalamityHazardAuthority.clearForStand(
                serverLevel, getUUID());
    }

    private boolean hasOwnerAuthority() {
        LivingEntity user = getUser();
        StandPower power = user != null ? StandPower.get(user) : null;
        return user != null
                && user.isAlive()
                && !user.isRemoved()
                && power != null
                && power.hasPower()
                && power.getPowerType()
                        == WonderOfUAddon.WONDER_OF_YOU.get()
                && power.getSummonedStandEntity() == this
                && getUser() == user;
    }

    // Donor parity: the Stand only forgets a car that is already in the
    // world; the car itself still lands and explodes.
    private void releaseCurrentCar() {
        entityData.set(CURRENT_CAR_UUID, Optional.empty());
        activeCar = null;
    }

    private CarCalamityEntity resolveCurrentCar(
            ServerLevel serverLevel) {
        if (activeCar != null
                && activeCar.isAlive()
                && !activeCar.isRemoved()) {
            return activeCar;
        }
        UUID uuid = getCurrentCarUuid();
        Entity entity = uuid != null
                ? serverLevel.getEntity(uuid)
                : null;
        return entity instanceof CarCalamityEntity car
                && car.isAlive()
                && !car.isRemoved()
                ? car
                : null;
    }
}
