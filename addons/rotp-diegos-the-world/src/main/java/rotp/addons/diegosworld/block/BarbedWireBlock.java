package rotp.addons.diegosworld.block;

import javax.annotation.Nullable;

import rotp.core.util.functions.DamageUtil;
import com.mojang.serialization.MapCodec;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BarbedWireBlock
        extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BarbedWireBlock> CODEC =
            simpleCodec(BarbedWireBlock::new);
    public static final IntegerProperty PART =
            IntegerProperty.create("barbed_wire_part", 0, 5);
    public static final int CONTROLLER_PART = 4;

    private static final float DAMAGE_AMOUNT = 3.0F;
    private static final VoxelShape NORTH_SOUTH_SHAPE =
            Block.box(0.0D, 5.0D, 6.0D, 16.0D, 11.0D, 10.0D);
    private static final VoxelShape EAST_WEST_SHAPE =
            Block.box(6.0D, 5.0D, 0.0D, 10.0D, 11.0D, 16.0D);

    public BarbedWireBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, CONTROLLER_PART));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos controllerPos = context.getClickedPos();
        Level level = context.getLevel();
        if (controllerPos.getY() >= level.getMaxBuildHeight() - 1) {
            return null;
        }

        Direction facing = context.getHorizontalDirection();
        Direction right = facing.getClockWise();
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 2; row++) {
                BlockPos partPos = controllerPos
                        .relative(right, column - 2)
                        .above(row);
                if (!level.getBlockState(partPos)
                        .canBeReplaced(context)
                        || !level.getWorldBorder()
                                .isWithinBounds(partPos)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        Direction right = rightDirection(state);
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 2; row++) {
                int part = part(column, row);
                if (part == CONTROLLER_PART) {
                    continue;
                }
                level.setBlock(
                        pos.relative(right, column - 2).above(row),
                        state.setValue(PART, part),
                        3);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player) {
        BlockPos controllerPos =
                posByPart(state, pos, CONTROLLER_PART);
        Direction right = rightDirection(state);
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 2; row++) {
                int part = part(column, row);
                if (part == state.getValue(PART)) {
                    continue;
                }
                BlockPos partPos = controllerPos
                        .relative(right, column - 2)
                        .above(row);
                BlockState partState = level.getBlockState(partPos);
                if (partState.is(this)
                        && partState.getValue(PART) == part) {
                    level.setBlock(
                            partPos,
                            Blocks.AIR.defaultBlockState(),
                            35);
                    level.levelEvent(
                            player,
                            2001,
                            partPos,
                            Block.getId(partState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity) {
        if (level.isClientSide()
                || !(entity instanceof LivingEntity living)) {
            return;
        }
        boolean damaged = living.hurt(
                DamageUtil.make(
                        level,
                        DiegosTheWorldAddon.WIRE_DAMAGE,
                        Vec3.atCenterOf(pos)),
                DAMAGE_AMOUNT);
        if (!damaged) {
            return;
        }
        living.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 15, 2));
        living.addEffect(new MobEffectInstance(
                DiegosTheWorldAddon.BLEEDING,
                200,
                0,
                false,
                false,
                true));
        BlockEntity blockEntity = level.getBlockEntity(
                posByPart(state, pos, CONTROLLER_PART));
        if (blockEntity instanceof BarbedWireBlockEntity wire) {
            wire.incrementAbsorbedLife();
        }
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return state.getValue(FACING).getAxis()
                        == Direction.Axis.Z
                ? NORTH_SOUTH_SHAPE
                : EAST_WEST_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(
                FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE
                ? state
                : state.rotate(
                        mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos, BlockState state) {
        return state.getValue(PART) == CONTROLLER_PART
                ? new BarbedWireBlockEntity(pos, state)
                : null;
    }

    private static int part(int column, int row) {
        return column * 2 + row;
    }

    private static BlockPos posByPart(
            BlockState state,
            BlockPos startingPos,
            int desiredPart) {
        int startingPart = state.getValue(PART);
        int startingColumn = startingPart / 2;
        int startingRow = startingPart % 2;
        int desiredColumn = desiredPart / 2;
        int desiredRow = desiredPart % 2;
        return startingPos
                .relative(
                        rightDirection(state),
                        desiredColumn - startingColumn)
                .above(desiredRow - startingRow);
    }

    private static Direction rightDirection(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }
}
