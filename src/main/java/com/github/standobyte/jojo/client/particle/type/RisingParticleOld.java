package com.github.standobyte.jojo.client.particle.type;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

// делать людям нехуй, партиклы переписывают, кому мешало
public class RisingParticleOld extends TextureSheetParticle {
	private final SpriteSet sprites;
	private final double fallSpeed;

	public RisingParticleOld(ClientLevel level, double x, double y, double z, 
			float xSpeedMult, float ySpeedMult, float zSpeedMult, 
			double xSpeed, double ySpeed, double zSpeed, float size, 
			SpriteSet sprites, float brightness, double lifetime, double fallSpeed, 
			boolean hasPhysics) {
		super(level, x, y, z, 0, 0, 0);
		this.fallSpeed = fallSpeed;
		this.sprites = sprites;
		this.xd *= (double)xSpeedMult;
		this.yd *= (double)ySpeedMult;
		this.zd *= (double)zSpeedMult;
		this.xd += xSpeed;
		this.yd += ySpeed;
		this.zd += zSpeed;
		float f = level.random.nextFloat() * brightness;
		this.rCol = f;
		this.gCol = f;
		this.bCol = f;
		this.quadSize *= 0.75F * size;
		this.lifetime = (int)(lifetime / ((double) level.random.nextFloat() * 0.8 + 0.2));
		this.lifetime = (int)((float)this.lifetime * size);
		this.lifetime = Math.max(this.lifetime, 1);
		this.setSpriteFromAge(sprites);
		this.hasPhysics = hasPhysics;
	}

	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}

	public float getQuadSize(float pScaleFactor) {
		return this.quadSize * Mth.clamp(((float)this.age + pScaleFactor) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
	}

	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ >= this.lifetime) {
			this.remove();
		} else {
			this.setSpriteFromAge(this.sprites);
			this.yd += this.fallSpeed;
			this.move(this.xd, this.yd, this.zd);
			if (this.y == this.yo) {
				this.xd *= 1.1D;
				this.zd *= 1.1D;
			}

			this.xd *= (double)0.96F;
			this.yd *= (double)0.96F;
			this.zd *= (double)0.96F;
			if (this.onGround) {
				this.xd *= (double)0.7F;
				this.zd *= (double)0.7F;
			}

		}
	}
}