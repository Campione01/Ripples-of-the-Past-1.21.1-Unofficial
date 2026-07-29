package com.github.standobyte.jojo.api.client.render;

import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One exact-entity mask group ready for a private-target composite.
 *
 * <p>The target and texture ownership stays inside the core. Addons can bind a
 * core shader and its uniforms without receiving mutable framebuffer access.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class EntityMaskCompositeContext {
	private final Object groupKey;
	private final List<Entity> entities;
	private final float partialTick;
	private final float auraThicknessScale;
	private final float minU;
	private final float minV;
	private final float maxU;
	private final float maxV;
	private final int outputWidth;
	private final int outputHeight;
	private final int sourceWidth;
	private final int sourceHeight;
	private final RenderTarget maskTarget;
	private final RenderTarget auraTarget;
	private final RenderTarget sceneTarget;
	private boolean drewComposite;

	EntityMaskCompositeContext(
			Object groupKey,
			List<Entity> entities,
			float partialTick,
			float auraThicknessScale,
			float minU,
			float minV,
			float maxU,
			float maxV,
			int outputWidth,
			int outputHeight,
			int sourceWidth,
			int sourceHeight,
			RenderTarget maskTarget,
			RenderTarget auraTarget,
			RenderTarget sceneTarget) {
		this.groupKey = Objects.requireNonNull(groupKey, "groupKey");
		this.entities = List.copyOf(entities);
		this.partialTick = partialTick;
		this.auraThicknessScale = auraThicknessScale;
		this.minU = minU;
		this.minV = minV;
		this.maxU = maxU;
		this.maxV = maxV;
		this.outputWidth = outputWidth;
		this.outputHeight = outputHeight;
		this.sourceWidth = sourceWidth;
		this.sourceHeight = sourceHeight;
		this.maskTarget = maskTarget;
		this.auraTarget = auraTarget;
		this.sceneTarget = sceneTarget;
	}

	public Object groupKey() {
		return groupKey;
	}

	public List<Entity> entities() {
		return entities;
	}

	public Entity primaryEntity() {
		return entities.get(0);
	}

	public float partialTick() {
		return partialTick;
	}

	public float auraThicknessScale() {
		return auraThicknessScale;
	}

	public int width() {
		return outputWidth;
	}

	public int height() {
		return outputHeight;
	}

	public int sourceWidth() {
		return sourceWidth;
	}

	public int sourceHeight() {
		return sourceHeight;
	}

	/**
	 * Draws the clipped group into the core-owned transparent aura target.
	 */
	public void draw(
			ShaderInstance shader,
			UniformUpdater uniformUpdater) {
		EntityMaskPostEffect.drawComposite(
				this,
				Objects.requireNonNull(shader, "shader"),
				Objects.requireNonNull(
						uniformUpdater, "uniformUpdater"));
	}

	RenderTarget maskTarget() {
		return maskTarget;
	}

	RenderTarget auraTarget() {
		return auraTarget;
	}

	RenderTarget sceneTarget() {
		return sceneTarget;
	}

	float minU() {
		return minU;
	}

	float minV() {
		return minV;
	}

	float maxU() {
		return maxU;
	}

	float maxV() {
		return maxV;
	}

	void markDrewComposite() {
		drewComposite = true;
	}

	boolean drewComposite() {
		return drewComposite;
	}

	@FunctionalInterface
	public interface UniformUpdater {
		void update(
				ShaderInstance shader,
				EntityMaskCompositeContext context);
	}
}
