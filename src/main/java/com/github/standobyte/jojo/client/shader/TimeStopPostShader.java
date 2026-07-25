package com.github.standobyte.jojo.client.shader;

import java.util.SequencedMap;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.client.shader.core.RotpShader;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public class TimeStopPostShader extends RotpShader {
    private final TimeStopShaderManager manager;
    private PostChain postChain;
    private ResourceLocation loadedShader;
    private ResourceLocation failedShaderLogged;
    private boolean activeThisFrame;

    public TimeStopPostShader(Minecraft mc, SequencedMap<RenderType, ByteBufferBuilder> fixedRenderBuffers, TimeStopShaderManager manager) {
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
            manager.update(event);
            if (manager.justReset()) {
                closePostChain();
                loadedShader = null;
            }
            if (!manager.active()) {
                activeThisFrame = false;
                return;
            }
            ResourceLocation selected = manager.selectedShader();
            if (selected != null && (!selected.equals(loadedShader) || postChain == null)) {
                closePostChain();
                postChain = ModShaders.loadPostShaderChain(selected, Minecraft.getInstance().getMainRenderTarget());
                loadedShader = selected;
                if (postChain == null && !selected.equals(failedShaderLogged)) {
                    failedShaderLogged = selected;
                    JojoMod.getLogger().warn("Time stop shader {} was selected but its post chain did not load.", selected);
                }
            }
            activeThisFrame = postChain != null;
            if (activeThisFrame) {
                RotpShader.modifyShader(postChain, pass -> {
                    if (pass.getEffect().safeGetUniform("TSTicks") != null) {
                        pass.getEffect().safeGetUniform("TSTicks").set(manager.ticks());
                    }
                    if (pass.getEffect().safeGetUniform("TSLength") != null) {
                        pass.getEffect().safeGetUniform("TSLength").set(manager.length());
                    }
                    if (pass.getEffect().safeGetUniform("TSEffectLength") != null) {
                        pass.getEffect().safeGetUniform("TSEffectLength").set(manager.effectLength());
                    }
                    if (pass.getEffect().safeGetUniform("CenterScreenCoord") != null) {
                        pass.getEffect().safeGetUniform("CenterScreenCoord").set(manager.center().x, manager.center().y);
                    }
                });
            }
        }
    }

    @Override
    public void frameRenderCallback(RenderLevelStageEvent.Stage stage) {
        if (activeThisFrame && stage == CustomLevelRenderStages.BEFORE_SPECTATOR_SHADER) {
            Minecraft mc = Minecraft.getInstance();
            ModShaders.prepareMainTargetPostChain(postChain);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            postChain.process(mc.getTimer().getGameTimeDeltaTicks());
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.defaultBlendFunc();
        }
    }
}
