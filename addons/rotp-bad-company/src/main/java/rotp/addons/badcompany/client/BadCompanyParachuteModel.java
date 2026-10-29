package rotp.addons.badcompany.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanySoldierEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class BadCompanyParachuteModel
        extends EntityModel<BadCompanySoldierEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    BadCompanyAddon.id("bad_parachute"), "main");

    private final ModelPart pack;

    public BadCompanyParachuteModel(ModelPart root) {
        pack = root.getChild("pack");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition pack = mesh.getRoot().addOrReplaceChild(
                "pack",
                CubeListBuilder.create()
                        .texOffs(24, 46)
                        .addBox(-5.0F, 7.0F, 2.0F, 10, 5, 5)
                        .texOffs(54, 46)
                        .addBox(
                                -4.5F,
                                0.0F,
                                2.0F,
                                9,
                                7,
                                4,
                                new CubeDeformation(0.1F))
                        .texOffs(104, 0)
                        .addBox(
                                -4.0F,
                                9.0F,
                                -2.0F,
                                8,
                                1,
                                4,
                                new CubeDeformation(0.1F))
                        .texOffs(0, 101)
                        .addBox(
                                -4.0F,
                                2.0F,
                                -2.0F,
                                2,
                                7,
                                1,
                                new CubeDeformation(0.1F))
                        .texOffs(0, 72)
                        .addBox(
                                2.0F,
                                2.0F,
                                -2.0F,
                                2,
                                7,
                                1,
                                new CubeDeformation(0.1F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        addCord(
                pack,
                "cord_left_front",
                -3.0F,
                2.0F,
                -1.0F,
                0.2898F,
                0.1975F,
                -0.582F);
        addCord(
                pack,
                "cord_left_back",
                -4.0F,
                2.0F,
                3.0F,
                -0.4084F,
                -0.5843F,
                -0.0961F);
        addCord(
                pack,
                "cord_right_back",
                4.0F,
                2.0F,
                3.0F,
                -0.4084F,
                0.5843F,
                0.0961F);
        addCord(
                pack,
                "cord_right_front",
                3.0F,
                2.0F,
                -1.0F,
                0.2898F,
                -0.1975F,
                0.582F);
        pack.addOrReplaceChild(
                "canopy",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -10.0F,
                                -10.0F,
                                -10.0F,
                                20,
                                10,
                                20),
                PartPose.offsetAndRotation(
                        0.0F,
                        -18.0F,
                        0.0F,
                        0.0F,
                        -0.7854F,
                        0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addCord(
            PartDefinition parent,
            String name,
            float x,
            float y,
            float z,
            float xRot,
            float yRot,
            float zRot) {
        parent.addOrReplaceChild(
                name,
                CubeListBuilder.create()
                        .texOffs(90, 0)
                        .addBox(
                                -1.0F,
                                -13.0F,
                                -1.0F,
                                2,
                                13,
                                0),
                PartPose.offsetAndRotation(
                        x, y, z, xRot, yRot, zRot));
    }

    @Override
    public void setupAnim(
            BadCompanySoldierEntity entity,
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
        pack.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }
}
