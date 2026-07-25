package com.github.standobyte.jojo.mixin.client.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.github.standobyte.jojo.client.ClientTimeStopHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
public class GameRendererTimeStopMixin {
	@ModifyVariable(method = "bobHurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float jojo_ripples$freezeHurtCameraPartialTick(float partialTick) {
		return ClientTimeStopHandler.shouldFreezeHurtCamera(Minecraft.getInstance().getCameraEntity()) ? 1.0F : partialTick;
	}
}
