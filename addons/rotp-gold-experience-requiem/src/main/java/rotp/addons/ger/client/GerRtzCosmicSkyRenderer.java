package rotp.addons.ger.client;

import java.io.IOException;

import org.joml.Vector3f;
import org.slf4j.Logger;

import rotp.core.api.client.render.ClientSkyRenderContext;
import rotp.core.api.client.render.ClientSkyRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class GerRtzCosmicSkyRenderer
        implements ClientSkyRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER =
            GoldExperienceRequiemAddon.id("rtz_cosmic_sky");
    private static final float SKY_RADIUS = 96.0F;

    private static ShaderInstance cosmicSkyShader;

    public static void onRegisterShaders(
            RegisterShadersEvent event) {
        cosmicSkyShader = null;
        try {
            ShaderInstance shader = new ShaderInstance(
                    event.getResourceProvider(),
                    SHADER,
                    DefaultVertexFormat.POSITION);
            event.registerShader(
                    shader,
                    loaded -> cosmicSkyShader = loaded);
        }
        catch (IOException | RuntimeException exception) {
            LOGGER.error(
                    "Unable to load the donor GER RTZ cosmic sky shader",
                    exception);
        }
    }

    @Override
    public void render(ClientSkyRenderContext context) {
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(context.frustumMatrix());
        renderSky(
                poseStack,
                context.camera(),
                context.partialTick(),
                context.skyFogSetup());
    }

    private static void renderSky(
            PoseStack poseStack,
            Camera camera,
            float partialTick,
            Runnable skyFogSetup) {
        ShaderInstance shader = cosmicSkyShader;
        float alpha = GerRtzClientState.lifecycleAlpha(partialTick);
        if (shader == null || alpha <= 0.001F) {
            return;
        }

        Vector3f look = new Vector3f(
                camera.getLookVector()).normalize();
        Vector3f up = new Vector3f(
                camera.getUpVector()).normalize();
        Vector3f right = new Vector3f(look)
                .cross(up)
                .normalize();
        shader.safeGetUniform("Time").set(
                GerRtzClientState.elapsedTicks()
                        + partialTick);
        shader.safeGetUniform("Alpha").set(alpha);
        shader.safeGetUniform("CameraLook").set(
                look.x(), look.y(), look.z());
        shader.safeGetUniform("CameraUp").set(
                up.x(), up.y(), up.z());
        shader.safeGetUniform("CameraRight").set(
                right.x(), right.y(), right.z());

        skyFogSetup.run();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);
        try {
            renderCube(poseStack, SKY_RADIUS);
        }
        finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    @Override
    public boolean suppressVanillaClouds() {
        return true;
    }

    private static void renderCube(
            PoseStack poseStack, float radius) {
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION);
        var pose = poseStack.last().pose();
        vertex(builder, pose, -radius, -radius, -radius);
        vertex(builder, pose, -radius, radius, -radius);
        vertex(builder, pose, radius, radius, -radius);
        vertex(builder, pose, radius, -radius, -radius);
        vertex(builder, pose, radius, -radius, radius);
        vertex(builder, pose, radius, radius, radius);
        vertex(builder, pose, -radius, radius, radius);
        vertex(builder, pose, -radius, -radius, radius);
        vertex(builder, pose, -radius, -radius, radius);
        vertex(builder, pose, -radius, radius, radius);
        vertex(builder, pose, -radius, radius, -radius);
        vertex(builder, pose, -radius, -radius, -radius);
        vertex(builder, pose, radius, -radius, -radius);
        vertex(builder, pose, radius, radius, -radius);
        vertex(builder, pose, radius, radius, radius);
        vertex(builder, pose, radius, -radius, radius);
        vertex(builder, pose, -radius, radius, -radius);
        vertex(builder, pose, -radius, radius, radius);
        vertex(builder, pose, radius, radius, radius);
        vertex(builder, pose, radius, radius, -radius);
        vertex(builder, pose, -radius, -radius, radius);
        vertex(builder, pose, -radius, -radius, -radius);
        vertex(builder, pose, radius, -radius, -radius);
        vertex(builder, pose, radius, -radius, radius);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void vertex(
            BufferBuilder builder,
            org.joml.Matrix4f pose,
            float x,
            float y,
            float z) {
        builder.addVertex(pose, x, y, z);
    }
}
