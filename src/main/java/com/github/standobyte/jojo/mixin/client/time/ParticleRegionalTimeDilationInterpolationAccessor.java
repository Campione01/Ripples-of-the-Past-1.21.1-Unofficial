package com.github.standobyte.jojo.mixin.client.time;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.particle.Particle;

@Mixin(Particle.class)
public interface ParticleRegionalTimeDilationInterpolationAccessor {
	@Accessor("x")
	double jojo_ripples$getCurrentX();

	@Accessor("y")
	double jojo_ripples$getCurrentY();

	@Accessor("z")
	double jojo_ripples$getCurrentZ();

	@Accessor("roll")
	float jojo_ripples$getCurrentRoll();

	@Accessor("xo")
	void jojo_ripples$setPreviousX(double x);

	@Accessor("yo")
	void jojo_ripples$setPreviousY(double y);

	@Accessor("zo")
	void jojo_ripples$setPreviousZ(double z);

	@Accessor("oRoll")
	void jojo_ripples$setPreviousRoll(float roll);
}
