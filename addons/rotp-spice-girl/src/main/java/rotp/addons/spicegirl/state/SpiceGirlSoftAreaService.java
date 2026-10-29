package rotp.addons.spicegirl.state;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.addons.spicegirl.SpiceGirlAddon;
import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SpiceGirlSoftAreaService {
    public static final int DURATION_TICKS = 450;
    public static final int AREA_RADIUS = 1;

    private SpiceGirlSoftAreaService() {}

    public static boolean mark(
            ServerPlayer owner,
            SpiceGirlStandEntity stand,
            BlockPos center) {
        if (!(owner.level() instanceof ServerLevel level)
                || stand != SpiceGirlAddon.activeStand(owner)
                || center == null
                || center.distSqr(owner.blockPosition())
                        > SpiceGirlAddon.MAX_RANGE
                                * SpiceGirlAddon.MAX_RANGE) {
            return false;
        }

        List<BlockPos> positions =
                areaPositions(center);
        if (positions.stream().anyMatch(
                position -> loadedChunk(level, position) == null)) {
            return false;
        }

        SoftAreaIndexState.World index =
                SpiceGirlAddon.softAreaIndex(level);
        long gameTime = level.getGameTime();
        long expiresAt =
                gameTime + DURATION_TICKS;
        if (!index.upsertArea(
                positions,
                owner.getUUID(),
                stand.getUUID(),
                expiresAt,
                gameTime)) {
            return false;
        }

        SpiceGirlAddon.softAreaState(owner).set(
                positions,
                stand.getUUID(),
                level.dimension().location(),
                expiresAt);
        emitAreaFeedback(level, center);
        return true;
    }

    public static boolean isSoftArea(
            ServerLevel level, BlockPos position) {
        return controllerAt(level, position) != null;
    }

    @Nullable
    public static SoftAreaIndexState.Entry controllerAt(
            ServerLevel level, BlockPos position) {
        if (level == null || position == null) {
            return null;
        }
        if (loadedChunk(level, position) == null
                || !level.hasData(SpiceGirlAddon.SOFT_AREA_INDEX)) {
            return null;
        }
        List<SoftAreaIndexState.Entry> entries =
                SpiceGirlAddon.softAreaIndex(level)
                        .entriesAt(position, level.getGameTime());
        return entries.isEmpty() ? null : entries.get(0);
    }

    @Nullable
    public static SoftAreaState validate(ServerPlayer owner) {
        if (owner == null
                || !owner.hasData(SpiceGirlAddon.SOFT_AREA)) {
            return null;
        }
        SoftAreaState state =
                SpiceGirlAddon.softAreaState(owner);
        if (!state.isActive()) {
            return null;
        }
        if (!(owner.level() instanceof ServerLevel level)
                || !owner.isAlive()
                || owner.isRemoved()
                || owner.isSpectator()
                || !level.dimension().location().equals(
                        state.dimension())
                || level.getGameTime() >= state.expiresAt()) {
            clearOwner(owner);
            return null;
        }
        return state;
    }

    public static void removeBlock(
            ServerLevel level, BlockPos position) {
        if (loadedChunk(level, position) == null
                || !level.hasData(SpiceGirlAddon.SOFT_AREA_INDEX)) {
            return;
        }
        SpiceGirlAddon.softAreaIndex(level).removePosition(position);
    }

    // Owner state is bookkeeping; world entries expire independently.
    public static boolean clearOwner(ServerPlayer owner) {
        if (owner == null
                || !owner.hasData(SpiceGirlAddon.SOFT_AREA)) {
            return false;
        }
        return SpiceGirlAddon.softAreaState(owner).clear();
    }

    private static List<BlockPos> areaPositions(
            BlockPos center) {
        List<BlockPos> positions =
                new ArrayList<>(SoftAreaState.MAX_POSITIONS);
        for (int x = -AREA_RADIUS; x <= AREA_RADIUS; x++) {
            for (int y = -AREA_RADIUS;
                    y <= AREA_RADIUS; y++) {
                for (int z = -AREA_RADIUS;
                        z <= AREA_RADIUS; z++) {
                    positions.add(
                            center.offset(x, y, z));
                }
            }
        }
        return positions;
    }

    @Nullable
    private static LevelChunk loadedChunk(
            ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(
                position.getX() >> 4,
                position.getZ() >> 4);
    }

    private static void emitAreaFeedback(
            ServerLevel level, BlockPos center) {
        for (int x = -AREA_RADIUS; x <= AREA_RADIUS; x++) {
            for (int y = -AREA_RADIUS;
                    y <= AREA_RADIUS; y++) {
                for (int z = -AREA_RADIUS;
                        z <= AREA_RADIUS; z++) {
                    if (Math.abs(x) != AREA_RADIUS
                            && Math.abs(y) != AREA_RADIUS
                            && Math.abs(z) != AREA_RADIUS) {
                        continue;
                    }
                    BlockPos position =
                            center.offset(x, y, z);
                    level.sendParticles(
                            ParticleTypes.CLOUD,
                            position.getX() + 0.5D,
                            position.getY() + 0.5D,
                            position.getZ() + 0.5D,
                            3,
                            0.2D,
                            0.2D,
                            0.2D,
                            0.02D);
                }
            }
        }
        level.playSound(
                null,
                center,
                SoundEvents.HONEY_BLOCK_PLACE,
                SoundSource.BLOCKS,
                1.0F,
                0.8F + level.random.nextFloat() * 0.4F);
    }
}
