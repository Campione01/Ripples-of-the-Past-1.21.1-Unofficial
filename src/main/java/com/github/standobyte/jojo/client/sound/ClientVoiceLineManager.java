package com.github.standobyte.jojo.client.sound;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.sound.sounds.EntityLingeringSoundInstance;
import com.github.standobyte.jojo.config.client.ClientModSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public final class ClientVoiceLineManager {
	private static final Map<Integer, SoundInstance> CURRENT_VOICE_LINES = new HashMap<>();
	private static final Set<Integer> LAST_VOICE_LINE_TRIGGERED = new HashSet<>();

	private ClientVoiceLineManager() {}

	public static boolean playVoiceLine(Entity entity, @Nullable SoundEvent soundEvent, @Nullable SoundSource source,
			float volume, float pitch, boolean interrupt) {
		if (entity == null || soundEvent == null || source == null
				|| !ClientModSettings.getSettingsReadOnly().characterVoiceLines) {
			voiceLineNotTriggered(entity);
			return false;
		}
		if (!interrupt && isVoiceLinePlaying(entity)) {
			voiceLineNotTriggered(entity);
			return false;
		}

		SoundInstance sound = new EntityLingeringSoundInstance(soundEvent, source, volume, pitch, entity, entity.level());
		CURRENT_VOICE_LINES.put(entity.getId(), sound);
		LAST_VOICE_LINE_TRIGGERED.add(entity.getId());
		ClientsideSoundsHelper.playNonVanillaClassSound(sound);
		return true;
	}

	public static void voiceLineNotTriggered(@Nullable Entity entity) {
		if (entity != null) {
			LAST_VOICE_LINE_TRIGGERED.remove(entity.getId());
		}
	}

	public static boolean lastVoiceLineTriggered(Entity entity) {
		return entity != null && LAST_VOICE_LINE_TRIGGERED.contains(entity.getId());
	}

	private static boolean isVoiceLinePlaying(Entity entity) {
		SoundInstance currentVoiceLine = CURRENT_VOICE_LINES.get(entity.getId());
		if (currentVoiceLine == null) {
			return false;
		}
		boolean active = Minecraft.getInstance().getSoundManager().isActive(currentVoiceLine);
		if (!active) {
			CURRENT_VOICE_LINES.remove(entity.getId());
		}
		return active;
	}
}
