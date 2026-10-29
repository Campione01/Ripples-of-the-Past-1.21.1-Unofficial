package rotp.addons.killerqueen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.world.entity.Entity;

public final class KillerQueenProjectileModel<T extends Entity>
        extends EntityModel<T> {
    private final ModelPart bomb;

    public KillerQueenProjectileModel(ModelPart root) {
        bomb = root.getChild("bomb");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
                "bomb",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -1.0F,
                                -2.0F,
                                -1.0F,
                                2.0F,
                                2.0F,
                                2.0F)
                        .texOffs(6, 0)
                        .addBox(
                                -1.0F,
                                -1.5F,
                                -0.5F,
                                2.0F,
                                1.0F,
                                1.0F,
                                new CubeDeformation(0.2F))
                        .texOffs(0, 4)
                        .addBox(
                                -0.5F,
                                -2.0F,
                                -0.5F,
                                1.0F,
                                2.0F,
                                1.0F,
                                new CubeDeformation(0.2F))
                        .texOffs(6, 2)
                        .addBox(
                                -0.5F,
                                -1.5F,
                                -1.0F,
                                1.0F,
                                1.0F,
                                2.0F,
                                new CubeDeformation(0.2F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        bomb.yRot = netHeadYaw
                * ((float) Math.PI / 180.0F);
        bomb.xRot = headPitch
                * ((float) Math.PI / 180.0F);
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        bomb.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }
}
