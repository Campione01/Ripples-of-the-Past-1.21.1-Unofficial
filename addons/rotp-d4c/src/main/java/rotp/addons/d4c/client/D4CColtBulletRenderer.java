package rotp.addons.d4c.client;

import java.util.ArrayList;
import java.util.List;

import rotp.core.client.util.functions.ClientUtil;
import rotp.core.core.JojoMod;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.d4c.entity.D4CColtBulletEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class D4CColtBulletRenderer
        extends EntityRenderer<D4CColtBulletEntity> {
    private static final ResourceLocation TRAIL_TEXTURE =
            JojoMod.resLoc(
                    "textures/entity/projectiles/bullet_trace.png");
    private static final double MAX_TRAIL_LENGTH = 4.0D;
    private static final float V1 = 0.015625F;
    private static final float BEAM_WIDTH = 0.015F;
    private static final double BULLET_U = 0.015625D;

    public D4CColtBulletRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(
            D4CColtBulletEntity entity) {
        return TRAIL_TEXTURE;
    }

    @Override
    public void render(
            D4CColtBulletEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
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
        poseStack.translate(
                0.0D, entity.getBbHeight() / 2.0D, 0.0D);
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityTranslucentCull(
                        getTextureLocation(entity)));
        double remaining = MAX_TRAIL_LENGTH;
        boolean first = true;
        for (int index = trace.size() - 1;
                index > 0 && remaining > 0.0D;
                index--) {
            Vec3 current = trace.get(index);
            Vec3 previous = trace.get(index - 1);
            float u1 = (float) (remaining / MAX_TRAIL_LENGTH);
            Vec3 backwards = previous.subtract(current);
            double length = backwards.length();
            if (length > remaining && length > 1.0E-7D) {
                previous = current.add(
                        backwards.normalize().scale(remaining));
                remaining = 0.0D;
            } else {
                remaining -= length;
            }
            float u0 = (float) (remaining / MAX_TRAIL_LENGTH);
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
                buffer,
                packedLight);
    }

    private static void trailSegment(
            Vec3 previous,
            Vec3 current,
            float u0,
            float u1,
            PoseStack poseStack,
            VertexConsumer vertices,
            boolean first) {
        poseStack.pushPose();
        Vec3 segment = previous.subtract(current);
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
        for (int side = 0; side < 4; side++) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            renderSide(poseStack, vertices, length, u0, u1);
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
        vertex(poseStack, vertices, 0, -1, 0, u1, 0);
        vertex(poseStack, vertices, length, -1, 0, u0, 0);
        vertex(poseStack, vertices, length, 1, 0, u0, V1);
        vertex(poseStack, vertices, 0, 1, 0, u1, V1);
    }

    private static void renderFront(
            PoseStack poseStack, VertexConsumer vertices) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        vertex(poseStack, vertices, -1, -1, 0, V1, V1);
        vertex(poseStack, vertices, 1, -1, 0, 0, V1);
        vertex(poseStack, vertices, 1, 1, 0, 0, V1 * 2.0F);
        vertex(poseStack, vertices, -1, 1, 0, V1, V1 * 2.0F);
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
