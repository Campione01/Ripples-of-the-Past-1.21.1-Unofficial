package rotp.core.powersystem.entityaction.syncdata;

import java.util.List;

import rotp.core.entityattachment.syncheddata.SynchedDataExtended;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;

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
		onStartedTracking(tracking, performer, 0, actionGeneration, curAction);
	}

	public static void onStartedTracking(
			ServerPlayer tracking,
			LivingEntity performer,
			int slot,
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
									nonDefaultData, slot));
				}
			}
		}
	}
	
	public static void tickSyncDirtyData(
			Entity performer,
			long actionGeneration,
			SynchedDataExtended synchedData) {
		tickSyncDirtyData(performer, 0, actionGeneration, synchedData);
	}

	public static void tickSyncDirtyData(
			Entity performer,
			int slot,
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
								dirtyData, slot));
			}
		}
	}
	
	public static void setDataClientSide(LivingEntity entity, List<SynchedEntityData.DataValue<?>> packedItems) {
		setDataClientSide(entity, 0, packedItems);
	}

	public static void setDataClientSide(LivingEntity entity, int slot, List<SynchedEntityData.DataValue<?>> packedItems) {
		EntityActionInstance action = LivingComponentAction.getCurEntityActionForSlot(entity, slot);
		if (action != null) {
			SynchedDataExtended synchedData = action.synchedData.getDataSyncher();
			if (synchedData != null) {
				synchedData.assignValues(packedItems);
			}
		}
	}
	
}
