package rotp.addons.stonefree.client;

import java.util.EnumSet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import rotp.core.util.functions.MathUtil;

final class StoneFreeBarrierGeometry {
    private static final double SEGMENT_LENGTH = 0.5D;
    private static final int MAX_SEGMENTS = 256;
    private static final ModelPart.Cube SEGMENT = new ModelPart.Cube(
            0, 9, -0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 8.0F,
            0.0F, 0.0F, 0.0F, false, 32.0F, 32.0F,
            EnumSet.allOf(Direction.class));

    private StoneFreeBarrierGeometry() {}

    static Vec3 looseEndpoint(Vec3 userRenderPosition, float eyeHeight) {
        return userRenderPosition.add(0.15D, (double) eyeHeight - 1.4D, 0.0D);
    }

    static void renderAnchored(
            Vec3 endpoint,
            Vec3 origin,
            Vec3 dispatcherPosition,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int color) {
        Vec3 translation = endpoint.subtract(dispatcherPosition);
        poseStack.pushPose();
        poseStack.translate(translation.x, translation.y, translation.z);
        render(endpoint.subtract(origin), poseStack, vertices, packedLight, color);
        poseStack.popPose();
    }

    static void render(
            Vec3 extent,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int color) {
        double length = extent.length();
        if (!(length > 0.0D) || !Double.isFinite(length)) {
            return;
        }

        // Coarsen only distant-span texture repeats, never truncate the axis.
        double segmentLength = SEGMENT_LENGTH * Math.max(
                1.0D, Math.ceil(length / (MAX_SEGMENTS * SEGMENT_LENGTH)));
        int segmentCount = (int) Math.ceil(length / segmentLength);
        Matrix4f placement = new Matrix4f(poseStack.last().pose());
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                MathUtil.yRotDegFromVec(extent)));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                MathUtil.xRotDegFromVec(extent)));
        PoseStack.Pose frame = poseStack.last();
        Vector3f normal = new Vector3f();
        Vector3f position = new Vector3f();
        Vector3f center = new Vector3f();

        for (int segment = 0; segment < segmentCount; segment++) {
            double start = segment * segmentLength;
            double end = Math.min(length, (segment + 1) * segmentLength);
            float fraction = (float) ((end - start) / segmentLength);
            for (ModelPart.Polygon face : SEGMENT.polygons) {
                frame.transformNormal(face.normal, normal);
                for (ModelPart.Vertex vertex : face.vertices) {
                    boolean far = vertex.pos.z() != 0.0F;
                    double along = (far ? end : start) / length;
                    // Interpolate the actual endpoints, avoiding accumulated
                    // translations or rotation error amplified by long spans.
                    placement.transformPosition(
                            (float) (-extent.x * along),
                            (float) (-extent.y * along),
                            (float) (-extent.z * along), center);
                    frame.pose().transformDirection(
                            vertex.pos.x() / 16.0F,
                            vertex.pos.y() / 16.0F,
                            0.0F, position);
                    position.add(center);

                    float u = vertex.u;
                    float v = vertex.v;
                    if (far && fraction < 1.0F && face.normal.z() == 0.0F) {
                        // Crop along each physical face, retaining the donor's
                        // winding and UV orientation (including reversed faces).
                        for (ModelPart.Vertex near : face.vertices) {
                            if (near.pos.z() == 0.0F
                                    && near.pos.x() == vertex.pos.x()
                                    && near.pos.y() == vertex.pos.y()) {
                                u = near.u + fraction * (vertex.u - near.u);
                                v = near.v + fraction * (vertex.v - near.v);
                                break;
                            }
                        }
                    }
                    vertices.addVertex(
                            position.x(), position.y(), position.z(), color,
                            u, v, OverlayTexture.NO_OVERLAY, packedLight,
                            normal.x(), normal.y(), normal.z());
                }
            }
        }
        poseStack.popPose();
    }
}
