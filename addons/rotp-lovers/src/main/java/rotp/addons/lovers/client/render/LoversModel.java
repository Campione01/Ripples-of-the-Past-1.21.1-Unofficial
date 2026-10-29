package rotp.addons.lovers.client.render;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;

import rotp.addons.lovers.entity.LoversStandEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class LoversModel extends StandEntityModel<
        LoversStandEntity, StandEntityRenderState> {
    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 128;

    public LoversModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition standPos = root.addOrReplaceChild(
                "stand_pos",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition bodyRot = standPos.addOrReplaceChild(
                "body_rot",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, -24.0F, 0.0F));
        PartDefinition body = bodyRot.addOrReplaceChild(
                "body",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition torso = body.addOrReplaceChild(
                "torso",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        PartDefinition torsoBend = torso.addOrReplaceChild(
                "torso_bend",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 6.0F, 0.0F));
        PartDefinition head = torsoBend.addOrReplaceChild(
                "head",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, -6.0F, 0.0F));
        head.addOrReplaceChild(
                "head_rot",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -4.0F, -8.0F, -4.0F,
                                8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        torsoBend.addOrReplaceChild(
                "torso_no_arms",
                CubeListBuilder.create()
                        .texOffs(0, 64)
                        .addBox(
                                -4.0F, -6.0F, -2.0F,
                                8.0F, 12.0F, 4.0F),
                PartPose.ZERO);
        torso.addOrReplaceChild(
                "torso_lower",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        addArm(torsoBend, true);
        addArm(torsoBend, false);
        addLeg(body, true);
        addLeg(body, false);

        return LayerDefinition.create(
                mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addArm(
            PartDefinition torso, boolean left) {
        String side = left ? "left" : "right";
        float x = left ? 6.0F : -6.0F;
        int upperU = left ? 32 : 0;
        PartDefinition armXRot = torso.addOrReplaceChild(
                side + "_arm_xrot",
                CubeListBuilder.create(),
                PartPose.offset(x, -4.0F, 0.0F));
        PartDefinition arm = armXRot.addOrReplaceChild(
                side + "_arm",
                CubeListBuilder.create()
                        .texOffs(upperU, 108)
                        .addBox(
                                -2.0F, -2.0F, -2.0F,
                                4.0F, 6.0F, 4.0F)
                        .texOffs(upperU, 102)
                        .mirror(left)
                        .addBox(
                                -1.5F, 2.5F, -1.5F,
                                3.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.125F))
                        .mirror(false),
                PartPose.ZERO);
        PartDefinition armBend = arm.addOrReplaceChild(
                side + "_arm_bend",
                CubeListBuilder.create()
                        .texOffs(upperU, 118)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        armBend.addOrReplaceChild(
                side + "_item",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 3.75F, -2.0F));
    }

    private static void addLeg(
            PartDefinition body, boolean left) {
        String side = left ? "left" : "right";
        float x = left ? 2.0F : -2.0F;
        int upperU = left ? 96 : 64;
        PartDefinition legXRot = body.addOrReplaceChild(
                side + "_leg_xrot",
                CubeListBuilder.create(),
                PartPose.offset(x, 0.0F, 0.0F));
        PartDefinition leg = legXRot.addOrReplaceChild(
                side + "_leg",
                CubeListBuilder.create()
                        .texOffs(upperU, 108)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F)
                        .texOffs(upperU, 102)
                        .mirror(left)
                        .addBox(
                                -1.5F, 4.5F, -1.5F,
                                3.0F, 3.0F, 3.0F,
                                new CubeDeformation(-0.125F))
                        .mirror(false),
                PartPose.ZERO);
        leg.addOrReplaceChild(
                side + "_leg_bend",
                CubeListBuilder.create()
                        .texOffs(upperU, 118)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(-0.001F)),
                PartPose.offset(0.0F, 6.0F, 0.0F));
    }
}
