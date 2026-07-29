package com.github.standobyte.jojo.client;

import com.github.standobyte.jojo.client.input.ClientsideAim;
import com.github.standobyte.jojo.client.polaroid.PhotosCache;
import com.github.standobyte.jojo.client.polaroid.PolaroidHelper;
import com.github.standobyte.jojo.client.sound.StandOstSound;
import com.github.standobyte.jojo.client.shader.ModShaders;
import com.github.standobyte.jojo.api.client.render.AddonPostEffect;
import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.client.ui.hud_power.PowerHud;
import com.github.standobyte.jojo.client.ui.utils.FadeOut;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.entityattachment.custom_effect.ClientCustomEffectSyncQueue;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.modcompat.ModInteractionUtil;
import com.github.standobyte.jojo.network.s2c.TrSetStandEntityPacket;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.ClientEntityActionSyncQueue;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectInstance;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopClientAwareness;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.ClientEntityController;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.sound.OstSoundList;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.mrpresident.client.CocoJumboClientDiscovery;
import com.github.standobyte.jojoimpl.stands.goldexperience.client.GoldExperienceLifeDetectorClient;
import com.github.standobyte.jojoimpl.stands.goldexperience.client.GoldExperienceLifeformDiscovery;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
			com.github.standobyte.jojo.client.ui.hud_misc.BottomLeftNotifications._tick();
		}
		ClientEntityActionSyncQueue.tick(mc);
		ClientCustomEffectSyncQueue.tick(mc);
		ClientTickables._tick();
		PhotosCache.tick();
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
		if (mc.player != null && mc.player.isAlive()) {
			MobEffectInstance resolve = mc.player.getEffect(ModStatusEffects.RESOLVE);
			if (resolve != null) {
				if (!resolveOstActive) {
					startPlayingOst(mc, resolve.getAmplifier());
					resolveOstActive = true;
				}
				if (resolve.getDuration() == 40) {
					fadeAwayOst(mc, 100);
				}
				if (mc.player.tickCount % 100 == 0) {
					mc.getMusicManager().stopPlaying();
				}
				return;
			}
		}
		resolveOstActive = false;
		fadeAwayOst(mc, 20);
	}

	private static void startPlayingOst(Minecraft mc, int level) {
		mc.getMusicManager().stopPlaying();
		if (ost == null || ost.isStopped()) {
			ost = null;
			StandPower stand = StandPower.get(mc.player);
			if (stand != null && stand.hasPower() && stand.getPowerType() != null) {
				OstSoundList ostList = stand.getPowerType().getOst(mc.player);
				if (ostList != null) {
					SoundEvent ostSound = ostList.get(level);
					if (ostSound != null) {
						ost = new StandOstSound(ostSound, mc);
						mc.getSoundManager().play(ost);
					}
				}
			}
		}
	}

	private static void fadeAwayOst(Minecraft mc, int fadeAwayTicks) {
		if (ost != null) {
			if (!ost.isStopped()) {
				ost.setFadeAway(fadeAwayTicks);
			}
			else {
				mc.getSoundManager().stop(ost);
			}
			ost = null;
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
