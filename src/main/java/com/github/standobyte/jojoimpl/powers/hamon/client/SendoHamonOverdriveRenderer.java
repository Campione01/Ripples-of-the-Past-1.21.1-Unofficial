package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonSendoOverdriveEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class SendoHamonOverdriveRenderer extends EntityRenderer<HamonSendoOverdriveEntity> {
	private static final ResourceLocation FALLBACK_TEXTURE = JojoMod.resLoc("textures/particle/spark_0.png");

	public SendoHamonOverdriveRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(HamonSendoOverdriveEntity entity) {
		return FALLBACK_TEXTURE;
	}

	@Override
	public void render(HamonSendoOverdriveEntity entity, float yRotation, float partialTick,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
			super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
		}
	}
}
