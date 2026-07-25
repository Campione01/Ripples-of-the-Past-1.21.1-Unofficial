package com.github.standobyte.jojo.client.rendertype;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.standobyte.jojo.client.shader.ModShaders;
import com.github.standobyte.jojo.client.shader.StandTranslucencyFramebuffer;
import com.github.standobyte.jojo.client.shader.core.RenderTargetState;
import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class ModRenderTypes extends RenderType {
	private static final RenderStateShard.ShaderStateShard RENDERTYPE_STAND_TRANSLUCENT_SHADER =
			new RenderStateShard.ShaderStateShard(() -> ModShaders.getInstance().coreStandTranslucent);
	private static final RenderTargetState STAND_OUTLINE_TARGET_STATE = new RenderTargetState();
	private static final RenderStateShard.OutputStateShard STAND_TRANSLUCENCY_TARGET =
			new RenderStateShard.OutputStateShard(JojoMod.MOD_ID + ":stand_translucency_target",
					ModRenderTypes::bindStandTranslucencyTarget,
					ModRenderTypes::restoreStandTranslucencyTarget);
	private static final RenderStateShard.OutputStateShard STAND_OUTLINE_TARGET =
			new RenderStateShard.OutputStateShard(JojoMod.MOD_ID + ":stand_outline_target",
					ModRenderTypes::bindStandOutlineTarget,
					STAND_OUTLINE_TARGET_STATE::restore);

	private static final BiFunction<ResourceLocation, StandTranslucentState, RenderType> STAND_TRANSLUCENT = Util.memoize(
			(texture, renderState) -> {
				RenderType.CompositeState state = RenderType.CompositeState.builder()
						.setShaderState(RENDERTYPE_STAND_TRANSLUCENT_SHADER)
						.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
						.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
						.setCullState(renderState.cull ? CULL : NO_CULL)
						.setLightmapState(LIGHTMAP)
						.setOverlayState(OVERLAY)
						.setOutputState(STAND_TRANSLUCENCY_TARGET)
						.createCompositeState(renderState.outline);
				String suffix = renderState.cull ? "_cull" : "";
				return create(JojoMod.MOD_ID + ":stand_translucent" + suffix, DefaultVertexFormat.NEW_ENTITY,
						VertexFormat.Mode.QUADS, 1536, true, true, state);
			});
	private static final Function<ResourceLocation, RenderType> STAND_OUTLINE = Util.memoize(texture -> {
		RenderType.CompositeState state = RenderType.CompositeState.builder()
				.setShaderState(RENDERTYPE_OUTLINE_SHADER)
				.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
				.setCullState(NO_CULL)
				.setDepthTestState(NO_DEPTH_TEST)
				.setOutputState(STAND_OUTLINE_TARGET)
				.createCompositeState(OutlineProperty.IS_OUTLINE);
		return create(JojoMod.MOD_ID + ":stand_outline", DefaultVertexFormat.POSITION_TEX_COLOR,
				VertexFormat.Mode.QUADS, 1536, false, false, state);
	});

	@Deprecated
	private ModRenderTypes(String name, VertexFormat format, Mode mode, int bufferSize,
			boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
	}

	public static RenderType standTranslucent(ResourceLocation texture, boolean outline) {
		if (IrisShaderPipelineCompat.isShaderPackInUse()) {
			return RenderType.entityTranslucent(texture, outline);
		}
		return STAND_TRANSLUCENT.apply(texture, new StandTranslucentState(outline, false));
	}

	public static RenderType standTranslucent(ResourceLocation texture) {
		return standTranslucent(texture, true);
	}

	public static RenderType standTranslucentCull(ResourceLocation texture) {
		if (IrisShaderPipelineCompat.isShaderPackInUse()) {
			return RenderType.entityTranslucentCull(texture);
		}
		return STAND_TRANSLUCENT.apply(texture, new StandTranslucentState(true, true));
	}

	public static RenderType standTranslucentDirectCull(ResourceLocation texture) {
		return RenderType.entityTranslucentCull(texture);
	}

	public static RenderType standOutline(ResourceLocation texture) {
		return STAND_OUTLINE.apply(texture);
	}

	private static void bindStandTranslucencyTarget() {
		ModShaders shaders = ModShaders.getInstance();
		StandTranslucencyFramebuffer framebuffer = shaders != null ? shaders.standTranslucencyFramebuffer : null;
		if (framebuffer != null) {
			framebuffer.bindForWrite();
		}
	}

	private static void restoreStandTranslucencyTarget() {
		ModShaders shaders = ModShaders.getInstance();
		StandTranslucencyFramebuffer framebuffer = shaders != null ? shaders.standTranslucencyFramebuffer : null;
		if (framebuffer != null) {
			framebuffer.restoreRenderTarget();
		}
	}

	private static void bindStandOutlineTarget() {
		if (net.minecraft.client.Minecraft.getInstance().levelRenderer.entityTarget() != null) {
			STAND_OUTLINE_TARGET_STATE.bind(net.minecraft.client.Minecraft.getInstance().levelRenderer.entityTarget());
		}
	}

	private record StandTranslucentState(boolean outline, boolean cull) {}
}
