package com.github.standobyte.jojo.subsystems.rollback;

import com.github.standobyte.jojo.api.rollback.RollbackInvalidationReason;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class RollbackManagerEvents {
	private RollbackManagerEvents() {}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel level) {
			var attachmentType =
					ModDataAttachmentTypes.ROLLBACK_TRANSACTIONS.get();
			if (level.hasData(attachmentType)) {
				level.getData(attachmentType).tick();
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(
			PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			invalidateOwner(
					player.getServer(),
					player,
					RollbackInvalidationReason.OWNER_LOGOUT);
		}
	}

	@SubscribeEvent
	public static void onPlayerChangedDimension(
			PlayerEvent.PlayerChangedDimensionEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ServerLevel previousLevel = server.getLevel(event.getFrom());
		if (previousLevel == null) {
			return;
		}
		var attachmentType =
				ModDataAttachmentTypes.ROLLBACK_TRANSACTIONS.get();
		if (previousLevel.hasData(attachmentType)) {
			previousLevel.getData(attachmentType).invalidateOwner(
					player.getUUID(),
					RollbackInvalidationReason.OWNER_CHANGED_DIMENSION);
		}
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel() instanceof ServerLevel level) {
			var attachmentType =
					ModDataAttachmentTypes.ROLLBACK_TRANSACTIONS.get();
			if (level.hasData(attachmentType)) {
				level.getData(attachmentType).close(
						RollbackInvalidationReason.LEVEL_UNLOAD);
			}
		}
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		for (ServerLevel level : event.getServer().getAllLevels()) {
			var attachmentType =
					ModDataAttachmentTypes.ROLLBACK_TRANSACTIONS.get();
			if (level.hasData(attachmentType)) {
				level.getData(attachmentType).close(
						RollbackInvalidationReason.SERVER_STOPPING);
			}
		}
	}

	private static void invalidateOwner(
			MinecraftServer server,
			ServerPlayer player,
			RollbackInvalidationReason invalidationReason) {
		if (server == null) {
			return;
		}
		var attachmentType =
				ModDataAttachmentTypes.ROLLBACK_TRANSACTIONS.get();
		for (ServerLevel level : server.getAllLevels()) {
			if (level.hasData(attachmentType)) {
				level.getData(attachmentType).invalidateOwner(
						player.getUUID(), invalidationReason);
			}
		}
	}
}
