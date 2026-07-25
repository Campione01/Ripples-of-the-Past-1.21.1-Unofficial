package com.github.standobyte.jojo.mixin.client.visual;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.VisualPipelineDiagnostics;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

@Mixin(ClientLevel.class)
public class ClientLevelEntityAddMixin {

	@Inject(method = "addEntity", at = @At("TAIL"))
	private void jojo_ripples$logRotpVisualEntityAdded(Entity entity, CallbackInfo ci) {
		if (VisualPipelineDiagnostics.isRotpEntity(entity)) {
			VisualPipelineDiagnostics.logOnce("client_add_entity_" + entity.getType().builtInRegistryHolder().key().location() + "_" + entity.getId(),
					"ClientLevel.addEntity received ROTP entity: entityId={}, type={}, complexSpawn={}, invisible={}, pos={}.",
					entity.getId(), entity.getType().builtInRegistryHolder().key().location(),
					entity instanceof IEntityWithComplexSpawn, entity.isInvisible(), entity.position());
		}
	}
}
