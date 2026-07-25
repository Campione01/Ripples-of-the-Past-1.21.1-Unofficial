package com.github.standobyte.jojo.mixin.client.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.ClientTimeStopHandler;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;

@Mixin(LevelRenderer.class)
public class LevelRendererTimeStopMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$cancelLevelRendererTickInTimeStop(CallbackInfo ci) {
		if (ClientTimeStopHandler.shouldFreezeVisualTick()) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "renderSnowAndRain", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float jojo_ripples$freezeWeatherPartialTick(float partialTick) {
		return ClientTimeStopHandler.getConstantWorldPartialTick(partialTick);
	}

	@ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float jojo_ripples$freezeCloudPartialTick(float partialTick) {
		return ClientTimeStopHandler.getConstantWorldPartialTick(partialTick);
	}

	@ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float jojo_ripples$freezeEntityPartialTick(float partialTick, Entity entity,
			double camX, double camY, double camZ, float originalPartialTick,
			PoseStack poseStack, MultiBufferSource bufferSource) {
		return ClientTimeStopHandler.getConstantEntityPartialTick(entity, partialTick);
	}
}
