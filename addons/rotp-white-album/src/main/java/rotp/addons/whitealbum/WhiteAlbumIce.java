package rotp.addons.whitealbum;

import java.util.Set;

import rotp.core.subsystems.target.LiquidOnlyClipContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

public final class WhiteAlbumIce {
    private static final BlockState FROSTED_ICE =
            Blocks.FROSTED_ICE.defaultBlockState();
    // Donor WhiteAlbumEntity.setIceLayer (059 l.143-150) placed the layer
    // only into Material.AIR or Material.REPLACEABLE_PLANT. 1.21 has no
    // materials, and its replaceable flag also covers water, lava, fire,
    // snow layers, bubble columns and light blocks, so the 1.16.5 members
    // are named instead: isAir() is air/cave air/void air, and these are
    // the vanilla REPLACEABLE_PLANT blocks (1.16 "grass" is SHORT_GRASS).
    private static final Set<Block> DONOR_LAYER_PLANTS = Set.of(
            Blocks.SHORT_GRASS,
            Blocks.FERN,
            Blocks.DEAD_BUSH,
            Blocks.VINE,
            Blocks.SUNFLOWER,
            Blocks.LILAC,
            Blocks.ROSE_BUSH,
            Blocks.PEONY,
            Blocks.TALL_GRASS,
            Blocks.LARGE_FERN);

    private WhiteAlbumIce() {}

    public static void freezeHeldWater(
            LivingEntity user,
            Level level) {
        if (level.isClientSide()) {
            return;
        }
        if (user.onGround()) {
            BlockPos center = user.blockPosition();
            int radius = WhiteAlbumSpec.FREEZE_WATER_RADIUS;
            BlockPos.MutableBlockPos above =
                    new BlockPos.MutableBlockPos();
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-radius, -1, -radius),
                    center.offset(radius, -1, radius))) {
                if (!pos.closerToCenterThan(
                        user.position(), radius)) {
                    continue;
                }
                above.set(pos.getX(), pos.getY() + 1, pos.getZ());
                if (level.getBlockState(above).isAir()) {
                    freezeWater(level, pos, user);
                }
            }
        }

        Vec3 eye = user.getEyePosition(1.0F);
        Vec3 look = user.getLookAngle();
        HitResult result = level.clip(
                new LiquidOnlyClipContext(
                        eye.add(look),
                        eye.add(look.scale(
                                WhiteAlbumSpec
                                        .FREEZE_WATER_RAY_RANGE)),
                        ClipContext.Fluid.SOURCE_ONLY,
                        user));
        if (result.getType() == HitResult.Type.BLOCK) {
            freezeWater(
                    level,
                    ((BlockHitResult) result).getBlockPos(),
                    user);
        }
    }

    public static void freezeFloor(
            LivingEntity user,
            Level level) {
        if (level.isClientSide()) {
            return;
        }
        int radius = WhiteAlbumSpec.FREEZE_FLOOR_RADIUS;
        BlockPos center = user.blockPosition();
        BlockPos.MutableBlockPos above =
                new BlockPos.MutableBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -1, -radius),
                center.offset(radius, -1, radius))) {
            above.set(pos.getX(), pos.getY() + 1, pos.getZ());
            if (pos.closerToCenterThan(user.position(), radius)
                    && level.getBlockState(above).isAir()) {
                freezeWater(level, pos, user);
            }
            placeLayer(level, pos, user);
        }
    }

    public static void unfreeze(
            LivingEntity user,
            Level level) {
        if (level.isClientSide()) {
            return;
        }
        int radius = WhiteAlbumSpec.UNFREEZE_RADIUS;
        BlockPos center = user.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (pos.closerToCenterThan(user.position(), radius)
                    && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos)
                            .is(Blocks.FROSTED_ICE)) {
                level.setBlockAndUpdate(
                        pos, Blocks.WATER.defaultBlockState());
            }
            BlockPos above = pos.above();
            if (level.getBlockState(above)
                    .is(WhiteAlbumAddon.ICE_LAYER.get())) {
                level.setBlockAndUpdate(
                        above, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void freezeWater(
            Level level,
            BlockPos pos,
            LivingEntity user) {
        BlockState state = level.getBlockState(pos);
        boolean sourceWater = state.is(Blocks.WATER)
                && state.getValue(LiquidBlock.LEVEL) == 0;
        if (sourceWater
                && FROSTED_ICE.canSurvive(level, pos)
                && level.noCollision(
                        FROSTED_ICE
                                .getCollisionShape(level, pos)
                                .bounds()
                                .move(pos))
                && !EventHooks.onBlockPlace(
                        user,
                        BlockSnapshot.create(
                                level.dimension(), level, pos),
                        Direction.UP)) {
            level.setBlockAndUpdate(pos, FROSTED_ICE);
            level.scheduleTick(
                    pos,
                    Blocks.FROSTED_ICE,
                    Mth.nextInt(
                            user.getRandom(),
                            WhiteAlbumSpec.ICE_MELT_MIN_TICKS,
                            WhiteAlbumSpec.ICE_MELT_MAX_TICKS));
        }
    }

    private static void placeLayer(
            Level level,
            BlockPos belowPos,
            LivingEntity user) {
        BlockPos pos = belowPos.above();
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(belowPos);
        BlockState layer =
                WhiteAlbumAddon.ICE_LAYER.get().defaultBlockState();
        // The donor floor test was func_235785_r_, isCollisionShapeFullBlock:
        // a full collision cube, not merely a sturdy top face.
        if ((state.isAir()
                        || DONOR_LAYER_PLANTS.contains(state.getBlock()))
                && !below.isAir()
                && !below.is(WhiteAlbumTags.ICE_SPEED)
                && below.isCollisionShapeFullBlock(level, belowPos)
                && layer.canSurvive(level, pos)
                && level.noCollision(
                        layer.getCollisionShape(level, pos)
                                .bounds()
                                .move(pos))
                && !EventHooks.onBlockPlace(
                        user,
                        BlockSnapshot.create(
                                level.dimension(), level, pos),
                        Direction.UP)) {
            level.setBlockAndUpdate(pos, layer);
            level.scheduleTick(
                    pos,
                    WhiteAlbumAddon.ICE_LAYER.get(),
                    Mth.nextInt(
                            user.getRandom(),
                            WhiteAlbumSpec.ICE_MELT_MIN_TICKS,
                            WhiteAlbumSpec.ICE_MELT_MAX_TICKS));
        }
    }
}
