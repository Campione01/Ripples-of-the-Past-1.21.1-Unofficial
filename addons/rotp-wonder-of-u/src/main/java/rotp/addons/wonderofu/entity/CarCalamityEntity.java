package rotp.addons.wonderofu.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.util.functions.DamageUtil;

import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.WonderOfUDamageTypes;
import rotp.addons.wonderofu.WonderOfUGameEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public final class CarCalamityEntity extends Mob {
    public static final float EXPLOSION_RADIUS = 6.0F;
    public static final int LIFETIME_TICKS = 100;
    public static final int EXPLOSION_PARTICLE_COUNT = 200;
    public static final int SMOKE_PARTICLE_COUNT = 100;

    private static final String OWNER_TAG = "Owner";
    private static final String STAND_TAG = "Stand";
    private static final String TARGET_TAG = "Target";
    private static final String DIMENSION_TAG = "AuthorityDimension";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String REMOVAL_REASON_TAG = "RemovalReason";

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    CarCalamityEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> STAND_UUID =
            SynchedEntityData.defineId(
                    CarCalamityEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> TARGET_UUID =
            SynchedEntityData.defineId(
                    CarCalamityEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> AUTHORITY_DIMENSION =
            SynchedEntityData.defineId(
                    CarCalamityEntity.class,
                    EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> EXPIRES_AT =
            SynchedEntityData.defineId(
                    CarCalamityEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> REMOVAL_REASON =
            SynchedEntityData.defineId(
                    CarCalamityEntity.class,
                    EntityDataSerializers.INT);

    public CarCalamityEntity(
            EntityType<? extends CarCalamityEntity> type,
            Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    public void initializeAuthority(
            LivingEntity owner,
            WonderOfUStandEntity stand,
            LivingEntity target,
            long expiresAt) {
        setOwnerUuid(owner.getUUID());
        setStandUuid(stand.getUUID());
        setTargetUuid(target.getUUID());
        setAuthorityDimension(level().dimension().location().toString());
        setExpiresAt(expiresAt);
        setRemovalReason(CarRemovalReason.NONE);
        setTarget(target);
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public UUID getStandUuid() {
        return entityData.get(STAND_UUID).orElse(null);
    }

    public UUID getTargetUuid() {
        return entityData.get(TARGET_UUID).orElse(null);
    }

    public String getAuthorityDimension() {
        return entityData.get(AUTHORITY_DIMENSION);
    }

    public long getExpiresAt() {
        return entityData.get(EXPIRES_AT);
    }

    public CarRemovalReason getCarRemovalReason() {
        return CarRemovalReason.fromId(entityData.get(REMOVAL_REASON));
    }

    public void removeWithReason(CarRemovalReason reason) {
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        releaseFromStand(reason);
        discard();
    }

    private void releaseFromStand(CarRemovalReason reason) {
        setRemovalReason(reason);
        if (level() instanceof ServerLevel serverLevel) {
            WonderOfUStandEntity stand = resolveStand(serverLevel);
            if (stand != null) {
                stand.onCarRemoved(this);
            }
        }
    }

    // The relation filter is the port's; it applies for as long as the
    // caster entity can still be found. A car whose caster is gone hurts
    // whatever its blast reaches, as the donor's ownerless car does.
    public boolean canDamage(LivingEntity victim) {
        if (victim == this
                || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        LivingEntity owner = resolveLiving(
                serverLevel, getOwnerUuid());
        return owner == null
                || WonderOfUGameEvents.canAffect(
                        owner,
                        WonderOfUGameEvents.resolveLiving(victim),
                        resolveStand(serverLevel));
    }

    public boolean retarget(LivingEntity target) {
        if (level().isClientSide()
                || target == null
                || !isAlive()
                || isRemoved()) {
            return false;
        }
        setTargetUuid(target.getUUID());
        setTarget(target);
        return true;
    }

    // Donor CarProjectileEntity: once in the world the car answers to
    // nobody. It falls and explodes whether or not its caster, Stand,
    // car mode or target still exist. Only the lifetime is port
    // hardening, for a car that is kept from ever landing.
    @Override
    public void tick() {
        if (level() instanceof ServerLevel serverLevel
                && !isDeadOrDying()
                && serverLevel.getGameTime() >= getExpiresAt()) {
            removeWithReason(CarRemovalReason.EXPIRED);
            return;
        }

        super.tick();
        if (level() instanceof ServerLevel serverLevel
                && isAlive()
                && !isRemoved()
                && (onGround()
                        || horizontalCollision
                        || verticalCollision)) {
            explode(serverLevel);
        }
    }

    private WonderOfUStandEntity resolveStand(
            ServerLevel serverLevel) {
        UUID standUuid = getStandUuid();
        Entity entity = standUuid != null
                ? serverLevel.getEntity(standUuid)
                : null;
        return entity instanceof WonderOfUStandEntity stand
                ? stand
                : null;
    }

    private static LivingEntity resolveLiving(
            ServerLevel serverLevel, UUID uuid) {
        Entity entity = uuid != null
                ? serverLevel.getEntity(uuid)
                : null;
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    private void explode(ServerLevel serverLevel) {
        // The caster may be dead or gone; the blast is then the car's own.
        DamageSource damageSource = DamageUtil.make(
                serverLevel,
                WonderOfUDamageTypes.CALAMITY_CAR,
                this,
                resolveLiving(serverLevel, getOwnerUuid()));
        Level.ExplosionInteraction interaction =
                serverLevel.getGameRules()
                        .getBoolean(GameRules.RULE_MOBGRIEFING)
                        ? Level.ExplosionInteraction.MOB
                        : Level.ExplosionInteraction.NONE;
        serverLevel.explode(
                this,
                damageSource,
                createDamageCalculator(),
                getX(),
                getY(),
                getZ(),
                EXPLOSION_RADIUS,
                false,
                interaction);
        serverLevel.sendParticles(
                ParticleTypes.EXPLOSION,
                getX(),
                getY(),
                getZ(),
                EXPLOSION_PARTICLE_COUNT,
                3.0D,
                3.0D,
                3.0D,
                0.1D);
        serverLevel.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                getX(),
                getY(),
                getZ(),
                SMOKE_PARTICLE_COUNT,
                3.0D,
                3.0D,
                3.0D,
                0.1D);
        removeWithReason(CarRemovalReason.COLLISION);
    }

    private ExplosionDamageCalculator createDamageCalculator() {
        return new EntityBasedExplosionDamageCalculator(this) {
            @Override
            public boolean shouldDamageEntity(
                    Explosion explosion, Entity entity) {
                return !(entity instanceof LivingEntity living)
                        || canDamage(living);
            }

            @Override
            public float getKnockbackMultiplier(Entity entity) {
                if (entity instanceof LivingEntity living
                        && !canDamage(living)) {
                    return 0.0F;
                }
                return super.getKnockbackMultiplier(entity);
            }
        };
    }

    // Donor: a destroyed car is a dying mob. It stays for vanilla's 20 death
    // ticks, which the renderer shows as the tip-over, and tickDeath then
    // removes it. The Stand lets go of it at once.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide() && dead && !isRemoved()) {
            releaseFromStand(CarRemovalReason.DESTROYED);
        }
    }

    // Donor: the car is a TameableEntity, so 1.16 AnimalEntity's reward. As
    // there, vanilla drops it only for a kill by a player.
    @Override
    protected int getBaseExperienceReward() {
        return 1 + level().random.nextInt(3);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WonderOfUAddon.CAR_DAMAGE.get();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(STAND_UUID, Optional.empty());
        builder.define(TARGET_UUID, Optional.empty());
        builder.define(AUTHORITY_DIMENSION, "");
        builder.define(EXPIRES_AT, 0L);
        builder.define(
                REMOVAL_REASON, CarRemovalReason.NONE.id());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        putUuid(tag, OWNER_TAG, getOwnerUuid());
        putUuid(tag, STAND_TAG, getStandUuid());
        putUuid(tag, TARGET_TAG, getTargetUuid());
        tag.putString(DIMENSION_TAG, getAuthorityDimension());
        tag.putLong(EXPIRES_AT_TAG, getExpiresAt());
        tag.putInt(
                REMOVAL_REASON_TAG,
                getCarRemovalReason().id());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOwnerUuid(tag.hasUUID(OWNER_TAG)
                ? tag.getUUID(OWNER_TAG)
                : null);
        setStandUuid(tag.hasUUID(STAND_TAG)
                ? tag.getUUID(STAND_TAG)
                : null);
        setTargetUuid(tag.hasUUID(TARGET_TAG)
                ? tag.getUUID(TARGET_TAG)
                : null);
        setAuthorityDimension(tag.getString(DIMENSION_TAG));
        setExpiresAt(tag.getLong(EXPIRES_AT_TAG));
        setRemovalReason(CarRemovalReason.fromId(
                tag.getInt(REMOVAL_REASON_TAG)));
    }

    private void setOwnerUuid(UUID uuid) {
        entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    private void setStandUuid(UUID uuid) {
        entityData.set(STAND_UUID, Optional.ofNullable(uuid));
    }

    private void setTargetUuid(UUID uuid) {
        entityData.set(TARGET_UUID, Optional.ofNullable(uuid));
    }

    private void setAuthorityDimension(String dimension) {
        entityData.set(
                AUTHORITY_DIMENSION,
                dimension != null ? dimension : "");
    }

    private void setExpiresAt(long expiresAt) {
        entityData.set(EXPIRES_AT, expiresAt);
    }

    private void setRemovalReason(CarRemovalReason reason) {
        entityData.set(REMOVAL_REASON, reason.id());
    }

    private static void putUuid(
            CompoundTag tag, String key, UUID uuid) {
        if (uuid != null) {
            tag.putUUID(key, uuid);
        }
    }

    public enum CarRemovalReason {
        NONE(0),
        EXPIRED(1),
        OWNER_INVALID(2),
        STAND_INVALID(3),
        TARGET_INVALID(4),
        DIMENSION_MISMATCH(5),
        MODE_DISABLED(6),
        REPLACED(7),
        STAND_REMOVED(8),
        COLLISION(9),
        DESTROYED(10),
        OWNER_LOGOUT(11),
        OWNER_DEATH(12),
        DIMENSION_CHANGE(13),
        POWER_CHANGED(14);

        private final int id;

        CarRemovalReason(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static CarRemovalReason fromId(int id) {
            for (CarRemovalReason reason : values()) {
                if (reason.id == id) {
                    return reason;
                }
            }
            return NONE;
        }
    }
}
