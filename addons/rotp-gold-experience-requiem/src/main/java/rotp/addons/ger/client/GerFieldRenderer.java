package rotp.addons.ger.client;

import java.util.UUID;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.entity.GerFieldEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class GerFieldRenderer
        extends EntityRenderer<GerFieldEntity> {
    private static final ResourceLocation DISABLER_OUTER =
            GoldExperienceRequiemAddon.id(
                    "textures/effect/disabler.png");
    private static final ResourceLocation DISABLER_INNER =
            GoldExperienceRequiemAddon.id(
                    "textures/effect/disabler2.png");
    private static final ResourceLocation EVOLUTION =
            GoldExperienceRequiemAddon.id(
                    "textures/action/ger_evolution.png");
    private static final int EVOLUTION_SEGMENTS = 24;
    private static UUID lastEvolutionEntityUuid;
    private static long evolutionRenderSequence;
    private static long lastEvolutionGameTime = Long.MIN_VALUE;
    private static int lastEvolutionPrimitiveCount;

    public GerFieldRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            GerFieldEntity entity) {
        return entity.getKind() == GerFieldEntity.EVOLUTION
                ? EVOLUTION
                : DISABLER_OUTER;
    }

    @Override
    public void render(
            GerFieldEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                GerVisuals.forOwnerUuid(
                        entity.level(),
                        entity.getOwnerUuid(),
                        partialTick);
        float now = entity.level().getGameTime() + partialTick;
        float age = now - entity.getCreatedAt();
        float remaining = entity.getExpiresAt() - now;
        float fadeOutTicks =
                entity.getKind() == GerFieldEntity.DISABLER
                        ? 4.0F
                        : 8.0F;
        float ownAlpha = Math.min(
                Mth.clamp(age / 8.0F, 0.0F, 1.0F),
                Mth.clamp(
                        remaining / fadeOutTicks,
                        0.0F,
                        1.0F));
        if (visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F
                || ownAlpha <= 0.0F) {
            return;
        }

        MultiBufferSource standBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha()
                                * ownAlpha);
        float growth = Mth.clamp(age / 8.0F, 0.1F, 1.0F);
        float radius = entity.getRadius() * growth;
        drawPlane(
                poseStack,
                standBuffer.getBuffer(
                        ModRenderTypes.standTranslucent(
                                getTextureLocation(entity))),
                radius,
                0.03F);
        if (entity.getKind() == GerFieldEntity.DISABLER) {
            float innerGrowth =
                    Mth.clamp(age / 6.0F, 0.1F, 1.0F);
            drawPlane(
                    poseStack,
                    standBuffer.getBuffer(
                            ModRenderTypes.standTranslucent(
                                    DISABLER_INNER)),
                    entity.getRadius()
                            * 0.8F
                            * innerGrowth,
                    0.06F);
        }
        else {
            int primitives = drawEvolutionPresentation(
                    poseStack,
                    standBuffer.getBuffer(RenderType.lightning()),
                    radius,
                    age);
            recordEvolutionRender(entity, primitives);
        }
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                standBuffer,
                ClientUtil.MAX_LIGHT);
    }

    public static synchronized EvolutionRenderReceipt evolutionReceipt() {
        return new EvolutionRenderReceipt(
                lastEvolutionEntityUuid,
                evolutionRenderSequence,
                lastEvolutionGameTime,
                lastEvolutionPrimitiveCount);
    }

    private static synchronized void recordEvolutionRender(
            GerFieldEntity entity, int primitiveCount) {
        lastEvolutionEntityUuid = entity.getUUID();
        evolutionRenderSequence++;
        lastEvolutionGameTime = entity.level().getGameTime();
        lastEvolutionPrimitiveCount = primitiveCount;
    }

    private static int drawEvolutionPresentation(
            PoseStack poseStack,
            VertexConsumer vertices,
            float radius,
            float age) {
        float height = Math.max(4.0F, radius * 1.8F);
        int primitives = drawEnergyColumn(
                poseStack, vertices, radius, height, age);
        primitives += drawSpiralRibbon(
                poseStack, vertices, radius, height, age, false);
        primitives += drawSpiralRibbon(
                poseStack, vertices, radius, height, age, true);
        primitives += drawCyclingShockwaves(
                poseStack, vertices, radius, age);
        return primitives;
    }

    private static int drawEnergyColumn(
            PoseStack poseStack,
            VertexConsumer vertices,
            float radius,
            float height,
            float age) {
        PoseStack.Pose pose = poseStack.last();
        float pulse = 0.92F + 0.08F * Mth.sin(age * 0.35F);
        float bottomRadius = radius * 0.52F * pulse;
        float topRadius = radius * 0.26F * pulse;
        for (int segment = 0; segment < EVOLUTION_SEGMENTS;
                segment++) {
            float angle0 = Mth.TWO_PI * segment
                    / EVOLUTION_SEGMENTS;
            float angle1 = Mth.TWO_PI * (segment + 1)
                    / EVOLUTION_SEGMENTS;
            float bx0 = Mth.cos(angle0) * bottomRadius;
            float bz0 = Mth.sin(angle0) * bottomRadius;
            float bx1 = Mth.cos(angle1) * bottomRadius;
            float bz1 = Mth.sin(angle1) * bottomRadius;
            float tx0 = Mth.cos(angle0) * topRadius;
            float tz0 = Mth.sin(angle0) * topRadius;
            float tx1 = Mth.cos(angle1) * topRadius;
            float tz1 = Mth.sin(angle1) * topRadius;
            colorVertex(pose, vertices, bx0, 0.04F, bz0,
                    255, 205, 48, 128);
            colorVertex(pose, vertices, bx1, 0.04F, bz1,
                    255, 205, 48, 128);
            colorVertex(pose, vertices, tx1, height, tz1,
                    105, 255, 145, 16);
            colorVertex(pose, vertices, tx0, height, tz0,
                    105, 255, 145, 16);
        }
        return EVOLUTION_SEGMENTS;
    }

    private static int drawSpiralRibbon(
            PoseStack poseStack,
            VertexConsumer vertices,
            float radius,
            float height,
            float age,
            boolean clockwise) {
        PoseStack.Pose pose = poseStack.last();
        int segments = 32;
        float halfWidth = 0.11F;
        float baseRadius = radius * 0.62F;
        float direction = clockwise ? -1.0F : 1.0F;
        float rotation = age * 0.12F
                + (clockwise ? Mth.PI : 0.0F);
        for (int segment = 0; segment < segments; segment++) {
            float t0 = segment / (float) segments;
            float t1 = (segment + 1) / (float) segments;
            float angle0 = rotation
                    + direction * t0 * Mth.TWO_PI * 1.75F;
            float angle1 = rotation
                    + direction * t1 * Mth.TWO_PI * 1.75F;
            float inner = baseRadius - halfWidth;
            float outer = baseRadius + halfWidth;
            int alpha0 = (int) (190.0F * (1.0F - t0 * 0.45F));
            int alpha1 = (int) (190.0F * (1.0F - t1 * 0.45F));
            colorVertex(pose, vertices,
                    Mth.cos(angle0) * inner,
                    0.08F + t0 * height,
                    Mth.sin(angle0) * inner,
                    255, 225, 72, alpha0);
            colorVertex(pose, vertices,
                    Mth.cos(angle0) * outer,
                    0.08F + t0 * height,
                    Mth.sin(angle0) * outer,
                    255, 245, 130, alpha0);
            colorVertex(pose, vertices,
                    Mth.cos(angle1) * outer,
                    0.08F + t1 * height,
                    Mth.sin(angle1) * outer,
                    120, 255, 165, alpha1);
            colorVertex(pose, vertices,
                    Mth.cos(angle1) * inner,
                    0.08F + t1 * height,
                    Mth.sin(angle1) * inner,
                    95, 235, 135, alpha1);
        }
        return segments;
    }

    private static int drawCyclingShockwaves(
            PoseStack poseStack,
            VertexConsumer vertices,
            float radius,
            float age) {
        PoseStack.Pose pose = poseStack.last();
        int waves = 3;
        for (int wave = 0; wave < waves; wave++) {
            float phase = age * 0.055F + wave / (float) waves;
            phase -= (float) Math.floor(phase);
            float ringRadius = radius * (0.22F + phase * 0.95F);
            float halfWidth = 0.07F + (1.0F - phase) * 0.06F;
            int alpha = (int) (180.0F * (1.0F - phase));
            float inner = Math.max(0.02F, ringRadius - halfWidth);
            float outer = ringRadius + halfWidth;
            for (int segment = 0; segment < EVOLUTION_SEGMENTS;
                    segment++) {
                float angle0 = Mth.TWO_PI * segment
                        / EVOLUTION_SEGMENTS;
                float angle1 = Mth.TWO_PI * (segment + 1)
                        / EVOLUTION_SEGMENTS;
                colorVertex(pose, vertices,
                        Mth.cos(angle0) * inner, 0.1F,
                        Mth.sin(angle0) * inner,
                        255, 218, 52, alpha);
                colorVertex(pose, vertices,
                        Mth.cos(angle0) * outer, 0.1F,
                        Mth.sin(angle0) * outer,
                        140, 255, 155, alpha);
                colorVertex(pose, vertices,
                        Mth.cos(angle1) * outer, 0.1F,
                        Mth.sin(angle1) * outer,
                        140, 255, 155, alpha);
                colorVertex(pose, vertices,
                        Mth.cos(angle1) * inner, 0.1F,
                        Mth.sin(angle1) * inner,
                        255, 218, 52, alpha);
            }
        }
        return waves * EVOLUTION_SEGMENTS;
    }

    private static void colorVertex(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            float x,
            float y,
            float z,
            int red,
            int green,
            int blue,
            int alpha) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(red, green, blue, alpha);
    }

    private static void drawPlane(
            PoseStack poseStack,
            VertexConsumer vertices,
            float radius,
            float y) {
        vertex(poseStack, vertices, -radius, y, -radius, 0.0F, 0.0F);
        vertex(poseStack, vertices, radius, y, -radius, 1.0F, 0.0F);
        vertex(poseStack, vertices, radius, y, radius, 1.0F, 1.0F);
        vertex(poseStack, vertices, -radius, y, radius, 0.0F, 1.0F);
    }

    private static void vertex(
            PoseStack poseStack,
            VertexConsumer vertices,
            float x,
            float y,
            float z,
            float u,
            float v) {
        PoseStack.Pose pose = poseStack.last();
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(ClientUtil.MAX_LIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    public record EvolutionRenderReceipt(
            UUID entityUuid,
            long sequence,
            long gameTime,
            int primitiveCount) {}
}
