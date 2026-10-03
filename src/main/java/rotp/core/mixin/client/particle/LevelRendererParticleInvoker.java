package rotp.core.mixin.client.particle;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.particles.ParticleOptions;

@Mixin(LevelRenderer.class)
public interface LevelRendererParticleInvoker {
	@Nullable
	@Invoker("addParticleInternal")
	Particle jojo_ripples$addParticle(ParticleOptions options, boolean force,
			double x, double y, double z, double xSpeed, double ySpeed, double zSpeed);
}
