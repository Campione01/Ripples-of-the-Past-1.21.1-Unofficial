package com.github.standobyte.jojo.client.rendertype;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;

public final class AlphaMultiBufferSource implements MultiBufferSource {
	private final MultiBufferSource source;
	private final float alphaMultiplier;

	private AlphaMultiBufferSource(MultiBufferSource source, float alphaMultiplier) {
		this.source = source;
		this.alphaMultiplier = Mth.clamp(alphaMultiplier, 0.0F, 1.0F);
	}

	public static MultiBufferSource wrap(MultiBufferSource source, float alphaMultiplier) {
		return new AlphaMultiBufferSource(source, alphaMultiplier);
	}

	@Override
	public VertexConsumer getBuffer(RenderType renderType) {
		return new AlphaVertexConsumer(source.getBuffer(alphaCompatibleRenderType(renderType)), alphaMultiplier);
	}

	private RenderType alphaCompatibleRenderType(RenderType renderType) {
		if (alphaMultiplier < 1.0F && (renderType == Sheets.solidBlockSheet()
				|| renderType == Sheets.cutoutBlockSheet()
				|| renderType == Sheets.translucentItemSheet()
				|| renderType == Sheets.translucentCullBlockSheet())) {
			return ModRenderTypes.standTranslucent(TextureAtlas.LOCATION_BLOCKS);
		}
		return renderType;
	}
}
