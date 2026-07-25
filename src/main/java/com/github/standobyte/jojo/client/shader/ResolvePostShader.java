package com.github.standobyte.jojo.client.shader;

import java.util.SequencedMap;

import com.github.standobyte.jojo.client.shader.core.RotpShader;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public class ResolvePostShader extends RotpShader {
	private final ResolveShaderManager manager;
	private PostChain postChain;
	private ResourceLocation loadedShader;
	private boolean activeThisFrame;

	public ResolvePostShader(Minecraft mc, SequencedMap<RenderType, ByteBufferBuilder> fixedRenderBuffers, ResolveShaderManager manager) {
		this.manager = manager;
	}

	@Override
	public void loadPostShader(ResourceManager resourceManager) {
		closePostChain();
		loadedShader = null;
	}

	@Override
	public void resize(int width, int height) {
		if (postChain != null) {
			postChain.resize(width, height);
		}
	}

	@Override
	public void close() {
		closePostChain();
	}

	private void closePostChain() {
		if (postChain != null) {
			postChain.close();
			postChain = null;
		}
	}

	@Override
	public void frameRenderCallback(RenderLevelStageEvent event) {
		RenderLevelStageEvent.Stage stage = event.getStage();
		if (isBeforeEntities(stage)) {
			if (manager.justReset()) {
				closePostChain();
				loadedShader = null;
			}
			if (ModShaders.getInstance().timeStopShaderManager.active() || !manager.active()) {
				activeThisFrame = false;
				return;
			}
			ResourceLocation selected = manager.selectedShader();
			if (selected != null && (!selected.equals(loadedShader) || postChain == null)) {
				closePostChain();
				postChain = ModShaders.loadPostShaderChain(selected, Minecraft.getInstance().getMainRenderTarget());
				loadedShader = selected;
				if (postChain == null) {
					manager.markShaderLoadFailed(selected);
				}
			}
			activeThisFrame = postChain != null;
		}
		else if (activeThisFrame && stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
			Minecraft mc = Minecraft.getInstance();
			RenderSystem.disableBlend();
			RenderSystem.disableDepthTest();
			RenderSystem.resetTextureMatrix();
			postChain.process(mc.getTimer().getGameTimeDeltaTicks());
			mc.getMainRenderTarget().bindWrite(true);
			RenderSystem.defaultBlendFunc();
		}
	}
}
