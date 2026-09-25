package rotp.core.client.sound.sounds;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import javax.annotation.Nullable;

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
	// Running volume (optional 0..1 factor, 1.16 HamonEnergySound: energy / max energy) and fade-out
	private final StoppableSoundVolume volumeTracker;
	public boolean ITS_FUCKING_STOPPED_ALREADY = false;

	public EntityStoppableSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, Entity entity, long seed, BooleanSupplier stopWhen) {
		this(soundEvent, source, volume, pitch, false, entity, seed, stopWhen);
	}

	public EntityStoppableSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, boolean looping, Entity entity, long seed, BooleanSupplier stopWhen) {
		this(soundEvent, source, volume, pitch, looping, entity, seed, stopWhen, 0);
	}

	public EntityStoppableSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, boolean looping, Entity entity, long seed, BooleanSupplier stopWhen, int fadeOutTicks) {
		this(soundEvent, source, volume, pitch, looping, entity, seed, stopWhen, fadeOutTicks, null);
	}

	public EntityStoppableSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, boolean looping, Entity entity, long seed, BooleanSupplier stopWhen, int fadeOutTicks,
			@Nullable DoubleSupplier volumeFactor) {
		this(soundEvent, source, volume, pitch, looping, entity, seed, stopWhen, fadeOutTicks, 0.0F, volumeFactor);
	}

	// fadeOutStep above 0: after stopping, the volume drops by that much per tick instead of over fadeOutTicks
	public EntityStoppableSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, boolean looping, Entity entity, long seed, BooleanSupplier stopWhen, int fadeOutTicks,
			float fadeOutStep, @Nullable DoubleSupplier volumeFactor) {
		super(soundEvent, source, RandomSource.create(seed));
		this.volumeTracker = new StoppableSoundVolume(volume, fadeOutTicks, fadeOutStep, volumeFactor);
		this.volume = this.volumeTracker.volume();
		this.pitch = pitch;
		this.looping = looping;
		this.entity = entity;
		this.x = entity.getX();
		this.y = entity.getY();
		this.z = entity.getZ();
		this.stopWhen = stopWhen;
	}

	@Override
	public boolean canPlaySound() {
		return !this.entity.isSilent();
	}

	// A scaled sound may start at 0 volume and swell later
	@Override
	public boolean canStartSilent() {
		return volumeTracker.isScaled();
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
		boolean keepPlaying = volumeTracker.tick(stopWhen.getAsBoolean());
		this.volume = volumeTracker.volume();
		if (!keepPlaying) {
			ITS_FUCKING_STOPPED_ALREADY = true;
			this.stop();
		}
	}

}
