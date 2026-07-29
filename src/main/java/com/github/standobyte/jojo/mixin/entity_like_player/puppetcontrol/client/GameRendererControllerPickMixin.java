package com.github.standobyte.jojo.mixin.entity_like_player.puppetcontrol.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.ClientEntityController;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;

@Mixin(GameRenderer.class)
public abstract class GameRendererControllerPickMixin {
	@ModifyVariable(method = "pick", at = @At("STORE"), ordinal = 0)
	private Entity jojo_ripples$controllerPickSource(Entity cameraEntity) {
		ClientEntityController controller =
				ClientEntityController.getInstance();
		if (controller == null || controller.entity != cameraEntity) {
			return cameraEntity;
		}
		Entity pickSource = controller.getPickSource(cameraEntity);
		return pickSource != null
				&& pickSource.level() == cameraEntity.level()
						? pickSource
						: cameraEntity;
	}
}
