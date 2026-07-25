package com.github.standobyte.jojoimpl.stands.magiciansred.client;

import com.github.standobyte.jojo.client.VisualPipelineDiagnostics;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRDetectorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class MRDetectorRenderer extends EntityRenderer<MRDetectorEntity> {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/mr_detector.png");
	private final MRDetectorModel model = new MRDetectorModel();

	public MRDetectorRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(MRDetectorEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(MRDetectorEntity entity, float yRotation, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		VisualPipelineDiagnostics.logEntityVisibilityOnce("mr_detector_render_gate", entity, "MR detector renderer gate reached");
		if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
			VisualPipelineDiagnostics.logOnce("mr_detector_render",
					"MR detector renderer reached: entityId={}, texture={}, pos={}.",
					entity.getId(), getTextureLocation(entity), entity.position());
			poseStack.pushPose();
			poseStack.translate(0.0D, Mth.sin((entity.tickCount + partialTick) * 0.04F) * 0.04F, 0.0D);
			model.setupAnim(entity, 0.0F, 0.0F, entity.tickCount + partialTick, yRotation, 0.0F);
			VertexConsumer vertexBuilder = buffer.getBuffer(model.renderType(getTextureLocation(entity)));
			model.renderToBuffer(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			model.renderFlames(poseStack, buffer, Minecraft.getInstance().gameRenderer.getMainCamera());
			poseStack.popPose();
			super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
		}
	}
}
