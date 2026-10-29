package rotp.addons.weatherreport.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BloodPuddleBlock extends Block {
    public static final IntegerProperty STAGE =
            IntegerProperty.create("stage", 0, 3);
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE =
            Block.box(2.0D, 0.1D, 2.0D, 14.0D, 0.1D, 14.0D);

    public BloodPuddleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(STAGE, 0)
                .setValue(FACING, Direction.NORTH));
    }

    public static Direction randomDirection(RandomSource random) {
        double roll = random.nextDouble()
                + random.nextDouble()
                + random.nextDouble()
                + random.nextDouble();
        if (roll <= 1.0D) {
            return Direction.NORTH;
        }
        if (roll <= 2.0D) {
            return Direction.WEST;
        }
        if (roll <= 3.0D) {
            return Direction.SOUTH;
        }
        return Direction.EAST;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos position,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(
            BlockState state,
            LevelReader level,
            BlockPos position) {
        BlockPos below = position.below();
        BlockState support = level.getBlockState(below);
        return !support.is(Blocks.AIR)
                && !support.is(Blocks.CAVE_AIR)
                && support.isFaceSturdy(
                        level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos position,
            BlockPos neighborPosition) {
        return !state.canSurvive(level, position)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(
                        state,
                        direction,
                        neighborState,
                        level,
                        position,
                        neighborPosition);
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, FACING);
    }
}
