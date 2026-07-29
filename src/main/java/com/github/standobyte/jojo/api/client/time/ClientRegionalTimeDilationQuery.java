package com.github.standobyte.jojo.api.client.time;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Immutable context for one regional client time-dilation query.
 */
@OnlyIn(Dist.CLIENT)
public record ClientRegionalTimeDilationQuery(
		ClientRegionalTimeDilationSurface surface,
		Vec3 position,
		@Nullable LocalPlayer localPlayer) {
	public ClientRegionalTimeDilationQuery {
		Objects.requireNonNull(surface, "surface");
		Objects.requireNonNull(position, "position");
	}
}
