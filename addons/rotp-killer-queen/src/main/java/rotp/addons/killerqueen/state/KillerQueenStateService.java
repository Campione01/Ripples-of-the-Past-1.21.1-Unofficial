package rotp.addons.killerqueen.state;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.JojoModConfig;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenPhase1Spec;
import rotp.addons.killerqueen.btd.BitesTheDustService;
import rotp.addons.killerqueen.entity.PrimaryBombMarkerEntity;
import rotp.addons.killerqueen.entity.SheerHeartAttackEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KillerQueenStateService {
    private static final double MARKER_SEARCH_RANGE = 256.0D;

    private KillerQueenStateService() {}

    public static boolean markPrimaryBomb(
            ServerPlayer owner, LivingEntity target) {
        if (!KillerQueenAddon.isKillerQueenUser(owner)
                || target == owner
                || !target.isAlive()) {
            return false;
        }
        boolean changed = KillerQueenAddon.state(owner).setPrimaryBomb(
                target.getUUID(),
                target.level().dimension().location());
        if (changed) {
            KillerQueenAddon.syncState(owner);
        }
        ensureMarkers(owner);
        return changed;
    }

    public static boolean clearPrimaryBomb(Player owner) {
        if (owner.level().isClientSide()
                || !owner.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        boolean changed =
                KillerQueenAddon.state(owner).clearPrimaryBomb();
        if (owner instanceof ServerPlayer serverPlayer) {
            discardMarkers(
                    serverPlayer,
                    PrimaryBombMarkerEntity.Kind.LIVING);
            if (changed) {
                KillerQueenAddon.syncState(serverPlayer);
            }
        }
        return changed;
    }

    public static boolean markBlockBomb(
            ServerPlayer owner, BlockPos position) {
        if (!KillerQueenAddon.isKillerQueenUser(owner)
                || position == null
                || owner.level().getBlockState(position).isAir()) {
            return false;
        }
        boolean changed = KillerQueenAddon.state(owner)
                .setBlockBomb(
                        position,
                        owner.level().dimension().location());
        if (changed) {
            KillerQueenAddon.syncState(owner);
        }
        ensureMarkers(owner);
        return changed;
    }

    public static boolean clearBlockBomb(Player owner) {
        if (owner.level().isClientSide()
                || !owner.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        boolean changed =
                KillerQueenAddon.state(owner).clearBlockBomb();
        if (owner instanceof ServerPlayer serverPlayer) {
            discardMarkers(
                    serverPlayer,
                    PrimaryBombMarkerEntity.Kind.BLOCK);
            if (changed) {
                KillerQueenAddon.syncState(serverPlayer);
            }
        }
        return changed;
    }

    // 1.16 kept the block bomb on the Stand entity, so removing or
    // replacing the Stand dropped it. Primary bomb and Sheer Heart stay.
    public static void onStandCleared(@Nullable LivingEntity user) {
        if (user instanceof Player owner) {
            clearBlockBomb(owner);
        }
    }

    public static boolean linkSheerHeart(
            ServerPlayer owner,
            SheerHeartAttackEntity sheerHeart) {
        if (!KillerQueenAddon.isKillerQueenUser(owner)
                || sheerHeart == null
                || sheerHeart.isRemoved()
                || !sheerHeart.isOwnedBy(owner.getUUID())) {
            return false;
        }
        boolean changed = KillerQueenAddon.state(owner)
                .setSheerHeart(
                        sheerHeart.getUUID(),
                        sheerHeart.level()
                                .dimension()
                                .location());
        if (changed) {
            KillerQueenAddon.syncState(owner);
        }
        return changed;
    }

    // 1.16 isSHpresent counts any SheerHeart of the owner around him, so one
    // whose link was let go while he was away (in another dimension) is his
    // again when he is back, unless he has summoned another meanwhile.
    public static boolean reclaimSheerHeart(
            ServerPlayer owner,
            SheerHeartAttackEntity sheerHeart) {
        // A respawned owner has no state yet; the bomb is still his
        if (!owner.isAlive()
                || !KillerQueenAddon.isKillerQueenUser(owner)
                || KillerQueenAddon.state(owner).hasSheerHeart()
                || sheerHeart.level() != owner.level()) {
            return false;
        }
        return linkSheerHeart(owner, sheerHeart);
    }

    // 1.16 SheerHeartAttack.isSHpresent looks for the bomb itself, so the
    // owner can summon again once it is gone.
    public static boolean releaseSheerHeart(
            ServerPlayer owner, UUID sheerHeartUuid) {
        if (sheerHeartUuid == null
                || !owner.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        KillerQueenState state = KillerQueenAddon.state(owner);
        if (!sheerHeartUuid.equals(state.sheerHeartUuid())) {
            return false;
        }
        boolean changed = state.clearSheerHeart();
        if (changed) {
            KillerQueenAddon.syncState(owner);
        }
        return changed;
    }

    // 1.16 isSHpresent searched the box 140 blocks around the user, so a
    // bomb farther away, ticking or not, no longer blocks a new summon; the
    // bomb itself stays and hunts.
    public static boolean releaseAbsentSheerHeart(ServerPlayer owner) {
        if (!owner.hasData(KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        KillerQueenState state = KillerQueenAddon.state(owner);
        if (!state.hasSheerHeart() || sheerHeartNearby(owner, state)) {
            return false;
        }
        boolean changed = state.clearSheerHeart();
        if (changed) {
            KillerQueenAddon.syncState(owner);
        }
        return changed;
    }

    private static boolean sheerHeartNearby(
            ServerPlayer owner, KillerQueenState state) {
        UUID link = state.sheerHeartUuid();
        if (link == null
                || !owner.level().dimension().location().equals(
                        state.sheerHeartDimension())
                || !(owner.level() instanceof ServerLevel level)) {
            return false;
        }
        return !level.getEntities(
                        KillerQueenAddon.SHEER_HEART_ENTITY.get(),
                        owner.getBoundingBox().inflate(
                                SheerHeartAttackEntity.MAX_OWNER_DISTANCE),
                        sheer -> !sheer.isRemoved()
                                && sheer.isOwnedBy(owner.getUUID())
                                && link.equals(sheer.getUUID()))
                .isEmpty();
    }

    // 1.16 SHABack removes every SheerHeart of the user inside the 140-block
    // box of isSHpresent, linked or not, and none beyond it.
    public static boolean recallSheerHeart(
            ServerPlayer owner) {
        if (!owner.hasData(
                KillerQueenAddon.KILLER_QUEEN_STATE)
                || !(owner.level() instanceof ServerLevel level)) {
            return false;
        }
        List<SheerHeartAttackEntity> bombs = List.copyOf(
                level.getEntities(
                        KillerQueenAddon.SHEER_HEART_ENTITY.get(),
                        owner.getBoundingBox().inflate(
                                SheerHeartAttackEntity.MAX_OWNER_DISTANCE),
                        sheer -> !sheer.isRemoved()
                                && sheer.isOwnedBy(owner.getUUID())));
        bombs.forEach(Entity::discard);
        boolean changed =
                KillerQueenAddon.state(owner).clearSheerHeart();
        if (changed) {
            KillerQueenAddon.syncState(owner);
        }
        return !bombs.isEmpty() || changed;
    }

    public static boolean clearAll(Player owner) {
        if (owner.level().isClientSide()
                || !owner.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        if (owner instanceof ServerPlayer serverPlayer) {
            // 1.16 SheerHeart outlives its owner's death; only its link goes
            discardMarkers(serverPlayer, null);
            BitesTheDustService.cancelForOwner(
                    serverPlayer.getServer(),
                    serverPlayer.getUUID());
            BitesTheDustService.removeTimeMarker(serverPlayer);
        }
        boolean changed = KillerQueenAddon.state(owner).clearAll();
        if (changed && owner instanceof ServerPlayer serverPlayer) {
            KillerQueenAddon.syncState(serverPlayer);
        }
        return changed;
    }

    /**
     * A logout or a dimension change. 1.16 saved the primary bomb with the player, so it stays, and its SheerHeart
     * is a saved mob of its own that stays where it is.
     */
    public static boolean clearSessionState(Player owner) {
        if (owner.level().isClientSide()
                || !owner.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        if (owner instanceof ServerPlayer serverPlayer) {
            // validateOwner puts the kept bomb's marker back once its target is in range again
            discardMarkers(serverPlayer, null);
        }
        boolean changed = KillerQueenAddon.state(owner)
                .clearSessionState();
        if (changed && owner instanceof ServerPlayer serverPlayer) {
            KillerQueenAddon.syncState(serverPlayer);
        }
        return changed;
    }

    @Nullable
    public static LivingEntity primaryBombTarget(Player owner) {
        return primaryBombTarget(
                owner, KillerQueenPhase1Spec.PRIMARY_BOMB_RANGE);
    }

    // 1.16 PunchBomb.entityInRange: the bomb is a UUID looked up around the owner, in whatever dimension that is,
    // with MCUtil.entitiesAround: any entity whose box reaches into the cube of range around the centre of the
    // owner's box (the slot, the press and the detonation all asked it), not a sphere. It had no alive filter, so
    // a killed target's body counts for the up to 20 ticks it stays in the level
    @Nullable
    public static LivingEntity primaryBombTarget(
            Player owner, double range) {
        KillerQueenState state = KillerQueenAddon.state(owner);
        UUID targetUuid = state.primaryTargetUuid();
        if (!state.hasPrimaryBomb()
                || !Double.isFinite(range)
                || range < 0.0D) {
            return null;
        }
        Vec3 center = owner.getBoundingBox().getCenter();
        AABB search = new AABB(
                center.subtract(range, range, range),
                center.add(range, range, range));
        for (LivingEntity candidate
                : owner.level().getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        entity -> entity != owner
                                && entity.getUUID().equals(targetUuid))) {
            return candidate;
        }
        return null;
    }

    // 1.16 KQStandEntity.tick (020 :38-47): the Stand held the block bomb and dropped it beyond 50 blocks of the
    // Stand, measured to the block's integer corner, or once the block was air, water or lava; a waterlogged block
    // kept it
    @Nullable
    public static BlockPos blockBombPos(Player owner) {
        KillerQueenState state = KillerQueenAddon.state(owner);
        StandEntity stand = KillerQueenAuthority.currentStand(owner);
        BlockPos position = state.blockBombPos();
        ResourceLocation dimension =
                state.blockBombDimension();
        if (stand == null
                || position == null
                || dimension == null
                || !dimension.equals(
                        owner.level().dimension().location())
                || stand.distanceToSqr(
                                position.getX(),
                                position.getY(),
                                position.getZ())
                        > KillerQueenPhase1Spec.BLOCK_BOMB_RANGE
                                * KillerQueenPhase1Spec
                                        .BLOCK_BOMB_RANGE
                || !isBlockBombBlock(owner.level().getBlockState(position))) {
            return null;
        }
        return position;
    }

    public static boolean isBlockBombBlock(BlockState state) {
        return !state.isAir() && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA);
    }

    @Nullable
    public static SheerHeartAttackEntity sheerHeart(
            ServerPlayer owner) {
        KillerQueenState state = KillerQueenAddon.state(owner);
        UUID uuid = state.sheerHeartUuid();
        ResourceLocation dimension =
                state.sheerHeartDimension();
        if (uuid == null
                || dimension == null
                || !dimension.equals(
                        owner.level().dimension().location())
                || !(owner.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(uuid);
        return entity instanceof SheerHeartAttackEntity sheer
                && sheer.isOwnedBy(owner.getUUID())
                        ? sheer
                        : null;
    }

    public static void validateOwner(ServerPlayer owner) {
        if (!owner.hasData(KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return;
        }
        if (!KillerQueenAddon.isKillerQueenUser(owner)) {
            KillerQueenState state = KillerQueenAddon.state(owner);
            boolean changed = BitesTheDustService
                    .removeTimeMarker(owner)
                    | state.clearOperationalState();
            if (changed) {
                KillerQueenAddon.syncState(owner);
            }
            return;
        }
        boolean changed = false;
        KillerQueenState state = KillerQueenAddon.state(owner);
        if (state.hasBlockBomb() && blockBombPos(owner) == null) {
            changed = state.clearBlockBomb();
            discardMarkers(
                    owner,
                    PrimaryBombMarkerEntity.Kind.BLOCK);
        }
        // The client offers Sheer Heart Attack from the synced state alone and
        // sends no press while it holds one, so the release that 1.16's
        // client-side isSHpresent gave runs here, with the dimension check.
        if (state.hasSheerHeart() && !sheerHeartNearby(owner, state)) {
            changed = state.clearSheerHeart() || changed;
        }
        if (changed) {
            KillerQueenAddon.syncState(owner);
        }
        ensureMarkers(owner);
    }

    public static int clearPrimaryBombReferences(
            MinecraftServer server, UUID targetUuid) {
        if (server == null || targetUuid == null) {
            return 0;
        }
        int cleared = 0;
        for (ServerPlayer owner
                : server.getPlayerList().getPlayers()) {
            if (owner.hasData(
                            KillerQueenAddon.KILLER_QUEEN_STATE)
                    && targetUuid.equals(
                            KillerQueenAddon.state(owner)
                                    .primaryTargetUuid())
                    && clearPrimaryBomb(owner)) {
                cleared++;
            }
        }
        return cleared;
    }

    private static void ensureMarkers(ServerPlayer owner) {
        StandEntity stand =
                KillerQueenAuthority.currentStand(owner);
        if (stand == null
                || !(owner.level()
                        instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = primaryBombTarget(owner);
        if (target != null
                && !hasMarker(
                        owner,
                        PrimaryBombMarkerEntity.Kind.LIVING)) {
            PrimaryBombMarkerEntity marker =
                    KillerQueenAddon.PRIMARY_BOMB_MARKER_ENTITY
                            .get()
                            .create(level);
            if (marker != null) {
                marker.bindLiving(owner, stand, target);
                if (!marker.isRemoved()) {
                    level.addFreshEntity(marker);
                }
            }
        }
        BlockPos position = blockBombPos(owner);
        if (position != null
                && !hasMarker(
                        owner,
                        PrimaryBombMarkerEntity.Kind.BLOCK)) {
            PrimaryBombMarkerEntity marker =
                    KillerQueenAddon.PRIMARY_BOMB_MARKER_ENTITY
                            .get()
                            .create(level);
            if (marker != null) {
                marker.bindBlock(owner, stand, position);
                if (!marker.isRemoved()) {
                    level.addFreshEntity(marker);
                }
            }
        }
    }

    private static boolean hasMarker(
            ServerPlayer owner,
            PrimaryBombMarkerEntity.Kind kind) {
        return !owner.level().getEntitiesOfClass(
                        PrimaryBombMarkerEntity.class,
                        owner.getBoundingBox().inflate(
                                MARKER_SEARCH_RANGE),
                        marker -> marker.isOwnedBy(
                                owner.getUUID(), kind))
                .isEmpty();
    }

    private static void discardMarkers(
            ServerPlayer owner,
            @Nullable PrimaryBombMarkerEntity.Kind kind) {
        for (PrimaryBombMarkerEntity marker
                : owner.level().getEntitiesOfClass(
                        PrimaryBombMarkerEntity.class,
                        owner.getBoundingBox().inflate(
                                MARKER_SEARCH_RANGE),
                        marker -> marker.getOwnerUuid() != null
                                && marker.getOwnerUuid().equals(
                                        owner.getUUID())
                                && (kind == null
                                        || marker.markerKind()
                                                == kind))) {
            marker.discard();
        }
    }

    public static boolean armBitesTheDust(
            ServerPlayer owner,
            UUID victimUuid,
            long markerGameTime,
            ResourceLocation markerDimension) {
        return BitesTheDustService.armBitesTheDust(
                owner,
                victimUuid,
                markerGameTime,
                markerDimension);
    }

    public static boolean clearBitesTheDust(ServerPlayer owner) {
        return BitesTheDustService.removeTimeMarker(owner);
    }

    public static boolean linkTimeStop(
            ServerPlayer owner, int instanceId) {
        boolean changed = KillerQueenAddon.state(owner)
                .linkTimeStop(instanceId);
        if (changed) {
            KillerQueenAddon.syncState(owner);
        }
        return changed;
    }

    public static boolean unlinkTimeStop(
            ServerPlayer owner, int instanceId) {
        if (!owner.hasData(
                KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        boolean changed = KillerQueenAddon.state(owner)
                .unlinkTimeStop(instanceId);
        if (changed) {
            KillerQueenAddon.syncState(owner);
        }
        return changed;
    }

    /** 1.16 ExplodeBlockAction: sqrt(standDamageMultiplier) * 2. */
    public static float blockBlastRadius() {
        float multiplier = JojoModConfig.getCommonConfigInstance(false)
                .standDamageMultiplier.get().floatValue();
        return (float) Math.sqrt(multiplier) * 2.0F;
    }

    /**
     * 1.16 ExplodeBlockAction.standPerform: the bombed block was set to air only when its explosion resistance was
     * below 3600000 (bedrock, barriers, command blocks and the like stay).
     */
    public static boolean blockBombBreaks(BlockState state, BlockGetter level, BlockPos position) {
        return !state.isAir() && state.getExplosionResistance(level, position, null) < 3600000.0F;
    }
}
