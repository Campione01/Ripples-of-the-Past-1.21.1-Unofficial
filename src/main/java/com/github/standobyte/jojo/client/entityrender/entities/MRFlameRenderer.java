package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojoimpl.stands.magiciansred.MRFlameEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

public class MRFlameRenderer extends FlameRenderer<MRFlameEntity> {

	public MRFlameRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected Vec3 getStartingPos(MRFlameEntity entity) {
		return entity.getStartingPos();
	}
}
