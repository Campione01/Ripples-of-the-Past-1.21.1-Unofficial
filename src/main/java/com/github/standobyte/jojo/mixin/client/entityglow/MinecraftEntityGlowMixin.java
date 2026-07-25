package com.github.standobyte.jojo.mixin.client.entityglow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.client.VisualPipelineDiagnostics;
import com.github.standobyte.jojo.subsystems.entityglow.EntityGlowChannel;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

@Mixin(Minecraft.class)
public class MinecraftEntityGlowMixin {

	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$entityGlowChannelShouldGlow(Entity entity, CallbackInfoReturnable<Boolean> ci) {
		if (EntityGlowChannel.currentGlowAny(entity).isPresent()) {
			VisualPipelineDiagnostics.logOnce("ge_glow_should_" + entity.getType().builtInRegistryHolder().key().location(),
					"ROTP glow channel reached Minecraft outline decision: entityId={}, type={}, pos={}.",
					entity.getId(), entity.getType().builtInRegistryHolder().key().location(), entity.position());
			ci.setReturnValue(true);
		}
	}
}
