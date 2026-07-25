package com.github.standobyte.jojo.block;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModBlockEntities;
import com.github.standobyte.jojo.init.ModBlocks;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModItems;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StoneMaskBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<StoneMaskBlock> CODEC = simpleCodec(StoneMaskBlock::new);
	public static final BooleanProperty BLOOD_ACTIVATION = BooleanProperty.create("blood_activation");
	protected static final VoxelShape WALL_NORTH_SHAPE = Block.box(4.0D, 4.0D, 15.0D, 12.0D, 12.0D, 16.0D);
	protected static final VoxelShape WALL_SOUTH_SHAPE = Block.box(4.0D, 4.0D, 0.0D, 12.0D, 12.0D, 1.0D);
	protected static final VoxelShape WALL_WEST_SHAPE = Block.box(15.0D, 4.0D, 4.0D, 16.0D, 12.0D, 12.0D);
	protected static final VoxelShape WALL_EAST_SHAPE = Block.box(0.0D, 4.0D, 4.0D, 1.0D, 12.0D, 12.0D);
	protected static final VoxelShape FLOOR_SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 1.0D, 12.0D);
	protected static final VoxelShape FLOOR_ACTIVATED_SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 6.0D, 12.0D);
	protected static final VoxelShape CEILING_SHAPE = Block.box(4.0D, 15.0D, 4.0D, 12.0D, 16.0D, 12.0D);

	public StoneMaskBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH).setValue(BLOOD_ACTIVATION, false));
	}

	@Override
	protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state != null ? state.setValue(BLOOD_ACTIVATION, false) : null;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACE)) {
		case FLOOR -> state.getValue(BLOOD_ACTIVATION) ? FLOOR_ACTIVATED_SHAPE : FLOOR_SHAPE;
		case WALL -> switch (state.getValue(FACING)) {
		case EAST -> WALL_EAST_SHAPE;
		case WEST -> WALL_WEST_SHAPE;
		case SOUTH -> WALL_SOUTH_SHAPE;
		case NORTH -> WALL_NORTH_SHAPE;
		default -> WALL_NORTH_SHAPE;
		};
		case CEILING -> CEILING_SHAPE;
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.addItem(getItemFromBlock(level, pos, state));
			level.removeBlock(pos, false);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
		BlockState updatedState = super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
		if (updatedState == Blocks.AIR.defaultBlockState() && level instanceof Level realLevel && !realLevel.isClientSide()) {
			Block.popResource(realLevel, currentPos, StoneMaskBlock.getItemFromBlock(realLevel, currentPos, state));
		}
		return updatedState;
	}

	public static ItemStack getItemFromBlock(BlockGetter level, BlockPos pos, BlockState state) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof StoneMaskBlockEntity stoneMask) {
			return stoneMask.getStack();
		}
		if (state.is(ModBlocks.AJA_STONE_MASK.get())) {
			return new ItemStack(ModItems.AJA_STONE_MASK.get());
		}
		return new ItemStack(ModItems.STONE_MASK.get());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACE, FACING, BLOOD_ACTIVATION);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof StoneMaskBlockEntity stoneMask) {
			ItemStack maskStack = stack.copy();
			maskStack.setCount(1);
			stoneMask.setStack(maskStack);
		}
	}

	@Override
	public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity, ItemStack itemUsed) {
		super.playerDestroy(level, player, pos, state, blockEntity, itemUsed);
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && blockEntity instanceof StoneMaskBlockEntity stoneMask) {
			ModCriteriaTriggers.triggerStoneMaskDestroyed(serverPlayer, state.getBlock(), itemUsed, stoneMask.getStack());
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StoneMaskBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return blockEntityType == ModBlockEntities.STONE_MASK.get()
				? (tickLevel, tickPos, tickState, blockEntity) -> StoneMaskBlockEntity.tick(tickLevel, tickPos, tickState, (StoneMaskBlockEntity) blockEntity)
				: null;
	}
}
