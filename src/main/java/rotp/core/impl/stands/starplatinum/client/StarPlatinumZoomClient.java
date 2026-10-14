package rotp.core.impl.stands.starplatinum.client;

import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands.starplatinum.StarPlatinumZoomAbility;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

import javax.annotation.Nullable;

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
	@Nullable private static StandEntity zoomingStand;
	private static double zoomModifier = 1;

	private StarPlatinumZoomClient() {}

	private static void setZooming(boolean zooming) {
		StarPlatinumZoomClient.zooming = zooming;
		if (!zooming) {
			zoomingStand = null;
		}
	}

	public static void startZooming(StandEntity stand) {
		if (isLocalUser(stand)) {
			zoomingStand = stand;
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

	// 1.16 played it at the Stand for every client the held action reached, its user's and the players around
	public static void playZoomLoop(StandEntity stand, EntityActionInstance action) {
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
		// 1.16 stopped the zoom with the held action; a Stand removed with its action still on it never clears it
		else if (zooming && (zoomingStand == null || zoomingStand.level() != mc.level
				|| !StarPlatinumZoomAbility.isZoomRunningOn(zoomingStand))) {
			setZooming(false);
		}
	}
}
