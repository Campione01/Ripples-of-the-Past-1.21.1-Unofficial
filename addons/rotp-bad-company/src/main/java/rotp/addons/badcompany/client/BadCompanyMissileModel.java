package rotp.addons.badcompany.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyMissileEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public final class BadCompanyMissileModel
        extends EntityModel<BadCompanyMissileEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    BadCompanyAddon.id("bad_missile"), "main");

    private final ModelPart missile;

    public BadCompanyMissileModel(ModelPart root) {
        missile = root.getChild("missile");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
                "missile",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.0F, -2.0F, -2.0F, 2, 2, 3)
                        .texOffs(0, 5)
                        .addBox(
                                -0.5F,
                                -1.5F,
                                -2.0F,
                                1,
                                1,
                                3,
                                new CubeDeformation(0.3F))
                        .texOffs(5, 5)
                        .addBox(-1.0F, -2.0F, 1.2F, 2, 2, 1),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(
            BadCompanyMissileEntity entity,
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
        missile.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }
}
