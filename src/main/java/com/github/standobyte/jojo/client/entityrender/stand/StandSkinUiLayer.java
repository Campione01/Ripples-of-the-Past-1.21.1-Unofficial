package com.github.standobyte.jojo.client.entityrender.stand;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;

public interface StandSkinUiLayer {
	void renderForStandSkinUI(PoseStack poseStack, MultiBufferSource buffer, StandEntityRenderState renderState);
}
