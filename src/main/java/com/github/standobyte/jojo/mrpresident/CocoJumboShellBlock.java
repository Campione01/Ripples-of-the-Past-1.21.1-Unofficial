package com.github.standobyte.jojo.mrpresident;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CocoJumboShellBlock extends BaseEntityBlock {
	public static final MapCodec<CocoJumboShellBlock> CODEC = simpleCodec(CocoJumboShellBlock::new);

	public CocoJumboShellBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CocoJumboShellBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof CocoJumboShellBlockEntity shellBlockEntity)) {
			return InteractionResult.PASS;
		}
		MrPresidentRoomStateOwner owner = MrPresidentRoomStateOwner.get(serverPlayer.server);
		MrPresidentRoomStateOwner.RoomRecord room = owner.findRoomByOccupant(serverPlayer.getUUID());
		if (room == null) {
			return InteractionResult.PASS;
		}
		shellBlockEntity.bindRoom(room.roomId(), room.turtleUuid(), room.occupyingPlayer() != null);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
}
