package com.github.standobyte.jojo.mixin.client.modcompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.modcompat.JojoModsInteraction;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

@Mixin(EntityRenderDispatcher.class)
public class WoRStandRendering {
	private String jojo_ripples$WoRStandName = null;
	
	@Inject(method = "shouldRender", at = @At(value = "INVOKE", 
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldRender("
					+ "Lnet/minecraft/world/entity/Entity;"
					+ "Lnet/minecraft/client/renderer/culling/Frustum;"
					+ "DDD)Z"))
	public void jojo_ripples$beforeWorStandRenderCheck(Entity entity, Frustum frustum, 
			double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> ci) {
		if (ClientGlobals.canSeeStands && "jojowor".equals(EntityType.getKey(entity.getType()).getNamespace())) {
			String WoRStandName = JojoModsInteraction.WingsOfRequiem.getClientStandType();
			if (WoRStandName != null && "None".equals(WoRStandName)) {
				jojo_ripples$WoRStandName = WoRStandName;
				JojoModsInteraction.WingsOfRequiem.setClientStandType("OtherMod");
			}
		}
	}
	
	@Inject(method = "shouldRender", at = @At(value = "INVOKE", 
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldRender("
					+ "Lnet/minecraft/world/entity/Entity;"
					+ "Lnet/minecraft/client/renderer/culling/Frustum;"
					+ "DDD)Z", shift = At.Shift.AFTER))
	public void jojo_ripples$afterWorStandRenderCheck(Entity entity, Frustum frustum, 
			double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> ci) {
		if (jojo_ripples$WoRStandName != null) {
			JojoModsInteraction.WingsOfRequiem.setClientStandType(jojo_ripples$WoRStandName);
			jojo_ripples$WoRStandName = null;
		}
	}
}
