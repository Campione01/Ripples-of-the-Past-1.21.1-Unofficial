package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.client.VisualPipelineDiagnostics;
import com.github.standobyte.jojo.init.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public abstract class FlameRenderer<T extends Entity> extends EntityRenderer<T> {
	private static final double STEP_LENGTH = 0.4D;

	public FlameRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return null;
	}

	@Override
	public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		VisualPipelineDiagnostics.logEntityVisibilityOnce("flame_entity_render_gate", entity, "Flame entity renderer gate reached");
		if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
			Vec3 pos = entity.getPosition(partialTick);
			Vec3 vec = getStartingPos(entity).subtract(pos);
			double length = vec.length();
			if (length > 1.0E-6) {
				VisualPipelineDiagnostics.logOnce("flame_entity_render_" + entity.getType().builtInRegistryHolder().key().location(),
						"Flame entity renderer reached: entityId={}, type={}, length={}, start={}, pos={}.",
						entity.getId(), entity.getType().builtInRegistryHolder().key().location(), length, getStartingPos(entity), entity.position());
				Vec3 step = vec.scale(STEP_LENGTH / length);
				for (int i = Mth.floor(length / STEP_LENGTH); i > 0; i--) {
					entity.level().addParticle(ModParticles.FLAME_ONE_TICK.get(), true, pos.x, pos.y, pos.z, 0, 0, 0);
					pos = pos.add(step);
				}
			}
			super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
		}
	}

	protected abstract Vec3 getStartingPos(T entity);
}
