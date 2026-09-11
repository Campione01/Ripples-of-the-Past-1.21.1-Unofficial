package com.github.standobyte.jojo.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.api.client.render.ClientSkyPresentationProviders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.WorldTimeUniforms", remap = false)
public abstract class IrisWorldTimeSkyPresentationMixin {
	@Inject(method = "getWorldDayTime", at = @At("RETURN"),
			cancellable = true, remap = false, require = 1)
	private static void jojo_ripples$presentShaderWorldTime(CallbackInfoReturnable<Integer> cir) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			cir.setReturnValue(ClientSkyPresentationProviders.shaderWorldTime(
					level, 0.0F, cir.getReturnValueI(), level.getTimeOfDay(0.0F)));
		}
	}
}
