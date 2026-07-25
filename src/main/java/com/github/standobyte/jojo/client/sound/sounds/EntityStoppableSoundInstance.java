package com.github.standobyte.jojo.client.sound.sounds;

import java.util.function.BooleanSupplier;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Like {@link net.minecraft.client.resources.sounds.EntityBoundSoundInstance}, but can also stop on a specific condition
 */
public class EntityStoppableSoundInstance extends AbstractTickableSoundInstance {
	protected Entity entity;
	protected BooleanSupplier stopWhen;
	private final float baseVolume;
	private final int fadeOutTicks;
	private int fadeOutTicksLeft = -1;
	public boolean ITS_FUCKING_STOPPED_ALREADY = false;

	public EntityStoppableSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, Entity entity, long seed, BooleanSupplier stopWhen) {
		this(soundEvent, source, volume, pitch, false, entity, seed, stopWhen);
	}

	public EntityStoppableSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, boolean looping, Entity entity, long seed, BooleanSupplier stopWhen) {
		this(soundEvent, source, volume, pitch, looping, entity, seed, stopWhen, 0);
	}

	public EntityStoppableSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, boolean looping, Entity entity, long seed, BooleanSupplier stopWhen, int fadeOutTicks) {
		super(soundEvent, source, RandomSource.create(seed));
		this.volume = volume;
		this.baseVolume = volume;
		this.pitch = pitch;
		this.looping = looping;
		this.entity = entity;
		this.x = entity.getX();
		this.y = entity.getY();
		this.z = entity.getZ();
		this.stopWhen = stopWhen;
		this.fadeOutTicks = Math.max(fadeOutTicks, 0);
	}

	@Override
	public boolean canPlaySound() {
		return !this.entity.isSilent();
	}

	@Override
	public void tick() {
		if (entity.isRemoved() || ITS_FUCKING_STOPPED_ALREADY) {
			ITS_FUCKING_STOPPED_ALREADY = true;
			this.stop();
			return;
		}
		this.x = entity.getX();
		this.y = entity.getY();
		this.z = entity.getZ();
		if (stopWhen.getAsBoolean()) {
			if (fadeOutTicks > 0) {
				if (fadeOutTicksLeft < 0) {
					fadeOutTicksLeft = fadeOutTicks;
				}
				if (fadeOutTicksLeft <= 0) {
					ITS_FUCKING_STOPPED_ALREADY = true;
					this.stop();
					return;
				}
				this.volume = baseVolume * (float) fadeOutTicksLeft / (float) fadeOutTicks;
				fadeOutTicksLeft--;
			}
			else {
				ITS_FUCKING_STOPPED_ALREADY = true;
				this.stop();
			}
		}
		else {
			fadeOutTicksLeft = -1;
			this.volume = baseVolume;
		}
	}

}
