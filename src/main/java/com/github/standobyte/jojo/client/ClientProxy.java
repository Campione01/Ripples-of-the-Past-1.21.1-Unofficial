package com.github.standobyte.jojo.client;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.ClientEntityController;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.mob.ClientMobController;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.stand.ClientStandController;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ClientProxy {

	public static Player getClientPlayer() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player;
	}

	public static Entity getCameraEntity() {
		return Minecraft.getInstance().getCameraEntity();
	}

	public static Level getClientWorld() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level;
	}
	
	public static Vec3 getCameraPos() {
		Minecraft mc = Minecraft.getInstance();
		return mc.gameRenderer.getMainCamera().getPosition();
	}

	public static Entity getEntityById(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.getEntity(entityId) : null;
	}

	public static void syncLocalEntityController(LivingEntity controller, @Nullable Entity target, @Nullable String controllerType) {
		if (controller != getClientPlayer()) {
			return;
		}
		if (target == null) {
			ClientEntityController.setInstance(null);
		}
		else if ("stand".equals(controllerType) && target instanceof StandEntity stand) {
			ClientEntityController.setInstance(new ClientStandController(stand));
		}
		else if ("mob".equals(controllerType) && target instanceof LivingEntity) {
			ClientEntityController.setInstance(new ClientMobController(target));
		}
		else if (ClientEntityController.isBeingControlledByClient(target)) {
			ClientEntityController.setInstance(null);
		}
	}
	
	public static Iterable<Entity> getEntities(Level level) {
		return ((ClientLevel) level).entitiesForRendering();
	}
	
	public static void openScreen(Object screen) {
		Minecraft.getInstance().setScreen((Screen) screen);
	}
	
	public static void setOverlayMessage(Component message, boolean animateColor) {
		Minecraft.getInstance().gui.setOverlayMessage(message, animateColor);
	}

	public static boolean isDestroyingBlock() {
		Minecraft mc = Minecraft.getInstance();
		return mc.gameMode != null && mc.gameMode.isDestroying();
	}
	
	public static boolean isClientPaused() {
		return Minecraft.getInstance().isPaused();
	}
	
    public static final ItemTracking clientTrackedItems = new ItemTracking(null);
	
}
