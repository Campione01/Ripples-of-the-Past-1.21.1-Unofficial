package rotp.addons.ger.client;

import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.shader.core.RenderTargetState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.ger.client.GerRtzVfxSettings.Mode;
import rotp.addons.ger.network.GerRtzPayloads.GhostTrail;
import rotp.addons.ger.network.GerRtzPayloads.StartPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.GlStateBackup;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class GerRtzGhostTrailRenderer {
    private static final int PRIVATE_BUFFER_SIZE = 786_432;
    private static final int MAX_RENDERED_GHOSTS_PER_ENTITY = 12;

    private GerRtzGhostTrailRenderer() {}

    public static void render(
            RenderLevelStageEvent event) {
        if (event.getStage()
                        != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || GerRtzClientState.activeMode()
                        != Mode.TIME_ERASE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        StartPayload active = GerRtzClientState.active();
        if (minecraft.level == null || active == null) {
            return;
        }
        float lifecycle =
                GerRtzClientState.lifecycleAlpha(
                        event.getPartialTick()
                                .getGameTimeDeltaPartialTick(
                                        true));
        if (lifecycle <= 0.0F) {
            return;
        }

        PoseStack stack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick()
                .getGameTimeDeltaPartialTick(true);
        int renderedTotal = 0;
        boolean ghostSubmitted = false;
        RenderTargetState targetState = new RenderTargetState();
        GlStateBackup glState = new GlStateBackup();
        targetState.capture();
        RenderSystem.backupGlState(glState);
        GerRtzDiagnostics.RenderTargetSnapshot beforeState =
                captureRenderTargetState();
        PrivateBuffers.ALPHA_BLEND.setRenderTarget(
                beforeState.drawFramebuffer(),
                beforeState.readFramebuffer(),
                beforeState.viewportX(),
                beforeState.viewportY(),
                beforeState.viewportWidth(),
                beforeState.viewportHeight());
        try {
            for (GhostTrail trail : active.trails()) {
                Entity entity =
                        minecraft.level.getEntity(trail.entityId());
                if (entity == null
                        || entity == minecraft.getCameraEntity()
                                && minecraft.options
                                        .getCameraType()
                                        .isFirstPerson()) {
                    continue;
                }
                int stride = Math.max(
                        1,
                        (trail.points().size()
                                + MAX_RENDERED_GHOSTS_PER_ENTITY
                                - 1)
                                / MAX_RENDERED_GHOSTS_PER_ENTITY);
                int rendered = 0;
                for (int pointIndex = 0;
                        pointIndex < trail.points().size()
                                && rendered
                                        < MAX_RENDERED_GHOSTS_PER_ENTITY;
                        pointIndex += stride) {
                    Vec3 point = trail.points().get(pointIndex);
                    float trailAlpha = lifecycle
                            * (0.42F
                                    - rendered
                                            * 0.28F
                                            / MAX_RENDERED_GHOSTS_PER_ENTITY);
                    MultiBufferSource buffer =
                            AlphaMultiBufferSource.wrap(
                                    PrivateBuffers.ALPHA_BLEND,
                                    trailAlpha);
                    stack.pushPose();
                    ghostSubmitted = true;
                    try {
                        minecraft.getEntityRenderDispatcher().render(
                                entity,
                                point.x - camera.x,
                                point.y - camera.y,
                                point.z - camera.z,
                                entity.getYRot(),
                                partialTick,
                                stack,
                                buffer,
                                LightTexture.FULL_BRIGHT);
                    }
                    finally {
                        stack.popPose();
                    }
                    rendered++;
                    renderedTotal++;
                }
            }
        }
        finally {
            try {
                if (ghostSubmitted) {
                    PrivateBuffers.SOURCE.endBatch();
                }
            }
            finally {
                PrivateBuffers.ALPHA_BLEND.clearRenderTarget();
                targetState.restore();
                RenderSystem.restoreGlState(glState);
                GerRtzDiagnostics.recordGhostRenderState(
                        beforeState,
                        captureRenderTargetState());
            }
        }
        GerRtzDiagnostics.recordGhostDraw(renderedTotal);
    }

    private static GerRtzDiagnostics.RenderTargetSnapshot
            captureRenderTargetState() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return new GerRtzDiagnostics.RenderTargetSnapshot(
                GL11.glGetInteger(
                        GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(
                        GL30.GL_READ_FRAMEBUFFER_BINDING),
                viewport[0],
                viewport[1],
                viewport[2],
                viewport[3]);
    }

    private static final class PrivateBuffers {
        private static final ByteBufferBuilder STORAGE =
                new ByteBufferBuilder(PRIVATE_BUFFER_SIZE);
        private static final MultiBufferSource.BufferSource SOURCE =
                MultiBufferSource.immediate(STORAGE);
        private static final GerRtzAlphaBlendBufferSource
                ALPHA_BLEND =
                new GerRtzAlphaBlendBufferSource(SOURCE);
    }
}
