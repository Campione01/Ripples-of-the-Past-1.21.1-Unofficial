package com.github.standobyte.jojo.client.sound;

import java.util.ConcurrentModificationException;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class StandOstSound extends AbstractTickableSoundInstance {
	private int fadeAwayTicks = -1;
	private int fadeAwayInitialTicks = -1;

	@Nullable
	private final Options options;
	private final float musicVolume;

	public StandOstSound(SoundEvent sound, Minecraft mc) {
		super(sound, SoundSource.RECORDS, RandomSource.create());
		this.volume = 1.0F;
		this.pitch = 1.0F;
		this.x = 0;
		this.y = 0;
		this.z = 0;
		this.looping = false;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = true;

		Options options = mc.options;
		this.musicVolume = options.getSoundSourceVolume(SoundSource.MUSIC);
		try {
			options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0D);
		}
		catch (ConcurrentModificationException e) {
			JojoMod.LOGGER.warn("Failed setting Minecraft music volume to 0 when playing OST.");
			options = null;
		}
		this.options = options;
	}

	@Override
	public void tick() {
		if (!isStopped()) {
			if (fadeAwayInitialTicks > -1 && fadeAwayTicks > 0) {
				volume = (float) fadeAwayTicks-- / (float) fadeAwayInitialTicks;
			}
			if (fadeAwayTicks == 0) {
				stopOst();
			}
		}
	}

	private void stopOst() {
		stop();
		if (options != null) {
			options.getSoundSourceOptionInstance(SoundSource.MUSIC).set((double) musicVolume);
		}
	}

	public void setFadeAway(int ticks) {
		if (ticks > -1 && fadeAwayInitialTicks == -1) {
			fadeAwayTicks = ticks;
			fadeAwayInitialTicks = ticks;
		}
	}
}
