package rotp.core.client;

import rotp.core.client.input.ClientsideAim;
import rotp.core.client.polaroid.PhotosCache;
import rotp.core.client.polaroid.PolaroidHelper;
import rotp.core.client.sound.StandOstSound;
import rotp.core.client.shader.ModShaders;
import rotp.core.api.client.render.AddonPostEffect;
import rotp.core.api.client.render.EntityMaskPostEffect;
import rotp.core.client.ui.hud_power.PowerHud;
import rotp.core.client.ui.utils.FadeOut;
import rotp.core.core.JojoMod;
import rotp.core.entityattachment.custom_effect.ClientCustomEffectSyncQueue;
import rotp.core.init.ModStatusEffects;
import rotp.core.modcompat.ModInteractionUtil;
import rotp.core.network.s2c.TrSetStandEntityPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.subsystems.timestop.TimeStopClientAwareness;
import rotp.core.subsystems.entity_puppetcontrol.client.ClientEntityController;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.sound.OstSoundList;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.mrpresident.client.CocoJumboClientDiscovery;
import rotp.core.impl.stands.goldexperience.client.GoldExperienceLifeDetectorClient;
import rotp.core.impl.stands.goldexperience.client.GoldExperienceLifeformDiscovery;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class ClientTickHandler {
	public static int tickCount;
	private static float frozenRainLevel = -1f;
	private static float frozenThunderLevel = -1f;
	private static StandOstSound ost;
	private static boolean resolveOstActive;

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		ModInteractionUtil.clientTickPre();
		ClientGlobals.tick(mc);
		ClientEntityController.clientTickPre();
		ClientTimeStopHandler.clientTick(mc);
		++tickCount;
		PowerHud.tickHamonOutOfBreath();
		if (!mc.isPaused()) {
			PowerHud.tickHamonNoEnergyFeedback();
		}

		ClientLevel level = mc.level;
		if (level != null) {
			if (ClientTimeStopHandler.isTimeStoppedStatic()) {
				if (frozenRainLevel < 0f) {
					frozenRainLevel = level.getRainLevel(1.0f);
					frozenThunderLevel = level.getThunderLevel(1.0f);
				}
				level.setRainLevel(frozenRainLevel);
				level.setThunderLevel(frozenThunderLevel);
			}
			else if (frozenRainLevel >= 0f) {
				frozenRainLevel = -1f;
				frozenThunderLevel = -1f;
			}
		}
	}

	@SubscribeEvent
	public static void onClientTickPost(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		EntityMaskPostEffect.onClientTick();
		AddonPostEffect.onClientTick();
		boolean screenFreezePresentation = TimeStopClientAwareness.isVisionRestricted();
		ClientsideAim.updateTarget(mc, 1);
		ClientsideAim.updateTargetWithServer(mc);
		GoldExperienceLifeDetectorClient.tick(mc);
		GoldExperienceLifeformDiscovery.tick(mc);
		CocoJumboClientDiscovery.tick(mc);
		tickResolveShader(mc);
		tickResolveOst(mc);
		ClientEntityController.clientTickPost();
		TrSetStandEntityPacket.Handler.tickPendingLinks();
		if (!mc.isPaused() && !screenFreezePresentation) {
			for (var fadeOut : FadeOut.__TO_TICK) fadeOut.__tick();
		}
		TimeStopClientAwareness.consumeEnterTransition();
		TimeStopClientAwareness.consumeMovementBlockTransition();
		TimeStopClientAwareness.consumeRestoreTransition();
		if (!screenFreezePresentation) {
			rotp.core.client.ui.hud_misc.BottomLeftNotifications._tick();
		}
		Component entityActionDisconnect = ClientEntityActionSyncQueue.tick(mc);
		if (entityActionDisconnect != null) {
			disconnectEntityActionSync(mc, entityActionDisconnect);
			return;
		}
		ClientCustomEffectSyncQueue.tick(mc);
		ClientTickables._tick();
		PhotosCache.tick();
	}

	private static void disconnectEntityActionSync(
			Minecraft mc, Component reason) {
		if (!mc.isSameThread()) {
			mc.execute(() -> disconnectEntityActionSync(mc, reason));
			return;
		}
		var listener = mc.getConnection();
		if (listener != null) {
			listener.getConnection().disconnect(reason);
		}
	}

	private static void tickResolveShader(Minecraft mc) {
		ModShaders shaders = ModShaders.getInstance();
		if (shaders == null || shaders.resolveShaderManager == null) {
			return;
		}
		if (mc.player != null && mc.player.isAlive() && mc.player.hasEffect(ModStatusEffects.RESOLVE)) {
			shaders.resolveShaderManager.setRandomResolveShader(StandPower.get(mc.player));
		}
		else {
			shaders.resolveShaderManager.stopResolveShader();
		}
	}

	private static void tickResolveOst(Minecraft mc) {
		releaseFinishedOst(mc);
		if (mc.player != null && mc.player.isAlive()) {
			MobEffectInstance resolve = mc.player.getEffect(ModStatusEffects.RESOLVE);
			if (resolve != null) {
				if (!resolveOstActive) {
					startPlayingOst(mc, resolve.getAmplifier());
					resolveOstActive = true;
				}
				if (resolve.getDuration() == 40) {
					fadeAwayOst(100);
				}
				if (mc.player.tickCount % 100 == 0) {
					mc.getMusicManager().stopPlaying();
				}
				return;
			}
		}
		resolveOstActive = false;
		fadeAwayOst(20);
	}

	// The OST holds Minecraft's Music slider at 0 while it plays, and that slider is a persisted option: left at 0
	// it stays there for good and is written to options.txt the next time any options screen closes. The
	// instance can only put it back from tick(), and the sound engine stops ticking an instance the moment it
	// drops it - when the track ends on its own, when a resource reload or a disconnect clears every sound, or
	// when it never accepted the instance at all. So the handler that started the OST watches for it going away
	// and restores the music itself; the instance's own restore, if it already ran, makes this a no-op.
	private static void releaseFinishedOst(Minecraft mc) {
		if (ost != null && (ost.isStopped() || !mc.getSoundManager().isActive(ost))) {
			ost.restoreMusic();
			ost = null;
		}
	}

	private static void startPlayingOst(Minecraft mc, int level) {
		mc.getMusicManager().stopPlaying();
		if (ost != null) {
			// Resolve came back while the previous OST was still fading out. Two instances must never hold the
			// slider at once: the second would read the first's 0 as the volume to come back to.
			ost.stopOst();
			mc.getSoundManager().stop(ost);
			ost = null;
		}
		StandPower stand = StandPower.get(mc.player);
		if (stand != null && stand.hasPower() && stand.getPowerType() != null) {
			OstSoundList ostList = stand.getPowerType().getOst(mc.player);
			if (ostList != null) {
				SoundEvent ostSound = ostList.get(level);
				// The stock Stands register their OST events and ship no audio (the tracks come from a resource
				// pack), so on a stock install this is a sound that was never going to play. WalkmanSoundHandler
				// already asks the same question before listing a cassette track.
				if (ostSound != null && WalkmanSoundHandler.hasLoadedSound(ostSound)) {
					StandOstSound sound = new StandOstSound(ostSound, mc);
					mc.getSoundManager().play(sound);
					// SoundEngine.play declines silently for more reasons than a missing sound - the Records slider
					// at 0, no audio device - and an instance it declined is one nobody will ever tick or stop.
					// Only an accepted instance gets to mute the music.
					if (mc.getSoundManager().isActive(sound)) {
						sound.muteMusic();
						ost = sound;
					}
				}
			}
		}
	}

	private static void fadeAwayOst(int fadeAwayTicks) {
		// The instance stays referenced through the fade. releaseFinishedOst lets go of it once it has stopped or
		// the engine has dropped it, and only then is the music known to be back.
		if (ost != null) {
			ost.setFadeAway(fadeAwayTicks);
		}
	}

	@SubscribeEvent
	public static void onFrameRender(RenderFrameEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			ClientTimeStopHandler.applyLockedRotation(mc.player);
			if (mc.player.isSpectator() && mc.options.keySpectatorOutlines.isDown()) {
				JojoModUtil.getActualGameModeWhilePossessing(mc.player).ifPresent(actualGameMode -> {
					if (actualGameMode != GameType.SPECTATOR) {
						mc.options.keySpectatorOutlines.setDown(false);
					}
				});
			}
		}

		if (mc.level != null) {
			float tickDelta = mc.getTimer().getGameTimeDeltaTicks();
			float renderTicks = ClientUtil.getTime(false);
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof StandEntity stand && stand.clientStuff != null) {
					stand.clientStuff.barrageHitSounds.playSound(stand, renderTicks);
				}
				if (entity instanceof LivingEntity living) {
					StandPower standPower = entity == mc.player ? ClientPowerCache.getPower(PowerClass.STAND) : StandPower.get(living);
					if (standPower != null) {
						for (StandEffectInstance standEffect : standPower.userStandEffects.getEffects()) {
							standEffect.onFrame(tickDelta);
						}
					}
				}
			}
		}
	}

	@SubscribeEvent
	public static void onLivingRender(RenderLivingEvent.Pre<?, ?> event) {
		LivingEntity entity = event.getEntity();
		if (entity.hasEffect(ModStatusEffects.FULL_INVISIBILITY)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onRenderFrame(RenderLevelStageEvent event) {
		PolaroidHelper.capturePhoto(event);
	}

	@SubscribeEvent
	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		PolaroidHelper.onCameraAngles(event);
	}
	
}
