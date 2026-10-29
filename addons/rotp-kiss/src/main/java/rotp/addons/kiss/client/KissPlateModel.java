package rotp.addons.kiss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.kiss.KissAddon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.world.entity.Entity;

public final class KissPlateModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            KissAddon.id("kiss_sticker_bomb_plate"), "main");

    private final ModelPart plate;

    public KissPlateModel(ModelPart root) {
        plate = root.getChild("plate");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
                "plate",
                CubeListBuilder.create()
                        .texOffs(5, 0)
                        .addBox(
                                -2.5F,
                                -2.5F,
                                -0.1F,
                                5.0F,
                                5.0F,
                                0.2F,
                                CubeDeformation.NONE),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        plate.yRot = (float) Math.toRadians(-netHeadYaw);
        plate.xRot = (float) Math.toRadians(headPitch);
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int color) {
        plate.render(poseStack, vertices, packedLight, packedOverlay, color);
    }
}
