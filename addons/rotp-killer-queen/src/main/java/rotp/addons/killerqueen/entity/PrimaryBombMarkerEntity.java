package rotp.addons.killerqueen.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.customobjects.EntityWithStandSkin;
import rotp.core.init.ModEntityDataSerializers;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.state.KillerQueenAuthority;
import rotp.addons.killerqueen.state.KillerQueenState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PrimaryBombMarkerEntity extends Entity
        implements EntityWithStandSkin {
    public enum Kind {
        LIVING,
        BLOCK
    }

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    PrimaryBombMarkerEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            STAND_UUID = SynchedEntityData.defineId(
                    PrimaryBombMarkerEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            TARGET_UUID = SynchedEntityData.defineId(
                    PrimaryBombMarkerEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<BlockPos>
            BLOCK_POSITION = SynchedEntityData.defineId(
                    PrimaryBombMarkerEntity.class,
                    EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Byte> KIND =
            SynchedEntityData.defineId(
                    PrimaryBombMarkerEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<
            Optional<ResourceLocation>> SELECTED_SKIN =
            SynchedEntityData.defineId(
                    PrimaryBombMarkerEntity.class,
                    ModEntityDataSerializers
                            .RESOURCE_LOCATION_OPTIONAL.get());

    private String boundDimension = "";

    public PrimaryBombMarkerEntity(
            EntityType<? extends PrimaryBombMarkerEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        noCulling = true;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(STAND_UUID, Optional.empty());
        builder.define(TARGET_UUID, Optional.empty());
        builder.define(BLOCK_POSITION, BlockPos.ZERO);
        builder.define(KIND, (byte) Kind.LIVING.ordinal());
        builder.define(SELECTED_SKIN, Optional.empty());
    }

    public void bindLiving(
            ServerPlayer owner,
            StandEntity stand,
            LivingEntity target) {
        if (!bind(owner, stand)
                || target == null
                || target.isRemoved()) {
            discard();
            return;
        }
        entityData.set(
                KIND, (byte) Kind.LIVING.ordinal(), true);
        entityData.set(
                TARGET_UUID,
                Optional.of(target.getUUID()),
                true);
        setMarkerPosition(target.position().add(
                0.0D, target.getBbHeight() + 0.35D, 0.0D));
    }

    public void bindBlock(
            ServerPlayer owner,
            StandEntity stand,
            BlockPos position) {
        if (!bind(owner, stand) || position == null) {
            discard();
            return;
        }
        entityData.set(
                KIND, (byte) Kind.BLOCK.ordinal(), true);
        entityData.set(
                BLOCK_POSITION, position.immutable(), true);
        setMarkerPosition(
                Vec3.atCenterOf(position).add(0.0D, 0.8D, 0.0D));
    }

    private boolean bind(
            ServerPlayer owner, StandEntity stand) {
        if (!(level() instanceof ServerLevel level)
                || !KillerQueenAuthority.validController(
                        owner, stand)) {
            return false;
        }
        entityData.set(
                OWNER_UUID,
                Optional.of(owner.getUUID()),
                true);
        entityData.set(
                STAND_UUID,
                Optional.of(stand.getUUID()),
                true);
        entityData.set(
                SELECTED_SKIN,
                stand.getStandSkin(),
                true);
        boundDimension = level.dimension().location().toString();
        return true;
    }

    public Kind markerKind() {
        int ordinal = entityData.get(KIND);
        return ordinal >= 0 && ordinal < Kind.values().length
                ? Kind.values()[ordinal]
                : Kind.LIVING;
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public UUID getStandUuid() {
        return entityData.get(STAND_UUID).orElse(null);
    }

    public boolean isOwnedBy(UUID ownerUuid, Kind kind) {
        return ownerUuid != null
                && markerKind() == kind
                && entityData.get(OWNER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        noPhysics = true;
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID ownerUuid = getOwnerUuid();
        UUID standUuid = getStandUuid();
        if (ownerUuid == null
                || standUuid == null
                || !serverLevel.dimension().location().toString()
                        .equals(boundDimension)
                || !KillerQueenAuthority.validController(
                        serverLevel, ownerUuid, standUuid)) {
            discard();
            return;
        }
        Entity ownerEntity = serverLevel.getEntity(ownerUuid);
        if (!(ownerEntity instanceof ServerPlayer owner)
                || !owner.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            discard();
            return;
        }
        KillerQueenState state = KillerQueenAddon.state(owner);
        if (markerKind() == Kind.LIVING) {
            updateLiving(serverLevel, state);
        } else {
            updateBlock(state);
        }
    }

    private void updateLiving(
            ServerLevel level, KillerQueenState state) {
        UUID targetUuid =
                entityData.get(TARGET_UUID).orElse(null);
        if (targetUuid == null
                || !targetUuid.equals(
                        state.primaryTargetUuid())) {
            discard();
            return;
        }
        // 1.16 EntityBombMarker drew over a killed target's body until it left the level
        Entity target = level.getEntity(targetUuid);
        if (!(target instanceof LivingEntity living)
                || living.isRemoved()) {
            discard();
            return;
        }
        setMarkerPosition(living.position().add(
                0.0D, living.getBbHeight() + 0.35D, 0.0D));
    }

    private void updateBlock(KillerQueenState state) {
        BlockPos position = entityData.get(BLOCK_POSITION);
        if (!position.equals(state.blockBombPos())
                || level().getBlockState(position).isAir()) {
            discard();
            return;
        }
        setMarkerPosition(
                Vec3.atCenterOf(position).add(0.0D, 0.8D, 0.0D));
    }

    private void setMarkerPosition(Vec3 position) {
        setPos(position.x, position.y, position.z);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER_UUID)
                .ifPresent(uuid -> tag.putUUID("Owner", uuid));
        entityData.get(STAND_UUID)
                .ifPresent(uuid -> tag.putUUID("Stand", uuid));
        entityData.get(TARGET_UUID)
                .ifPresent(uuid -> tag.putUUID("Target", uuid));
        tag.putLong(
                "BlockPosition",
                entityData.get(BLOCK_POSITION).asLong());
        tag.putByte("MarkerKind", entityData.get(KIND));
        getStandSkin().ifPresent(skin ->
                tag.putString("StandSkin", skin.toString()));
        tag.putString("BoundDimension", boundDimension);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("Owner")
                        ? Optional.of(tag.getUUID("Owner"))
                        : Optional.empty());
        entityData.set(
                STAND_UUID,
                tag.hasUUID("Stand")
                        ? Optional.of(tag.getUUID("Stand"))
                        : Optional.empty());
        entityData.set(
                TARGET_UUID,
                tag.hasUUID("Target")
                        ? Optional.of(tag.getUUID("Target"))
                        : Optional.empty());
        entityData.set(
                BLOCK_POSITION,
                BlockPos.of(tag.getLong("BlockPosition")));
        entityData.set(KIND, tag.getByte("MarkerKind"));
        entityData.set(
                SELECTED_SKIN,
                Optional.ofNullable(ResourceLocation.tryParse(
                        tag.getString("StandSkin"))));
        boundDimension = tag.getString("BoundDimension");
    }

    @Override
    public ResourceLocation getStandType() {
        return KillerQueenAddon.id("killer_queen");
    }

    @Override
    public Optional<ResourceLocation> getStandSkin() {
        return entityData.get(SELECTED_SKIN);
    }
}
