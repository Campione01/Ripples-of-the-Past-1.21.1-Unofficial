package rotp.core.client.sound.sounds;

import java.util.function.DoubleSupplier;

import javax.annotation.Nullable;

import net.minecraft.util.Mth;

/**
 * Volume of an {@link EntityStoppableSoundInstance} tick by tick, free of client classes so it can be checked on a server.
 */
public final class StoppableSoundVolume {
	private final float baseVolume;
	private final int fadeOutTicks;
	private final float fadeOutStep;
	@Nullable
	private final DoubleSupplier volumeFactor;
	private float volume;
	private float fadeFromVolume;
	private int fadeOutTicksLeft = -1;
	private boolean started;

	/**
	 * @param fadeOutTicks after stopping, fade linearly over this many ticks (0: stop at once)
	 * @param fadeOutStep above 0: after stopping, drop by this much per tick instead (1.16 HamonEnergySound)
	 * @param volumeFactor optional 0..1 factor read each running tick
	 */
	public StoppableSoundVolume(float baseVolume, int fadeOutTicks, float fadeOutStep, @Nullable DoubleSupplier volumeFactor) {
		this.baseVolume = baseVolume;
		this.fadeOutTicks = Math.max(fadeOutTicks, 0);
		this.fadeOutStep = Math.max(fadeOutStep, 0.0F);
		this.volumeFactor = volumeFactor;
		this.volume = running(baseVolume, volumeFactor);
		this.fadeFromVolume = this.volume;
	}

	public float volume() {
		return volume;
	}

	// A scaled sound may start at 0 volume and swell later
	public boolean isScaled() {
		return volumeFactor != null;
	}

	// One sound tick; false once the sound must stop
	public boolean tick(boolean stopping) {
		try {
			if (!stopping) {
				fadeOutTicksLeft = -1;
				volume = running(baseVolume, volumeFactor);
				fadeFromVolume = volume;
				return true;
			}
			if (fadeOutStep > 0.0F) {
				volume = stepped(volume, fadeOutStep, started);
				return volume > 0.0F;
			}
			if (fadeOutTicks > 0) {
				if (fadeOutTicksLeft < 0) {
					fadeOutTicksLeft = fadeOutTicks;
				}
				if (fadeOutTicksLeft <= 0) {
					return false;
				}
				volume = fading(fadeFromVolume, fadeOutTicksLeft, fadeOutTicks);
				fadeOutTicksLeft--;
				return true;
			}
			return false;
		}
		finally {
			started = true;
		}
	}

	// Volume while the sound runs: the base volume scaled by the optional 0..1 factor.
	public static float running(float baseVolume, @Nullable DoubleSupplier volumeFactor) {
		if (volumeFactor == null) {
			return baseVolume;
		}
		return baseVolume * Mth.clamp((float) volumeFactor.getAsDouble(), 0.0F, 1.0F);
	}

	// Linear fade from the volume the sound had when it was stopped.
	public static float fading(float fadeFromVolume, int fadeOutTicksLeft, int fadeOutTicks) {
		if (fadeOutTicks <= 0) {
			return 0.0F;
		}
		return fadeFromVolume * (float) fadeOutTicksLeft / (float) fadeOutTicks;
	}

	// 1.16 HamonEnergySound after release: the volume drops by a fixed step each tick until silent,
	// and a sound released before its first tick stops at once.
	public static float stepped(float volume, float step, boolean started) {
		return started ? Math.max(volume - step, 0.0F) : 0.0F;
	}
}
