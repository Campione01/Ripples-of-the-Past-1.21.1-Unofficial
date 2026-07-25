package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.entityattachment.SynchronizableEntityData;
import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.network.s2c.TrGEStuckObjectsPacket;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public class GEStuckObjectsState implements TickingEntityData, SynchronizableEntityData, INBTSerializable<CompoundTag> {
	private final LivingEntity entity;
	private int stuckKnives;
	private int stuckKnifeRemoveTime;

	public GEStuckObjectsState(LivingEntity entity) {
		this.entity = entity;
		addTicking(entity);
		addSynchronization(entity);
	}

	public int getStuckKnives() {
		return stuckKnives;
	}

	public void incrementStuckKnife() {
		setStuckKnives(stuckKnives + 1);
	}

	public boolean decrementStuckKnife() {
		if (stuckKnives <= 0) {
			return false;
		}
		setStuckKnives(stuckKnives - 1);
		return true;
	}

	public void setStuckKnives(int stuckKnives) {
		setStuckKnives(stuckKnives, true);
	}

	public void setStuckKnivesFromSync(int stuckKnives) {
		setStuckKnives(stuckKnives, false);
	}

	private void setStuckKnives(int stuckKnives, boolean sync) {
		stuckKnives = Math.max(stuckKnives, 0);
		if (this.stuckKnives != stuckKnives) {
			this.stuckKnives = stuckKnives;
			if (stuckKnives == 0) {
				stuckKnifeRemoveTime = 0;
			}
			if (sync && !entity.level().isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TrGEStuckObjectsPacket(entity.getId(), stuckKnives));
			}
		}
	}

	@Override
	public void tick() {
		if (entity.level().isClientSide() || stuckKnives <= 0) {
			return;
		}
		if (stuckKnifeRemoveTime <= 0) {
			stuckKnifeRemoveTime = 20 * (30 - stuckKnives);
		}
		if (--stuckKnifeRemoveTime <= 0) {
			setStuckKnives(stuckKnives - 1);
		}
	}

	@Override
	public void syncToTracking(ServerPlayer trackingPlayer) {
		if (stuckKnives > 0) {
			PacketDistributor.sendToPlayer(trackingPlayer, new TrGEStuckObjectsPacket(entity.getId(), stuckKnives));
		}
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Knives", stuckKnives);
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		stuckKnives = tag.getInt("Knives");
	}

	public static GEStuckObjectsState get(LivingEntity entity) {
		return entity.getData(ModDataAttachmentTypes.GE_STUCK_OBJECTS_STATE);
	}
}
