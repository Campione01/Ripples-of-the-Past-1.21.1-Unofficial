package com.github.standobyte.jojo.client.rendertype;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class CustomRenderType extends RenderType {
	private CustomRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
			boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
	}

	public static RenderType goldExperienceLifeformOverlay(ResourceLocation overlayTexture, float xScale, float yScale) {
		CompositeState state = CompositeState.builder()
				.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
				.setTextureState(new RenderStateShard.TextureStateShard(overlayTexture, false, false))
				.setTexturingState(new ScaledTexturingState(xScale, yScale))
				.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
				.setCullState(NO_CULL)
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(false);
		return create("jojo_ge_lifeform_overlay", DefaultVertexFormat.NEW_ENTITY,
				VertexFormat.Mode.QUADS, 256, true, true, state);
	}

	public static RenderType hamonProjectileShield(ResourceLocation texture) {
		CompositeState state = CompositeState.builder()
				.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
				.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
				.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
				.setCullState(NO_CULL)
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(false);
		return create("jojo_hamon_projectile_shield", DefaultVertexFormat.NEW_ENTITY,
				VertexFormat.Mode.QUADS, 256, true, true, state);
	}
}
