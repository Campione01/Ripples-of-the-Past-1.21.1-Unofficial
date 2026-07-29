package com.github.standobyte.jojo.client.entityrender.stand;

import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderState.ObstructionRenderMode;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.FastColor;

public class StandClassicObstructionLayer<
			T extends StandEntity,
			S extends StandEntityRenderState,
			M extends StandEntityModel<T, S>>
		extends RenderLayer<T, M> {
	private final StandEntityRenderer<T, S, M> entityRenderer;

	public StandClassicObstructionLayer(StandEntityRenderer<T, S, M> entityRenderer) {
		super(entityRenderer);
		this.entityRenderer = entityRenderer;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
			T entity, float walkAnimPos, float walkAnimSpeed, float partialTick,
			float ticks, float headYRotation, float headXRotation) {
		if (!(RenderStateCrutches.currentStandEntityRenderState instanceof StandEntityRenderState currentState)
				|| currentState.obstructionRenderMode != ObstructionRenderMode.CLASSIC_OUTLINE) {
			return;
		}

		S renderState = (S) currentState;
		M model = getParentModel();
		if (EntityMaskPostEffect.isCapturePass()) {
			renderClassicOutline(poseStack, bufferSource, packedLight, entity, partialTick, renderState, model);
		}
		else {
			renderGameplayOutline(poseStack, packedLight, entity, partialTick, renderState, model, bufferSource);
		}

		renderState.obstructionRenderMode = ObstructionRenderMode.CLASSIC_ARMS_ONLY;
		model.setVisibleParts(renderState, renderState.classicSolidParts);
		if (renderState.classicSolidParts.length > 0) {
			VertexConsumer vertexConsumer = bufferSource.getBuffer(entityRenderer.classicSolidRenderType(entity));
			model.renderClassicSolidToBuffer(poseStack, vertexConsumer, packedLight,
					entityRenderer.getPackedOverlay(entity, partialTick), entityRenderer.classicSolidColor(entity));
		}
	}

	private void renderGameplayOutline(PoseStack poseStack, int packedLight, T entity, float partialTick,
			S renderState, M model, MultiBufferSource bufferSource) {
		OutlineBufferSource outlineBuffer = Minecraft.getInstance().renderBuffers().outlineBufferSource();
		boolean gameplayOutlineBuffer = bufferSource == outlineBuffer;
		Minecraft.getInstance().levelRenderer.requestOutlineEffect();
		try {
			setOutlineColor(outlineBuffer, renderState.classicOutlineColor);
			renderClassicOutline(poseStack, outlineBuffer, packedLight, entity, partialTick, renderState, model);
		}
		finally {
			restoreOutlineColor(outlineBuffer, entity, gameplayOutlineBuffer);
		}
	}

	private void renderClassicOutline(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
			T entity, float partialTick, S renderState, M model) {
		model.setVisibleParts(renderState, renderState.classicOutlineParts);
		if (renderState.classicOutlineParts.length > 0) {
			VertexConsumer outlineConsumer = bufferSource.getBuffer(entityRenderer.classicOutlineRenderType(entity));
			model.renderClassicLayerToBuffer(poseStack, outlineConsumer, packedLight,
					entityRenderer.getPackedOverlay(entity, partialTick), -1);
		}
		entityRenderer.renderClassicOutlineLayers(poseStack, bufferSource, packedLight, entity, renderState);
	}

	private static void setOutlineColor(OutlineBufferSource outlineBuffer, int rgbColor) {
		int color = rgbColor == -1 ? 0xFFFFFF : rgbColor;
		outlineBuffer.setColor(
				FastColor.ARGB32.red(color),
				FastColor.ARGB32.green(color),
				FastColor.ARGB32.blue(color),
				255);
	}

	private static void restoreOutlineColor(OutlineBufferSource outlineBuffer, StandEntity entity, boolean gameplayOutlineBuffer) {
		setOutlineColor(outlineBuffer, gameplayOutlineBuffer ? entity.getTeamColor() : 0xFFFFFF);
	}
}
