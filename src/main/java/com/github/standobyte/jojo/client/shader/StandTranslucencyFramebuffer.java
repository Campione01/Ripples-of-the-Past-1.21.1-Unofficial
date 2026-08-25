package com.github.standobyte.jojo.client.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.github.standobyte.jojo.client.shader.core.RenderTargetState;
import com.github.standobyte.jojo.client.shader.core.RotpShader;
import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class StandTranslucencyFramebuffer extends RotpShader {
	private final RenderTarget buffer;
	private final RenderTarget sceneDepthBuffer;
	private final RenderTargetState targetState = new RenderTargetState();
	private ShaderInstance compositeShader;
	private boolean preparedThisFrame;
	private boolean usedThisFrame;

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
	}

	@Override
	public void resize(int width, int height) {
		preparedThisFrame = false;
	}

	@Override
	public void close() {
		buffer.destroyBuffers();
		sceneDepthBuffer.destroyBuffers();
	}

	public void beginFrame() {
		preparedThisFrame = false;
		usedThisFrame = false;
	}

	public void bindForWrite() {
		if (compositeShader == null) {
			return;
		}
		targetState.capture();
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
		targetState.restore();
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
		target.bindWrite(false);
		int targetFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
		GL30.glBlitFramebuffer(
				sourceX, sourceY, sourceX + width, sourceY + height,
				0, 0, width, height,
				GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
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

	private void compositeAndReset() {
		try {
			compositeToCurrentTarget();
		}
		finally {
			preparedThisFrame = false;
			usedThisFrame = false;
		}
	}

	private void compositeToCurrentTarget() {
		targetState.capture();
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
			RenderSystem.disableBlend();
			RenderSystem.defaultBlendFunc();
			targetState.restore();
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
