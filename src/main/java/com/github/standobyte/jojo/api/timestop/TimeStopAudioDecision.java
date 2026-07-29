package com.github.standobyte.jojo.api.timestop;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * Tri-state audio selection. {@link Kind#PASS} preserves the core selector,
 * {@link Kind#SOUND} supplies an explicit sound, and {@link Kind#SILENT}
 * suppresses both the core selector and playback.
 */
public final class TimeStopAudioDecision {
	private static final TimeStopAudioDecision PASS =
			new TimeStopAudioDecision(Kind.PASS, null);
	private static final TimeStopAudioDecision SILENT =
			new TimeStopAudioDecision(Kind.SILENT, null);

	private final Kind kind;
	@Nullable
	private final Holder<SoundEvent> sound;

	private TimeStopAudioDecision(
			Kind kind, @Nullable Holder<SoundEvent> sound) {
		this.kind = kind;
		this.sound = sound;
	}

	public static TimeStopAudioDecision pass() {
		return PASS;
	}

	public static TimeStopAudioDecision silent() {
		return SILENT;
	}

	public static TimeStopAudioDecision sound(Holder<SoundEvent> sound) {
		return new TimeStopAudioDecision(
				Kind.SOUND, Objects.requireNonNull(sound, "sound"));
	}

	public Kind kind() {
		return kind;
	}

	@Nullable
	public Holder<SoundEvent> sound() {
		return sound;
	}

	public enum Kind {
		PASS,
		SOUND,
		SILENT
	}
}
