package com.github.standobyte.jojo.client.particle.type;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

public class OneTickFlameParticle extends TextureSheetParticle {

	protected OneTickFlameParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
		super(level, x, y, z, xSpeed, ySpeed, zSpeed);
		this.lifetime = 1;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}

	@Override
	public void move(double x, double y, double z) {
		setBoundingBox(getBoundingBox().move(x, y, z));
		setLocationFromBoundingbox();
	}

	@Override
	public int getLightColor(float partialTick) {
		return LightTexture.FULL_BRIGHT;
	}

	public static class Factory implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprite;

		public Factory(SpriteSet sprite) {
			this.sprite = sprite;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			OneTickFlameParticle particle = new OneTickFlameParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
			particle.pickSprite(sprite);
			return particle;
		}
	}
}
