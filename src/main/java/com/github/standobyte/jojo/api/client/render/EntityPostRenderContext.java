package com.github.standobyte.jojo.api.client.render;

import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Immutable metadata for one post-entity callback.
 *
 * <p>The pose stack contains an isolated copy of the transform used by the
 * target's registered renderer. Mutating it cannot affect vanilla rendering
 * or another extension.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class EntityPostRenderContext {
	private final Entity entity;
	private final EntityRenderer<?> renderer;
	private final PoseStack poseStack;
	private final MultiBufferSource buffer;
	private final int packedLight;
	private final float entityYaw;
	private final float partialTick;
	private final long frameId;

	EntityPostRenderContext(
			Entity entity,
			EntityRenderer<?> renderer,
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			float entityYaw,
			float partialTick,
			long frameId) {
		this.entity = Objects.requireNonNull(entity, "entity");
		this.renderer = Objects.requireNonNull(renderer, "renderer");
		this.poseStack = Objects.requireNonNull(poseStack, "poseStack");
		this.buffer = Objects.requireNonNull(buffer, "buffer");
		this.packedLight = packedLight;
		this.entityYaw = entityYaw;
		this.partialTick = partialTick;
		this.frameId = frameId;
	}

	public Entity entity() {
		return entity;
	}

	public EntityRenderer<?> renderer() {
		return renderer;
	}

	public PoseStack poseStack() {
		return poseStack;
	}

	public MultiBufferSource buffer() {
		return buffer;
	}

	public int packedLight() {
		return packedLight;
	}

	public float entityYaw() {
		return entityYaw;
	}

	public float partialTick() {
		return partialTick;
	}

	public long frameId() {
		return frameId;
	}
}
