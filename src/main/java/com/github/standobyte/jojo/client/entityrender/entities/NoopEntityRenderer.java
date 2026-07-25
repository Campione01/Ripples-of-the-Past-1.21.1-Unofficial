package com.github.standobyte.jojo.client.entityrender.entities;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class NoopEntityRenderer<T extends Entity> extends EntityRenderer<T> {
	public NoopEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
