package rotp.core.client.sound;

import java.util.ConcurrentModificationException;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The Resolve OST. Plays on the Records source and, while it plays, holds Minecraft's own Music slider at 0 so
 * the game's music does not play over it.
 * <p>
 * That slider is a persisted option, so whoever sets it to 0 has to be certain it comes back. The instance never
 * decides that on its own: {@link rotp.core.client.ClientTickHandler} calls {@link #muteMusic()} only once the
 * sound engine has accepted the instance, and calls {@link #restoreMusic()} itself when the engine drops it.
 * {@link #tick()} runs only while the engine keeps the instance in its ticking set - one that was never
 * accepted, finished on its own, or was cleared by a resource reload or a disconnect is never ticked again - so
 * nothing here may rely on tick() to put the slider back.
 */
public class StandOstSound extends AbstractTickableSoundInstance {
	private int fadeAwayTicks = -1;
	private int fadeAwayInitialTicks = -1;

	private final Options options;
	/** The Music volume to come back to while this instance has it muted; null when untouched or already restored. */
	@Nullable
	private Float mutedMusicVolume;

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
		this.options = mc.options;
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

	/**
	 * Mutes Minecraft's music for this OST. The volume to come back to is recorded once; a second call is a
	 * no-op, so the baseline can never become the 0 this instance itself set.
	 */
	public void muteMusic() {
		if (mutedMusicVolume == null) {
			mutedMusicVolume = options.getSoundSourceVolume(SoundSource.MUSIC);
			setMusicVolume(0.0D);
		}
	}

	/** Puts the Music slider back where {@link #muteMusic()} found it. Safe to call any number of times. */
	public void restoreMusic() {
		if (mutedMusicVolume != null) {
			double volume = mutedMusicVolume;
			mutedMusicVolume = null;
			setMusicVolume(volume);
		}
	}

	/** Stops the sound and gives the music back: the end of the fade-away, or a Resolve that restarts mid-fade. */
	public void stopOst() {
		stop();
		restoreMusic();
	}

	public void setFadeAway(int ticks) {
		if (ticks > -1 && fadeAwayInitialTicks == -1) {
			fadeAwayTicks = ticks;
			fadeAwayInitialTicks = ticks;
		}
	}

	private void setMusicVolume(double volume) {
		try {
			options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(volume);
		}
		catch (ConcurrentModificationException e) {
			// Kept from 1.16, which saw the engine's channel walk throw here. OptionInstance.set stores the value
			// before it notifies the engine, so the slider has moved even when this throws, and the baseline
			// recorded in muteMusic still brings it back.
			JojoMod.LOGGER.warn("Failed applying Minecraft music volume {} to the sound engine for the OST.", volume, e);
		}
	}
}
