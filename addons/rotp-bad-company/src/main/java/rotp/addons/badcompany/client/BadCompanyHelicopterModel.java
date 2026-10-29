package rotp.addons.badcompany.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyHelicopterEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class BadCompanyHelicopterModel
        extends EntityModel<BadCompanyHelicopterEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    BadCompanyAddon.id("bad_helicopter"), "main");

    private final ModelPart body;
    private final ModelPart rotor;
    private final ModelPart rearRotor;
    private final ModelPart[] missiles = new ModelPart[6];

    public BadCompanyHelicopterModel(ModelPart root) {
        body = root.getChild("body");
        rotor = body.getChild("rotor");
        rearRotor = body.getChild("rear_rotor");
        for (int i = 0; i < missiles.length; i++) {
            missiles[i] = body.getChild("missile_" + (i + 1));
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(78, 163)
                        .addBox(
                                -14.0F,
                                -32.0F,
                                -9.0F,
                                28,
                                32,
                                11)
                        .texOffs(0, 150)
                        .addBox(
                                -14.0F,
                                -32.0F,
                                32.0F,
                                28,
                                32,
                                11)
                        .texOffs(102, 120)
                        .addBox(
                                -12.0F,
                                -14.0F,
                                -38.0F,
                                24,
                                14,
                                29)
                        .texOffs(206, 0)
                        .addBox(
                                -9.0F,
                                -14.0F,
                                -55.0F,
                                18,
                                14,
                                17)
                        .texOffs(159, 48)
                        .addBox(
                                -9.0F,
                                -34.0F,
                                -12.0F,
                                18,
                                5,
                                25)
                        .texOffs(84, 84)
                        .addBox(
                                -14.0F,
                                -6.0F,
                                2.0F,
                                28,
                                6,
                                30)
                        .texOffs(73, 48)
                        .addBox(
                                -14.0F,
                                -32.0F,
                                2.0F,
                                28,
                                6,
                                30)
                        .texOffs(202, 200)
                        .addBox(
                                -10.0F,
                                -19.0F,
                                43.0F,
                                20,
                                19,
                                14)
                        .texOffs(0, 48)
                        .addBox(
                                -8.0F,
                                -19.0F,
                                57.0F,
                                16,
                                14,
                                41)
                        .texOffs(130, 200)
                        .addBox(
                                -5.0F,
                                -19.0F,
                                98.0F,
                                10,
                                10,
                                26)
                        .texOffs(0, 48)
                        .addBox(
                                -1.0F,
                                -58.0F,
                                122.0F,
                                2,
                                19,
                                18)
                        .texOffs(211, 78)
                        .addBox(
                                -38.0F,
                                -12.0F,
                                -28.0F,
                                26,
                                2,
                                15)
                        .texOffs(208, 140)
                        .addBox(
                                12.0F,
                                -12.0F,
                                -28.0F,
                                26,
                                2,
                                15)
                        .texOffs(32, 206)
                        .addBox(
                                0.0F,
                                -22.0F,
                                115.0F,
                                26,
                                2,
                                15)
                        .texOffs(206, 31)
                        .addBox(
                                -26.0F,
                                -22.0F,
                                115.0F,
                                26,
                                2,
                                15)
                        .texOffs(0, 46)
                        .addBox(
                                -1.0F,
                                -46.0F,
                                -1.0F,
                                2,
                                12,
                                2)
                        .texOffs(211, 95)
                        .addBox(
                                -7.0F,
                                -48.0F,
                                -7.0F,
                                14,
                                2,
                                14)
                        .texOffs(0, 64)
                        .addBox(
                                1.0F,
                                -49.0F,
                                127.0F,
                                7,
                                1,
                                1),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        body.addOrReplaceChild(
                "tail_fin",
                CubeListBuilder.create()
                        .texOffs(96, 218)
                        .addBox(
                                -1.0F,
                                -34.0F,
                                -13.0F,
                                2,
                                35,
                                18),
                PartPose.offsetAndRotation(
                        0.0F,
                        -13.0F,
                        119.0F,
                        -0.5236F,
                        0.0F,
                        0.0F));
        body.addOrReplaceChild(
                "nose_upper",
                CubeListBuilder.create()
                        .texOffs(156, 163)
                        .addBox(
                                -8.0F,
                                -13.0F,
                                -23.0F,
                                16,
                                13,
                                24),
                PartPose.offsetAndRotation(
                        0.0F,
                        -18.0F,
                        -28.0F,
                        0.7418F,
                        0.0F,
                        0.0F));
        body.addOrReplaceChild(
                "nose_lower",
                CubeListBuilder.create()
                        .texOffs(0, 103)
                        .addBox(
                                -11.0F,
                                -15.0F,
                                -28.0F,
                                22,
                                18,
                                29),
                PartPose.offsetAndRotation(
                        0.0F,
                        -15.0F,
                        -10.0F,
                        0.0436F,
                        0.0F,
                        0.0F));
        body.addOrReplaceChild(
                "right_door",
                CubeListBuilder.create()
                        .texOffs(0, 193)
                        .addBox(
                                -1.0F,
                                -6.0F,
                                -1.0F,
                                1,
                                20,
                                30),
                PartPose.offset(14.0F, -20.0F, 3.0F));
        body.addOrReplaceChild(
                "left_door",
                CubeListBuilder.create()
                        .texOffs(179, 90)
                        .addBox(
                                -1.0F,
                                -6.0F,
                                -1.0F,
                                1,
                                20,
                                30),
                PartPose.offset(-13.0F, -20.0F, 3.0F));

        PartDefinition rotor = body.addOrReplaceChild(
                "rotor",
                CubeListBuilder.create()
                        .texOffs(0, 42)
                        .addBox(
                                -50.0F,
                                -2.0F,
                                -3.0F,
                                100,
                                0,
                                6)
                        .texOffs(0, 24)
                        .addBox(
                                -3.0F,
                                -2.0F,
                                -50.0F,
                                6,
                                0,
                                100),
                PartPose.offset(0.0F, -39.0F, 0.0F));
        rotor.addOrReplaceChild(
                "cross_a",
                CubeListBuilder.create()
                        .texOffs(0, 18)
                        .addBox(
                                -50.0F,
                                -2.0F,
                                -3.0F,
                                100,
                                0,
                                6)
                        .texOffs(0, 12)
                        .addBox(
                                -3.0F,
                                -2.0F,
                                -50.0F,
                                6,
                                0,
                                100),
                PartPose.rotation(0.0F, 0.1745F, 0.0F));
        rotor.addOrReplaceChild(
                "cross_b",
                CubeListBuilder.create()
                        .texOffs(0, 30)
                        .addBox(
                                -50.0F,
                                -2.0F,
                                -3.0F,
                                100,
                                0,
                                6)
                        .texOffs(0, 0)
                        .addBox(
                                -3.0F,
                                -2.0F,
                                -50.0F,
                                6,
                                0,
                                100),
                PartPose.rotation(0.0F, -0.1745F, 0.0F));

        PartDefinition rearRotor = body.addOrReplaceChild(
                "rear_rotor",
                CubeListBuilder.create()
                        .texOffs(73, 74)
                        .addBox(
                                5.0F,
                                -2.0F,
                                -1.0F,
                                0,
                                3,
                                10),
                PartPose.offset(2.0F, -48.0F, 128.0F));
        rearRotor.addOrReplaceChild(
                "blade_b",
                CubeListBuilder.create()
                        .texOffs(73, 65)
                        .addBox(
                                1.0F,
                                -2.0F,
                                -1.0F,
                                0,
                                3,
                                10),
                PartPose.offsetAndRotation(
                        4.0F,
                        0.0F,
                        0.0F,
                        2.0944F,
                        0.0F,
                        0.0F));
        rearRotor.addOrReplaceChild(
                "blade_c",
                CubeListBuilder.create()
                        .texOffs(73, 62)
                        .addBox(
                                1.0F,
                                -2.0F,
                                -1.0F,
                                0,
                                3,
                                10),
                PartPose.offsetAndRotation(
                        4.0F,
                        0.0F,
                        0.0F,
                        -2.0944F,
                        0.0F,
                        0.0F));

        addMissile(body, "missile_1", 0.0F, true, 156, 174);
        addMissile(body, "missile_2", -5.0F, true, 170, 95);
        addMissile(body, "missile_3", -10.0F, true, 170, 84);
        addMissile(body, "missile_4", 0.0F, false, 159, 59);
        addMissile(body, "missile_5", 5.0F, false, 67, 150);
        addMissile(body, "missile_6", 10.0F, false, 73, 103);
        return LayerDefinition.create(mesh, 512, 512);
    }

    private static void addMissile(
            PartDefinition body,
            String name,
            float xOffset,
            boolean right,
            int u,
            int v) {
        float x = right ? 32.0F : -37.0F;
        float coreX = right ? 32.5F : -36.5F;
        body.addOrReplaceChild(
                name,
                CubeListBuilder.create()
                        .texOffs(u, v)
                        .addBox(
                                x,
                                -10.0F,
                                -25.0F,
                                4,
                                4,
                                7)
                        .texOffs(right ? 97 : 0, 120)
                        .addBox(
                                coreX,
                                -9.5F,
                                -25.5F,
                                3,
                                3,
                                9)
                        .texOffs(right ? 88 : 22, 60)
                        .addBox(
                                x,
                                -10.0F,
                                -17.0F,
                                4,
                                4,
                                2),
                PartPose.offset(xOffset, 0.0F, 0.0F));
    }

    @Override
    public void setupAnim(
            BadCompanyHelicopterEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        rotor.yRot = ageInTicks * 2.0F;
        rearRotor.xRot = ageInTicks * 2.0F;
        for (int i = 0; i < missiles.length; i++) {
            missiles[i].visible =
                    entity.missiles() > missiles.length - 1 - i;
        }
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
