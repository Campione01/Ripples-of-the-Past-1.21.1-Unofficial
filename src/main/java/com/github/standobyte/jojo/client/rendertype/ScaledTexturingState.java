package com.github.standobyte.jojo.client.rendertype;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.RenderStateShard;

public class ScaledTexturingState extends RenderStateShard.TexturingStateShard {
	private final float xScale;
	private final float yScale;

	public ScaledTexturingState(float xScale, float yScale) {
		super("jojo_scaled_texturing",
				() -> RenderSystem.setTextureMatrix(new Matrix4f().scaling(xScale, yScale, 1.0F)),
				RenderSystem::resetTextureMatrix);
		this.xScale = xScale;
		this.yScale = yScale;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		ScaledTexturingState other = (ScaledTexturingState) obj;
		return Float.compare(xScale, other.xScale) == 0 && Float.compare(yScale, other.yScale) == 0;
	}

	@Override
	public int hashCode() {
		int result = Float.hashCode(xScale);
		result = 31 * result + Float.hashCode(yScale);
		return result;
	}
}
