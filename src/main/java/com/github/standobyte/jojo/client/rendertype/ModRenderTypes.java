package com.github.standobyte.jojo.client.rendertype;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.standobyte.jojo.api.client.render.ClientRenderCompatibility;
import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderState;
import com.github.standobyte.jojo.client.shader.ModShaders;
import com.github.standobyte.jojo.client.shader.StandTranslucencyFramebuffer;
import com.github.standobyte.jojo.client.shader.core.RenderTargetState;
import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class ModRenderTypes extends RenderType {
	private static final RenderStateShard.ShaderStateShard RENDERTYPE_STAND_TRANSLUCENT_SHADER =
			new RenderStateShard.ShaderStateShard(() -> ModShaders.getInstance().coreStandTranslucent);
	private static final RenderStateShard.TransparencyStateShard STAND_SURFACE_DIAGNOSTIC_TRANSPARENCY =
			new RenderStateShard.TransparencyStateShard(JojoMod.MOD_ID + ":stand_surface_diagnostic",
					() -> {
						// Keep only the nearest surface's premultiplied RGBA in the body scratch target.
						RenderSystem.enableBlend();
						RenderSystem.blendFuncSeparate(
								GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ZERO,
								GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
					}, () -> {
						RenderSystem.disableBlend();
						RenderSystem.defaultBlendFunc();
					});
	private static final RenderTargetState STAND_OUTLINE_TARGET_STATE = new RenderTargetState();
	private static boolean restoreIrisWorldTargetAfterOutline;
	private static final RenderStateShard.OutputStateShard STAND_TRANSLUCENCY_TARGET =
			new RenderStateShard.OutputStateShard(JojoMod.MOD_ID + ":stand_translucency_target",
					ModRenderTypes::bindStandTranslucencyTarget,
					ModRenderTypes::restoreStandTranslucencyTarget);
	private static final RenderStateShard.OutputStateShard STAND_OUTLINE_TARGET =
			new RenderStateShard.OutputStateShard(JojoMod.MOD_ID + ":stand_outline_target",
					ModRenderTypes::bindStandOutlineTarget,
					ModRenderTypes::restoreStandOutlineTarget);

	private static final BiFunction<ResourceLocation, StandTranslucentState, RenderType> STAND_TRANSLUCENT = Util.memoize(
			(texture, renderState) -> {
				RenderType.CompositeState state = RenderType.CompositeState.builder()
						.setShaderState(RENDERTYPE_STAND_TRANSLUCENT_SHADER)
						.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
						.setTransparencyState(renderState.nearestSurface
								? STAND_SURFACE_DIAGNOSTIC_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
						.setCullState(renderState.cull ? CULL : NO_CULL)
						.setLightmapState(LIGHTMAP)
						.setOverlayState(OVERLAY)
						.setOutputState(renderState.isolatedOutput ? MAIN_TARGET : STAND_TRANSLUCENCY_TARGET)
						.createCompositeState(renderState.outline);
				String suffix = (renderState.nearestSurface ? "_surface_diagnostic" : "")
						+ (renderState.cull ? "_cull" : "");
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
		return queuedStandTranslucent(texture, outline, false);
	}

	public static RenderType standTranslucent(ResourceLocation texture) {
		return standTranslucent(texture, true);
	}

	public static RenderType standTranslucentCull(ResourceLocation texture) {
		return queuedStandTranslucent(texture, true, true);
	}

	private static RenderType queuedStandTranslucent(ResourceLocation texture, boolean outline, boolean cull) {
		int ownerId = RenderStateCrutches.currentStandEntityRenderState instanceof StandEntityRenderState state
				? state.entityId : 0;
		// Ordinary effects/trails keep their blending, but must share the body queue's depth ordering.
		return new StandSurfaceRenderType(
				STAND_TRANSLUCENT.apply(texture, new StandTranslucentState(outline, cull, false, true)),
				STAND_TRANSLUCENT.apply(texture, new StandTranslucentState(outline, cull)),
				RenderType.entityTranslucent(texture), 0.0, ownerId, true,
				surfaceGroupKey(), nextSurfaceOrder(), false);
	}

	private static Object surfaceGroupKey() {
		if (RenderStateCrutches.currentStandEntityRenderState instanceof StandEntityRenderState state
				&& state.surfaceDrawGroup != null) {
			return state.surfaceDrawGroup;
		}
		return new Object();
	}

	private static int nextSurfaceOrder() {
		return RenderStateCrutches.currentStandEntityRenderState instanceof StandEntityRenderState state
				&& state.surfaceDrawGroup != null ? state.surfaceDrawSequence++ : 0;
	}

	public static RenderType asStandBody(RenderType renderType) {
		if (renderType instanceof StandSurfaceRenderType surface) {
			return new StandSurfaceRenderType(surface.surfaceMaterial, surface.fallbackMaterial, surface.shadowMaterial,
					surface.viewDepth, surface.ownerId, surface.consolidate, surface.groupKey, surface.emissionOrder, true);
		}
		return renderType;
	}

	public static RenderType standSurfaceDiagnostic(ResourceLocation texture, boolean cull) {
		return standSurfaceDiagnostic(texture, cull, 0.0, 0);
	}

	public static RenderType standSurfaceDiagnostic(ResourceLocation texture, boolean cull,
			double viewDepth, int ownerId) {
		// Iris batches by RenderType identity without consulting canConsolidateConsecutiveGeometry.
		return new StandSurfaceRenderType(
				STAND_TRANSLUCENT.apply(texture, new StandTranslucentState(true, cull, true)),
				STAND_TRANSLUCENT.apply(texture, new StandTranslucentState(true, cull)),
				RenderType.entityTranslucent(texture), viewDepth, ownerId, false,
				surfaceGroupKey(), nextSurfaceOrder(), true);
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
			restoreIrisWorldTargetAfterOutline =
					ClientRenderCompatibility.snapshot().irisShaderPackInUse()
					&& !EntityMaskPostEffect.isCapturePass();
			STAND_OUTLINE_TARGET_STATE.bind(net.minecraft.client.Minecraft.getInstance().levelRenderer.entityTarget());
		}
	}

	private static void restoreStandOutlineTarget() {
		if (restoreIrisWorldTargetAfterOutline) {
			STAND_OUTLINE_TARGET_STATE.restoreAfterLogicalMainTarget(
					net.minecraft.client.Minecraft.getInstance().getMainRenderTarget());
		}
		else {
			STAND_OUTLINE_TARGET_STATE.restore();
		}
		restoreIrisWorldTargetAfterOutline = false;
	}

	private static final class StandSurfaceRenderType extends RenderType {
		private final RenderType surfaceMaterial;
		private final RenderType fallbackMaterial;
		private final RenderType shadowMaterial;
		private final double viewDepth;
		private final int ownerId;
		private final boolean consolidate;
		private final Object groupKey;
		private final int emissionOrder;
		private final boolean bodyPass;

		private StandSurfaceRenderType(RenderType surfaceMaterial, RenderType fallbackMaterial, RenderType shadowMaterial,
				double viewDepth, int ownerId, boolean consolidate, Object groupKey, int emissionOrder, boolean bodyPass) {
			super(surfaceMaterial.name + "_isolated", surfaceMaterial.format(), surfaceMaterial.mode(),
					surfaceMaterial.bufferSize(), surfaceMaterial.affectsCrumbling(), surfaceMaterial.sortOnUpload(),
					() -> {}, () -> {});
			this.surfaceMaterial = surfaceMaterial;
			this.fallbackMaterial = fallbackMaterial;
			this.shadowMaterial = shadowMaterial;
			this.viewDepth = viewDepth;
			this.ownerId = ownerId;
			this.consolidate = consolidate;
			this.groupKey = groupKey;
			this.emissionOrder = emissionOrder;
			this.bodyPass = bodyPass;
		}

		@Override
		public boolean canConsolidateConsecutiveGeometry() {
			return consolidate;
		}

		@Override
		public Optional<RenderType> outline() {
			return fallbackMaterial.outline();
		}

		@Override
		public boolean isOutline() {
			return fallbackMaterial.isOutline();
		}

		@Override
		public void draw(MeshData meshData) {
			ModShaders shaders = ModShaders.getInstance();
			StandTranslucencyFramebuffer framebuffer = shaders != null ? shaders.standTranslucencyFramebuffer : null;
			if (framebuffer != null && framebuffer.canResolveBodySurface()
					&& !EntityMaskPostEffect.isCapturePass() && !ClientRenderCompatibility.isIrisShadowPass()) {
				framebuffer.drawBodySurface(meshData, surfaceMaterial, viewDepth, ownerId, groupKey, emissionOrder, bodyPass);
			}
			else {
				RenderType material = ClientRenderCompatibility.isIrisShadowPass() ? shadowMaterial : fallbackMaterial;
				try (meshData) {
					try {
						material.setupRenderState();
						BufferUploader.drawWithShader(meshData);
					}
					finally {
						material.clearRenderState();
					}
				}
			}
		}
	}

	private record StandTranslucentState(boolean outline, boolean cull, boolean nearestSurface, boolean isolatedOutput) {
		private StandTranslucentState(boolean outline, boolean cull) {
			this(outline, cull, false, false);
		}

		private StandTranslucentState(boolean outline, boolean cull, boolean nearestSurface) {
			this(outline, cull, nearestSurface, nearestSurface);
		}
	}
}
