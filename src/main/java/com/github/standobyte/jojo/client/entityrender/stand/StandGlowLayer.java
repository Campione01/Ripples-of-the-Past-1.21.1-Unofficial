package com.github.standobyte.jojo.client.entityrender.stand;

import com.github.standobyte.jojo.client.ResourcePathChecker;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class StandGlowLayer<
				T extends StandEntity,
				S extends StandEntityRenderState,
				M extends StandEntityModel<T, S>>
		extends RenderLayer<T, M> implements StandSkinUiLayer {

	public StandGlowLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			T entity, float walkAnimPos, float walkAnimSpeed, float partialTick,
			float ticks, float headYRotation, float headXRotation) {
		StandEntityRenderState renderState = RenderStateCrutches.currentStandEntityRenderState;
		renderGlow(poseStack, buffer, renderState, false);
	}

	@Override
	public void renderForStandSkinUI(PoseStack poseStack, MultiBufferSource buffer, StandEntityRenderState renderState) {
		renderGlow(poseStack, buffer, renderState, true);
	}

	@SuppressWarnings("unchecked")
	private void renderGlow(PoseStack poseStack, MultiBufferSource buffer, StandEntityRenderState renderState,
			boolean directTarget) {
		if (renderState == null || renderState.skin == null || renderState.alpha <= 0.0F) {
			return;
		}
		if (renderState.isInvisibleToPlayer && !renderState.visibleForSpectator) {
			return;
		}

		ResourceLocation glowTexture = glowTexture(renderState.skin);
		if (!ResourcePathChecker.resourceExists(glowTexture)) {
			return;
		}

		M model = getParentModel();
		model.setupAnim((S) renderState);
		RenderType renderType = directTarget
				? ModRenderTypes.standTranslucentDirectCull(glowTexture)
				: ModRenderTypes.standTranslucentCull(glowTexture);
		VertexConsumer vertexBuilder = buffer.getBuffer(renderType);
		int color = 0xFFFFFFFF;
		model.renderToBuffer(poseStack, vertexBuilder, ClientUtil.MAX_LIGHT, OverlayTexture.NO_OVERLAY, color);
	}

	@SuppressWarnings("unchecked")
	void renderClassicOutline(PoseStack poseStack, MultiBufferSource buffer, StandEntityRenderState renderState) {
		if (renderState == null || renderState.skin == null
				|| renderState.isInvisibleToPlayer && !renderState.visibleForSpectator) {
			return;
		}
		ResourceLocation glowTexture = glowTexture(renderState.skin);
		if (!ResourcePathChecker.resourceExists(glowTexture)) {
			return;
		}
		M model = getParentModel();
		VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.outline(glowTexture));
		model.renderClassicLayerToBuffer(poseStack, vertexBuilder, ClientUtil.MAX_LIGHT,
				OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	private static ResourceLocation glowTexture(StandSkin skin) {
		ResourceLocation baseTexture = ResourceLocation.fromNamespaceAndPath(
				skin.standTypeId.getNamespace(),
				"textures/entity/glow/" + skin.standTypeId.getPath() + ".png");
		return skin.getTexture(baseTexture, baseTexture);
	}
}
