package com.github.standobyte.jojo.client;

import java.util.HashSet;
import java.util.Set;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class VisualPipelineDiagnostics {
	private static final Set<String> LOGGED_KEYS = new HashSet<>();

	private VisualPipelineDiagnostics() {
	}

	public static void logOnce(String key, String message, Object... args) {
		if (LOGGED_KEYS.add(key)) {
			JojoMod.getLogger().info("[ROTP visual] " + message, args);
		}
	}

	public static boolean isRotpEntity(Entity entity) {
		if (entity == null || entity.getType() == null) {
			return false;
		}
		ResourceLocation type = entity.getType().builtInRegistryHolder().key().location();
		return JojoMod.MOD_ID.equals(type.getNamespace());
	}

	public static void logEntityVisibilityOnce(String keyPrefix, Entity entity, String label) {
		if (!isRotpEntity(entity)) {
			return;
		}
		ResourceLocation type = entity.getType().builtInRegistryHolder().key().location();
		Player player = Minecraft.getInstance().player;
		logOnce(keyPrefix + "_" + type + "_" + entity.getId(),
				"{}: entityId={}, type={}, invisible={}, invisibleToPlayer={}, clientCanSeeStands={}, pos={}.",
				label, entity.getId(), type, entity.isInvisible(), player != null && entity.isInvisibleTo(player),
				ClientGlobals.canSeeStands, entity.position());
	}
}
