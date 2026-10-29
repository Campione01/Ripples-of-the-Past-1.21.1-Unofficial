package rotp.addons.ger.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

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
import net.minecraft.world.phys.Vec3;

public final class GerBeamEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    GerBeamEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> END_X =
            SynchedEntityData.defineId(
                    GerBeamEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Y =
            SynchedEntityData.defineId(
                    GerBeamEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Z =
            SynchedEntityData.defineId(
                    GerBeamEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> EXPIRES_AT =
            SynchedEntityData.defineId(
                    GerBeamEntity.class,
                    EntityDataSerializers.LONG);

    public GerBeamEntity(
            EntityType<? extends GerBeamEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
    }

    public GerBeamEntity(
            ServerPlayer owner,
            Vec3 start,
            Vec3 end,
            int lifetimeTicks) {
        this(GoldExperienceRequiemAddon.BEAM_ENTITY.get(),
                owner.level());
        setPos(start);
        setOwnerUuid(owner.getUUID());
        setEnd(end);
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

    public Vec3 getEnd() {
        return new Vec3(
                entityData.get(END_X),
                entityData.get(END_Y),
                entityData.get(END_Z));
    }

    public void setEnd(Vec3 end) {
        entityData.set(END_X, (float) end.x);
        entityData.set(END_Y, (float) end.y);
        entityData.set(END_Z, (float) end.z);
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
                || !GoldExperienceRequiemAddon.isGerUser(owner)) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(END_X, 0.0F);
        builder.define(END_Y, 0.0F);
        builder.define(END_Z, 0.0F);
        builder.define(EXPIRES_AT, 0L);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        Vec3 end = getEnd();
        tag.putDouble("EndX", end.x);
        tag.putDouble("EndY", end.y);
        tag.putDouble("EndZ", end.z);
        tag.putLong("ExpiresAt", getExpiresAt());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setOwnerUuid(tag.hasUUID("Owner")
                ? tag.getUUID("Owner")
                : null);
        setEnd(new Vec3(
                tag.getDouble("EndX"),
                tag.getDouble("EndY"),
                tag.getDouble("EndZ")));
        setExpiresAt(tag.getLong("ExpiresAt"));
    }
}
