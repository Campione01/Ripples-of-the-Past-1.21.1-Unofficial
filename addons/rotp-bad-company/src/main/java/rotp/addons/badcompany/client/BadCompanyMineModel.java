package rotp.addons.badcompany.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyMineEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class BadCompanyMineModel
        extends EntityModel<BadCompanyMineEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    BadCompanyAddon.id("bad_mine"), "main");

    private final ModelPart mine;

    public BadCompanyMineModel(ModelPart root) {
        mine = root.getChild("mine");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition minePart =
                mesh.getRoot().addOrReplaceChild(
                        "mine",
                        CubeListBuilder.create()
                                .texOffs(0, 0)
                                .addBox(
                                        -3.0F,
                                        -2.0F,
                                        -3.0F,
                                        6,
                                        2,
                                        6)
                                .texOffs(12, 14)
                                .addBox(
                                        -2.0F,
                                        -1.0F,
                                        -5.0F,
                                        4,
                                        1,
                                        2)
                                .texOffs(0, 14)
                                .addBox(
                                        -2.0F,
                                        -1.0F,
                                        3.0F,
                                        4,
                                        1,
                                        2)
                                .texOffs(8, 9)
                                .addBox(
                                        3.0F,
                                        -1.0F,
                                        -2.0F,
                                        2,
                                        1,
                                        4)
                                .texOffs(0, 8)
                                .addBox(
                                        -5.0F,
                                        -1.0F,
                                        -2.0F,
                                        2,
                                        1,
                                        4),
                        PartPose.ZERO);
        minePart.addOrReplaceChild(
                "fuse",
                CubeListBuilder.create()
                        .texOffs(16, 8)
                        .addBox(
                                -1.0F,
                                -2.0F,
                                -1.0F,
                                2,
                                1,
                                2,
                                new CubeDeformation(0.4F)),
                PartPose.rotation(0.0F, -0.7854F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(
            BadCompanyMineEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        mine.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }
}
