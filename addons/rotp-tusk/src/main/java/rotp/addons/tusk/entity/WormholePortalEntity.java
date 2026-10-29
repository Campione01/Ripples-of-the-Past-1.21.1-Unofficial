package rotp.addons.tusk.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.state.TuskPortalOperations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class WormholePortalEntity extends TuskOperationEntity {
    public static final int OCCUPANT_DELAY_TICKS = 60;
    public static final int ACCELERATED_DELAY_TICKS = 2;
    public static final float NON_TUSK_DAMAGE = 15.0F;

    private static final String PEER_TAG = "Peer";
    private static final String ENTRY_TAG = "Entry";

    private static final EntityDataAccessor<Optional<UUID>> PEER_UUID =
            SynchedEntityData.defineId(
                    WormholePortalEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> ENTRY =
            SynchedEntityData.defineId(
                    WormholePortalEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private final Map<UUID, Integer> occupantCountdowns =
            new HashMap<>();

    public WormholePortalEntity(
            EntityType<? extends WormholePortalEntity> entityType,
            Level level) {
        super(entityType, level);
    }

    public WormholePortalEntity(
            Level level,
            UUID operationUuid,
            UUID ownerUuid,
            long expiresAt,
            boolean entry) {
        this(TuskAddon.WORMHOLE_PORTAL_ENTITY.get(), level);
        initializeOperation(operationUuid, ownerUuid, expiresAt);
        setEntry(entry);
    }

    public UUID getPeerUuid() {
        return entityData.get(PEER_UUID).orElse(null);
    }

    public void setPeerUuid(UUID peerUuid) {
        entityData.set(PEER_UUID, Optional.ofNullable(peerUuid));
    }

    public boolean isEntry() {
        return entityData.get(ENTRY);
    }

    private void setEntry(boolean entry) {
        entityData.set(ENTRY, entry);
    }

    @Override
    public TuskPortalOperations.MemberRole memberRole() {
        return isEntry()
                ? TuskPortalOperations.MemberRole.ENTRY
                : TuskPortalOperations.MemberRole.EXIT;
    }

    public boolean contains(Entity entity) {
        return occupancyBounds().intersects(entity.getBoundingBox());
    }

    public void accelerateOccupant(UUID occupantUuid) {
        if (occupantUuid != null) {
            occupantCountdowns.compute(
                    occupantUuid,
                    (uuid, remaining) -> Math.min(
                            remaining != null
                                    ? remaining
                                    : OCCUPANT_DELAY_TICKS,
                            ACCELERATED_DELAY_TICKS));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        TuskPortalOperations operations =
                TuskPortalOperations.get(serverLevel.getServer());
        WormholePortalEntity peer =
                operations.resolvePeer(this);
        if (peer == null || peer.level() != level()) {
            occupantCountdowns.clear();
            return;
        }

        // 1.16 WormholeTeleporterEntity.tick: an AbstractWormholeEntity (portal, arm, damaging wormhole) is never sent.
        Set<UUID> present = new HashSet<>();
        for (Entity occupant : serverLevel.getEntities(
                this,
                occupancyBounds(),
                entity -> entity.isAlive()
                        && entity.canUsePortal(false)
                        && !(entity instanceof TuskOperationEntity)
                        && !(entity instanceof WormholeEntity))) {
            UUID occupantUuid = occupant.getUUID();
            present.add(occupantUuid);
            int remaining = occupantCountdowns.getOrDefault(
                    occupantUuid, OCCUPANT_DELAY_TICKS);
            if (remaining > 1) {
                occupantCountdowns.put(occupantUuid, remaining - 1);
                continue;
            }

            occupantCountdowns.put(
                    occupantUuid, OCCUPANT_DELAY_TICKS);
            peer.occupantCountdowns.put(
                    occupantUuid, OCCUPANT_DELAY_TICKS);
            occupant.teleportTo(
                    peer.getX(),
                    peer.getY() + Math.max(0.1D, occupant.getBbHeight() * 0.1D),
                    peer.getZ());
            if (occupant instanceof LivingEntity living
                    && !(occupant instanceof net.minecraft.world.entity.player.Player
                            player
                            && TuskAddon.isTuskUser(player))) {
                living.hurt(
                        serverLevel.damageSources().magic(),
                        NON_TUSK_DAMAGE);
            }
            serverLevel.playSound(
                    null,
                    peer,
                    TuskAddon.WORMHOLE_TELEPORT.get(),
                    peer.getSoundSource(),
                    1.0F,
                    1.0F);
        }
        occupantCountdowns.keySet().retainAll(present);
    }

    private AABB occupancyBounds() {
        return getBoundingBox().inflate(0.05D, 0.45D, 0.05D);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PEER_UUID, Optional.empty());
        builder.define(ENTRY, false);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID peerUuid = getPeerUuid();
        if (peerUuid != null) {
            tag.putUUID(PEER_TAG, peerUuid);
        }
        tag.putBoolean(ENTRY_TAG, isEntry());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPeerUuid(
                tag.hasUUID(PEER_TAG) ? tag.getUUID(PEER_TAG) : null);
        setEntry(tag.getBoolean(ENTRY_TAG));
    }
}
