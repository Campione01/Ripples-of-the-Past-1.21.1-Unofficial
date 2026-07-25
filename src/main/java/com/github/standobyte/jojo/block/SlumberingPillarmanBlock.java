package com.github.standobyte.jojo.block;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonProtectionAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanAbsorptionAbility;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
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

public class SlumberingPillarmanBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<SlumberingPillarmanBlock> CODEC = simpleCodec(SlumberingPillarmanBlock::new);
	private static final int PART_WITH_BLOCK_ENTITY = 4;
	private static final float DAMAGE_AMOUNT = 4.0F;
	public static final IntegerProperty PART = IntegerProperty.create("pillarman_part", 0, 5);
	private static final VoxelShape NORTH_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 8.0D);
	private static final VoxelShape SOUTH_SHAPE = Block.box(0.0D, 0.0D, 8.0D, 16.0D, 16.0D, 16.0D);
	private static final VoxelShape WEST_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 8.0D, 16.0D, 16.0D);
	private static final VoxelShape EAST_SHAPE = Block.box(8.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

	public SlumberingPillarmanBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, PART_WITH_BLOCK_ENTITY));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		int multiBlockPart = state.getValue(PART);
		Direction right = rightDirection(state);
		BlockPos leftUpPos = pos.relative(right, -(multiBlockPart % 2)).relative(Direction.UP, multiBlockPart / 2);
		for (int column = 0; column < 2; column++) {
			for (int row = 0; row < 3; row++) {
				int part = row * 2 + column;
				if (part == multiBlockPart) {
					continue;
				}
				BlockPos blockPos = leftUpPos.relative(right, column).relative(Direction.DOWN, row);
				BlockState blockState = level.getBlockState(blockPos);
				if (blockState.is(this) && blockState.getValue(PART) == part) {
					level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 35);
					level.levelEvent(player, 2001, blockPos, Block.getId(blockState));
				}
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (pos.getY() >= level.getMaxBuildHeight() - 2) {
			return null;
		}
		Direction right = context.getHorizontalDirection().getClockWise();
		for (int column = 0; column < 2; column++) {
			for (int row = 0; row < 3; row++) {
				if (!level.getBlockState(pos.above(row).relative(right, column)).canBeReplaced(context)) {
					return null;
				}
			}
		}
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		BlockPos up1 = pos.above();
		BlockPos up2 = up1.above();
		level.setBlock(up1, state.setValue(PART, 2), 3);
		level.setBlock(up2, state.setValue(PART, 0), 3);
		Direction right = rightDirection(state);
		level.setBlock(pos.relative(right), state.setValue(PART, 5), 3);
		level.setBlock(up1.relative(right), state.setValue(PART, 3), 3);
		level.setBlock(up2.relative(right), state.setValue(PART, 1), 3);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return mirror == Mirror.NONE ? state : state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
		case EAST -> EAST_SHAPE;
		case WEST -> WEST_SHAPE;
		case SOUTH -> SOUTH_SHAPE;
		case NORTH -> NORTH_SHAPE;
		default -> NORTH_SHAPE;
		};
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (level.isClientSide() || level.getDifficulty() == Difficulty.PEACEFUL || !(entity instanceof LivingEntity livingEntity)) {
			return;
		}
		var damageSource = DamageUtil.make(level, ModDamageTypes.PILLAR_MAN_ABSORPTION, Vec3.atCenterOf(pos));
		if (HamonProtectionAbility.preventBlockDamage(livingEntity, damageSource, DAMAGE_AMOUNT)) {
			return;
		}
		if (PillarmanAbsorptionAbility.dealAbsorptionDamage(livingEntity, DAMAGE_AMOUNT, damageSource)) {
			livingEntity.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 10));
			BlockEntity blockEntity = level.getBlockEntity(posByPart(state, pos, PART_WITH_BLOCK_ENTITY));
			if (blockEntity instanceof SlumberingPillarmanBlockEntity slumberingPillarman) {
				slumberingPillarman.incAbsorbed();
			}
		}
	}

	private static BlockPos posByPart(BlockState state, BlockPos startingPos, int desiredPart) {
		int startingPart = state.getValue(PART);
		int downOffset = desiredPart / 2 - startingPart / 2;
		int rightOffset = desiredPart % 2 - startingPart % 2;
		if (rightOffset != 0) {
			return startingPos.relative(rightDirection(state), rightOffset).relative(Direction.DOWN, downOffset);
		}
		return startingPos.relative(Direction.DOWN, downOffset);
	}

	private static Direction rightDirection(BlockState state) {
		return state.getValue(FACING).getClockWise();
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(PART) == PART_WITH_BLOCK_ENTITY ? new SlumberingPillarmanBlockEntity(pos, state) : null;
	}
}
