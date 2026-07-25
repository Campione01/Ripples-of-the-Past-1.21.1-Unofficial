package com.github.standobyte.jojo.client.particle.type;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class MeteoriteVirusParticle extends TextureSheetParticle {

	protected MeteoriteVirusParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
		super(level, x, y, z, xSpeed, ySpeed, zSpeed);
		lifetime = 100;
		quadSize = 0.075F;
		hasPhysics = false;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_LIT;
	}

	@Override
	public float getQuadSize(float partialTick) {
		float floatAge = (float) age + partialTick;
		float sizeFactor = floatAge < lifetime / 2F ? floatAge : lifetime - floatAge;
		return quadSize * Mth.clamp(sizeFactor / (lifetime / 2F) * 4F, 0.0F, 1.0F);
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime) {
			remove();
		}
	}

	public static class Factory implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet spriteSet;

		public Factory(SpriteSet sprite) {
			this.spriteSet = sprite;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			MeteoriteVirusParticle particle = new MeteoriteVirusParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
			particle.pickSprite(spriteSet);
			return particle;
		}
	}
}
