package com.github.standobyte.jojo.client.particle.type.custom;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public abstract class EntityPosParticle extends TextureSheetParticle {
	protected final Entity entity;
	private final boolean firstPersonSeparateRender;

	protected EntityPosParticle(ClientLevel level, Entity entity, 
			boolean firstPersonSeparateRender /* for particles spawning at the player's arms */) {
		super(level, 0, 0, 0);
		this.entity = entity;
		this.hasPhysics = false;
		this.firstPersonSeparateRender = firstPersonSeparateRender;
	}

	protected final void initPos() {
		Vec3 pos = getNextTickPos(entity.getPosition(2.0F));
		this.setPos(pos.x, pos.y, pos.z);
		Vec3 posPrev = getNextTickPos(entity.getPosition(1.0F));
		this.xo = posPrev.x;
		this.yo = posPrev.y;
		this.zo = posPrev.z;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void tick() {
		if (age++ >= lifetime || entity == null || !entity.isAlive()) {
			remove();
			return;
		}
		Vec3 nextPos = getNextTickPos(entity.position());
		if (nextPos == null) {
			remove();
			return;
		}
		xo = x;
		yo = y;
		zo = z;
		x = nextPos.x;
		y = nextPos.y;
		z = nextPos.z;
	}

	@Override
	public void render(VertexConsumer vertexBuilder, Camera camera, float partialTick) {
		if (firstPersonSeparateRender && entity != null) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.cameraEntity == entity && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
				renderFirstPerson(vertexBuilder, camera, partialTick);
				return;
			}
		}
		super.render(vertexBuilder, camera, partialTick);
	}

	private void renderFirstPerson(VertexConsumer vertexBuilder, Camera camera, float partialTick) {

	}

	public Vec3 getPos() {
		return new Vec3(x, y, z);
	}

	protected abstract Vec3 getNextTickPos(Vec3 entityPos);
}
