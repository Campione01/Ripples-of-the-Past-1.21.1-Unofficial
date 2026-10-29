package rotp.addons.diegosworld.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.client.render.EntityPostRenderExtensions;
import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.core.JojoMod;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.diegosworld.entity.DiegoRevolverBulletEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DiegoRevolverBulletRenderer
        extends EntityRenderer<DiegoRevolverBulletEntity> {
    private static final ResourceLocation TRAIL_TEXTURE =
            JojoMod.resLoc(
                    "textures/entity/projectiles/bullet_trace.png");
    private static final double MAX_TRAIL_LENGTH = 4.0D;
    private static final float V1 = 0.015625F;
    private static final float BEAM_WIDTH = 0.015F;
    private static final double BULLET_U = 0.015625D;

    private static long frustumAcceptCount;
    private static long lastFrustumFrame = Long.MIN_VALUE;
    private static long drawCount;
    private static long lastDrawFrame = Long.MIN_VALUE;
    private static UUID lastEntityUuid;
    private static ResourceLocation lastTexture;
    private static int lastTracePoints;
    private static int lastSubmittedSegments;
    private static double lastSubmittedLength;
    private static float lastEffectiveAlpha;

    public DiegoRevolverBulletRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(
            DiegoRevolverBulletEntity entity) {
        return TRAIL_TEXTURE;
    }

    @Override
    public boolean shouldRender(
            DiegoRevolverBulletEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        StandVisualContext context =
                DiegoStandVisuals.fromProjectile(entity, 1.0F);
        boolean accepted = ClientGlobals.canSeeStands
                && context != null
                && context.effectiveAlpha() > 0.0F
                && (super.shouldRender(
                        entity,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ)
                || entity.initialPos != null
                        && frustum.isVisible(new AABB(
                                entity.initialPos,
                                entity.position())));
        if (accepted) {
            synchronized (DiegoRevolverBulletRenderer.class) {
                frustumAcceptCount++;
                lastFrustumFrame =
                        EntityPostRenderExtensions.currentFrameId();
                lastEntityUuid = entity.getUUID();
            }
        }
        return accepted;
    }

    @Override
    public void render(
            DiegoRevolverBulletEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext context =
                DiegoStandVisuals.fromProjectile(
                        entity, partialTick);
        if (!ClientGlobals.canSeeStands
                || context == null
                || context.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer, context.effectiveAlpha());
        List<Vec3> trace = entity.tracePos;
        if (trace.isEmpty()) {
            trace = fallbackTrace(entity);
        }

        poseStack.pushPose();
        poseStack.translate(
                0.0D, entity.getBbHeight() / 2.0D, 0.0D);
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(
                        getTextureLocation(entity)));
        double traceLength = MAX_TRAIL_LENGTH;
        boolean first = true;
        int submittedSegments = 0;
        double submittedLength = 0.0D;
        for (int i = trace.size() - 1;
                i > 0 && traceLength > 0.0D;
                i--) {
            Vec3 current = trace.get(i);
            Vec3 previous = trace.get(i - 1);
            float u1 =
                    (float) (traceLength / MAX_TRAIL_LENGTH);
            Vec3 backwards = previous.subtract(current);
            double length = backwards.length();

            double bulletStart =
                    MAX_TRAIL_LENGTH * (1.0D - BULLET_U);
            if (i == 1
                    && length < traceLength - bulletStart
                    && length > 1.0E-7D) {
                double ratio =
                        (traceLength - bulletStart) / length;
                length = traceLength - bulletStart;
                previous = current.add(
                        backwards.scale(ratio));
            }
            if (length > traceLength && length > 1.0E-7D) {
                previous = current.add(
                        backwards.normalize().scale(traceLength));
                traceLength = 0.0D;
            } else {
                traceLength -= length;
            }
            float u0 =
                    (float) (traceLength / MAX_TRAIL_LENGTH);
            trailSegment(
                    previous,
                    current,
                    u0,
                    u1,
                    poseStack,
                    vertices,
                    first);
            if (length > 1.0E-7D) {
                submittedSegments++;
                submittedLength += length;
            }
            first = false;
        }
        poseStack.popPose();
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                alphaBuffer,
                packedLight);
        if (submittedSegments > 0) {
            synchronized (DiegoRevolverBulletRenderer.class) {
                drawCount++;
                lastDrawFrame =
                        EntityPostRenderExtensions.currentFrameId();
                lastEntityUuid = entity.getUUID();
                lastTexture = getTextureLocation(entity);
                lastTracePoints = trace.size();
                lastSubmittedSegments = submittedSegments;
                lastSubmittedLength = submittedLength;
                lastEffectiveAlpha = context.effectiveAlpha();
            }
        }
    }

    public static synchronized void resetDiagnostics() {
        frustumAcceptCount = 0L;
        lastFrustumFrame = Long.MIN_VALUE;
        drawCount = 0L;
        lastDrawFrame = Long.MIN_VALUE;
        lastEntityUuid = null;
        lastTexture = null;
        lastTracePoints = 0;
        lastSubmittedSegments = 0;
        lastSubmittedLength = 0.0D;
        lastEffectiveAlpha = 0.0F;
    }

    public static synchronized DiagnosticSnapshot diagnosticSnapshot() {
        return new DiagnosticSnapshot(
                frustumAcceptCount,
                lastFrustumFrame,
                drawCount,
                lastDrawFrame,
                lastEntityUuid,
                lastTexture,
                lastTracePoints,
                lastSubmittedSegments,
                lastSubmittedLength,
                lastEffectiveAlpha);
    }

    private static List<Vec3> fallbackTrace(
            DiegoRevolverBulletEntity entity) {
        List<Vec3> trace = new ArrayList<>();
        Vec3 pos = entity.position();
        Vec3 movement = entity.getDeltaMovement();
        Vec3 start = movement.lengthSqr() > 1.0E-7D
                ? pos.subtract(movement.normalize().scale(
                        MAX_TRAIL_LENGTH * BULLET_U))
                : pos;
        trace.add(start);
        trace.add(pos);
        return trace;
    }

    private static void trailSegment(
            Vec3 pos1,
            Vec3 pos2,
            float u0,
            float u1,
            PoseStack poseStack,
            VertexConsumer vertices,
            boolean first) {
        poseStack.pushPose();
        Vec3 segment = pos1.subtract(pos2);
        if (segment.lengthSqr() < 1.0E-7D) {
            poseStack.popPose();
            return;
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(
                -90.0F - MathUtil.yRotDegFromVec(segment)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                -MathUtil.xRotDegFromVec(segment)));
        poseStack.scale(1.0F, BEAM_WIDTH, BEAM_WIDTH);
        float length = (float) segment.length();
        if (first) {
            renderFront(poseStack, vertices);
        }
        for (int i = 0; i < 4; i++) {
            poseStack.mulPose(
                    Axis.XP.rotationDegrees(90.0F));
            renderSide(
                    poseStack, vertices, length, u0, u1);
        }
        poseStack.popPose();
        poseStack.translate(segment.x, segment.y, segment.z);
    }

    private static void renderSide(
            PoseStack poseStack,
            VertexConsumer vertices,
            float length,
            float u0,
            float u1) {
        vertex(poseStack, vertices,
                0.0F, -1.0F, 0.0F, u1, 0.0F);
        vertex(poseStack, vertices,
                length, -1.0F, 0.0F, u0, 0.0F);
        vertex(poseStack, vertices,
                length, 1.0F, 0.0F, u0, V1);
        vertex(poseStack, vertices,
                0.0F, 1.0F, 0.0F, u1, V1);
    }

    private static void renderFront(
            PoseStack poseStack,
            VertexConsumer vertices) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        vertex(poseStack, vertices,
                -1.0F, -1.0F, 0.0F, V1, V1);
        vertex(poseStack, vertices,
                1.0F, -1.0F, 0.0F, 0.0F, V1);
        vertex(poseStack, vertices,
                1.0F, 1.0F, 0.0F, 0.0F, V1 * 2.0F);
        vertex(poseStack, vertices,
                -1.0F, 1.0F, 0.0F, V1, V1 * 2.0F);
        poseStack.popPose();
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

    public record DiagnosticSnapshot(
            long frustumAcceptCount,
            long lastFrustumFrame,
            long drawCount,
            long lastDrawFrame,
            UUID lastEntityUuid,
            ResourceLocation lastTexture,
            int lastTracePoints,
            int lastSubmittedSegments,
            double lastSubmittedLength,
            float lastEffectiveAlpha) {}
}
