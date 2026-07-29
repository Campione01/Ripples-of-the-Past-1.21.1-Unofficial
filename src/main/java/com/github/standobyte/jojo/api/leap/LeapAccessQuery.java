package com.github.standobyte.jojo.api.leap;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;

public record LeapAccessQuery(
		@Nullable LivingEntity user,
		LeapSource source,
		LeapSurface surface) {

	public LeapAccessQuery {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(surface, "surface");
	}
}
