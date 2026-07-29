package com.github.standobyte.jojo.api.client.render;

import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Frame-scoped access to one living renderer's current entity, model pose,
 * transform, and buffers.
 *
 * <p>The pose stack is an isolated copy. A provider can transform it without
 * affecting vanilla rendering or another provider.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class LivingEntityRenderLayerContext {
	private final LivingEntity entity;
	private final EntityModel<?> model;
	private final PoseStack poseStack;
	private final MultiBufferSource buffer;
	private final int packedLight;
	private final float partialTick;

	LivingEntityRenderLayerContext(
			LivingEntity entity,
			EntityModel<?> model,
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			float partialTick) {
		this.entity = Objects.requireNonNull(entity, "entity");
		this.model = Objects.requireNonNull(model, "model");
		this.poseStack = Objects.requireNonNull(poseStack, "poseStack");
		this.buffer = Objects.requireNonNull(buffer, "buffer");
		this.packedLight = packedLight;
		this.partialTick = partialTick;
	}

	public LivingEntity entity() {
		return entity;
	}

	public PoseStack poseStack() {
		return poseStack;
	}

	public VertexConsumer getBuffer(RenderType renderType) {
		return buffer.getBuffer(
				Objects.requireNonNull(renderType, "renderType"));
	}

	public int packedLight() {
		return packedLight;
	}

	public float partialTick() {
		return partialTick;
	}

	/**
	 * Renders the current renderer's already-posed base model.
	 */
	public void renderModel(
			VertexConsumer vertices,
			int light,
			int packedOverlay,
			int color) {
		model.renderToBuffer(
				poseStack,
				Objects.requireNonNull(vertices, "vertices"),
				light,
				packedOverlay,
				color);
	}
}
