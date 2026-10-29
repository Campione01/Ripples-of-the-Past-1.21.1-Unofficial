package rotp.addons.tournament.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LightBulbBlock
        extends FaceAttachedHorizontalDirectionalBlock {
    public static final MapCodec<LightBulbBlock> CODEC =
            simpleCodec(LightBulbBlock::new);

    private static final VoxelShape CEILING_SHAPE =
            Block.box(6.0D, 15.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private static final VoxelShape WALL_NORTH_SHAPE =
            Block.box(6.0D, 6.0D, 15.0D, 10.0D, 10.0D, 16.0D);
    private static final VoxelShape WALL_SOUTH_SHAPE =
            Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 1.0D);
    private static final VoxelShape WALL_WEST_SHAPE =
            Block.box(15.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D);
    private static final VoxelShape WALL_EAST_SHAPE =
            Block.box(0.0D, 6.0D, 6.0D, 1.0D, 10.0D, 10.0D);
    private static final VoxelShape FLOOR_SHAPE =
            Block.box(6.0D, 0.0D, 6.0D, 10.0D, 1.0D, 10.0D);

    public LightBulbBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(FACE, AttachFace.CEILING));
    }

    @Override
    protected MapCodec<? extends LightBulbBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR_SHAPE;
            case WALL -> switch (state.getValue(FACING)) {
                case EAST -> WALL_EAST_SHAPE;
                case WEST -> WALL_WEST_SHAPE;
                case SOUTH -> WALL_SOUTH_SHAPE;
                default -> WALL_NORTH_SHAPE;
            };
            case CEILING -> CEILING_SHAPE;
        };
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        BlockState updated = super.updateShape(
                state,
                direction,
                neighborState,
                level,
                pos,
                neighborPos);
        if (updated.is(Blocks.AIR)
                && level instanceof Level actualLevel
                && !actualLevel.isClientSide()) {
            popResource(
                    actualLevel,
                    pos,
                    new ItemStack(this));
        }
        return updated;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    @Override
    protected boolean isPathfindable(
            BlockState state,
            PathComputationType pathType) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }
}
