package rotp.core.client.entityrender.entities;

import rotp.core.client.ModEntityTypeRenderers;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.BladeHatEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class BladeHatRenderer extends SimpleEntityRenderer<BladeHatEntity, BladeHatEntityModel> {
	public BladeHatRenderer(EntityRendererProvider.Context context) {
		super(context);
		initTexture(JojoMod.resLoc("textures/entity/projectiles/opened_blade_hat.png"), false);
		initModel(new BladeHatEntityModel(context.bakeLayer(ModEntityTypeRenderers.BLADE_HAT)));
		offsetModelByEntityHeight(false);
	}
}
