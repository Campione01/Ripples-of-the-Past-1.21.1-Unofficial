package com.github.standobyte.jojo.powersystem.entityaction.syncdata;

import java.util.List;

import com.github.standobyte.jojo.entityattachment.syncheddata.SynchedDataExtended;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class SyncActionInstanceData {

	public static void onStartedTracking(
			ServerPlayer tracking,
			LivingEntity performer,
			long actionGeneration,
			EntityActionInstance curAction) {
		if (curAction != null) {
			SynchedDataExtended synchedData = curAction.synchedData.getDataSyncher();
			if (synchedData != null) {
				List<SynchedEntityData.DataValue<?>> nonDefaultData = synchedData.syncOnStartedTracking();
				if (nonDefaultData != null) {
					PacketDistributor.sendToPlayer(
							tracking,
							new TrActionSynchedDataPacket(
									performer.getId(), performer.getUUID(),
									actionGeneration,
									nonDefaultData));
				}
			}
		}
	}
	
	public static void tickSyncDirtyData(
			Entity performer,
			long actionGeneration,
			SynchedDataExtended synchedData) {
		if (synchedData != null) {
			List<SynchedEntityData.DataValue<?>> dirtyData = synchedData.syncDirtyData();
			if (dirtyData != null) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(
						performer,
						new TrActionSynchedDataPacket(
								performer.getId(), performer.getUUID(),
								actionGeneration,
								dirtyData));
			}
		}
	}
	
	public static void setDataClientSide(LivingEntity entity, List<SynchedEntityData.DataValue<?>> packedItems) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(entity);
		if (action != null) {
			SynchedDataExtended synchedData = action.synchedData.getDataSyncher();
			if (synchedData != null) {
				synchedData.assignValues(packedItems);
			}
		}
	}
	
}
