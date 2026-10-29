package rotp.addons.wonderofu.client;

import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.entity.CarCalamityEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class CarCalamityModel
        extends HierarchicalModel<CarCalamityEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    WonderOfUAddon.id("car_projectile"), "main");
    public static final float BODY_PIVOT_Y = 24.0F;

    private final ModelPart root;
    private final ModelPart car;

    public CarCalamityModel(ModelPart root) {
        this.root = root;
        this.car = root.getChild("car");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder carCubes = CubeListBuilder.create()
                .texOffs(98, 89).addBox(
                        -32.0F, -16.0F, -15.0F,
                        14.0F, 11.0F, 31.0F,
                        CubeDeformation.NONE)
                .texOffs(0, 48).addBox(
                        -6.0F, -28.0F, -15.0F,
                        25.0F, 10.0F, 31.0F,
                        CubeDeformation.NONE)
                .texOffs(88, 131).addBox(
                        -35.0F, -8.0F, -17.0F,
                        5.0F, 4.0F, 35.0F,
                        CubeDeformation.NONE)
                .texOffs(78, 170).addBox(
                        -29.0F, -18.0F, -14.0F,
                        11.0F, 2.0F, 29.0F,
                        CubeDeformation.NONE)
                .texOffs(188, 89).addBox(
                        -29.0F, -10.0F, -18.0F,
                        10.0F, 10.0F, 8.0F,
                        CubeDeformation.NONE)
                .texOffs(16, 215).addBox(
                        17.0F, -11.0F, 17.0F,
                        2.0F, 7.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(150, 201).addBox(
                        -32.0F, -13.0F, -17.0F,
                        2.0F, 5.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(30, 215).addBox(
                        -32.0F, -13.0F, 16.0F,
                        2.0F, 5.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(200, 83).addBox(
                        -30.0F, -13.0F, -17.0F,
                        12.0F, 2.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(0, 215).addBox(
                        -18.0F, -13.0F, -18.0F,
                        2.0F, 8.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(38, 207).addBox(
                        -30.0F, -13.0F, 16.0F,
                        12.0F, 2.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(88, 128).addBox(
                        -18.0F, -5.0F, -18.0F,
                        2.0F, 1.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(158, 170).addBox(
                        -20.0F, -13.0F, -18.0F,
                        2.0F, 2.0F, 1.0F,
                        CubeDeformation.NONE)
                .texOffs(204, 217).addBox(
                        -20.0F, -13.0F, 18.0F,
                        2.0F, 2.0F, 1.0F,
                        CubeDeformation.NONE)
                .texOffs(210, 213).addBox(
                        -18.0F, -13.0F, 17.0F,
                        2.0F, 9.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(184, 31).addBox(
                        -16.0F, -7.0F, -18.0F,
                        33.0F, 3.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(184, 36).addBox(
                        -16.0F, -7.0F, 17.0F,
                        33.0F, 3.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(188, 125).addBox(
                        17.0F, -13.0F, -18.0F,
                        14.0F, 2.0F, 1.0F,
                        CubeDeformation.NONE)
                .texOffs(8, 215).addBox(
                        17.0F, -11.0F, -18.0F,
                        2.0F, 7.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(38, 214).addBox(
                        31.0F, -13.0F, -18.0F,
                        2.0F, 9.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(188, 128).addBox(
                        17.0F, -13.0F, 18.0F,
                        14.0F, 2.0F, 1.0F,
                        CubeDeformation.NONE)
                .texOffs(46, 214).addBox(
                        31.0F, -13.0F, 17.0F,
                        2.0F, 9.0F, 2.0F,
                        CubeDeformation.NONE)
                .texOffs(112, 48).addBox(
                        33.0F, -8.0F, -18.0F,
                        7.0F, 4.0F, 37.0F,
                        CubeDeformation.NONE)
                .texOffs(158, 173).addBox(
                        -34.0F, -12.0F, -16.0F,
                        2.0F, 4.0F, 33.0F,
                        CubeDeformation.NONE)
                .texOffs(78, 201).addBox(
                        -34.0F, -15.0F, -7.0F,
                        2.0F, 3.0F, 15.0F,
                        CubeDeformation.NONE)
                .texOffs(112, 209).addBox(
                        -33.0F, -15.0F, 9.0F,
                        2.0F, 3.0F, 8.0F,
                        CubeDeformation.NONE)
                .texOffs(132, 209).addBox(
                        -33.0F, -15.0F, -16.0F,
                        2.0F, 3.0F, 8.0F,
                        CubeDeformation.NONE)
                .texOffs(54, 214).addBox(
                        8.0F, -28.0F, -16.0F,
                        2.0F, 10.0F, 1.0F,
                        CubeDeformation.NONE)
                .texOffs(152, 210).addBox(
                        37.0F, -16.0F, -18.0F,
                        3.0F, 3.0F, 7.0F,
                        CubeDeformation.NONE)
                .texOffs(172, 210).addBox(
                        37.0F, -16.0F, 12.0F,
                        3.0F, 3.0F, 7.0F,
                        CubeDeformation.NONE)
                .texOffs(66, 207).addBox(
                        37.0F, -16.0F, -11.0F,
                        3.0F, 1.0F, 3.0F,
                        CubeDeformation.NONE)
                .texOffs(192, 217).addBox(
                        37.0F, -16.0F, 9.0F,
                        3.0F, 1.0F, 3.0F,
                        CubeDeformation.NONE)
                .texOffs(24, 215).addBox(
                        8.0F, -28.0F, 16.0F,
                        2.0F, 10.0F, 1.0F,
                        CubeDeformation.NONE)
                .texOffs(184, 41).addBox(
                        -6.0F, -28.0F, -16.0F,
                        25.0F, 2.0F, 1.0F,
                        CubeDeformation.NONE)
                .texOffs(184, 44).addBox(
                        -6.0F, -28.0F, 16.0F,
                        25.0F, 2.0F, 1.0F,
                        CubeDeformation.NONE)
                .texOffs(218, 125).addBox(
                        -14.0F, -19.0F, -18.0F,
                        1.0F, 1.0F, 3.0F,
                        CubeDeformation.NONE)
                .texOffs(0, 0).addBox(
                        -18.0F, -18.0F, -17.0F,
                        57.0F, 13.0F, 35.0F,
                        CubeDeformation.NONE)
                .texOffs(218, 213).addBox(
                        -14.0F, -19.0F, 16.0F,
                        1.0F, 1.0F, 3.0F,
                        CubeDeformation.NONE)
                .texOffs(188, 107).addBox(
                        20.0F, -10.0F, -18.0F,
                        10.0F, 10.0F, 8.0F,
                        CubeDeformation.NONE)
                .texOffs(200, 47).addBox(
                        -29.0F, -10.0F, 11.0F,
                        10.0F, 10.0F, 8.0F,
                        CubeDeformation.NONE)
                .texOffs(200, 65).addBox(
                        20.0F, -10.0F, 11.0F,
                        10.0F, 10.0F, 8.0F,
                        CubeDeformation.NONE);
        PartDefinition car = root.addOrReplaceChild(
                "car", carCubes, PartPose.offset(0.0F, BODY_PIVOT_Y, 0.0F));

        car.addOrReplaceChild(
                "cube_r1",
                CubeListBuilder.create()
                        .texOffs(68, 218).addBox(
                                -1.0F, -2.0F, -2.0F,
                                1.0F, 2.0F, 3.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(
                        -12.5F, -18.0F, 20.0F,
                        0.0F, 0.4363F, 0.0F));
        car.addOrReplaceChild(
                "cube_r2",
                CubeListBuilder.create()
                        .texOffs(60, 218).addBox(
                                -1.0F, -2.0F, -2.0F,
                                1.0F, 2.0F, 3.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(
                        -13.0F, -18.0F, -18.0F,
                        0.0F, -0.4363F, 0.0F));
        car.addOrReplaceChild(
                "cube_r3",
                CubeListBuilder.create()
                        .texOffs(0, 207).addBox(
                                -1.0F, -1.2F, -20.0F,
                                18.0F, 7.0F, 1.0F,
                                CubeDeformation.NONE)
                        .texOffs(112, 201).addBox(
                                -1.0F, -1.2F, 12.0F,
                                18.0F, 7.0F, 1.0F,
                                CubeDeformation.NONE)
                        .texOffs(0, 89).addBox(
                                -1.0F, -1.2F, -19.0F,
                                18.0F, 8.0F, 31.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(
                        22.0F, -23.0F, 4.0F,
                        0.0F, 0.0F, 0.4363F));
        car.addOrReplaceChild(
                "cube_r4",
                CubeListBuilder.create()
                        .texOffs(192, 213).addBox(
                                -0.93F, -1.95F, -16.0F,
                                8.0F, 3.0F, 1.0F,
                                CubeDeformation.NONE)
                        .texOffs(60, 211).addBox(
                                -0.93F, -1.95F, 16.0F,
                                8.0F, 3.0F, 1.0F,
                                CubeDeformation.NONE)
                        .texOffs(0, 168).addBox(
                                -0.93F, -1.95F, -15.0F,
                                8.0F, 8.0F, 31.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(
                        18.2F, -26.0F, 0.0F,
                        0.0F, 0.0F, 0.829F));
        car.addOrReplaceChild(
                "cube_r5",
                CubeListBuilder.create()
                        .texOffs(38, 211).addBox(
                                -8.75F, -1.55F, 10.0F,
                                10.0F, 2.0F, 1.0F,
                                CubeDeformation.NONE)
                        .texOffs(192, 210).addBox(
                                -8.75F, -1.55F, -22.0F,
                                10.0F, 2.0F, 1.0F,
                                CubeDeformation.NONE)
                        .texOffs(0, 128).addBox(
                                -11.75F, -1.55F, -21.0F,
                                13.0F, 9.0F, 31.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(
                        -11.0F, -23.0F, 6.0F,
                        0.0F, 0.0F, -0.7854F));
        car.addOrReplaceChild(
                "cube_r6",
                CubeListBuilder.create()
                        .texOffs(60, 215).addBox(
                                -5.5F, -0.9F, 18.0F,
                                6.0F, 2.0F, 1.0F,
                                CubeDeformation.NONE)
                        .texOffs(214, 210).addBox(
                                -5.5F, -0.9F, -14.0F,
                                6.0F, 2.0F, 1.0F,
                                CubeDeformation.NONE)
                        .texOffs(168, 131).addBox(
                                -5.5F, -0.9F, -13.0F,
                                6.0F, 11.0F, 31.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(
                        -6.0F, -27.0F, -2.0F,
                        0.0F, 0.0F, -0.5236F));
        car.addOrReplaceChild(
                "cube_r7",
                CubeListBuilder.create()
                        .texOffs(184, 0).addBox(
                                -0.9F, -2.1F, -27.0F,
                                3.0F, 2.0F, 29.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(
                        -29.0F, -15.0F, 13.0F,
                        0.0F, 0.0F, -0.7854F));

        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            CarCalamityEntity entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float ticks,
            float yRotationOffset,
            float xRotation) {
        car.yRot =
                yRotationOffset * ((float) Math.PI / 180.0F);
    }
}
