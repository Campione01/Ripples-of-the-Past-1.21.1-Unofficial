package rotp.addons.scr.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Native 1.21.1 translation of the registered row-80 Blockbench model.
 */
public final class SilverChariotRequiemLegacyModel {
    public static final String MARKER = "legacy_row80_model_marker";

    private SilverChariotRequiemLegacyModel() {}

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition standPos = empty(
                root, "stand_pos", PartPose.ZERO);
        PartDefinition bodyRot = empty(
                standPos, "body_rot", PartPose.ZERO);
        PartDefinition body = empty(
                bodyRot, "body", PartPose.ZERO);
        PartDefinition torso = empty(
                body, "torso", PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition torsoBend = empty(
                torso, "torso_bend", PartPose.ZERO);

        PartDefinition head = empty(
                torsoBend,
                "head",
                PartPose.offset(0.0F, -12.0F, 0.0F));
        PartDefinition headRot = head.addOrReplaceChild(
                "head_rot",
                box(22, 42).addBox(
                        -4.0F, -7.0F, -4.0F,
                        8.0F, 7.0F, 8.0F),
                PartPose.ZERO);
        PartDefinition headCrown = headRot.addOrReplaceChild(
                "legacy_head_crown",
                box(22, 31)
                        .addBox(
                                -2.75F, -3.55F, -4.75F,
                                14.0F, 1.0F, 10.0F)
                        .texOffs(46, 49)
                        .addBox(
                                -0.75F, -4.55F, -3.75F,
                                10.0F, 1.0F, 8.0F)
                        .texOffs(22, 57)
                        .addBox(
                                1.25F, -6.55F, -3.75F,
                                6.0F, 2.0F, 8.0F)
                        .texOffs(22, 67)
                        .addBox(
                                -2.75F, -5.55F, -4.75F,
                                1.0F, 2.0F, 10.0F),
                PartPose.offsetAndRotation(
                        -4.25F, -4.45F, -0.25F,
                        0.0F, 0.0F, 0.1309F));
        headCrown.addOrReplaceChild(
                "legacy_head_spike",
                box(40, 58).addBox(
                        -33.0F, -16.0F, -5.0F,
                        1.0F, 5.0F, 10.0F),
                PartPose.offsetAndRotation(
                        4.25F, 28.45F, 0.25F,
                        0.0F, 0.0F, 1.0472F));

        PartDefinition torsoNoArms = torsoBend.addOrReplaceChild(
                "torso_no_arms",
                box(60, 75)
                        .addBox(
                                -1.5F, 0.5F, -2.5F,
                                3.0F, 3.0F, 5.0F,
                                new CubeDeformation(-0.6F))
                        .texOffs(87, 33)
                        .addBox(
                                -0.5F, -0.1F, -0.5F,
                                1.0F, 12.0F, 1.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(84, 57)
                        .addBox(
                                -2.5F, -0.1F, -2.0F,
                                1.0F, 2.0F, 4.0F,
                                new CubeDeformation(-0.2F))
                        .texOffs(34, 69)
                        .addBox(
                                1.5F, -0.1F, -2.0F,
                                1.0F, 2.0F, 4.0F,
                                new CubeDeformation(-0.2F))
                        .texOffs(56, 79)
                        .addBox(
                                -3.0F, 10.0F, -2.0F,
                                6.0F, 2.0F, 4.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(60, 31)
                        .addBox(
                                -4.0F, 1.0F, -1.5F,
                                8.0F, 5.0F, 3.0F)
                        .texOffs(52, 58)
                        .addBox(
                                -4.0F, 0.0F, -2.0F,
                                8.0F, 6.0F, 4.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(60, 35)
                        .addBox(
                                -3.5F, 0.75F, -2.5F,
                                7.0F, 2.0F, 1.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(46, 44)
                        .addBox(
                                -3.5F, 2.9F, -2.5F,
                                1.0F, 1.0F, 1.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(46, 42)
                        .addBox(
                                2.5F, 2.9F, -2.5F,
                                1.0F, 1.0F, 1.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(26, 39)
                        .addBox(
                                2.5F, 2.9F, 1.5F,
                                1.0F, 1.0F, 1.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(22, 39)
                        .addBox(
                                -3.5F, 2.9F, 1.5F,
                                1.0F, 1.0F, 1.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(69, 34)
                        .addBox(
                                -3.5F, 0.75F, 1.5F,
                                7.0F, 2.0F, 1.0F,
                                new CubeDeformation(0.1F))
                        .texOffs(46, 42)
                        .addBox(
                                -4.0F, 10.0F, -2.0F,
                                8.0F, 2.0F, 4.0F,
                                new CubeDeformation(0.2F)),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        torsoNoArms.addOrReplaceChild(
                "legacy_torso_strap_left",
                box(78, 32).addBox(
                        -2.75F, -18.0F, -0.5F,
                        3.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(
                        0.0F, 24.0F, 0.0F,
                        0.0F, 0.0F, 0.1745F));
        torsoNoArms.addOrReplaceChild(
                "legacy_torso_strap_right",
                box(52, 33).addBox(
                        -0.25F, -18.0F, -0.5F,
                        3.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(
                        0.0F, 24.0F, 0.0F,
                        0.0F, 0.0F, -0.1745F));
        empty(torso, "torso_lower", PartPose.ZERO);

        PartDefinition leftArmXRot = empty(
                torsoBend,
                "left_arm_xrot",
                PartPose.offset(6.0F, -10.0F, 0.0F));
        PartDefinition leftArm = leftArmXRot.addOrReplaceChild(
                "left_arm",
                box(76, 58).addBox(
                        -2.0F, -2.0F, -2.0F,
                        3.0F, 6.0F, 4.0F),
                PartPose.ZERO);
        leftArm.addOrReplaceChild(
                "left_arm_bend",
                box(58, 60)
                        .addBox(
                                -1.5F, -1.5F, -1.5F,
                                2.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.1F))
                        .texOffs(38, 83)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                3.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        addLeftShoulder(leftArm);

        PartDefinition rightArmXRot = empty(
                torsoBend,
                "right_arm_xrot",
                PartPose.offset(-6.0F, -10.0F, 0.0F));
        PartDefinition rightArm = rightArmXRot.addOrReplaceChild(
                "right_arm",
                box(78, 68).addBox(
                        -1.0F, -2.0F, -2.0F,
                        3.0F, 6.0F, 4.0F),
                PartPose.ZERO);
        rightArm.addOrReplaceChild(
                "right_arm_bend",
                box(56, 55)
                        .addBox(
                                -0.5F, -1.5F, -1.5F,
                                2.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.1F))
                        .texOffs(52, 85)
                        .addBox(
                                -1.0F, 0.0F, -2.0F,
                                3.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        addRightShoulder(rightArm);

        PartDefinition leftLegXRot = empty(
                body,
                "left_leg_xrot",
                PartPose.offset(1.9F, 12.0F, 0.0F));
        PartDefinition leftLeg = leftLegXRot.addOrReplaceChild(
                "left_leg",
                box(70, 39)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F)
                        .texOffs(85, 49)
                        .addBox(
                                1.85F, 0.0F, -1.0F,
                                1.0F, 4.0F, 2.0F)
                        .texOffs(76, 49)
                        .addBox(
                                1.85F, 1.0F, -2.0F,
                                1.0F, 2.0F, 4.0F),
                PartPose.ZERO);
        leftLeg.addOrReplaceChild(
                "left_leg_bend",
                box(67, 33)
                        .addBox(
                                -1.5F, -1.5F, -1.5F,
                                3.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.1F))
                        .texOffs(22, 79)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition rightLegXRot = empty(
                body,
                "right_leg_xrot",
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        PartDefinition rightLeg = rightLegXRot.addOrReplaceChild(
                "right_leg",
                box(60, 75)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F)
                        .texOffs(66, 86)
                        .addBox(
                                -2.85F, 0.0F, -1.0F,
                                1.0F, 4.0F, 2.0F)
                        .texOffs(34, 69)
                        .addBox(
                                -2.85F, 1.0F, -2.0F,
                                1.0F, 2.0F, 4.0F),
                PartPose.ZERO);
        rightLeg.addOrReplaceChild(
                "right_leg_bend",
                box(69, 32)
                        .addBox(
                                -1.5F, -1.5F, -1.5F,
                                3.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.1F))
                        .texOffs(44, 73)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addLeftShoulder(PartDefinition leftArm) {
        PartDefinition shoulder = leftArm.addOrReplaceChild(
                "legacy_left_shoulder_armor",
                box(80, 88)
                        .addBox(
                                -1.5F, -2.0F, -2.0F,
                                2.0F, 4.0F, 4.0F)
                        .texOffs(86, 55)
                        .addBox(
                                -1.0F, 2.0F, -1.5F,
                                1.0F, 4.0F, 3.0F)
                        .texOffs(26, 37)
                        .addBox(
                                -1.0F, -3.0F, -0.5F,
                                1.0F, 1.0F, 1.0F)
                        .texOffs(66, 88)
                        .addBox(
                                -1.0F, -0.5F, -3.0F,
                                1.0F, 1.0F, 6.0F),
                PartPose.offsetAndRotation(
                        2.0F, 0.0F, 0.0F,
                        0.7854F, 0.0F, 0.0F));
        shoulder.addOrReplaceChild(
                "legacy_left_shoulder_front",
                box(28, 33).addBox(
                        -1.5F, -0.5F, -0.5F,
                        1.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(
                        0.5F, -2.0F, -2.0F,
                        0.7854F, 0.0F, 0.0F));
        shoulder.addOrReplaceChild(
                "legacy_left_shoulder_back",
                box(28, 31).addBox(
                        -1.5F, -0.5F, -0.5F,
                        1.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(
                        0.5F, -2.0F, 2.0F,
                        -0.7854F, 0.0F, 0.0F));
    }

    private static void addRightShoulder(PartDefinition rightArm) {
        PartDefinition shoulder = rightArm.addOrReplaceChild(
                "legacy_right_shoulder_armor",
                box(22, 89)
                        .addBox(
                                0.0F, -2.0F, -2.0F,
                                2.0F, 4.0F, 4.0F)
                        .texOffs(86, 55)
                        .addBox(
                                0.5F, 2.0F, -1.5F,
                                1.0F, 4.0F, 3.0F)
                        .texOffs(22, 37)
                        .addBox(
                                0.5F, -3.0F, -0.5F,
                                1.0F, 1.0F, 1.0F)
                        .texOffs(66, 88)
                        .addBox(
                                0.5F, -0.5F, -3.0F,
                                1.0F, 1.0F, 6.0F),
                PartPose.offsetAndRotation(
                        -2.5F, 0.0F, 0.0F,
                        0.7854F, 0.0F, 0.0F));
        shoulder.addOrReplaceChild(
                "legacy_right_shoulder_front",
                box(22, 33).addBox(
                        0.5F, -0.5F, -0.5F,
                        1.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(
                        0.0F, -2.0F, -2.0F,
                        0.7854F, 0.0F, 0.0F));
        shoulder.addOrReplaceChild(
                "legacy_right_shoulder_back",
                box(22, 31).addBox(
                        0.5F, -0.5F, -0.5F,
                        1.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(
                        0.0F, -2.0F, 2.0F,
                        -0.7854F, 0.0F, 0.0F));
    }

    private static PartDefinition empty(
            PartDefinition parent,
            String name,
            PartPose pose) {
        return parent.addOrReplaceChild(
                name, CubeListBuilder.create(), pose);
    }

    private static CubeListBuilder box(int u, int v) {
        return CubeListBuilder.create().texOffs(u, v);
    }
}
