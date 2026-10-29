package rotp.addons.killerqueenlion.state;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.JojoModConfig;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.KillerQueenPhase1Spec;
import rotp.addons.killerqueenlion.btd.BitesTheDustService;
import rotp.addons.killerqueenlion.entity.PrimaryBombMarkerEntity;
import rotp.addons.killerqueenlion.entity.SheerHeartAttackEntity;
import rotp.addons.killerqueenlion.entity.BubbleMarkEntity;
import rotp.addons.killerqueenlion.entity.KillerQueenBombProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KillerQueenStateService {
    private static final double MARKER_SEARCH_RANGE = 256.0D;

    private KillerQueenStateService() {}

    public static boolean markPrimaryBomb(
            ServerPlayer owner, LivingEntity target) {
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)
                || target == owner
                || !target.isAlive()) {
            return false;
        }
        boolean changed = KillerQueenLionAddon.state(owner).setPrimaryBomb(
                target.getUUID(),
                target.level().dimension().location());
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        ensureMarkers(owner);
        return changed;
    }

    public static boolean clearPrimaryBomb(Player owner) {
        if (owner.level().isClientSide()
                || !owner.hasData(
                        KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        boolean changed =
                KillerQueenLionAddon.state(owner).clearPrimaryBomb();
        if (owner instanceof ServerPlayer serverPlayer) {
            discardMarkers(
                    serverPlayer,
                    PrimaryBombMarkerEntity.Kind.LIVING);
            if (changed) {
                KillerQueenLionAddon.syncState(serverPlayer);
            }
        }
        return changed;
    }

    public static boolean markBlockBomb(
            ServerPlayer owner, BlockPos position) {
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)
                || position == null
                || owner.level().getBlockState(position).isAir()) {
            return false;
        }
        boolean changed = KillerQueenLionAddon.state(owner)
                .setBlockBomb(
                        position,
                        owner.level().dimension().location());
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        ensureMarkers(owner);
        return changed;
    }

    public static boolean clearBlockBomb(Player owner) {
        if (owner.level().isClientSide()
                || !owner.hasData(
                        KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        boolean changed =
                KillerQueenLionAddon.state(owner).clearBlockBomb();
        if (owner instanceof ServerPlayer serverPlayer) {
            discardMarkers(
                    serverPlayer,
                    PrimaryBombMarkerEntity.Kind.BLOCK);
            if (changed) {
                KillerQueenLionAddon.syncState(serverPlayer);
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

    public static boolean markBubbleBlock(
            ServerPlayer owner, BlockPos position) {
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)
                || position == null
                || owner.level().getBlockState(position).isAir()) {
            return false;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        long now = owner.level().getGameTime();
        // 1.16 LivingData.setBlockMark: old marks are dropped here and nowhere else
        boolean changed = state.purgeExpiredBubbleBlockMarks(
                now,
                KillerQueenPhase1Spec.BUBBLE_BLOCK_MARK_LIFETIME_TICKS);
        changed = state.addBubbleBlockMark(
                        position,
                        now,
                        owner.level().dimension().location())
                || changed;
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }

    // The marks belong to the dimension they were placed in and wait there.
    public static boolean hasBubbleBlockMarks(Player owner) {
        if (owner == null
                || !owner.hasData(KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        return !state.bubbleBlockMarks().isEmpty()
                && owner.level().dimension().location().equals(
                        state.bubbleBlockDimension());
    }

    /** 1.16 ExplodeBlockAction: sqrt(standDamageMultiplier) * 2, for a bubble mark and for the block bomb. */
    public static float blockBlastRadius() {
        float multiplier = JojoModConfig.getCommonConfigInstance(false)
                .standDamageMultiplier.get().floatValue();
        return (float) Math.sqrt(multiplier) * 2.0F;
    }

    public static int detonateBubbleBlockMarks(ServerPlayer owner) {
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)
                || !(owner.level() instanceof ServerLevel level)) {
            return 0;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (!level.dimension().location().equals(
                state.bubbleBlockDimension())) {
            return 0;
        }
        List<BlockPos> marks = List.copyOf(state.bubbleBlockMarks());
        state.clearBubbleBlockMarks();
        if (!marks.isEmpty()) {
            KillerQueenLionAddon.syncState(owner);
        }
        float radius = blockBlastRadius();
        // 1.16 ExplodeBlockAction: every stored mark explodes at the integer corner of its block,
        // whether or not that block is still there; the block goes below the 3600000 resistance limit
        for (BlockPos mark : marks) {
            var blockState = level.getBlockState(mark);
            if (blockBombBreaks(blockState, level, mark)) {
                level.destroyBlock(mark, false, owner);
            }
            level.explode(
                    owner,
                    mark.getX(),
                    mark.getY(),
                    mark.getZ(),
                    radius,
                    Level.ExplosionInteraction.NONE);
        }
        return marks.size();
    }

    public static boolean linkSheerHeart(
            ServerPlayer owner,
            SheerHeartAttackEntity sheerHeart) {
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)
                || sheerHeart == null
                || sheerHeart.isRemoved()
                || !sheerHeart.isOwnedBy(owner.getUUID())) {
            return false;
        }
        boolean changed = KillerQueenLionAddon.state(owner)
                .setSheerHeart(
                        sheerHeart.summonLink(),
                        sheerHeart.level()
                                .dimension()
                                .location());
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }

    // 1.16 SHABack removes every SheerHeart of the user inside the 140-block
    // box of isSHpresent, of any summon, and none beyond it.
    public static boolean recallSheerHeart(
            ServerPlayer owner) {
        if (!owner.hasData(
                KillerQueenLionAddon.KILLER_QUEEN_STATE)
                || !(owner.level() instanceof ServerLevel level)) {
            return false;
        }
        List<SheerHeartAttackEntity> bombs = List.copyOf(
                level.getEntities(
                        KillerQueenLionAddon.SHEER_HEART_ENTITY.get(),
                        owner.getBoundingBox().inflate(
                                SheerHeartAttackEntity.MAX_OWNER_DISTANCE),
                        sheer -> !sheer.isRemoved()
                                && sheer.isOwnedBy(owner.getUUID())));
        bombs.forEach(Entity::discard);
        boolean changed =
                KillerQueenLionAddon.state(owner).clearSheerHeart();
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        return !bombs.isEmpty() || changed;
    }

    // 1.16 SheerHeartAttack.isSHpresent looks for the bombs themselves, so
    // the owner can summon again once the last one of a summon is gone.
    public static boolean releaseSheerHeart(
            ServerPlayer owner, UUID summonLink) {
        if (summonLink == null
                || !owner.hasData(
                        KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (!summonLink.equals(state.sheerHeartUuid())
                || !sheerHearts(owner).isEmpty()) {
            return false;
        }
        boolean changed = state.clearSheerHeart();
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }

    // 1.16 isSHpresent counts any loaded SheerHeart of the owner around him,
    // so a bomb whose summon was let go while it was not loaded (a login, a
    // return from another dimension) holds it again, unless he has summoned
    // anew meanwhile.
    public static boolean reclaimSheerHeart(
            ServerPlayer owner,
            SheerHeartAttackEntity sheerHeart) {
        // A respawned owner has no state yet; the bombs are still his
        if (!owner.isAlive()
                || !KillerQueenLionAddon.isKillerQueenUser(owner)
                || KillerQueenLionAddon.state(owner).hasSheerHeart()
                || sheerHeart.level() != owner.level()) {
            return false;
        }
        return linkSheerHeart(owner, sheerHeart);
    }

    // 1.16 isSHpresent searched the box 140 blocks around the user, so a
    // summon whose bombs are all farther, ticking or not, no longer blocks
    // a new one; the bombs themselves stay and hunt.
    public static boolean releaseAbsentSheerHeart(ServerPlayer owner) {
        if (!owner.hasData(KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (!state.hasSheerHeart() || sheerHeartNearby(owner, state)) {
            return false;
        }
        boolean changed = state.clearSheerHeart();
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
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
                        KillerQueenLionAddon.SHEER_HEART_ENTITY.get(),
                        owner.getBoundingBox().inflate(
                                SheerHeartAttackEntity.MAX_OWNER_DISTANCE),
                        sheer -> !sheer.isRemoved()
                                && sheer.isOwnedBy(owner.getUUID())
                                && link.equals(sheer.summonLink()))
                .isEmpty();
    }

    public static boolean clearAll(Player owner) {
        if (owner.level().isClientSide()
                || !owner.hasData(
                        KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        if (owner instanceof ServerPlayer serverPlayer) {
            // 1.16 SheerHearts outlive their owner's death; only the link goes
            discardMarkers(serverPlayer, null);
            discardProjectiles(serverPlayer);
            BitesTheDustService.cancelForOwner(
                    serverPlayer.getServer(),
                    serverPlayer.getUUID());
            BitesTheDustService.removeTimeMarker(serverPlayer);
        }
        boolean changed = KillerQueenLionAddon.state(owner).clearAll();
        if (changed && owner instanceof ServerPlayer serverPlayer) {
            KillerQueenLionAddon.syncState(serverPlayer);
        }
        return changed;
    }

    /**
     * A logout or a dimension change. 1.16 saved the primary bomb and the bubble block marks with the player, so
     * they stay, and its SheerHearts are saved mobs of their own that stay where they are.
     */
    public static boolean clearSessionState(Player owner) {
        if (owner.level().isClientSide()
                || !owner.hasData(
                        KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        if (owner instanceof ServerPlayer serverPlayer) {
            // validateOwner puts the kept bomb's marker back once its target is in range again
            discardMarkers(serverPlayer, null);
            discardProjectiles(serverPlayer);
        }
        boolean changed = KillerQueenLionAddon.state(owner)
                .clearSessionState();
        if (changed && owner instanceof ServerPlayer serverPlayer) {
            KillerQueenLionAddon.syncState(serverPlayer);
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
        KillerQueenState state = KillerQueenLionAddon.state(owner);
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

    @Nullable
    public static BlockPos blockBombPos(Player owner) {
        KillerQueenState state = KillerQueenLionAddon.state(owner);
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

    /**
     * 1.16 ExplodeBlockAction.standPerform: the bombed block was set to air only when its explosion resistance was
     * below 3600000 (bedrock, barriers, command blocks and the like stay).
     */
    public static boolean blockBombBreaks(BlockState state, BlockGetter level, BlockPos position) {
        return !state.isAir() && state.getExplosionResistance(level, position, null) < 3600000.0F;
    }

    // The loaded bombs of the owner's current summon
    public static List<SheerHeartAttackEntity> sheerHearts(
            ServerPlayer owner) {
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        UUID link = state.sheerHeartUuid();
        ResourceLocation dimension =
                state.sheerHeartDimension();
        if (link == null
                || dimension == null
                || !dimension.equals(
                        owner.level().dimension().location())
                || !(owner.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return List.copyOf(level.getEntities(
                KillerQueenLionAddon.SHEER_HEART_ENTITY.get(),
                sheer -> !sheer.isRemoved()
                        && sheer.isOwnedBy(owner.getUUID())
                        && link.equals(sheer.summonLink())));
    }

    public static void validateOwner(ServerPlayer owner) {
        if (!owner.hasData(KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return;
        }
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)) {
            KillerQueenState state = KillerQueenLionAddon.state(owner);
            boolean changed = BitesTheDustService
                    .removeTimeMarker(owner)
                    | state.clearOperationalState();
            if (changed) {
                KillerQueenLionAddon.syncState(owner);
            }
            return;
        }
        boolean changed = false;
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (state.hasBlockBomb() && blockBombPos(owner) == null) {
            changed = state.clearBlockBomb() || changed;
            discardMarkers(
                    owner,
                    PrimaryBombMarkerEntity.Kind.BLOCK);
        }
        // The client offers Sheer Heart Attack from the synced state alone and
        // sends no press while it holds a summon, so the release that 1.16's
        // client-side isSHpresent gave runs here, with the dimension check.
        if (state.hasSheerHeart() && !sheerHeartNearby(owner, state)) {
            changed = state.clearSheerHeart() || changed;
        }
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
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
                            KillerQueenLionAddon.KILLER_QUEEN_STATE)
                    && targetUuid.equals(
                            KillerQueenLionAddon.state(owner)
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
                    KillerQueenLionAddon.PRIMARY_BOMB_MARKER_ENTITY
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
                    KillerQueenLionAddon.PRIMARY_BOMB_MARKER_ENTITY
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

    private static void discardProjectiles(ServerPlayer owner) {
        for (KillerQueenBombProjectile projectile
                : owner.level().getEntitiesOfClass(
                        KillerQueenBombProjectile.class,
                        owner.getBoundingBox().inflate(MARKER_SEARCH_RANGE),
                        entity -> entity.isOwnedBy(owner.getUUID()))) {
            projectile.discard();
        }
        for (BubbleMarkEntity mark
                : owner.level().getEntitiesOfClass(
                        BubbleMarkEntity.class,
                        owner.getBoundingBox().inflate(MARKER_SEARCH_RANGE),
                        entity -> entity.isOwnedBy(owner.getUUID()))) {
            mark.discard();
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
        boolean changed = KillerQueenLionAddon.state(owner)
                .linkTimeStop(instanceId);
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }

    public static boolean unlinkTimeStop(
            ServerPlayer owner, int instanceId) {
        if (!owner.hasData(
                KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        boolean changed = KillerQueenLionAddon.state(owner)
                .unlinkTimeStop(instanceId);
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }
}
