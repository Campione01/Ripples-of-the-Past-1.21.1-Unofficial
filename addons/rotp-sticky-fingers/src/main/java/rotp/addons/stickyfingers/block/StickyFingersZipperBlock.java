package rotp.addons.stickyfingers.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.blockentity.StickyFingersZipperBlockEntity;
import rotp.addons.stickyfingers.state.ZipperLeaseData;
import rotp.addons.stickyfingers.state.ZipperPhasing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StickyFingersZipperBlock
        extends BaseEntityBlock {
    public static final DirectionProperty FACING =
            BlockStateProperties.FACING;
    public static final BooleanProperty OPEN =
            BlockStateProperties.OPEN;
    // 1.16 placeZippers on a wall one block thick: the linked zipper
    // sits in the free cell behind this block.
    public static final BooleanProperty THROUGH =
            BooleanProperty.create("through");
    public static final MapCodec<StickyFingersZipperBlock> CODEC =
            simpleCodec(StickyFingersZipperBlock::new);

    private static final VoxelShape NORTH_SOUTH_SHAPE =
            box(0.0D, 0.0D, 7.0D, 16.0D, 16.0D, 9.0D);
    private static final VoxelShape EAST_WEST_SHAPE =
            box(7.0D, 0.0D, 0.0D, 9.0D, 16.0D, 16.0D);
    private static final VoxelShape UP_DOWN_SHAPE =
            box(0.0D, 7.0D, 0.0D, 16.0D, 9.0D, 16.0D);

    public StickyFingersZipperBlock(
            BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OPEN, false)
                        .setValue(THROUGH, false));
    }

    // The sides of the block that carry a zipper.
    public static boolean faces(BlockState state, Direction side) {
        if (!(state.getBlock() instanceof StickyFingersZipperBlock)) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        return facing == side
                || (state.getValue(THROUGH)
                        && facing == side.getOpposite());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<
                            net.minecraft.world.level.block.Block,
                            BlockState>
                    builder) {
        builder.add(FACING, OPEN, THROUGH);
    }

    @Override
    public BlockEntity newBlockEntity(
            BlockPos position, BlockState state) {
        return new StickyFingersZipperBlockEntity(position, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(
                        type,
                        StickyFingersAddon
                                .STICKY_FINGERS_ZIPPER_BLOCK_ENTITY
                                .get(),
                        StickyFingersZipperBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos position,
            CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> EAST_WEST_SHAPE;
            case Y -> UP_DOWN_SHAPE;
            case Z -> NORTH_SOUTH_SHAPE;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos position,
            CollisionContext context) {
        return state.getValue(OPEN)
                ? ZipperPhasing.hollowShape(state, level, position)
                : Shapes.block();
    }

    // 1.16: the block behind a zipper stays the wall block for mob
    // pathfinding, open or not.
    @Override
    protected boolean isPathfindable(
            BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos position,
            BlockState newState,
            boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel serverLevel) {
            BlockEntity raw = level.getBlockEntity(position);
            StickyFingersZipperBlockEntity zipperEntity =
                    raw instanceof StickyFingersZipperBlockEntity zipper
                            ? zipper
                            : null;
            ZipperLeaseData.get(serverLevel)
                    .onZipperRemoved(
                            serverLevel, position, zipperEntity);
        }
        super.onRemove(
                state, level, position, newState, movedByPiston);
    }
}
