package com.github.standobyte.jojo.mixin.client.time;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationPolicies;
import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationQuery;
import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationSurface;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerRegionalTimeDilationMixin {
	@Redirect(
			method = "advanceGameTime",
			at = @At(
					value = "INVOKE",
					target = "Lit/unimi/dsi/fastutil/floats/"
							+ "FloatUnaryOperator;apply(F)F",
					remap = false))
	private float jojo_ripples$regionalTargetMillisecondsPerTick(
			FloatUnaryOperator provider,
			float baseMillisecondsPerTick) {
		float targetMillisecondsPerTick =
				provider.apply(baseMillisecondsPerTick);
		LocalPlayer player = Minecraft.getInstance().player;
		Vec3 position = player != null
				? player.position()
				: Vec3.ZERO;
		float factor = ClientRegionalTimeDilationPolicies.resolve(
				new ClientRegionalTimeDilationQuery(
						ClientRegionalTimeDilationSurface.TIMER,
						position,
						player));
		return targetMillisecondsPerTick / factor;
	}
}
