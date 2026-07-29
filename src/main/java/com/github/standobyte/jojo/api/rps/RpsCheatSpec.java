package com.github.standobyte.jojo.api.rps;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;

public record RpsCheatSpec(
		RpsCheatKind kind,
		Supplier<? extends SoundEvent> firstUseSound) {

	public RpsCheatSpec {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(firstUseSound, "firstUseSound");
	}

	public static RpsCheatSpec mindRead(
			Supplier<? extends SoundEvent> firstUseSound) {
		return new RpsCheatSpec(
				RpsCheatKind.MIND_READ, firstUseSound);
	}
}
