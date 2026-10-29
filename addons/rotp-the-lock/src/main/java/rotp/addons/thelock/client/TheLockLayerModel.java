package rotp.addons.thelock.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.thelock.TheLockAddon;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class TheLockLayerModel {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    TheLockAddon.id("the_lock_target_layer"),
                    "main");

    private final ModelPart lock;

    public TheLockLayerModel(ModelPart root) {
        lock = root.getChild("the_lock");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder cubes = CubeListBuilder.create()
                .texOffs(0, 0).addBox(
                        -2.0F, 0.0F, -5.5F,
                        3.0F, 8.0F, 11.0F)
                .texOffs(6, 20).addBox(
                        -1.5F, -3.0F, -5.0F,
                        2.0F, 3.0F, 1.0F)
                .texOffs(0, 20).addBox(
                        -1.5F, -3.0F, 4.0F,
                        2.0F, 3.0F, 1.0F, true)
                .texOffs(7, 20).addBox(
                        -1.0F, -2.5F, -5.5F,
                        1.0F, 1.0F, 11.0F)
                .texOffs(18, 0).addBox(
                        -2.5F, -0.5F, 3.0F,
                        4.0F, 2.0F, 3.0F)
                .texOffs(18, 5).addBox(
                        -2.5F, -0.5F, -6.0F,
                        4.0F, 2.0F, 3.0F)
                .texOffs(1, 4).addBox(
                        -0.5F, -0.5F, -1.5F,
                        2.0F, 4.0F, 3.0F);
        root.addOrReplaceChild(
                "the_lock",
                cubes,
                PartPose.offset(0.0F, 6.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    public void render(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay) {
        lock.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay);
    }
}
