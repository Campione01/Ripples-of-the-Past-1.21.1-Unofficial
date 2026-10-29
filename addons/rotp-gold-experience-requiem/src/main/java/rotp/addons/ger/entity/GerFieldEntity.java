package rotp.addons.ger.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.power.ModStands;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class GerFieldEntity extends Entity {
    public static final int DISABLER = 0;
    public static final int EVOLUTION = 1;

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    GerFieldEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(
                    GerFieldEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(
                    GerFieldEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> CREATED_AT =
            SynchedEntityData.defineId(
                    GerFieldEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> EXPIRES_AT =
            SynchedEntityData.defineId(
                    GerFieldEntity.class,
                    EntityDataSerializers.LONG);

    public GerFieldEntity(
            EntityType<? extends GerFieldEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
    }

    public GerFieldEntity(
            ServerPlayer owner,
            int kind,
            float radius,
            int lifetimeTicks) {
        this(GoldExperienceRequiemAddon.FIELD_ENTITY.get(),
                owner.level());
        setPos(owner.position());
        setOwnerUuid(owner.getUUID());
        setKind(kind);
        setRadius(radius);
        setCreatedAt(level().getGameTime());
        setExpiresAt(level().getGameTime()
                + Math.max(1, lifetimeTicks));
    }

    @Nullable
    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public void setOwnerUuid(@Nullable UUID uuid) {
        entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    public int getKind() {
        return entityData.get(KIND);
    }

    public void setKind(int kind) {
        entityData.set(KIND, kind);
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    public void setRadius(float radius) {
        entityData.set(RADIUS, Math.max(0.1F, radius));
    }

    public long getCreatedAt() {
        return entityData.get(CREATED_AT);
    }

    public void setCreatedAt(long createdAt) {
        entityData.set(CREATED_AT, createdAt);
    }

    public long getExpiresAt() {
        return entityData.get(EXPIRES_AT);
    }

    public void setExpiresAt(long expiresAt) {
        entityData.set(EXPIRES_AT, expiresAt);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID ownerUuid = getOwnerUuid();
        ServerPlayer owner = ownerUuid != null
                ? serverLevel.getServer().getPlayerList()
                        .getPlayer(ownerUuid)
                : null;
        if (serverLevel.getGameTime() >= getExpiresAt()
                || owner == null
                || owner.level() != serverLevel
                || !owner.isAlive()
                || !hasExpectedPower(owner)) {
            discard();
            return;
        }
        setPos(owner.position());
    }

    private boolean hasExpectedPower(ServerPlayer owner) {
        if (getKind() == DISABLER) {
            return GoldExperienceRequiemAddon.isGerUser(owner);
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.hasPower()
                && (power.getPowerType()
                                == ModStands.GOLD_EXPERIENCE.get()
                        || power.getPowerType()
                                == GoldExperienceRequiemAddon
                                        .GER_STAND.get());
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(KIND, DISABLER);
        builder.define(RADIUS, 1.0F);
        builder.define(CREATED_AT, 0L);
        builder.define(EXPIRES_AT, 0L);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putInt("Kind", getKind());
        tag.putFloat("Radius", getRadius());
        tag.putLong("CreatedAt", getCreatedAt());
        tag.putLong("ExpiresAt", getExpiresAt());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setOwnerUuid(tag.hasUUID("Owner")
                ? tag.getUUID("Owner")
                : null);
        setKind(tag.getInt("Kind"));
        setRadius(tag.getFloat("Radius"));
        setCreatedAt(tag.getLong("CreatedAt"));
        setExpiresAt(tag.getLong("ExpiresAt"));
    }
}
