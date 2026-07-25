package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.entity_projectile.BladeHatEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class BladeHatRenderer extends SimpleEntityRenderer<BladeHatEntity, BladeHatEntityModel> {
	public BladeHatRenderer(EntityRendererProvider.Context context) {
		super(context);
		initTexture(JojoMod.resLoc("textures/entity/projectiles/opened_blade_hat.png"), false);
		initModel(new BladeHatEntityModel(context.bakeLayer(ModEntityTypeRenderers.BLADE_HAT)));
		offsetModelByEntityHeight(false);
	}
}
