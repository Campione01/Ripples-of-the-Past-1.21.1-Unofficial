package com.github.standobyte.jojoimpl.stands.starplatinum.client;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityStoppableSoundInstance;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class StarPlatinumZoomClient {
	private static boolean zooming;
	private static double zoomModifier = 1;

	private StarPlatinumZoomClient() {}

	private static void setZooming(boolean zooming) {
		StarPlatinumZoomClient.zooming = zooming;
	}

	public static void startZooming(StandEntity stand) {
		if (isLocalUser(stand)) {
			setZooming(true);
		}
	}

	public static void clearZooming(StandEntity stand) {
		if (isLocalUser(stand)) {
			setZooming(false);
		}
	}

	public static boolean isZooming() {
		return zooming;
	}

	public static void playZoomLoop(StandEntity stand, EntityActionInstance action) {
		if (!isLocalUser(stand)) {
			return;
		}
		Level level = stand.level();
		ClientsideSoundsHelper.playNonVanillaClassSound(new EntityStoppableSoundInstance(ClientsideSoundsHelper.withStandSkin(
				ModSoundEvents.STAR_PLATINUM_ZOOM.get(), stand), 
				stand.getSoundSource(), 1, 1, stand, level.random.nextLong(), 
				() -> action.isOver() || action.getPhase() != ActionPhase.PERFORM));
	}

	public static void playZoomClick(StandEntity stand) {
		if (!isLocalUser(stand)) {
			return;
		}
		Level level = stand.level();
		level.playLocalSound(stand.getX(), stand.getY(), stand.getZ(), ClientsideSoundsHelper.withStandSkin(
				ModSoundEvents.STAR_PLATINUM_ZOOM_CLICK.get(), stand),
				stand.getSoundSource(), 1, 1, false);
	}

	private static boolean isLocalUser(StandEntity stand) {
		Minecraft mc = Minecraft.getInstance();
		LivingEntity user = stand.getUser();
		return mc.player != null && user == mc.player;
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onComputeFov(ViewportEvent.ComputeFov event) {
		Minecraft mc = Minecraft.getInstance();
		float deltaFrameTime = mc.getTimer().getGameTimeDeltaTicks();
		if (zooming) {
			zoomModifier = Math.min(zoomModifier + deltaFrameTime / 3F, 60);
		}
		else if (zoomModifier > 1) {
			zoomModifier = Math.max(zoomModifier - deltaFrameTime * 2F, 1);
		}
		if (zoomModifier > 1) {
			event.setFOV(event.getFOV() / zoomModifier);
		}
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			setZooming(false);
			zoomModifier = 1;
		}
	}
}
