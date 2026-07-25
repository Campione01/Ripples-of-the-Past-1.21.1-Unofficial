package com.github.standobyte.jojo.client.particle.type.custom;

import java.util.List;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.particle.type.BloodParticle;
import com.github.standobyte.jojo.init.ModEntityTypes;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;

public class BloodFromEntityParticle extends BloodParticle {
	private final Entity entity;

	protected BloodFromEntityParticle(ClientLevel level, Entity entity, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
		super(level, x, y, z, xSpeed, ySpeed, zSpeed);
		this.entity = entity;
	}

	@Override
	protected List<Entity> checkEntity() {
		return level.getEntities(entity, this.getBoundingBox(), 
				entity -> entity.getType() != ModEntityTypes.CD_BLOOD_CUTTER.get());
	}

	public static BloodFromEntityParticle createCustomParticle(ParticleOptions type, ClientLevel level, @Nullable Entity entity,
			double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
		BloodFromEntityParticle particle = new BloodFromEntityParticle(level, entity, x, y, z, xSpeed, ySpeed, zSpeed);
		particle.pickSprite(BloodParticle.Factory.getSprite());
		particle.setLifetime(60);
		particle.scale(0.5F);
		particle.gravity = 1;
		return particle;
	}

}
