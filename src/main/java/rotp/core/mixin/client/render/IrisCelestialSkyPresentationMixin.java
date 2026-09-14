package rotp.core.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import rotp.core.api.client.render.ClientSkyPresentationProviders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientLevel;

@Pseudo
@Mixin(targets = {
		"net.irisshaders.iris.uniforms.CelestialUniforms",
		"net.irisshaders.iris.shadows.ShadowRenderer"
}, remap = false)
public abstract class IrisCelestialSkyPresentationMixin {
	@WrapOperation(method = "getSkyAngle", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"),
			remap = false, require = 1)
	private static float jojo_ripples$presentShaderSkyAngle(
			ClientLevel level, float partialTick, Operation<Float> original) {
		return ClientSkyPresentationProviders.shaderTimeOfDay(
				level, partialTick, original.call(level, partialTick));
	}
}
