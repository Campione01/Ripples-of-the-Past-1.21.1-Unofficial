package rotp.addons.kingcrimson.client;

import java.io.IOException;
import java.util.Random;

import org.slf4j.Logger;

import rotp.core.api.client.render.ClientRenderCompatibility;
import rotp.core.api.client.render.ClientSkyRenderContext;
import rotp.core.api.client.render.ClientSkyRenderer;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class KingCrimsonTimeEraseSkyRenderer
        implements ClientSkyRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER =
            KingCrimsonAddon.id("time_erase_sky");
    private static final ResourceLocation FIRST_LAYER_TEXTURE =
            KingCrimsonAddon.id("textures/environment/erase_sky.png");
    private static final ResourceLocation PORTAL_TEXTURE =
            KingCrimsonAddon.id(
                    "textures/entity/erase_sky_second.png");
    private static final float DISTANCE = 100.0F;
    private static final float SCREEN_EXTENT = DISTANCE * 4.0F;
    private static final float IRIS_RED_GAIN = 16.0F;
    private static final float IRIS_GREEN_GAIN = 4.0F;
    private static final float IRIS_BLUE_GAIN = 6.0F;
    private static final float[][] PASS_COLORS = createPassColors();

    private static ShaderInstance timeEraseSkyShader;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        timeEraseSkyShader = null;
        try {
            ShaderInstance shader = new ShaderInstance(
                    event.getResourceProvider(),
                    SHADER,
                    DefaultVertexFormat.POSITION_COLOR);
            event.registerShader(
                    shader,
                    loaded -> timeEraseSkyShader = loaded);
        }
        catch (IOException | RuntimeException exception) {
            LOGGER.error(
                    "Unable to load the King Crimson Time Erase sky "
                            + "shader; using a colored-sky fallback.",
                    exception);
        }
    }

    @Override
    public void render(ClientSkyRenderContext context) {
        context.skyFogSetup().run();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        PoseStack poseStack = new PoseStack();
        boolean shaderPackInUse =
                ClientRenderCompatibility.snapshot()
                        .irisShaderPackInUse();
        RenderSystem.depthMask(shaderPackInUse);
        float gameTime = ((context.level().getGameTime()
                + context.partialTick()) % 24000.0F) / 24000.0F;
        try {
            if (shaderPackInUse) {
                RenderSystem.defaultBlendFunc();
                renderShaderPackBackdrop(poseStack, gameTime);
            } else {
                poseStack.mulPose(context.frustumMatrix());
                for (int layer = 1;
                        layer <= KingCrimsonPhase5Spec
                                .TIME_ERASE_SKY_PORTAL_LAYERS;
                        layer++) {
                    if (layer == 1) {
                        RenderSystem.defaultBlendFunc();
                    } else {
                        RenderSystem.blendFunc(
                                SourceFactor.ONE,
                                DestFactor.ONE);
                    }
                    renderLayer(poseStack, layer);
                }
            }
        }
        finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F);
        }
    }

    @Override
    public boolean suppressVanillaClouds() {
        return true;
    }

    private static void renderLayer(
            PoseStack poseStack,
            int layer) {
        ShaderInstance shader = timeEraseSkyShader;
        if (shader != null) {
            shader.safeGetUniform("PortalLayer").set(layer);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(
                    0,
                    layer == 1
                            ? FIRST_LAYER_TEXTURE
                            : PORTAL_TEXTURE);
        } else {
            RenderSystem.setShader(
                    GameRenderer::getPositionColorShader);
        }

        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        for (int face = 0; face < 6; face++) {
            poseStack.pushPose();
            rotateFace(poseStack, face);
            if (layer == 1) {
                renderSide(builder, poseStack, PASS_COLORS[0]);
                renderSide(builder, poseStack, PASS_COLORS[1]);
            } else {
                renderSide(builder, poseStack, PASS_COLORS[layer]);
            }
            poseStack.popPose();
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void renderShaderPackBackdrop(
            PoseStack poseStack,
            float gameTime) {
        RenderSystem.setShader(
                GameRenderer::getRendertypeEntityTranslucentEmissiveShader);
        RenderSystem.setShaderTexture(0, FIRST_LAYER_TEXTURE);

        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.NEW_ENTITY);
        var matrix = poseStack.last().pose();
        addBackdropVertex(builder, matrix, -SCREEN_EXTENT, -SCREEN_EXTENT);
        addBackdropVertex(builder, matrix, -SCREEN_EXTENT, SCREEN_EXTENT);
        addBackdropVertex(builder, matrix, SCREEN_EXTENT, SCREEN_EXTENT);
        addBackdropVertex(builder, matrix, SCREEN_EXTENT, -SCREEN_EXTENT);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(SourceFactor.ONE, DestFactor.ONE);
        renderShaderPackTexturePass(
                poseStack,
                FIRST_LAYER_TEXTURE,
                1,
                0,
                gameTime);
        renderShaderPackTexturePass(
                poseStack,
                FIRST_LAYER_TEXTURE,
                1,
                1,
                gameTime);
        for (int layer = 2;
                layer <= KingCrimsonPhase5Spec
                        .TIME_ERASE_SKY_PORTAL_LAYERS;
                layer++) {
            renderShaderPackTexturePass(
                    poseStack,
                    PORTAL_TEXTURE,
                    layer,
                    layer,
                    gameTime);
        }
    }

    private static void addBackdropVertex(
            BufferBuilder builder,
            org.joml.Matrix4f matrix,
            float x,
            float y) {
        builder.addVertex(matrix, x, y, -DISTANCE)
                .setUv(0.5F, 0.5F)
                .setColor(0.0F, 0.0F, 0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static void renderShaderPackTexturePass(
            PoseStack poseStack,
            ResourceLocation texture,
            int layer,
            int colorPass,
            float gameTime) {
        RenderSystem.setShader(
                GameRenderer::getRendertypeEntityTranslucentEmissiveShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.NEW_ENTITY);
        float angle = (float) Math.toRadians(
                (layer * layer * 4321.0F + layer * 9.0F)
                        * 2.0F);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float scale = (4.5F - layer / 4.0F) * 2.0F;
        float offsetU = 17.0F / layer;
        float offsetV = (2.0F + layer / 1.5F)
                * (gameTime * 1.5F);
        float red = Math.min(
                1.0F, PASS_COLORS[colorPass][0] * IRIS_RED_GAIN);
        float green = Math.min(
                1.0F, PASS_COLORS[colorPass][1] * IRIS_GREEN_GAIN);
        float blue = Math.min(
                1.0F, PASS_COLORS[colorPass][2] * IRIS_BLUE_GAIN);
        var matrix = poseStack.last().pose();
        addShaderPackTextureVertex(
                builder, matrix,
                -SCREEN_EXTENT, -SCREEN_EXTENT,
                0.0F, 0.0F,
                cos, sin, scale, offsetU, offsetV,
                red, green, blue);
        addShaderPackTextureVertex(
                builder, matrix,
                -SCREEN_EXTENT, SCREEN_EXTENT,
                0.0F, 1.0F,
                cos, sin, scale, offsetU, offsetV,
                red, green, blue);
        addShaderPackTextureVertex(
                builder, matrix,
                SCREEN_EXTENT, SCREEN_EXTENT,
                1.0F, 1.0F,
                cos, sin, scale, offsetU, offsetV,
                red, green, blue);
        addShaderPackTextureVertex(
                builder, matrix,
                SCREEN_EXTENT, -SCREEN_EXTENT,
                1.0F, 0.0F,
                cos, sin, scale, offsetU, offsetV,
                red, green, blue);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void addShaderPackTextureVertex(
            BufferBuilder builder,
            org.joml.Matrix4f matrix,
            float x,
            float y,
            float u,
            float v,
            float cos,
            float sin,
            float scale,
            float offsetU,
            float offsetV,
            float red,
            float green,
            float blue) {
        float centeredU = u - 0.5F;
        float centeredV = v - 0.5F;
        float transformedU = (centeredU * cos - centeredV * sin)
                * scale + 0.5F + offsetU;
        float transformedV = (centeredU * sin + centeredV * cos)
                * scale + 0.5F + offsetV;
        builder.addVertex(matrix, x, y, -DISTANCE)
                .setUv(transformedU, transformedV)
                .setColor(red, green, blue, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static void rotateFace(PoseStack poseStack, int face) {
        switch (face) {
            case 1 -> poseStack.mulPose(
                    Axis.XP.rotationDegrees(90.0F));
            case 2 -> poseStack.mulPose(
                    Axis.XP.rotationDegrees(-90.0F));
            case 3 -> poseStack.mulPose(
                    Axis.XP.rotationDegrees(180.0F));
            case 4 -> poseStack.mulPose(
                    Axis.ZP.rotationDegrees(90.0F));
            case 5 -> poseStack.mulPose(
                    Axis.ZP.rotationDegrees(-90.0F));
            default -> {
            }
        }
    }

    private static void renderSide(
            BufferBuilder builder,
            PoseStack poseStack,
            float[] color) {
        var matrix = poseStack.last().pose();
        builder.addVertex(
                        matrix,
                        -DISTANCE,
                        -DISTANCE,
                        -DISTANCE)
                .setColor(color[0], color[1], color[2], 1.0F);
        builder.addVertex(
                        matrix,
                        -DISTANCE,
                        -DISTANCE,
                        DISTANCE)
                .setColor(color[0], color[1], color[2], 1.0F);
        builder.addVertex(
                        matrix,
                        DISTANCE,
                        -DISTANCE,
                        DISTANCE)
                .setColor(color[0], color[1], color[2], 1.0F);
        builder.addVertex(
                        matrix,
                        DISTANCE,
                        -DISTANCE,
                        -DISTANCE)
                .setColor(color[0], color[1], color[2], 1.0F);
    }

    private static float[][] createPassColors() {
        float[][] colors = new float[
                KingCrimsonPhase5Spec.TIME_ERASE_SKY_PORTAL_LAYERS
                        + 1][3];
        Random random = new Random(
                KingCrimsonPhase5Spec.TIME_ERASE_SKY_RANDOM_SEED);
        for (int pass = 0; pass < colors.length; pass++) {
            float scale = pass == 0
                    ? 0.15F
                    : 2.0F / (19.0F - pass);
            colors[pass][0] =
                    (random.nextFloat() * 0.5F + 0.4F) * scale;
            colors[pass][1] =
                    random.nextFloat() * 0.5F * scale;
            colors[pass][2] =
                    (random.nextFloat() * 0.5F + 0.2F) * scale;
        }
        return colors;
    }

}
