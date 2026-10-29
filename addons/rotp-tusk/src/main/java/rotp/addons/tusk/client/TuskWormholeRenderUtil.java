package rotp.addons.tusk.client;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.tusk.client.TuskClientRenderDiagnostics.GeometryCapture;
import rotp.addons.tusk.client.TuskClientRenderDiagnostics.VisualRoute;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

final class TuskWormholeRenderUtil {
    static final float ROTATION_RADIANS_PER_TICK = 0.25F;
    static final float ROTATION_DEGREES_PER_TICK = 14.323945F;
    static final float TINY_PORTAL_HALF_SIZE = 0.5F;

    private TuskWormholeRenderUtil() {}

    static ModelPart createPortalPlane() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "portal",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -16.0F,
                                0.0F,
                                -16.0F,
                                32.0F,
                                0.0F,
                                32.0F),
                PartPose.offset(0.0F, -1.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128)
                .bakeRoot()
                .getChild("portal");
    }

    static ModelPart createTinyPortalPlane() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "portal",
                CubeListBuilder.create()
                        .texOffs(-16, 0)
                        .addBox(
                                -8.5F,
                                -0.1F,
                                -7.5F,
                                16.0F,
                                0.0F,
                                16.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64)
                .bakeRoot()
                .getChild("portal");
    }

    static ModelPart createShoulderPortalPlane(boolean leftSide) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "portal",
                CubeListBuilder.create()
                        .texOffs(0, -16)
                        .addBox(
                                0.0F,
                                leftSide ? -8.0F : -8.5F,
                                -7.5F,
                                -0.5F,
                                16.0F,
                                16.0F),
                PartPose.offset(
                        leftSide ? -4.1F : 4.1F,
                        2.0F,
                        0.0F));
        return LayerDefinition.create(mesh, 64, 64)
                .bakeRoot()
                .getChild("portal");
    }

    static void renderModelPart(
            ModelPart modelPart,
            PoseStack poseStack,
            MultiBufferSource buffer,
            ResourceLocation texture,
            int packedLight,
            VisualRoute visualRoute,
            Entity entity,
            @Nullable UUID ownerUuid) {
        VertexConsumer vertices = buffer.getBuffer(
                ModRenderTypes.standTranslucent(texture));
        try (GeometryCapture capture =
                TuskClientRenderDiagnostics.beginGeometry(
                        visualRoute,
                        entity,
                        ownerUuid,
                        texture,
                        vertices)) {
            modelPart.render(
                    poseStack,
                    capture.consumer(),
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF);
        }
    }

    static void renderTinyVertical(
            PoseStack poseStack,
            MultiBufferSource buffer,
            ResourceLocation texture,
            float halfSize,
            VisualRoute visualRoute,
            Entity entity,
            @Nullable UUID ownerUuid) {
        VertexConsumer vertices = buffer.getBuffer(
                ModRenderTypes.standTranslucent(texture));
        try (GeometryCapture capture =
                TuskClientRenderDiagnostics.beginGeometry(
                        visualRoute,
                        entity,
                        ownerUuid,
                        texture,
                        vertices)) {
            VertexConsumer captured = capture.consumer();
            verticalVertex(
                    poseStack,
                    captured,
                    -halfSize,
                    -halfSize,
                    0.0F,
                    0.25F);
            verticalVertex(
                    poseStack,
                    captured,
                    halfSize,
                    -halfSize,
                    0.25F,
                    0.25F);
            verticalVertex(
                    poseStack,
                    captured,
                    halfSize,
                    halfSize,
                    0.25F,
                    0.0F);
            verticalVertex(
                    poseStack,
                    captured,
                    -halfSize,
                    halfSize,
                    0.0F,
                    0.0F);
        }
    }

    private static void verticalVertex(
            PoseStack poseStack,
            VertexConsumer vertices,
            float x,
            float y,
            float u,
            float v) {
        PoseStack.Pose pose = poseStack.last();
        vertices.addVertex(pose.pose(), x, y, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
