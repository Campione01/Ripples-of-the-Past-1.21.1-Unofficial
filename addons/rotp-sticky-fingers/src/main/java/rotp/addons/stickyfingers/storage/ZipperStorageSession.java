package rotp.addons.stickyfingers.storage;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;

public record ZipperStorageSession(
        Kind kind,
        UUID viewerId,
        @Nullable UUID targetId,
        @Nullable BlockZipperStorageKey blockKey) {
    public enum Kind {
        ENTITY,
        INNER,
        BLOCK
    }

    public static ZipperStorageSession entity(
            ServerPlayer viewer,
            LivingEntity target,
            boolean inner) {
        return new ZipperStorageSession(
                inner ? Kind.INNER : Kind.ENTITY,
                viewer.getUUID(),
                target.getUUID(),
                null);
    }

    public static ZipperStorageSession block(
            ServerPlayer viewer,
            BlockZipperStorageKey key) {
        return new ZipperStorageSession(
                Kind.BLOCK,
                viewer.getUUID(),
                null,
                key);
    }

    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) {
            return true;
        }
        if (!(player instanceof ServerPlayer viewer)
                || !viewer.getUUID().equals(viewerId)
                || !StickyFingersAddon
                        .isStickyFingersActive(viewer)) {
            return false;
        }
        ZipperStorageData data =
                ZipperStorageData.get(viewer.getServer());
        return switch (kind) {
            case ENTITY -> validEntity(viewer, data, false);
            case INNER -> validEntity(viewer, data, true);
            case BLOCK -> validBlock(viewer, data);
        };
    }

    public boolean referencesViewer(UUID id) {
        return viewerId.equals(id);
    }

    public boolean referencesTarget(UUID id) {
        return targetId != null && targetId.equals(id);
    }

    public boolean referencesBlock(
            BlockZipperStorageKey key) {
        return blockKey != null && blockKey.equals(key);
    }

    public boolean referencesChunk(
            ServerLevel level, ChunkPos chunk) {
        if (blockKey != null) {
            return blockKey.dimension().equals(
                            level.dimension().location())
                    && new ChunkPos(blockKey.position())
                            .equals(chunk);
        }
        if (targetId == null) {
            return false;
        }
        Entity target = level.getEntity(targetId);
        return target != null
                && target.chunkPosition().equals(chunk);
    }

    private boolean validEntity(
            ServerPlayer viewer,
            ZipperStorageData data,
            boolean inner) {
        if (targetId == null
                || !data.hasEntityStorage(targetId)
                || inner
                        && !targetId.equals(viewer.getUUID())) {
            return false;
        }
        Entity entity = targetId.equals(viewer.getUUID())
                ? viewer
                : viewer.serverLevel().getEntity(targetId);
        return entity instanceof LivingEntity target
                && target.isAlive()
                && !target.isRemoved()
                && target.getBoundingBox().distanceToSqr(
                        viewer.getEyePosition())
                        <= StickyFingersPhase1Spec
                                .STORAGE_RANGE_SQR;
    }

    private boolean validBlock(
            ServerPlayer viewer,
            ZipperStorageData data) {
        if (blockKey == null
                || !blockKey.dimension().equals(
                        viewer.serverLevel()
                                .dimension().location())
                || !data.canAccessBlockStorage(
                        blockKey, viewer.getUUID())) {
            return false;
        }
        BlockPos position = blockKey.position();
        ServerLevel level = viewer.serverLevel();
        return level.hasChunkAt(position)
                && !level.getBlockState(position).isAir()
                && new AABB(position).distanceToSqr(
                        viewer.getEyePosition())
                        <= StickyFingersPhase1Spec
                                .STORAGE_RANGE_SQR;
    }
}
