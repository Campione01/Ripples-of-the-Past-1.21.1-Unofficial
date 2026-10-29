package rotp.addons.catchtherainbow.client;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

public final class CatchTheRainbowMaskModel
        extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    CatchTheRainbowAddon.id(
                            "catch_the_rainbow_mask"),
                    "main");

    public CatchTheRainbowMaskModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh =
                HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        CubeListBuilder head = CubeListBuilder.create()
                .texOffs(0, 0).addBox(
                        -4.0F, -8.0F, -4.0F,
                        8.0F, 8.0F, 8.0F,
                        new CubeDeformation(0.1F))
                .texOffs(12, 20).addBox(
                        -1.9F, -2.8F, -4.4F,
                        1.0F, 1.0F, 1.0F,
                        new CubeDeformation(-0.2F))
                .texOffs(4, 23).addBox(
                        -0.5F, -2.8F, -4.4F,
                        1.0F, 1.0F, 1.0F,
                        new CubeDeformation(-0.2F))
                .texOffs(4, 20).addBox(
                        -0.5F, -2.0F, -4.4F,
                        1.0F, 2.0F, 1.0F,
                        new CubeDeformation(-0.2F))
                .texOffs(0, 23).addBox(
                        0.9F, -2.8F, -4.4F,
                        1.0F, 1.0F, 1.0F,
                        new CubeDeformation(-0.2F))
                .texOffs(11, 17).mirror().addBox(
                        -4.3F, -5.85F, -4.3F,
                        4.0F, 2.0F, 1.0F,
                        new CubeDeformation(0.1F))
                .texOffs(11, 22).addBox(
                        0.3F, -5.85F, -4.3F,
                        4.0F, 2.0F, 1.0F,
                        new CubeDeformation(0.1F));
        PartDefinition headPart = root.addOrReplaceChild(
                "head", head, PartPose.ZERO);
        headPart.addOrReplaceChild(
                "right_cheek",
                CubeListBuilder.create()
                        .texOffs(0, 16).addBox(
                                -0.3F, -0.35F, 0.0F,
                                2.0F, 3.0F, 1.0F,
                                new CubeDeformation(0.1F)),
                PartPose.offsetAndRotation(
                        2.5F, -2.5F, -4.2F,
                        0.0F, 0.0F, 0.3927F));
        headPart.addOrReplaceChild(
                "left_cheek",
                CubeListBuilder.create()
                        .texOffs(5, 16).addBox(
                                -1.7F, -0.35F, 0.0F,
                                2.0F, 3.0F, 1.0F,
                                new CubeDeformation(0.1F)),
                PartPose.offsetAndRotation(
                        -2.5F, -2.5F, -4.2F,
                        0.0F, 0.0F, -0.3927F));
        headPart.addOrReplaceChild(
                "right_mouth",
                CubeListBuilder.create()
                        .texOffs(0, 20).addBox(
                                -0.5F, -0.2F, -0.5F,
                                1.0F, 2.0F, 1.0F,
                                new CubeDeformation(-0.2F)),
                PartPose.offsetAndRotation(
                        1.4F, -1.8F, -3.9F,
                        0.0F, 0.0F, 0.3927F));
        headPart.addOrReplaceChild(
                "left_mouth",
                CubeListBuilder.create()
                        .texOffs(8, 20).addBox(
                                -0.5F, -0.2F, -0.5F,
                                1.0F, 2.0F, 1.0F,
                                new CubeDeformation(-0.2F)),
                PartPose.offsetAndRotation(
                        -1.4F, -1.8F, -3.9F,
                        0.0F, 0.0F, -0.3927F));
        root.addOrReplaceChild(
                "hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
}
