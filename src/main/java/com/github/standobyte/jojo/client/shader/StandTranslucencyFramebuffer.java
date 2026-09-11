package com.github.standobyte.jojo.client.shader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.github.standobyte.jojo.api.client.render.ClientRenderCompatibility;
import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.client.shader.core.RenderTargetState;
import com.github.standobyte.jojo.client.shader.core.RotpShader;
import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.GlStateBackup;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class StandTranslucencyFramebuffer extends RotpShader {
	private final RenderTarget buffer;
	private final RenderTarget sceneDepthBuffer;
	private final RenderTargetState targetState = new RenderTargetState();
	private final RenderTargetState surfaceTargetState = new RenderTargetState();
	private RenderTarget surfaceBuffer;
	private ShaderInstance compositeShader;
	private ShaderInstance surfaceResolveShader;
	private final List<StandSurfaceDraw> pendingSurfaces = new ArrayList<>();
	private boolean preparedThisFrame;
	private boolean usedThisFrame;
	private boolean restoreIrisWorldTarget;
	private boolean acceptingSurfaceDraws;

	public StandTranslucencyFramebuffer(Minecraft minecraft) {
		buffer = createMainTargetBuffer(minecraft);
		sceneDepthBuffer = createMainTargetBuffer(minecraft);
	}

	@Override
	public void loadPostShader(ResourceManager resourceManager) {}

	@Override
	public void loadCoreShaders(RegisterShadersEvent event) {
		ModShaders.loadPrivateTargetCoreShader(event, JojoMod.resLoc("stand_translucency_composite"),
				DefaultVertexFormat.BLIT_SCREEN, shader -> compositeShader = shader);
		ModShaders.loadPrivateTargetCoreShader(event, JojoMod.resLoc("stand_surface_resolve"),
				DefaultVertexFormat.BLIT_SCREEN, shader -> surfaceResolveShader = shader);
	}

	@Override
	public void resize(int width, int height) {
		acceptingSurfaceDraws = false;
		preparedThisFrame = false;
	}

	@Override
	public void close() {
		acceptingSurfaceDraws = false;
		clearPendingSurfaces();
		buffer.destroyBuffers();
		sceneDepthBuffer.destroyBuffers();
		if (surfaceBuffer != null) {
			surfaceBuffer.destroyBuffers();
			surfaceBuffer = null;
		}
	}

	public void beginFrame() {
		clearPendingSurfaces();
		acceptingSurfaceDraws = true;
		preparedThisFrame = false;
		usedThisFrame = false;
		restoreIrisWorldTarget = false;
	}

	public void bindForWrite() {
		if (compositeShader == null) {
			return;
		}
		targetState.capture();
		restoreIrisWorldTarget = ClientRenderCompatibility.snapshot().irisShaderPackInUse()
				&& !EntityMaskPostEffect.isCapturePass();
		int width = Math.max(1, targetState.viewportWidth());
		int height = Math.max(1, targetState.viewportHeight());
		if (ensureSize(buffer, width, height)) {
			preparedThisFrame = false;
		}
		if (!preparedThisFrame) {
			buffer.clear(Minecraft.ON_OSX);
			copyDepthFrom(buffer, targetState.drawFramebuffer(), targetState.viewportX(), targetState.viewportY(), width, height);
			preparedThisFrame = true;
		}
		buffer.bindWrite(true);
		usedThisFrame = true;
	}

	public void restoreRenderTarget() {
		if (restoreIrisWorldTarget) {
			targetState.restoreAfterLogicalMainTarget(Minecraft.getInstance().getMainRenderTarget());
		}
		else {
			targetState.restore();
		}
		restoreIrisWorldTarget = false;
	}

	public boolean canResolveBodySurface() {
		return acceptingSurfaceDraws && compositeShader != null && surfaceResolveShader != null;
	}

	public void drawBodySurface(MeshData meshData, RenderType surfaceMaterial,
			double viewDepth, int ownerId, Object groupKey, int emissionOrder, boolean bodyPass) {
		drawBodySurface(meshData, surfaceMaterial, ModShaders.getInstance().coreStandTranslucent,
				viewDepth, ownerId, groupKey, emissionOrder, bodyPass);
	}

	public void drawBodySurface(MeshData meshData, RenderType surfaceMaterial, ShaderInstance replayShader,
			double viewDepth, int ownerId, Object groupKey, int emissionOrder, boolean bodyPass) {
		try (meshData) {
			RenderSystem.assertOnRenderThread();
			StandSurfaceDraw draw = StandSurfaceDraw.capture(meshData, surfaceMaterial,
					replayShader, viewDepth, ownerId, groupKey, emissionOrder, bodyPass);
			try {
				pendingSurfaces.add(draw);
				usedThisFrame = true;
			}
			catch (Throwable error) {
				draw.close();
				throw error;
			}
		}
	}

	private void drainBodySurfaces() {
		if (pendingSurfaces.isEmpty()) {
			return;
		}
		Map<Object, List<StandSurfaceDraw>> drawsByGroup = new LinkedHashMap<>();
		for (StandSurfaceDraw draw : pendingSurfaces) {
			drawsByGroup.computeIfAbsent(draw.groupKey(), key -> new ArrayList<>()).add(draw);
		}
		List<List<StandSurfaceDraw>> groups = new ArrayList<>(drawsByGroup.values());
		groups.forEach(group -> group.sort(Comparator.comparing(StandSurfaceDraw::bodyPass).reversed()
				.thenComparingInt(StandSurfaceDraw::emissionOrder)));
		groups.sort((left, right) -> StandSurfaceDraw.compareBackToFront(
				left.getFirst().viewDepth(), left.getFirst().ownerId(),
				right.getFirst().viewDepth(), right.getFirst().ownerId()));
		try {
			boolean restoreIrisTarget = ClientRenderCompatibility.snapshot().irisShaderPackInUse()
					&& !EntityMaskPostEffect.isCapturePass();
			ShaderInstance previousShader = RenderSystem.getShader();
			GlStateBackup glState = new GlStateBackup();
			RenderSystem.backupGlState(glState);
			surfaceTargetState.capture();
			try {
				int sourceFramebuffer = surfaceTargetState.drawFramebuffer();
				if (sourceFramebuffer == buffer.frameBufferId
						|| (surfaceBuffer != null && sourceFramebuffer == surfaceBuffer.frameBufferId)) {
					throw new IllegalStateException("Stand surface capture requires a world draw target");
				}
				int width = Math.max(1, surfaceTargetState.viewportWidth());
				int height = Math.max(1, surfaceTargetState.viewportHeight());
				RenderSystem.disableScissor();
				RenderSystem.colorMask(true, true, true, true);
				RenderSystem.depthMask(true);
				if (surfaceBuffer == null) {
					surfaceBuffer = createMainTargetBuffer(Minecraft.getInstance());
				}
				ensureSize(surfaceBuffer, width, height);
				if (ensureSize(buffer, width, height)) {
					preparedThisFrame = false;
				}
				if (!preparedThisFrame) {
					buffer.clear(Minecraft.ON_OSX);
					copyDepthFrom(buffer, sourceFramebuffer, surfaceTargetState.viewportX(),
							surfaceTargetState.viewportY(), width, height);
					preparedThisFrame = true;
				}
				for (List<StandSurfaceDraw> group : groups) {
					// The previous depth-only resolve disabled color writes and culling.
					RenderSystem.colorMask(true, true, true, true);
					RenderSystem.depthMask(true);
					RenderSystem.enableDepthTest();
					RenderSystem.depthFunc(GL11.GL_LEQUAL);
					RenderSystem.enableCull();
					surfaceBuffer.clear(Minecraft.ON_OSX);
					// Use the completed world depth, never another body's accumulated depth.
					copyDepthFrom(surfaceBuffer, sourceFramebuffer, surfaceTargetState.viewportX(),
							surfaceTargetState.viewportY(), width, height);
					surfaceBuffer.bindWrite(true);
					// Keep the body's depth for its overlays; a different render invocation gets a fresh scratch.
					for (StandSurfaceDraw draw : group) {
						RenderSystem.colorMask(true, true, true, true);
						RenderSystem.depthMask(true);
						RenderSystem.enableDepthTest();
						RenderSystem.depthFunc(GL11.GL_LEQUAL);
						RenderSystem.enableCull();
						draw.draw();
					}
					resolveBodySurface();
				}
				usedThisFrame = true;
			}
			finally {
				try {
					if (restoreIrisTarget) {
						surfaceTargetState.restoreAfterLogicalMainTarget(Minecraft.getInstance().getMainRenderTarget());
					}
					else {
						surfaceTargetState.restore();
					}
				}
				finally {
					RenderSystem.restoreGlState(glState);
					RenderSystem.setShader(() -> previousShader);
				}
			}
		}
		finally {
			clearPendingSurfaces();
		}
	}

	private void clearPendingSurfaces() {
		RuntimeException failure = null;
		try {
			for (StandSurfaceDraw draw : pendingSurfaces) {
				try {
					draw.close();
				}
				catch (RuntimeException error) {
					if (failure == null) {
						failure = error;
					}
					else {
						failure.addSuppressed(error);
					}
				}
			}
		}
		finally {
			pendingSurfaces.clear();
		}
		if (failure != null) {
			throw failure;
		}
	}

	private void resolveBodySurface() {
		buffer.bindWrite(true);
		RenderSystem.disableCull();
		surfaceResolveShader.setSampler("SurfaceColorSampler", surfaceBuffer.getColorTextureId());
		surfaceResolveShader.setSampler("SurfaceDepthSampler", surfaceBuffer.getDepthTextureId());
		try {
			surfaceResolveShader.apply();
			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(
					GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
					GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.disableDepthTest();
			RenderSystem.depthMask(false);
			drawSurfaceQuad();

			// Preserve every body's color contribution, then merge only the nearest depth.
			RenderSystem.disableBlend();
			RenderSystem.colorMask(false, false, false, false);
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.depthMask(true);
			drawSurfaceQuad();
		}
		finally {
			surfaceResolveShader.clear();
		}
	}

	private void drawSurfaceQuad() {
		BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(
				VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
		builder.addVertex(0.0F, 0.0F, 0.0F);
		builder.addVertex(1.0F, 0.0F, 0.0F);
		builder.addVertex(1.0F, 1.0F, 0.0F);
		builder.addVertex(0.0F, 1.0F, 0.0F);
		BufferUploader.draw(builder.buildOrThrow());
	}

	private boolean ensureSize(RenderTarget target, int width, int height) {
		if (target.viewWidth == width && target.viewHeight == height) {
			return false;
		}
		target.resize(width, height, Minecraft.ON_OSX);
		return true;
	}

	private void copyDepthFrom(RenderTarget target, int sourceFramebuffer,
			int sourceX, int sourceY, int width, int height) {
		int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		try {
			GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
			GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
			GL30.glBlitFramebuffer(
					sourceX, sourceY, sourceX + width, sourceY + height,
					0, 0, width, height,
					GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
		}
		finally {
			GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
			GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
		}
	}

	@Override
	public void frameRenderCallback(RenderLevelStageEvent.Stage stage) {
		if (usedThisFrame && stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
			compositeIfPending();
		}
	}

	public void compositeIfPending() {
		if (usedThisFrame) {
			compositeAndReset();
		}
	}

	public void finishFrame() {
		try {
			compositeIfPending();
		}
		finally {
			acceptingSurfaceDraws = false;
		}
	}

	private void compositeAndReset() {
		try {
			drainBodySurfaces();
			compositeToCurrentTarget();
		}
		finally {
			preparedThisFrame = false;
			usedThisFrame = false;
		}
	}

	private void compositeToCurrentTarget() {
		targetState.capture();
		GlStateBackup glState = new GlStateBackup();
		RenderSystem.backupGlState(glState);
		try {
			int width = Math.max(1, targetState.viewportWidth());
			int height = Math.max(1, targetState.viewportHeight());
			ensureSize(sceneDepthBuffer, width, height);
			copyDepthFrom(sceneDepthBuffer, targetState.drawFramebuffer(),
					targetState.viewportX(), targetState.viewportY(), width, height);
			targetState.restore();
			targetState.capture();

			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(
					com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
					com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
					com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
					com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			drawDepthAwareComposite(width, height);
		}
		finally {
			targetState.restore();
			RenderSystem.restoreGlState(glState);
		}
	}

	private void drawDepthAwareComposite(int width, int height) {
		if (compositeShader == null) {
			return;
		}

		RenderSystem.colorMask(true, true, true, false);
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		try {
			compositeShader.setSampler("StandColorSampler", buffer.getColorTextureId());
			compositeShader.setSampler("StandDepthSampler", buffer.getDepthTextureId());
			compositeShader.setSampler("SceneDepthSampler", sceneDepthBuffer.getDepthTextureId());
			compositeShader.apply();

			BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(
					VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
			builder.addVertex(0.0F, 0.0F, 0.0F);
			builder.addVertex(1.0F, 0.0F, 0.0F);
			builder.addVertex(1.0F, 1.0F, 0.0F);
			builder.addVertex(0.0F, 1.0F, 0.0F);
			BufferUploader.draw(builder.buildOrThrow());
		}
		finally {
			compositeShader.clear();
			RenderSystem.depthMask(true);
			RenderSystem.enableDepthTest();
			RenderSystem.colorMask(true, true, true, true);
		}
	}
}
