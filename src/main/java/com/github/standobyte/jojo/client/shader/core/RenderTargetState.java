package com.github.standobyte.jojo.client.shader.core;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

public final class RenderTargetState {
	private final int[] viewport = new int[4];
	private int drawFramebuffer;
	private int readFramebuffer;
	private boolean captured;

	public void capture() {
		RenderSystem.assertOnRenderThread();
		if (captured) {
			throw new IllegalStateException("Render target state is already captured");
		}
		drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
		captured = true;
	}

	public void bind(RenderTarget target) {
		capture();
		target.bindWrite(true);
	}

	public void restore() {
		if (!captured) {
			return;
		}
		restorePhysicalTarget();
		captured = false;
	}

	public void restoreAfterLogicalMainTarget(RenderTarget mainTarget) {
		if (!captured) {
			return;
		}
		mainTarget.bindWrite(false);
		restorePhysicalTarget();
		captured = false;
	}

	private void restorePhysicalTarget() {
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
		GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
	}

	public int drawFramebuffer() {
		return drawFramebuffer;
	}

	public int viewportX() {
		return viewport[0];
	}

	public int viewportY() {
		return viewport[1];
	}

	public int viewportWidth() {
		return viewport[2];
	}

	public int viewportHeight() {
		return viewport[3];
	}
}
