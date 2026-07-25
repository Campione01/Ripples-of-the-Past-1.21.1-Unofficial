package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojoimpl.stands.silverchariot.SCFlameSwingEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

public class SCFlameRenderer extends FlameRenderer<SCFlameSwingEntity> {

	public SCFlameRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected Vec3 getStartingPos(SCFlameSwingEntity entity) {
		return entity.getStartingPos();
	}
}
