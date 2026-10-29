package rotp.addons.emperor.client;

import java.util.List;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandOpacityPolicy;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.emperor.EmperorAddon;
import rotp.addons.emperor.entity.EmperorBulletEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EmperorBulletRenderer
        extends EntityRenderer<EmperorBulletEntity> {
    private static final ResourceLocation TRAIL_TEXTURE =
            EmperorAddon.id(
                    "textures/entity/projectiles/bullet_trace.png");
    private static final double MAX_TRAIL_LENGTH = 4.0D;
    private static final float BEAM_WIDTH = 0.015F;

    public EmperorBulletRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(
            EmperorBulletEntity entity) {
        return TRAIL_TEXTURE;
    }

    @Override
    public boolean shouldRender(
            EmperorBulletEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        if (!ClientGlobals.canSeeStands) {
            return false;
        }
        return super.shouldRender(
                        entity,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ)
                || entity.initialPos != null
                        && frustum.isVisible(new AABB(
                                entity.initialPos,
                                entity.position()));
    }

    @Override
    public void render(
            EmperorBulletEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float alpha = trailAlpha(entity);
        List<Vec3> trace = entity.tracePos;
        if (!ClientGlobals.canSeeStands
                || alpha <= 0.0F
                || trace.isEmpty()) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(buffer, alpha);

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
            if (length > traceLength) {
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

    // The donor draws a fired bullet whatever became of its Stand; only
    // the viewer's Stand opacity setting applies.
    private static float trailAlpha(EmperorBulletEntity entity) {
        return StandOpacityPolicy.apply(1.0F, entity.getOwner());
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
        PoseStack.Pose pose = poseStack.last();
        // The light normal is already in world space: the segment pose
        // must not turn it.
        EmperorTrailShell.emit(
                (float) segment.length(),
                u0,
                u1,
                first,
                (x, y, z, u, v, lightX, lightY, lightZ) ->
                        vertices.addVertex(pose.pose(), x, y, z)
                                .setColor(0xFFFFFFFF)
                                .setUv(u, v)
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(ClientUtil.MAX_LIGHT)
                                .setNormal(lightX, lightY, lightZ));
        poseStack.popPose();
        poseStack.translate(segment.x, segment.y, segment.z);
    }
}
