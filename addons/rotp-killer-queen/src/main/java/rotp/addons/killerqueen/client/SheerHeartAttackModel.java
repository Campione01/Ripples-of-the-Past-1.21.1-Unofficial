package rotp.addons.killerqueen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.entity.SheerHeartAttackEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public final class SheerHeartAttackModel
        extends EntityModel<SheerHeartAttackEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    KillerQueenAddon.id("sheer_heart"), "main");

    private final ModelPart body;

    public SheerHeartAttackModel(ModelPart root) {
        body = root.getChild("body");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 13)
                        .addBox(-4, -5, -4, 8, 4, 8)
                        .texOffs(22, 25)
                        .addBox(-2, -7, -2, 4, 1, 4)
                        .texOffs(24, 13)
                        .addBox(-3, -6, -3, 6, 1, 6)
                        .texOffs(0, 0)
                        .addBox(-3, -4, -5, 6, 3, 10)
                        .texOffs(8, 25)
                        .addBox(-2, -3, 4, 4, 2, 2)
                        .texOffs(22, 0)
                        .addBox(-5, -4, -3, 10, 3, 6)
                        .texOffs(0, 0)
                        .addBox(-2, -5, -6, 4, 3, 1)
                        .texOffs(0, 4)
                        .addBox(-1, -2, -6, 2, 1, 1)
                        .texOffs(0, 6)
                        .addBox(-1.5F, -6, -6, 1, 1, 1)
                        .texOffs(5, 5)
                        .addBox(0.5F, -6, -6, 1, 1, 1)
                        .texOffs(14, 25)
                        .addBox(5, -3, -3, 1, 3, 6)
                        .texOffs(0, 25)
                        .addBox(-6, -3, -3, 1, 3, 6)
                        .texOffs(56, 0)
                        .addBox(-1, -7.5F, -1, 2, 2, 2),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(
            SheerHeartAttackEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        body.yRot = netHeadYaw
                * ((float) Math.PI / 180.0F) * 0.15F;
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        body.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }
}
