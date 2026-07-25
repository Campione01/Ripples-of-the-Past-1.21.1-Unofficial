package com.github.standobyte.jojo.mrpresident;

import java.util.UUID;

import com.github.standobyte.jojo.init.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CocoJumboShellBlockEntity extends BlockEntity {
	private UUID roomId;
	private UUID turtleUuid;
	private boolean occupied;

	public CocoJumboShellBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.COCO_JUMBO_SHELL.get(), pos, blockState);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (roomId != null) {
			tag.putUUID("RoomId", roomId);
		}
		if (turtleUuid != null) {
			tag.putUUID("Turtle", turtleUuid);
		}
		tag.putBoolean("Occupied", occupied);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		roomId = tag.hasUUID("RoomId") ? tag.getUUID("RoomId") : null;
		turtleUuid = tag.hasUUID("Turtle") ? tag.getUUID("Turtle") : null;
		occupied = tag.getBoolean("Occupied");
	}

	public void bindRoom(UUID roomId, UUID turtleUuid, boolean occupied) {
		this.roomId = roomId;
		this.turtleUuid = turtleUuid;
		this.occupied = occupied;
		setChanged();
	}
}
