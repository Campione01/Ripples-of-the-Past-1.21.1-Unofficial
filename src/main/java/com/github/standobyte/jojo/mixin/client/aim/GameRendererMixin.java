package com.github.standobyte.jojo.mixin.client.aim;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.input.ClientsideAim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;

	@Inject(method = "renderLevel", at = @At(value = "INVOKE_STRING", target = 
			"Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V", args = "ldc=center"))
	public void jojo_ripples$afterAimTargetPick(CallbackInfo ci) {
		ClientsideAim.updateTarget(minecraft, minecraft.getTimer().getGameTimeDeltaPartialTick(true));
	}

}
