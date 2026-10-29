package rotp.addons.badcompany.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyTankEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public final class BadCompanyTankModel
        extends EntityModel<BadCompanyTankEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    BadCompanyAddon.id("bad_tank"), "main");

    private final ModelPart main;
    private final ModelPart cabin;
    private final ModelPart cannon;

    public BadCompanyTankModel(ModelPart root) {
        main = root.getChild("main");
        cabin = main.getChild("cabin");
        cannon = cabin.getChild("cannon");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition main = mesh.getRoot().addOrReplaceChild(
                "main",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -24.0F,
                                -32.0F,
                                -43.0F,
                                48,
                                22,
                                86)
                        .texOffs(184, 236)
                        .addBox(
                                -30.0F,
                                -32.0F,
                                -43.0F,
                                6,
                                22,
                                86)
                        .texOffs(240, 118)
                        .addBox(
                                -27.0F,
                                -10.0F,
                                -43.0F,
                                10,
                                10,
                                86)
                        .texOffs(182, 22)
                        .addBox(
                                17.0F,
                                -10.0F,
                                -43.0F,
                                10,
                                10,
                                86)
                        .texOffs(120, 122)
                        .addBox(
                                17.0F,
                                -24.0F,
                                -48.0F,
                                10,
                                14,
                                100)
                        .texOffs(0, 108)
                        .addBox(
                                -27.0F,
                                -24.0F,
                                -48.0F,
                                10,
                                14,
                                100)
                        .texOffs(0, 222)
                        .addBox(
                                24.0F,
                                -32.0F,
                                -43.0F,
                                6,
                                22,
                                86)
                        .texOffs(0, 40)
                        .addBox(
                                15.0F,
                                -40.0F,
                                22.0F,
                                9,
                                8,
                                32)
                        .texOffs(0, 131)
                        .addBox(
                                15.0F,
                                -37.0F,
                                4.0F,
                                9,
                                5,
                                18)
                        .texOffs(38, 113)
                        .addBox(
                                -24.0F,
                                -37.0F,
                                4.0F,
                                9,
                                5,
                                18)
                        .texOffs(0, 0)
                        .addBox(
                                -24.0F,
                                -40.0F,
                                22.0F,
                                9,
                                8,
                                32)
                        .texOffs(288, 50)
                        .addBox(
                                -24.0F,
                                -32.0F,
                                41.0F,
                                48,
                                13,
                                12)
                        .texOffs(282, 287)
                        .addBox(
                                -24.0F,
                                -32.0F,
                                -52.0F,
                                48,
                                13,
                                12),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        main.addOrReplaceChild(
                "front_slats",
                CubeListBuilder.create()
                        .texOffs(0, 154)
                        .addBox(
                                -1.0F,
                                -2.0F,
                                -14.0F,
                                8,
                                1,
                                15)
                        .texOffs(147, 154)
                        .addBox(
                                51.0F,
                                -2.0F,
                                -14.0F,
                                8,
                                1,
                                15),
                PartPose.offsetAndRotation(
                        -29.0F,
                        -30.0F,
                        -42.0F,
                        0.8727F,
                        0.0F,
                        0.0F));
        main.addOrReplaceChild(
                "front_plate",
                CubeListBuilder.create()
                        .texOffs(288, 0)
                        .addBox(
                                -47.0F,
                                -2.0F,
                                -1.0F,
                                48,
                                13,
                                12),
                PartPose.offsetAndRotation(
                        23.0F,
                        -26.0F,
                        -43.0F,
                        -0.7854F,
                        0.0F,
                        0.0F));
        main.addOrReplaceChild(
                "rear_plate",
                CubeListBuilder.create()
                        .texOffs(288, 25)
                        .addBox(
                                -47.0F,
                                -2.0F,
                                -1.0F,
                                48,
                                13,
                                12),
                PartPose.offsetAndRotation(
                        23.0F,
                        -26.0F,
                        43.0F,
                        -0.7854F,
                        0.0F,
                        0.0F));
        PartDefinition cabin = main.addOrReplaceChild(
                "cabin",
                CubeListBuilder.create()
                        .texOffs(282, 236)
                        .addBox(
                                -14.0F,
                                -53.0F,
                                -17.0F,
                                28,
                                21,
                                30)
                        .texOffs(120, 108)
                        .addBox(
                                -8.0F,
                                -47.0F,
                                -27.0F,
                                16,
                                15,
                                10)
                        .texOffs(0, 108)
                        .addBox(
                                -8.0F,
                                -47.0F,
                                13.0F,
                                16,
                                11,
                                12)
                        .texOffs(120, 149)
                        .addBox(
                                -6.0F,
                                -44.0F,
                                -36.0F,
                                12,
                                11,
                                9)
                        .texOffs(45, 145)
                        .addBox(
                                -8.0F,
                                -47.0F,
                                25.0F,
                                16,
                                5,
                                9)
                        .texOffs(120, 133)
                        .addBox(
                                -10.0F,
                                -49.0F,
                                34.0F,
                                20,
                                8,
                                8),
                PartPose.ZERO);
        cabin.addOrReplaceChild(
                "cannon",
                CubeListBuilder.create()
                        .texOffs(98, 236)
                        .addBox(
                                -2.0F,
                                -3.0F,
                                -58.0F,
                                4,
                                4,
                                59)
                        .texOffs(0, 0)
                        .addBox(
                                -2.0F,
                                -3.0F,
                                -28.0F,
                                4,
                                4,
                                11,
                                new CubeDeformation(0.4F)),
                PartPose.offset(0.0F, -39.0F, -34.0F));
        return LayerDefinition.create(mesh, 512, 512);
    }

    @Override
    public void setupAnim(
            BadCompanyTankEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        cabin.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        cannon.xRot = headPitch * Mth.DEG_TO_RAD;
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        main.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }
}
