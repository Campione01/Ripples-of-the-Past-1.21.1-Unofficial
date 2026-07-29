package com.github.standobyte.jojo.client.sound;

import java.util.Optional;

import com.github.standobyte.jojo.JojoModLivingVariables;
import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationPolicies;
import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationQuery;
import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationSurface;
import com.github.standobyte.jojo.client.standskin.sound.SoundInstanceWithStandSkin;
import com.github.standobyte.jojo.client.sound.sounds.EntityStoppableSoundInstance;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.mixin.client.time.AbstractSoundInstanceRegionalTimeDilationAccessor;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class ClientsideSoundsHelper {
	
	/**
	 * Call this right before calling {@link net.minecraft.client.sounds.SoundEngine#play(SoundInstance)}
	 * (or when it's abstracted behind something like ClientLevel#playLocalSound or SoundManager#play)
	 */
	public static SoundEvent withStandSkin(SoundEvent soundEvent, ResourceLocation standId, Optional<ResourceLocation> standSkin) {
		ClientsideSoundsHelper.standSkin_soundEvent = soundEvent;
		ClientsideSoundsHelper.standSkin_standId = standId;
		ClientsideSoundsHelper.standSkin_standSkin = standSkin;
		return soundEvent;
	}
	
	public static SoundEvent withStandSkin(SoundEvent soundEvent, StandEntity standEntity) {
		return withStandSkin(soundEvent, standEntity.getStandType(), standEntity.getStandSkin());
	}
	
	public static SoundEvent withStandSkin(SoundEvent soundEvent, StandPower standPower) {
		if (standPower != null) {
			StandType standType = standPower.getPowerType();
			return withStandSkin(soundEvent, standType != null ? standType.getId() : null, standPower.getSelectedSkin());
		}
		else {
			return soundEvent;
		}
	}

	
	/*
	 * We can actually reference our implementations of SoundInstance (for example, EntityStoppableSoundInstance) anywhere,
	 * unlike the vanilla classes (such as EntityBoundSoundInstance) that have @OnlyIn annotation and therefore are not present on dedicated server.
	 * We just can't reference the SoundInstance interface itself in this method's signature. because it is also only present on client.
	 * Of course we can still only call this on a logical client side (if Level#isClientSide() is true).
	 */
	public static void playNonVanillaClassSound(Object soundInstance) {
		Minecraft.getInstance().getSoundManager().play((SoundInstance) soundInstance);
	}

	public static void playLoopingActionSound(SoundEvent soundEvent, LivingEntity entity, EntityActionInstance action,
			ActionPhase phase, float volume, float pitch) {
		playLoopingActionSound(soundEvent, entity, action, phase, volume, pitch, 0);
	}

	public static void playLoopingActionSound(SoundEvent soundEvent, LivingEntity entity, EntityActionInstance action,
			ActionPhase phase, float volume, float pitch, int fadeOutTicks) {
		playNonVanillaClassSound(new EntityStoppableSoundInstance(soundEvent, entity.getSoundSource(), volume, pitch,
				true, entity, entity.level().random.nextLong(),
				() -> action.isOver() || action.getPhase() != phase, fadeOutTicks));
	}



	// Internal Stand skin handler section
	
	private static SoundEvent standSkin_soundEvent;
	private static ResourceLocation standSkin_standId;
	private static Optional<ResourceLocation> standSkin_standSkin;
	
	@SubscribeEvent
	public static void onSoundPlayed(PlaySoundEvent event) {
		if (standSkin_soundEvent != null) {
			SoundInstance sound = event.getSound();
			if (sound != null && standSkin_soundEvent.getLocation().equals(sound.getLocation())) {
				if (sound instanceof SoundInstanceWithStandSkin withSkin) {
					withSkin.jojo_ripples$setStandSkin(standSkin_standId, standSkin_standSkin);
				}
				standSkin_soundEvent = null;
				standSkin_standId = null;
				standSkin_standSkin = null;
			}
		}

		if (com.github.standobyte.jojo.client.ClientTimeStopHandler.shouldCancelSound(event.getSound())) {
			event.setSound(null);
		}

		attenuateDyingBodySound(event);
		applyRegionalTimeDilation(event);
	}

	private static void applyRegionalTimeDilation(
			PlaySoundEvent event) {
		SoundInstance sound = event.getSound();
		if (!(sound instanceof
				AbstractSoundInstanceRegionalTimeDilationAccessor access)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 position = sound.isRelative() && minecraft.player != null
				? minecraft.player.position()
				: new Vec3(sound.getX(), sound.getY(), sound.getZ());
		float factor = ClientRegionalTimeDilationPolicies.resolve(
				new ClientRegionalTimeDilationQuery(
						ClientRegionalTimeDilationSurface.SOUND,
						position,
						minecraft.player));
		if (factor < 1.0F) {
			access.jojo_ripples$setRegionalTimeDilationPitch(
					sound.getPitch() * factor);
		}
	}

	private static void attenuateDyingBodySound(PlaySoundEvent event) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		SoundInstance sound = event.getSound();
		if (player == null || player.isSpectator() || player.isDeadOrDying()
				|| sound == null || sound.getAttenuation() != SoundInstance.Attenuation.LINEAR) {
			return;
		}

		JojoModLivingVariables playerVars = JojoModLivingVariables.get(player);
		if (!playerVars.isDyingBody()) {
			return;
		}
		float progress = playerVars.getDyingBodyProgress();
		if (progress > 0.8F) {
			float volumeMult;
			if (progress < 0.84F) {
				volumeMult = 20.0F * (1.0F - progress) - 3.0F;
			}
			else {
				volumeMult = 1.25F * (1.0F - progress);
			}
			if (sound instanceof AbstractSoundInstance abstractSound) {
				abstractSound.volume *= volumeMult;
			}
		}
	}

	@SubscribeEvent
	public static void resetJustInCase(ClientTickEvent.Post event) {
		if (standSkin_soundEvent != null) {
			standSkin_soundEvent = null;
			standSkin_standId = null;
			standSkin_standSkin = null;
		}
	}
}
