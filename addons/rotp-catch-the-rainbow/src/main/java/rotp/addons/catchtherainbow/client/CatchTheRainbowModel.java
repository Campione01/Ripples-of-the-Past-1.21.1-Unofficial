package rotp.addons.catchtherainbow.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Native 1.21.1 translation of the donor Catch the Rainbow model. */
public final class CatchTheRainbowModel extends StandEntityModel<
        CatchTheRainbowEntity,
        StandEntityRenderState> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    CatchTheRainbowAddon.id("catch_the_rainbow"),
                    "donor");

    public CatchTheRainbowModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
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
        head.addOrReplaceChild(
                "head_rot",
                box(0, 0).addBox(
                        -4.0F, -8.0F, -4.0F,
                        8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        head.addOrReplaceChild(
                "spine_r1",
                box(59, 8)
                        .addBox(
                                -2.0F, 4.0F, -2.0F,
                                1.0F, 0.0F, 1.0F)
                        .texOffs(59, 8)
                        .addBox(
                                -3.0F, 3.0F, 0.0F,
                                1.0F, 0.0F, 1.0F)
                        .texOffs(59, 8)
                        .addBox(
                                2.0F, 2.0F, 0.0F,
                                1.0F, 0.0F, 1.0F),
                PartPose.rotation(0.0F, -0.5672F, 0.0F));

        torsoBend.addOrReplaceChild(
                "torso_no_arms",
                box(0, 64).addBox(
                        -4.0F, 0.0F, -2.0F,
                        8.0F, 12.0F, 4.0F),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        empty(torso, "torso_lower", PartPose.ZERO);

        PartDefinition leftArmXRot = empty(
                torsoBend,
                "left_arm_xrot",
                PartPose.offset(6.0F, -10.0F, 0.0F));
        PartDefinition leftArm = leftArmXRot.addOrReplaceChild(
                "left_arm",
                box(32, 108).addBox(
                        -2.0F, -2.0F, -2.0F,
                        4.0F, 6.0F, 4.0F),
                PartPose.ZERO);
        leftArm.addOrReplaceChild(
                "left_arm_bend",
                box(32, 102)
                        .mirror(true)
                        .addBox(
                                -1.5F, -1.5F, -1.5F,
                                3.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.125F))
                        .mirror(false)
                        .texOffs(32, 118)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition rightArmXRot = empty(
                torsoBend,
                "right_arm_xrot",
                PartPose.offset(-6.0F, -10.0F, 0.0F));
        PartDefinition rightArm = rightArmXRot.addOrReplaceChild(
                "right_arm",
                box(0, 108).addBox(
                        -2.0F, -2.0F, -2.0F,
                        4.0F, 6.0F, 4.0F),
                PartPose.ZERO);
        rightArm.addOrReplaceChild(
                "right_arm_bend",
                box(0, 102)
                        .addBox(
                                -1.5F, -1.5F, -1.5F,
                                3.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.125F))
                        .texOffs(0, 118)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition leftLegXRot = empty(
                body,
                "left_leg_xrot",
                PartPose.offset(2.0F, 12.0F, 0.0F));
        PartDefinition leftLeg = leftLegXRot.addOrReplaceChild(
                "left_leg",
                box(96, 108).addBox(
                        -2.0F, 0.0F, -2.0F,
                        4.0F, 6.0F, 4.0F),
                PartPose.ZERO);
        leftLeg.addOrReplaceChild(
                "left_leg_bend",
                box(96, 102)
                        .mirror(true)
                        .addBox(
                                -1.5F, -1.5F, -1.5F,
                                3.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.125F))
                        .mirror(false)
                        .texOffs(96, 118)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition rightLegXRot = empty(
                body,
                "right_leg_xrot",
                PartPose.offset(-2.0F, 12.0F, 0.0F));
        PartDefinition rightLeg = rightLegXRot.addOrReplaceChild(
                "right_leg",
                box(64, 108).addBox(
                        -2.0F, 0.0F, -2.0F,
                        4.0F, 6.0F, 4.0F),
                PartPose.ZERO);
        rightLeg.addOrReplaceChild(
                "right_leg_bend",
                box(64, 102)
                        .addBox(
                                -1.5F, -1.5F, -1.5F,
                                3.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.125F))
                        .texOffs(64, 118)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
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
