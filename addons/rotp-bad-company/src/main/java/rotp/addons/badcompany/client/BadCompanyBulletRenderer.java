package rotp.addons.badcompany.client;

import java.util.ArrayList;
import java.util.List;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyBulletEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BadCompanyBulletRenderer
        extends EntityRenderer<BadCompanyBulletEntity> {
    private static final ResourceLocation TRAIL_TEXTURE =
            BadCompanyAddon.id(
                    "textures/entity/damaging/bullet_trace.png");
    private static final double MAX_TRAIL_LENGTH = 4.0D;
    private static final float V1 = 0.015625F;
    private static final float BEAM_WIDTH = 0.015F;
    private static final double BULLET_U = 0.015625D;

    public BadCompanyBulletRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(
            BadCompanyBulletEntity entity) {
        return BadCompanyVisuals.texture(entity, TRAIL_TEXTURE);
    }

    @Override
    public boolean shouldRender(
            BadCompanyBulletEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        if (!ClientGlobals.canSeeStands) {
            return false;
        }
        StandVisualContext visualContext =
                BadCompanyVisuals.context(entity, 1.0F);
        return visualContext != null
                && visualContext.effectiveAlpha() > 0.0F
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
    }

    @Override
    public void render(
            BadCompanyBulletEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                BadCompanyVisuals.context(entity, partialTick);
        if (!ClientGlobals.canSeeStands
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
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
                previous = current.add(backwards.scale(ratio));
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
    }

    private static List<Vec3> fallbackTrace(
            BadCompanyBulletEntity entity) {
        List<Vec3> trace = new ArrayList<>();
        Vec3 position = entity.position();
        Vec3 movement = entity.getDeltaMovement();
        Vec3 start = movement.lengthSqr() > 1.0E-7D
                ? position.subtract(
                        movement.normalize().scale(
                                MAX_TRAIL_LENGTH * BULLET_U))
                : position;
        trace.add(start);
        trace.add(position);
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
        poseStack.scale(
                1.0F, BEAM_WIDTH, BEAM_WIDTH);
        float length = (float) segment.length();
        if (first) {
            renderFront(poseStack, vertices);
        }
        for (int side = 0; side < 4; side++) {
            poseStack.mulPose(
                    Axis.XP.rotationDegrees(90.0F));
            renderSide(
                    poseStack,
                    vertices,
                    length,
                    u0,
                    u1);
        }
        poseStack.popPose();
        poseStack.translate(
                segment.x, segment.y, segment.z);
    }

    private static void renderSide(
            PoseStack poseStack,
            VertexConsumer vertices,
            float length,
            float u0,
            float u1) {
        vertex(
                poseStack, vertices,
                0.0F, -1.0F, 0.0F, u1, 0.0F);
        vertex(
                poseStack, vertices,
                length, -1.0F, 0.0F, u0, 0.0F);
        vertex(
                poseStack, vertices,
                length, 1.0F, 0.0F, u0, V1);
        vertex(
                poseStack, vertices,
                0.0F, 1.0F, 0.0F, u1, V1);
    }

    private static void renderFront(
            PoseStack poseStack,
            VertexConsumer vertices) {
        poseStack.pushPose();
        poseStack.mulPose(
                Axis.YP.rotationDegrees(90.0F));
        vertex(
                poseStack, vertices,
                -1.0F, -1.0F, 0.0F, V1, V1);
        vertex(
                poseStack, vertices,
                1.0F, -1.0F, 0.0F, 0.0F, V1);
        vertex(
                poseStack, vertices,
                1.0F, 1.0F, 0.0F, 0.0F, V1 * 2.0F);
        vertex(
                poseStack, vertices,
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
}
