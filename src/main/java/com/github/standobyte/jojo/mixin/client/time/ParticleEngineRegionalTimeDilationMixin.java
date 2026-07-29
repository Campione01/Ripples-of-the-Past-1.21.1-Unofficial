package com.github.standobyte.jojo.mixin.client.time;

import java.util.Map;
import java.util.WeakHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationPolicies;
import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationQuery;
import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationSurface;
import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationTickAccumulator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.world.phys.Vec3;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineRegionalTimeDilationMixin {
	@Unique
	private final Map<Particle, ClientRegionalTimeDilationTickAccumulator>
			jojo_ripples$regionalTickAccumulators = new WeakHashMap<>();

	@Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$scheduleRegionalTick(
			Particle particle,
			CallbackInfo ci) {
		if (!particle.isAlive()) {
			jojo_ripples$regionalTickAccumulators.remove(particle);
			return;
		}

		Vec3 position = particle.getPos();
		float factor = ClientRegionalTimeDilationPolicies.resolve(
				new ClientRegionalTimeDilationQuery(
						ClientRegionalTimeDilationSurface.PARTICLE,
						position,
						Minecraft.getInstance().player));
		if (factor >= 1.0F) {
			jojo_ripples$regionalTickAccumulators.remove(particle);
			return;
		}

		ClientRegionalTimeDilationTickAccumulator accumulator =
				jojo_ripples$regionalTickAccumulators.computeIfAbsent(
						particle,
						ignored ->
								new ClientRegionalTimeDilationTickAccumulator());
		if (!accumulator.advance(factor)) {
			jojo_ripples$stabilizeSkippedTick(
					(ParticleRegionalTimeDilationInterpolationAccessor)
							(Object) particle);
			ci.cancel();
		}
	}

	@Unique
	static void jojo_ripples$stabilizeSkippedTick(
			ParticleRegionalTimeDilationInterpolationAccessor particle) {
		particle.jojo_ripples$setPreviousX(
				particle.jojo_ripples$getCurrentX());
		particle.jojo_ripples$setPreviousY(
				particle.jojo_ripples$getCurrentY());
		particle.jojo_ripples$setPreviousZ(
				particle.jojo_ripples$getCurrentZ());
		particle.jojo_ripples$setPreviousRoll(
				particle.jojo_ripples$getCurrentRoll());
	}

	@Inject(method = "tickParticle", at = @At("TAIL"))
	private void jojo_ripples$discardRemovedParticle(
			Particle particle,
			CallbackInfo ci) {
		if (!particle.isAlive()) {
			jojo_ripples$regionalTickAccumulators.remove(particle);
		}
	}

	@Inject(method = "clearParticles", at = @At("HEAD"))
	private void jojo_ripples$clearRegionalTickAccumulators(
			CallbackInfo ci) {
		jojo_ripples$regionalTickAccumulators.clear();
	}
}
