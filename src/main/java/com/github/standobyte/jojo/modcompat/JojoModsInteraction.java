package com.github.standobyte.jojo.modcompat;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class JojoModsInteraction {

	public static boolean clientHasStandFromAnotherMod() {
		String worStandType = WingsOfRequiem.getClientStandType();
		if (worStandType != null && !"None".equals(worStandType)) {
			return true;
		}

		return false;
	}
	
	public static boolean entityHasStandFromAnotherMod(LivingEntity entity) {
		if (entity.level().isClientSide() && entity == ClientProxy.getClientPlayer()) {
			return clientHasStandFromAnotherMod();
		}
		// XXX masochism
		return false;
	}

	public static class WingsOfRequiem {
		private static final ResourceLocation WOR_PLAYER_DATA_ATTACHMENT_ID = ResourceLocation.fromNamespaceAndPath("jojowor", "player_variables");
		
		public static Object WoRPlayerData;
		public static AttachmentType<?> WoRPlayerDataAttachmentType;

		private static Class<?> WOR_PLAYER_VARIABLES;
		private static Field WOR_PLAYER_VARIABLES_PLAYER_STAND;
		private static boolean worReflectionFailed = false;
		
		@Nullable
		static void _cacheWoRClientPlayerData() {
			WoRPlayerData = null;
			if (!ModInteractionUtil.isModLoaded("jojowor") || worReflectionFailed) return;

			if (WOR_PLAYER_VARIABLES == null || WOR_PLAYER_VARIABLES_PLAYER_STAND == null) {
				if (!worReflectionFailed) {
					try {
						WOR_PLAYER_VARIABLES = Class.forName("net.noiilive.jojowor.network.JojoworModVariables$PlayerVariables");
						WOR_PLAYER_VARIABLES_PLAYER_STAND = WOR_PLAYER_VARIABLES.getDeclaredField("PlayerStand");
					}
					catch (ClassNotFoundException | NoSuchFieldException | SecurityException e) {
						JojoMod.getLogger().error("", e);
						worReflectionFailed = true;
						return;
					}
				}
			}
			
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				if (WoRPlayerDataAttachmentType == null) {
					WoRPlayerDataAttachmentType = NeoForgeRegistries.ATTACHMENT_TYPES.get(WOR_PLAYER_DATA_ATTACHMENT_ID);
				}
				if (WoRPlayerDataAttachmentType != null) {
					WoRPlayerData = mc.player.getExistingDataOrNull(WoRPlayerDataAttachmentType);
				}
			}
		}
		
		@Nullable
		public static String getClientStandType() {
			if (WoRPlayerData != null) {
				try {
					return (String) WOR_PLAYER_VARIABLES_PLAYER_STAND.get(WoRPlayerData);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					worReflectionFailed = true;
					JojoMod.getLogger().error("", e);
				}
			}
			return null;
		}
		
		@Nullable
		public static void setClientStandType(String type) {
			if (WoRPlayerData != null) {
				try {
					WOR_PLAYER_VARIABLES_PLAYER_STAND.set(WoRPlayerData, type);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					worReflectionFailed = true;
					JojoMod.getLogger().error("", e);
				}
			}
		}
	}
}
