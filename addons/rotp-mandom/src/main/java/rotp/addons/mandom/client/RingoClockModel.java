package rotp.addons.mandom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.mandom.MandomAddon;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * One standalone clock instance from the canonical paired arm geometry.
 */
public final class RingoClockModel {
    public static final int CANONICAL_DISTINCT_CUBES = 3;
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    MandomAddon.id("ringo_clock"),
                    "main");

    private final ModelPart clock;
    private final ModelPart hand;

    public RingoClockModel(ModelPart root) {
        clock = root.getChild("clock");
        hand = clock.getChild("hand");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder clockCubes = CubeListBuilder.create()
                .texOffs(23, 2)
                .addBox(
                        -0.25F, -1.0F, -1.0F,
                        0.5F, 2.0F, 2.0F,
                        new CubeDeformation(0.0F))
                .texOffs(2, 0)
                .addBox(
                        -2.25F, -0.75F, -2.25F,
                        4.5F, 1.5F, 4.5F,
                        new CubeDeformation(0.0F));
        PartDefinition clock = root.addOrReplaceChild(
                "clock",
                clockCubes,
                PartPose.ZERO);
        clock.addOrReplaceChild(
                "hand",
                CubeListBuilder.create()
                        .texOffs(1, -1)
                        .addBox(
                                0.0F, -0.37F, -0.874F,
                                0.0F, 1.0F, 1.0F,
                                new CubeDeformation(0.0F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void setupAnim(float ageInTicks) {
        hand.xRot = (0.5F + ageInTicks) / 40.0F;
    }

    public void render(
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay) {
        clock.render(
                poseStack,
                vertices,
                packedLight,
                packedOverlay);
    }
}
