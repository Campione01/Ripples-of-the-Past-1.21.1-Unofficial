package com.github.standobyte.jojo.mrpresident;

import com.mojang.serialization.MapCodec;

import com.github.standobyte.jojo.world.dimension.ModDimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class MrPresidentGemBlock extends Block {
	public static final MapCodec<MrPresidentGemBlock> CODEC = simpleCodec(MrPresidentGemBlock::new);
	/*
	 * 1 - up
	 * 2 - right
	 * 3 - down
	 * 4 - left
	 * 5 - left up
	 * 6 - right up
	 * 7 - right down
	 * 8 - left down
	 */
	public static final IntegerProperty BORDER_VARIANT = IntegerProperty.create("border_variant", 0, 8);

	public MrPresidentGemBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(BORDER_VARIANT, 0));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player.isShiftKeyDown() || level.dimension() != ModDimensions.MR_PRESIDENT) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		return MrPresidentRoomStateOwner.get(serverPlayer.server).returnFromRoom(serverPlayer, pos)
				? InteractionResult.CONSUME : InteractionResult.PASS;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BORDER_VARIANT);
	}
}
