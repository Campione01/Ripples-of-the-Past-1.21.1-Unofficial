package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.entity_projectile.ClackersEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ClackersRenderer extends SimpleEntityRenderer<ClackersEntity, ClackersModel> {
	public ClackersRenderer(EntityRendererProvider.Context context) {
		super(context);
		initTexture(JojoMod.resLoc("textures/entity/projectiles/clackers.png"), false);
		initModel(new ClackersModel(context.bakeLayer(ModEntityTypeRenderers.CLACKERS)));
	}
}
