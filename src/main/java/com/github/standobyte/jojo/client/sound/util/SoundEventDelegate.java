package com.github.standobyte.jojo.client.sound.util;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.MultipliedFloats;

public class SoundEventDelegate implements Weighted<Sound> {
	public final ResourceLocation soundLocation;
	
	public SoundEventDelegate(ResourceLocation soundLocation) {
		this.soundLocation = soundLocation;
	}

	@Override
	public int getWeight() {
		WeighedSoundEvents delegate = getSoundEvent(soundLocation);
		return delegate == null ? 0 : delegate.getWeight();
	}

	@Override
	public Sound getSound(RandomSource random) {
		WeighedSoundEvents delegate = getSoundEvent(soundLocation);
		if (delegate == null) {
			return SoundManager.EMPTY_SOUND;
		} else {
			Sound sound = delegate.getSound(random);
			return new Sound(
					sound.getLocation(),
					new MultipliedFloats(sound.getVolume(), sound.getVolume()),
					new MultipliedFloats(sound.getPitch(), sound.getPitch()),
					sound.getWeight(),
					Sound.Type.FILE,
					sound.shouldStream() || sound.shouldStream(),
					sound.shouldPreload(),
					sound.getAttenuationDistance()
					);
		}
	}

	@Override
	public void preloadIfRequired(SoundEngine engine) {
		WeighedSoundEvents delegate = getSoundEvent(soundLocation);
		if (delegate != null) {
			delegate.preloadIfRequired(engine);
		}
	}
	
	@Nullable
	public static WeighedSoundEvents getSoundEvent(ResourceLocation location) {
		return Minecraft.getInstance().getSoundManager().getSoundEvent(location);
	}
}
