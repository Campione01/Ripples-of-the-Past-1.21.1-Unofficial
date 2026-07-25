package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.customobjects.AfterimageEntity;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderer;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class AfterimageRenderer extends EntityRenderer<AfterimageEntity> {

	public AfterimageRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(AfterimageEntity entity) {
		return null;
	}

	@Override
	public void render(AfterimageEntity entity, float yRotation, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		Entity originEntity = entity.getOriginEntity();
		if (originEntity != null) {
			Minecraft mc = Minecraft.getInstance();
			if (!entity.shouldRenderAfterimage()
					|| originEntity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
				return;
			}
			EntityRenderer<? super Entity> originRenderer = entityRenderDispatcher.getRenderer(originEntity);
			if (originEntity instanceof StandEntity standEntity
					&& originRenderer instanceof StandEntityRenderer<?, ?, ?> standRenderer) {
				renderStandAfterimage(standRenderer, standEntity, yRotation, partialTick, poseStack, buffer, packedLight);
			}
			else {
				originRenderer.render(originEntity, yRotation, partialTick, poseStack, buffer, packedLight);
			}
		}
		super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void renderStandAfterimage(StandEntityRenderer<?, ?, ?> renderer, StandEntity entity,
			float yRotation, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		((StandEntityRenderer) renderer).renderAfterimage(
				entity, yRotation, partialTick, poseStack, buffer, packedLight);
	}
}
