package rotp.addons.harvest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.entity.HarvestMemberEntity;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

public final class HarvestMemberModel
        extends EntityModel<HarvestMemberEntity>
        implements ArmedModel {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    HarvestAddon.id("harvest_member"), "main");

    private final ModelPart defaultBody;
    private final ModelPart rabbit;
    private final ModelPart sukunaBody;
    private final ModelPart defaultRightArm;
    private final ModelPart defaultRightArmUpper;
    private final ModelPart defaultLeftArm;
    private final ModelPart defaultLeftArmUpper;
    private final ModelPart defaultRightLeg;
    private final ModelPart defaultLeftLeg;
    private final ModelPart tail;
    private final ModelPart rabbitUpper;
    private final ModelPart rabbitRightArm;
    private final ModelPart rabbitLeftArm;
    private final ModelPart rabbitRightLeg;
    private final ModelPart rabbitLeftLeg;
    private final ModelPart sukunaRightArm;
    private final ModelPart sukunaRightArmUpper;
    private final ModelPart sukunaLeftArm;
    private final ModelPart sukunaLeftArmUpper;
    private final ModelPart sukunaRightLeg;
    private final ModelPart sukunaLeftLeg;
    private boolean rabbitActive;
    private boolean sukunaActive;

    public HarvestMemberModel(ModelPart root) {
        defaultBody = root.getChild("default_body");
        rabbit = root.getChild("rabbit");
        sukunaBody = root.getChild("sukuna_body");
        defaultRightArm = defaultBody.getChild("right_arm");
        defaultRightArmUpper =
                defaultBody.getChild("right_arm_upper");
        defaultLeftArm = defaultBody.getChild("left_arm");
        defaultLeftArmUpper =
                defaultBody.getChild("left_arm_upper");
        defaultRightLeg = defaultBody.getChild("right_leg");
        defaultLeftLeg = defaultBody.getChild("left_leg");
        tail = defaultBody.getChild("tail");
        rabbitUpper = rabbit.getChild("upper");
        rabbitRightArm = rabbitUpper.getChild("right_arm");
        rabbitLeftArm = rabbitUpper.getChild("left_arm");
        rabbitRightLeg = rabbit.getChild("right_leg");
        rabbitLeftLeg = rabbit.getChild("left_leg");
        sukunaRightArm = sukunaBody.getChild("right_arm");
        sukunaRightArmUpper =
                sukunaBody.getChild("right_arm_upper");
        sukunaLeftArm = sukunaBody.getChild("left_arm");
        sukunaLeftArmUpper =
                sukunaBody.getChild("left_arm_upper");
        sukunaRightLeg = sukunaBody.getChild("right_leg");
        sukunaLeftLeg = sukunaBody.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        addDefaultBody(root);
        addRabbit(root);
        addSukunaBody(root);
        return LayerDefinition.create(mesh, 256, 256);
    }

    private static void addDefaultBody(PartDefinition root) {
        CubeListBuilder torso = CubeListBuilder.create()
                .texOffs(uv(0), uv(0))
                .addBox(-9.0F, -1.0F, -6.0F, 18, 11, 12)
                .texOffs(uv(0), uv(44))
                .addBox(-7.0F, 10.0F, -5.0F, 14, 6, 10)
                .texOffs(uv(50), uv(38))
                .addBox(-9.0F, -3.0F, -6.0F, 8, 2, 1)
                .texOffs(uv(68), uv(38))
                .addBox(3.0F, -5.0F, -6.0F, 6, 2, 1)
                .texOffs(uv(74), uv(18))
                .addBox(5.0F, -7.0F, -6.0F, 4, 2, 1)
                .texOffs(uv(68), uv(41))
                .addBox(-9.0F, -7.0F, -6.0F, 4, 2, 1)
                .texOffs(uv(60), uv(18))
                .addBox(-9.0F, -5.0F, -6.0F, 6, 2, 1)
                .texOffs(uv(50), uv(41))
                .addBox(1.0F, -3.0F, -6.0F, 8, 2, 1)
                .texOffs(uv(60), uv(0))
                .addBox(-9.0F, -8.0F, -5.0F, 1, 7, 11)
                .texOffs(uv(0), uv(70))
                .addBox(8.0F, -8.0F, -5.0F, 1, 7, 11)
                .texOffs(uv(47), uv(43))
                .addBox(-8.0F, -3.0F, -4.0F, 16, 2, 10)
                .texOffs(uv(67), uv(110))
                .addBox(-7.0F, -6.0F, -4.0F, 14, 3, 10)
                .texOffs(uv(77), uv(86))
                .addBox(-5.0F, -8.0F, -4.0F, 10, 2, 10)
                .texOffs(uv(37), uv(28))
                .addBox(-1.0F, 1.5F, -11.0F, 2, 2, 5)
                .texOffs(184, 38)
                .addBox(
                        -3.0F,
                        -0.5F,
                        -6.35F,
                        6,
                        3,
                        1,
                        new CubeDeformation(0.05F));
        PartDefinition body = root.addOrReplaceChild(
                "default_body", torso, PartPose.ZERO);
        body.addOrReplaceChild(
                "right_arm",
                limb(24, 77, -3.0F, true),
                PartPose.offset(-10.0F, 9.0F, 0.0F));
        body.addOrReplaceChild(
                "right_arm_upper",
                limb(40, 86, -3.0F, true),
                PartPose.offset(-10.0F, -1.0F, 0.0F));
        body.addOrReplaceChild(
                "left_arm",
                limb(84, 0, -1.0F, false),
                PartPose.offset(10.0F, 9.0F, 0.0F));
        body.addOrReplaceChild(
                "left_arm_upper",
                limb(56, 86, -1.0F, false),
                PartPose.offset(10.0F, -1.0F, 0.0F));
        body.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(uv(54), uv(70))
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4)
                        .texOffs(uv(0), uv(112))
                        .addBox(
                                -2.05F,
                                0.0F,
                                -2.0F,
                                4,
                                12,
                                4,
                                new CubeDeformation(0.25F)),
                PartPose.offset(-6.9F, 12.0F, 0.0F));
        body.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(uv(70), uv(70))
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4)
                        .texOffs(uv(0), uv(92))
                        .addBox(
                                -2.0F,
                                0.0F,
                                -2.0F,
                                4,
                                12,
                                4,
                                new CubeDeformation(0.25F)),
                PartPose.offset(6.9F, 12.0F, 0.0F));
        body.addOrReplaceChild(
                "tail",
                CubeListBuilder.create()
                        .texOffs(uv(84), uv(12))
                        .addBox(-3.0F, -2.0F, 14.0F, 6, 4, 3)
                        .texOffs(uv(48), uv(55))
                        .addBox(-4.0F, 2.0F, -1.0F, 8, 2, 13)
                        .texOffs(uv(0), uv(23))
                        .addBox(-5.0F, -4.0F, -1.0F, 10, 6, 15)
                        .texOffs(uv(50), uv(23))
                        .addBox(-4.0F, -6.0F, -1.0F, 8, 2, 13),
                PartPose.offset(0.0F, 14.0F, 6.0F));
    }

    private static CubeListBuilder limb(
            int u, int v, float x, boolean right) {
        return CubeListBuilder.create()
                .texOffs(uv(u), uv(v))
                .addBox(x, -2.0F, -2.0F, 4, 8, 4)
                .texOffs(uv(right ? 19 : 38), uv(right ? 116 : 102))
                .addBox(
                        x,
                        -2.0F,
                        -2.0F,
                        4,
                        8,
                        4,
                        new CubeDeformation(0.25F));
    }

    private static void addRabbit(PartDefinition root) {
        PartDefinition rabbit = root.addOrReplaceChild(
                "rabbit",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition head = rabbit.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(uv(48), uv(34))
                        .addBox(-4.0F, 5.0F, -4.0F, 8, 2, 8)
                        .texOffs(uv(46), uv(47))
                        .addBox(-4.5F, -3.0F, -4.5F, 9, 8, 9)
                        .texOffs(uv(46), uv(47))
                        .addBox(-5.0F, 0.0F, -6.0F, 2, 2, 2)
                        .texOffs(uv(74), uv(47))
                        .mirror()
                        .addBox(3.0F, 0.0F, -6.0F, 2, 2, 2)
                        .texOffs(202, 38)
                        .addBox(
                                -2.0F,
                                -1.0F,
                                -4.65F,
                                4,
                                3,
                                1,
                                new CubeDeformation(0.03F)),
                PartPose.offset(0.0F, -21.0F, 0.0F));
        head.addOrReplaceChild(
                "left_ear",
                CubeListBuilder.create()
                        .texOffs(uv(73), uv(27))
                        .addBox(-2.75F, -8.75F, -0.5F, 4, 7, 1)
                        .texOffs(uv(74), uv(24))
                        .addBox(-2.25F, -9.75F, -0.5F, 3, 1, 1)
                        .texOffs(uv(46), uv(37))
                        .addBox(-2.25F, -1.75F, -0.5F, 3, 2, 1),
                PartPose.offsetAndRotation(
                        -3.0F, -3.25F, 0.0F, 0.0F, 0.0F, -0.2182F));
        head.addOrReplaceChild(
                "right_ear",
                CubeListBuilder.create()
                        .texOffs(uv(74), uv(37))
                        .mirror()
                        .addBox(-0.75F, -1.75F, -0.5F, 3, 2, 1)
                        .texOffs(uv(45), uv(27))
                        .addBox(-1.25F, -8.75F, -0.5F, 4, 7, 1)
                        .texOffs(uv(46), uv(24))
                        .addBox(-0.75F, -9.75F, -0.5F, 3, 1, 1),
                PartPose.offsetAndRotation(
                        3.0F, -3.25F, 0.0F, 0.0F, 0.0F, 0.2182F));
        rabbit.addOrReplaceChild(
                "body",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, -15.0F, 0.0F));
        PartDefinition upper = rabbit.addOrReplaceChild(
                "upper",
                CubeListBuilder.create()
                        .texOffs(uv(52), uv(68))
                        .addBox(-4.0F, -11.0F, -2.0F, 8, 2, 4)
                        .texOffs(uv(50), uv(77))
                        .addBox(-4.5F, -9.0F, -3.0F, 9, 7, 5),
                PartPose.offset(0.0F, -3.0F, 0.0F));
        upper.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create()
                        .texOffs(uv(78), uv(68))
                        .addBox(-2.0F, -0.5F, -1.0F, 3, 8, 3),
                PartPose.offset(6.0F, -10.0F, 0.0F));
        upper.addOrReplaceChild(
                "right_arm",
                CubeListBuilder.create()
                        .texOffs(uv(38), uv(68))
                        .addBox(-1.0F, -0.5F, -1.0F, 3, 8, 3),
                PartPose.offset(-6.0F, -10.0F, 0.0F));
        rabbit.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(uv(65), uv(91))
                        .addBox(-1.65F, -2.0F, -2.0F, 4, 5, 4),
                PartPose.offset(1.9F, -3.0F, 0.0F));
        rabbit.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(uv(47), uv(91))
                        .addBox(-2.35F, -2.0F, -2.0F, 4, 5, 4),
                PartPose.offset(-1.9F, -3.0F, 0.0F));
    }

    private static void addSukunaBody(PartDefinition root) {
        PartDefinition body = root.addOrReplaceChild(
                "sukuna_body",
                CubeListBuilder.create()
                        .texOffs(uv(0), uv(0))
                        .addBox(-9.0F, -8.0F, -6.0F, 18, 18, 12)
                        .texOffs(uv(0), uv(30))
                        .addBox(
                                -9.0F,
                                -8.0F,
                                -6.0F,
                                18,
                                18,
                                12,
                                new CubeDeformation(0.25F))
                        .texOffs(uv(0), uv(60))
                        .addBox(-8.0F, 10.0F, -5.0F, 16, 6, 10)
                        .texOffs(216, 38)
                        .addBox(
                                -3.0F,
                                -0.5F,
                                -6.35F,
                                6,
                                3,
                                1,
                                new CubeDeformation(0.05F)),
                PartPose.ZERO);
        body.addOrReplaceChild(
                "right_arm",
                skinLimb(52, 64, -3.0F, 68, 64),
                PartPose.offset(-10.0F, 9.0F, 0.0F));
        body.addOrReplaceChild(
                "right_arm_upper",
                skinLimb(0, 76, -3.0F, 76, 0),
                PartPose.offset(-10.0F, -1.0F, 0.0F));
        body.addOrReplaceChild(
                "left_arm",
                skinLimb(76, 12, -1.0F, 16, 76),
                PartPose.offset(10.0F, 9.0F, 0.0F));
        body.addOrReplaceChild(
                "left_arm_upper",
                skinLimb(76, 24, -1.0F, 32, 76),
                PartPose.offset(10.0F, -1.0F, 0.0F));
        body.addOrReplaceChild(
                "right_leg",
                skinLeg(60, 0, 60, 48, -2.05F),
                PartPose.offset(-6.9F, 12.0F, 0.0F));
        body.addOrReplaceChild(
                "left_leg",
                skinLeg(60, 16, 60, 32, -2.0F),
                PartPose.offset(6.9F, 12.0F, 0.0F));
    }

    private static CubeListBuilder skinLimb(
            int u, int v, float x, int outerU, int outerV) {
        return CubeListBuilder.create()
                .texOffs(uv(u), uv(v))
                .addBox(x, -2.0F, -2.0F, 4, 8, 4)
                .texOffs(uv(outerU), uv(outerV))
                .addBox(
                        x,
                        -2.0F,
                        -2.0F,
                        4,
                        8,
                        4,
                        new CubeDeformation(0.25F));
    }

    private static CubeListBuilder skinLeg(
            int u, int v, int outerU, int outerV, float outerX) {
        return CubeListBuilder.create()
                .texOffs(uv(u), uv(v))
                .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4)
                .texOffs(uv(outerU), uv(outerV))
                .addBox(
                        outerX,
                        0.0F,
                        -2.0F,
                        4,
                        12,
                        4,
                        new CubeDeformation(0.25F));
    }

    @Override
    public void setupAnim(
            HarvestMemberEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        String skin = entity.getStandSkin()
                .map(id -> id.getPath())
                .orElse("");
        boolean rabbitSkin = "rabbit".equals(skin);
        boolean sukunaSkin = skin.equals("ryomen_sukuna")
                || skin.equals("sukuna_itadori")
                || skin.equals("sukuna_megumi");
        rabbitActive = rabbitSkin;
        sukunaActive = sukunaSkin;
        rabbit.visible = rabbitSkin;
        sukunaBody.visible = sukunaSkin;
        defaultBody.visible = !rabbitSkin && !sukunaSkin;

        float rightLeg = Mth.cos(limbSwing)
                * 1.4F * limbSwingAmount;
        float leftLeg = Mth.cos(limbSwing + Mth.PI)
                * 1.4F * limbSwingAmount;
        float rightArm = Mth.cos(limbSwing + Mth.PI)
                * 1.4F * limbSwingAmount;
        float leftArm = Mth.cos(limbSwing)
                * 1.4F * limbSwingAmount;
        defaultRightLeg.xRot = sukunaRightLeg.xRot =
                rabbitRightLeg.xRot = rightLeg;
        defaultLeftLeg.xRot = sukunaLeftLeg.xRot =
                rabbitLeftLeg.xRot = leftLeg;
        defaultRightArm.xRot = defaultRightArmUpper.xRot =
                sukunaRightArm.xRot = sukunaRightArmUpper.xRot =
                        rabbitRightArm.xRot = rightArm;
        defaultLeftArm.xRot = defaultLeftArmUpper.xRot =
                sukunaLeftArm.xRot = sukunaLeftArmUpper.xRot =
                        rabbitLeftArm.xRot = leftArm;
        tail.yRot = Mth.sin(ageInTicks * 0.1F) * 0.1F;

        if (entity.attackTicks() > 0) {
            defaultRightArmUpper.xRot =
                    sukunaRightArmUpper.xRot =
                            rabbitRightArm.xRot = -1.35F;
        }
    }

    @Override
    public void translateToHand(
            HumanoidArm side, PoseStack poseStack) {
        if (rabbitActive) {
            rabbit.translateAndRotate(poseStack);
            rabbitUpper.translateAndRotate(poseStack);
            ModelPart arm = side == HumanoidArm.LEFT
                    ? rabbitLeftArm
                    : rabbitRightArm;
            arm.translateAndRotate(poseStack);
            return;
        }
        ModelPart body = sukunaActive
                ? sukunaBody
                : defaultBody;
        ModelPart arm = side == HumanoidArm.LEFT
                ? (sukunaActive
                        ? sukunaLeftArmUpper
                        : defaultLeftArmUpper)
                : (sukunaActive
                        ? sukunaRightArmUpper
                        : defaultRightArmUpper);
        body.translateAndRotate(poseStack);
        arm.translateAndRotate(poseStack);
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        defaultBody.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
        rabbit.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
        sukunaBody.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }

    private static int uv(int coordinate) {
        return coordinate * 2;
    }
}
