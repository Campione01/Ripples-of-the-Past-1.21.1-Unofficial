package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.entity_projectile.LightBeamEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class LightBeamRenderer<T extends LightBeamEntity> extends BeamRenderer<T> {
	private static final ResourceLocation BEAM_TEX = JojoMod.resLoc("textures/entity/projectiles/aja_beam.png");

	public LightBeamRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return BEAM_TEX;
	}

	@Override
	protected Vec3 pointB(T entity, float partialTick) {
		return entity.getEndPoint();
	}

	@Override
	protected float getBeamWidth(T entity) {
		return entity.getBaseDamage() * 0.002F;
	}
}
