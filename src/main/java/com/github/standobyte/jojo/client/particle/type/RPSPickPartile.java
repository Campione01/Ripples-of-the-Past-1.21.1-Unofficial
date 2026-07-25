package com.github.standobyte.jojo.client.particle.type;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

public class RPSPickPartile extends RisingParticleOld {

	protected RPSPickPartile(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float size, SpriteSet sprite) {
		super(level, x, y, z, 0, 0, 0, xSpeed, ySpeed, zSpeed, size, sprite, 1, 5, 0.004D, false);
		this.rCol = 1;
		this.gCol = 1;
		this.bCol = 1;
		this.lifetime = 40;
	}

	@Override
	public void tick() {
		super.tick();
		setAlpha(Math.min((1 - (float) (age) / (float) lifetime) * 4, 1));
	}

	public static class Factory implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Factory(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			return new RPSPickPartile(level, x, y, z, xSpeed, ySpeed, zSpeed, 2.0F, this.sprites);
		}
	}

}
