package rotp.addons.hermitpurple.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

final class HermitVineGeometry {
    static final int TEXTURE_U = 10;
    static final int TEXTURE_V = 26;
    static final float CROSS_SECTION_PIXELS = 1.0F;
    static final float SEGMENT_PIXELS = 8.0F;
    static final float PIXELS_PER_BLOCK = 16.0F;
    static final float SEGMENT_LENGTH_BLOCKS =
            SEGMENT_PIXELS / PIXELS_PER_BLOCK;

    static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        // Donor HPVineModel: the tube runs z -4..4 around the part pivot and
        // sits x 0..1, y -1..0, so each segment is centred on its point.
        PartDefinition vine = mesh.getRoot().addOrReplaceChild(
                "segment",
                CubeListBuilder.create()
                        .texOffs(TEXTURE_U, TEXTURE_V)
                        .addBox(0.0F, -CROSS_SECTION_PIXELS,
                                -SEGMENT_PIXELS * 0.5F,
                                CROSS_SECTION_PIXELS,
                                CROSS_SECTION_PIXELS, SEGMENT_PIXELS),
                PartPose.ZERO);
        vine.addOrReplaceChild("cube_r1",
                CubeListBuilder.create()
                        .texOffs(37, 5)
                        .addBox(-1.0F, -2.0F, -1.0F, 1.0F, 1.0F, 0.0F)
                        .texOffs(37, 9)
                        .addBox(-1.0F, -2.0F, -6.0F, 1.0F, 1.0F, 0.0F),
                PartPose.offsetAndRotation(2.0F, 0.0F, 3.0F,
                        0.0F, 0.0F, -1.0472F));
        vine.addOrReplaceChild("cube_r2",
                CubeListBuilder.create().texOffs(37, 9)
                        .addBox(-1.0F, -2.0F, -1.0F, 1.0F, 1.0F, 0.0F),
                PartPose.offsetAndRotation(2.0F, 0.0F, 1.0F,
                        0.0F, 0.0F, -1.3526F));
        vine.addOrReplaceChild("cube_r3",
                CubeListBuilder.create().texOffs(31, 5)
                        .addBox(-1.0F, -2.0F, -1.0F, 1.0F, 1.0F, 0.0F),
                PartPose.offsetAndRotation(2.0F, -1.0F, 4.0F,
                        0.0F, 0.0F, -1.3526F));
        vine.addOrReplaceChild("cube_r4",
                CubeListBuilder.create().texOffs(31, 5)
                        .addBox(-1.0F, -2.0F, -1.0F, 1.0F, 1.0F, 0.0F),
                PartPose.offsetAndRotation(1.0F, 1.0F, -1.0F,
                        0.0F, 0.0F, -0.2618F));
        vine.addOrReplaceChild("cube_r5",
                CubeListBuilder.create().texOffs(31, 5)
                        .addBox(-1.0F, -2.0F, -1.0F, 1.0F, 1.0F, 0.0F),
                PartPose.offsetAndRotation(2.0F, 1.0F, 2.0F,
                        0.0F, 0.0F, -0.2618F));
        vine.addOrReplaceChild("cube_r6",
                CubeListBuilder.create().texOffs(37, 5)
                        .addBox(-1.0F, -2.0F, -1.0F, 1.0F, 1.0F, 0.0F),
                PartPose.offsetAndRotation(1.0F, 0.0F, 0.0F,
                        0.0F, 0.0F, -0.6545F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    static Vec3 donorOriginOffset(
            boolean lash, boolean commandGrab, boolean leftHand) {
        if (lash) {
            return new Vec3(leftHand ? 0.3D : -0.3D, -0.2D, 0.0D);
        }
        return new Vec3(0.0D, commandGrab ? -0.25D : -0.2D, 0.0D);
    }

    static Vec3 donorOrigin(Vec3 eyePosition, float bodyYawRadians,
            boolean lash, boolean commandGrab, boolean leftHand) {
        return eyePosition.add(donorOriginOffset(lash, commandGrab, leftHand)
                .yRot(-bodyYawRadians));
    }

    // The animated wrist (D-rotp-hermit-purple-05) belongs to the third-person
    // body; in the user's own first-person view that body is not drawn, so the
    // vine starts at the donor origin there (owner, 2026-10-28).
    static boolean startsAtWrist(
            boolean userIsCameraEntity, CameraType cameraType) {
        return !(userIsCameraEntity && cameraType.isFirstPerson());
    }

    // Donor RepeatingModel: one segment per full half block, plus a final
    // full-length one at the remainder.
    static int segmentCount(float length) {
        if (!Float.isFinite(length) || length < 0.0F) {
            return 0;
        }
        return (int) Math.floor(length / SEGMENT_LENGTH_BLOCKS) + 1;
    }

    static void renderSegments(
            ModelPart segment,
            float length,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int color) {
        int segments = segmentCount(length);
        for (int index = 0; index < segments; index++) {
            segment.render(poseStack, vertices, packedLight,
                    OverlayTexture.NO_OVERLAY, color);
            poseStack.translate(0.0D, 0.0D, SEGMENT_LENGTH_BLOCKS);
        }
    }

    private HermitVineGeometry() {}
}
