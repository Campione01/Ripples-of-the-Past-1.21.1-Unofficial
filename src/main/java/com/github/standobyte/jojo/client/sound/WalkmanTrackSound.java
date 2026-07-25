package com.github.standobyte.jojo.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class WalkmanTrackSound extends AbstractTickableSoundInstance {
	private int ticks;
	private final int distortionLevel;

	public WalkmanTrackSound(SoundEvent sound, int distortionLevel) {
		super(sound, SoundSource.RECORDS, RandomSource.create());
		this.distortionLevel = distortionLevel;
		this.volume = 1.0F;
		this.pitch = switch (distortionLevel) {
			case 1 -> 0.9875F;
			case 2 -> 0.975F;
			case 3 -> 0.95F;
			default -> 1.0F;
		};
		this.x = 0;
		this.y = 0;
		this.z = 0;
		this.looping = false;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = true;
	}

	@Override
	public void tick() {
		ticks++;
		if (distortionLevel >= 4) {
			pitch = 0.8F + (Mth.sin((float) ticks * 0.05F) + 1) * 0.05F;
		}
	}

	public void setVolume(float volume) {
		this.volume = volume;
	}

	public void stopPlaying() {
		stop();
	}
}
