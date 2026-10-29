package rotp.addons.guanggun.client;

import java.util.ArrayList;
import java.util.List;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.guanggun.GuanggunAddon;
import rotp.addons.guanggun.entity.GuanggunBulletEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GuanggunBulletRenderer
        extends EntityRenderer<GuanggunBulletEntity> {
    private static final ResourceLocation TEXTURE =
            GuanggunAddon.id("textures/entity/bullet.png");
    private static final double MAX_TRAIL_LENGTH = 4.0D;
    private static final float V1 = 0.015625F;
    private static final float BEAM_WIDTH = 0.015F;
    private static final double BULLET_U = 0.015625D;

    public GuanggunBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(
            GuanggunBulletEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(
            GuanggunBulletEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return super.shouldRender(
                        entity, frustum, cameraX, cameraY, cameraZ)
                || entity.initialPos != null
                        && frustum.isVisible(new AABB(
                                entity.initialPos,
                                entity.position()));
    }

    @Override
    public void render(
            GuanggunBulletEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visual = entity.getOwner()
                instanceof StandEntity stand
                ? StandVisualContext.world(stand, 1.0F)
                : StandVisualContext.preview();
        if (visual == null || visual.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource standBuffer = AlphaMultiBufferSource.wrap(
                buffer, visual.effectiveAlpha());
        List<Vec3> trace = entity.tracePos;
        if (trace.isEmpty()) {
            trace = new ArrayList<>();
            Vec3 pos = entity.position();
            Vec3 movement = entity.getDeltaMovement();
            Vec3 start = movement.lengthSqr() > 1.0E-7D
                    ? pos.subtract(movement.normalize().scale(
                            MAX_TRAIL_LENGTH * BULLET_U))
                    : pos;
            trace.add(start);
            trace.add(pos);
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        VertexConsumer vertices = standBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(TEXTURE));
        double remaining = MAX_TRAIL_LENGTH;
        boolean first = true;
        for (int i = trace.size() - 1;
                i > 0 && remaining > 0.0D;
                i--) {
            Vec3 end = trace.get(i);
            Vec3 start = trace.get(i - 1);
            float alphaEnd = (float) (remaining / MAX_TRAIL_LENGTH);
            Vec3 backwards = start.subtract(end);
            double length = backwards.length();
            double bulletStart =
                    MAX_TRAIL_LENGTH * (1.0D - BULLET_U);
            if (i == 1
                    && length < remaining - bulletStart
                    && length > 1.0E-7D) {
                double ratio = (remaining - bulletStart) / length;
                length = remaining - bulletStart;
                start = end.add(backwards.scale(ratio));
            }
            if (length > remaining && length > 1.0E-7D) {
                start = end.add(backwards.normalize().scale(remaining));
                remaining = 0.0D;
            }
            else {
                remaining -= length;
            }
            float alphaStart = (float) (remaining / MAX_TRAIL_LENGTH);
            trailSegment(
                    start,
                    end,
                    alphaStart,
                    alphaEnd,
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
                standBuffer,
                packedLight);
    }

    private static void trailSegment(
            Vec3 start,
            Vec3 end,
            float alphaStart,
            float alphaEnd,
            PoseStack poseStack,
            VertexConsumer vertices,
            boolean first) {
        poseStack.pushPose();
        Vec3 segment = start.subtract(end);
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
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            renderSide(
                    poseStack,
                    vertices,
                    length,
                    alphaStart,
                    alphaEnd);
        }
        poseStack.popPose();
        poseStack.translate(segment.x, segment.y, segment.z);
    }

    private static void renderSide(
            PoseStack poseStack,
            VertexConsumer vertices,
            float length,
            float alphaStart,
            float alphaEnd) {
        vertex(poseStack, vertices, 0.0F, -1.0F, 0.0F, alphaEnd, 0.0F);
        vertex(poseStack, vertices, length, -1.0F, 0.0F, alphaStart, 0.0F);
        vertex(poseStack, vertices, length, 1.0F, 0.0F, alphaStart, V1);
        vertex(poseStack, vertices, 0.0F, 1.0F, 0.0F, alphaEnd, V1);
    }

    private static void renderFront(
            PoseStack poseStack, VertexConsumer vertices) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        vertex(poseStack, vertices, -1.0F, -1.0F, 0.0F, V1, V1);
        vertex(poseStack, vertices, 1.0F, -1.0F, 0.0F, 0.0F, V1);
        vertex(poseStack, vertices, 1.0F, 1.0F, 0.0F, 0.0F, V1 * 2.0F);
        vertex(poseStack, vertices, -1.0F, 1.0F, 0.0F, V1, V1 * 2.0F);
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
