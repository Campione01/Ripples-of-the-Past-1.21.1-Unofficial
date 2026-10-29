// Made with Blockbench 4.12.4
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class WOU<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "wou"), "main");
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart headpiece;
	private final ModelPart hat;
	private final ModelPart body;
	private final ModelPart rightLeg;
	private final ModelPart rightUpperLeg;
	private final ModelPart rightLegJoint;
	private final ModelPart rightLowerLeg;
	private final ModelPart leftLeg;
	private final ModelPart leftUpperLeg;
	private final ModelPart leftLegJoint;
	private final ModelPart leftLowerLeg;
	private final ModelPart rightArm;
	private final ModelPart shoulderR;
	private final ModelPart rightArmJoint;
	private final ModelPart rightForeArm;
	private final ModelPart stick;
	private final ModelPart leftArm;
	private final ModelPart shoulderL;
	private final ModelPart leftArmJoint;
	private final ModelPart leftForeArm;
	private final ModelPart torso;
	private final ModelPart badges;
	private final ModelPart right_badge;
	private final ModelPart left_badge;
	private final ModelPart skarf;
	private final ModelPart scarfEdge2;
	private final ModelPart bone3;
	private final ModelPart bone4;
	private final ModelPart scarfEdge1;
	private final ModelPart bone;
	private final ModelPart bone2;

	public WOU(ModelPart root) {
		this.root = root.getChild("root");
		this.head = this.root.getChild("head");
		this.headpiece = this.head.getChild("headpiece");
		this.hat = this.head.getChild("hat");
		this.body = this.root.getChild("body");
		this.rightLeg = this.body.getChild("rightLeg");
		this.rightUpperLeg = this.rightLeg.getChild("rightUpperLeg");
		this.rightLegJoint = this.rightLeg.getChild("rightLegJoint");
		this.rightLowerLeg = this.rightLeg.getChild("rightLowerLeg");
		this.leftLeg = this.body.getChild("leftLeg");
		this.leftUpperLeg = this.leftLeg.getChild("leftUpperLeg");
		this.leftLegJoint = this.leftLeg.getChild("leftLegJoint");
		this.leftLowerLeg = this.leftLeg.getChild("leftLowerLeg");
		this.rightArm = this.body.getChild("rightArm");
		this.shoulderR = this.rightArm.getChild("shoulderR");
		this.rightArmJoint = this.rightArm.getChild("rightArmJoint");
		this.rightForeArm = this.rightArm.getChild("rightForeArm");
		this.stick = this.rightForeArm.getChild("stick");
		this.leftArm = this.body.getChild("leftArm");
		this.shoulderL = this.leftArm.getChild("shoulderL");
		this.leftArmJoint = this.leftArm.getChild("leftArmJoint");
		this.leftForeArm = this.leftArm.getChild("leftForeArm");
		this.torso = this.body.getChild("torso");
		this.badges = this.torso.getChild("badges");
		this.right_badge = this.badges.getChild("right_badge");
		this.left_badge = this.badges.getChild("left_badge");
		this.skarf = this.badges.getChild("skarf");
		this.scarfEdge2 = this.skarf.getChild("scarfEdge2");
		this.bone3 = this.scarfEdge2.getChild("bone3");
		this.bone4 = this.bone3.getChild("bone4");
		this.scarfEdge1 = this.skarf.getChild("scarfEdge1");
		this.bone = this.scarfEdge1.getChild("bone");
		this.bone2 = this.bone.getChild("bone2");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(40, 80).addBox(-3.7205F, -7.8991F, -3.5783F, 7.6F, 9.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -25.0F, 0.0F));

		PartDefinition headpiece = head.addOrReplaceChild("headpiece", CubeListBuilder.create().texOffs(15, 60).mirror().addBox(3.25F, -0.9041F, 0.3856F, 1.6F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(14, 43).addBox(-4.85F, -0.9041F, 0.3856F, 1.6F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 61).mirror().addBox(3.52F, -3.6041F, -1.9644F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(0, 44).mirror().addBox(-4.52F, -3.6041F, -1.9644F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(110, 58).mirror().addBox(-2.7F, 2.7059F, -4.2144F, 5.5F, 1.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(57, 41).mirror().addBox(-3.3F, -0.0591F, 3.7856F, 6.6F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(57, 41).mirror().addBox(-3.3F, 0.1409F, 3.7856F, 6.6F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(57, 39).mirror().addBox(-3.3F, 1.0459F, 3.7856F, 6.6F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(57, 43).mirror().addBox(-3.3F, 1.2959F, 3.7856F, 6.6F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(57, 45).mirror().addBox(-3.3F, -0.8541F, 3.7856F, 6.6F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(57, 37).mirror().addBox(-3.3F, -1.1041F, 3.7856F, 6.6F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(64, 78).mirror().addBox(-2.1F, 0.6959F, -3.7644F, 4.0F, 2.5F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0795F, -3.8451F, -0.0139F));

		PartDefinition LEFTEYE_r1 = headpiece.addOrReplaceChild("LEFTEYE_r1", CubeListBuilder.create().texOffs(6, 61).mirror().addBox(0.45F, -6.5F, -4.95F, 1.75F, 1.75F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.35F, 5.2459F, 0.7356F, 0.0F, -0.0611F, 0.0F));

		PartDefinition RIGHTEYE_r1 = headpiece.addOrReplaceChild("RIGHTEYE_r1", CubeListBuilder.create().texOffs(7, 44).mirror().addBox(-2.2F, -6.5F, -4.95F, 1.75F, 1.75F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.45F, 5.2459F, 0.7356F, 0.0F, 0.0611F, 0.0F));

		PartDefinition cube_r1 = headpiece.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(12, 66).mirror().addBox(1.8F, -5.25F, 2.1F, 1.25F, 6.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.55F, 2.0459F, -6.4144F, 0.0F, -0.1833F, 0.0F));

		PartDefinition cube_r2 = headpiece.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(6, 65).mirror().addBox(3.05F, -6.0F, 0.3F, 1.5F, 8.5F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.25F, 2.3459F, -5.2144F, 0.0F, -0.3578F, 0.0F));

		PartDefinition cube_r3 = headpiece.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 65).mirror().addBox(2.55F, -6.0F, -3.7F, 1.5F, 8.5F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.25F, 2.3459F, -5.1144F, 0.0F, -1.405F, 0.0F));

		PartDefinition cube_r4 = headpiece.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(55, 51).mirror().addBox(-32.05F, -0.9F, 3.8F, 8.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(55, 51).mirror().addBox(-32.05F, -1.1F, 3.8F, 8.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(55, 53).mirror().addBox(-32.05F, -2.15F, 3.8F, 8.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(55, 59).mirror().addBox(-32.05F, -1.9F, 3.8F, 8.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(55, 49).mirror().addBox(-32.05F, 0.0F, 3.8F, 8.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(55, 63).mirror().addBox(-32.05F, 0.25F, 3.8F, 8.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.5F, 1.0459F, -27.2644F, 0.0F, 1.5708F, 0.0F));

		PartDefinition cube_r5 = headpiece.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(56, 67).mirror().addBox(22.05F, 0.025F, -0.5F, 7.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(56, 67).mirror().addBox(22.05F, -0.175F, -0.5F, 7.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(56, 75).addBox(22.05F, -1.225F, -0.5F, 7.0F, 0.25F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(56, 55).mirror().addBox(22.05F, -0.975F, -0.5F, 7.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(56, 71).mirror().addBox(22.05F, 0.925F, -0.5F, 7.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(56, 47).mirror().addBox(22.05F, 1.175F, -0.5F, 7.0F, 0.25F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.8F, 0.1209F, -24.2644F, 0.0F, -1.5708F, 0.0F));

		PartDefinition cube_r6 = headpiece.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(0, 49).mirror().addBox(-4.05F, -6.0F, -3.7F, 1.5F, 8.5F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.25F, 2.3459F, -5.3144F, 0.0F, 1.405F, 0.0F));

		PartDefinition cube_r7 = headpiece.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(6, 49).mirror().addBox(-4.55F, -6.0F, 0.3F, 1.5F, 8.5F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.3F, 2.3459F, -5.3644F, 0.0F, 0.3578F, 0.0F));

		PartDefinition cube_r8 = headpiece.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(12, 50).mirror().addBox(-3.05F, -5.25F, 0.6F, 1.25F, 6.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.45F, 2.0459F, -4.9144F, 0.0F, 0.1833F, 0.0F));

		PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-4.6606F, -0.5175F, -4.4767F, 9.3F, 1.5F, 9.3F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(0, 0).mirror().addBox(-5.4606F, 0.9816F, -5.2767F, 10.9F, 1.0F, 10.9F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(62, 5).mirror().addBox(-4.5606F, -1.7675F, -4.3767F, 9.1F, 1.25F, 9.1F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(34, 0).mirror().addBox(-4.4106F, -3.0175F, -4.2267F, 8.8F, 1.25F, 8.8F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(26, 36).mirror().addBox(-3.6606F, -3.5175F, -3.4767F, 7.3F, 1.0F, 7.3F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0901F, -7.3816F, 0.2485F));

		PartDefinition cube_r9 = hat.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(23, 12).mirror().addBox(-5.5795F, -0.3931F, -0.95F, 11.0F, 1.0F, 1.9F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0894F, 1.2747F, 6.0237F, 0.0873F, 0.0F, 0.0F));

		PartDefinition cube_r10 = hat.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(63, 1).mirror().addBox(-5.4795F, -0.2185F, -1.075F, 11.0F, 1.0F, 2.15F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.0106F, 1.1F, -5.8197F, -0.0873F, 0.0F, 0.0F));

		PartDefinition cube_r11 = hat.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(55, 18).mirror().addBox(-1.075F, -0.25F, -5.45F, 2.0F, 1.0F, 10.9F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(6.0156F, 1.0283F, 0.1733F, 0.0F, 0.0F, -0.1745F));

		PartDefinition cube_r12 = hat.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(29, 23).mirror().addBox(-1.075F, -0.4814F, -5.45F, 2.15F, 1.0F, 10.9F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-6.031F, 1.313F, 0.1733F, 0.0F, 0.0F, 0.1745F));

		PartDefinition cube_r13 = hat.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(46, 25).mirror().addBox(-0.475F, -0.45F, -0.675F, 1.15F, 1.0F, 1.15F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-5.6356F, 1.2325F, 5.7983F, 0.0873F, 0.0F, 0.1745F));

		PartDefinition cube_r14 = hat.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(46, 22).mirror().addBox(-0.675F, -0.4009F, -0.675F, 1.15F, 1.0F, 1.15F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.6144F, 1.2325F, 5.7983F, 0.0873F, 0.0F, -0.1745F));

		PartDefinition cube_r15 = hat.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(52, 22).mirror().addBox(-0.475F, -0.4009F, -0.475F, 1.15F, 1.0F, 1.15F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-5.6356F, 1.2325F, -5.4517F, -0.0873F, -0.0003F, 0.1745F));

		PartDefinition cube_r16 = hat.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(52, 25).mirror().addBox(-0.675F, -0.4209F, -0.475F, 1.15F, 1.0F, 1.15F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.6144F, 1.2325F, -5.4517F, -0.0873F, 0.0F, -0.1745F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, -25.0F, 0.0F));

		PartDefinition rightLeg = body.addOrReplaceChild("rightLeg", CubeListBuilder.create(), PartPose.offset(-1.9F, 13.0F, 0.0F));

		PartDefinition rightUpperLeg = rightLeg.addOrReplaceChild("rightUpperLeg", CubeListBuilder.create().texOffs(0, 83).addBox(-3.9F, -12.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(1.8F, 12.0F, 0.0F));

		PartDefinition SAIADIREITA_r1 = rightUpperLeg.addOrReplaceChild("SAIADIREITA_r1", CubeListBuilder.create().texOffs(44, 100).addBox(-3.0F, -5.0F, -1.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.8F, -7.2F, -1.5F, 0.0F, 0.0F, 0.1745F));

		PartDefinition rightLegJoint = rightLeg.addOrReplaceChild("rightLegJoint", CubeListBuilder.create().texOffs(2, 105).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.1F)), PartPose.offset(0.0F, 6.0F, 0.0F));

		PartDefinition rightLowerLeg = rightLeg.addOrReplaceChild("rightLowerLeg", CubeListBuilder.create().texOffs(0, 94).addBox(-2.1F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0F, 0.0F));

		PartDefinition leftLeg = body.addOrReplaceChild("leftLeg", CubeListBuilder.create(), PartPose.offset(1.9F, 13.0F, 0.0F));

		PartDefinition SAIAESQUERDA_r1 = leftLeg.addOrReplaceChild("SAIAESQUERDA_r1", CubeListBuilder.create().texOffs(43, 111).addBox(-4.0F, -5.0F, -1.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 4.5F, -1.5F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leftUpperLeg = leftLeg.addOrReplaceChild("leftUpperLeg", CubeListBuilder.create().texOffs(18, 83).addBox(-1.1F, -12.0F, -2.0F, 5.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.8F, 12.0F, 0.0F));

		PartDefinition leftLegJoint = leftLeg.addOrReplaceChild("leftLegJoint", CubeListBuilder.create().texOffs(20, 105).addBox(-2.5F, -1.5F, -1.5F, 4.0F, 3.0F, 3.0F, new CubeDeformation(-0.1F)), PartPose.offset(0.0F, 6.0F, 0.0F));

		PartDefinition leftLowerLeg = leftLeg.addOrReplaceChild("leftLowerLeg", CubeListBuilder.create().texOffs(18, 94).addBox(-2.9F, 0.0F, -2.0F, 5.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0F, 0.0F));

		PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create(), PartPose.offset(-6.0F, 4.0F, 0.0F));

		PartDefinition shoulderR = rightArm.addOrReplaceChild("shoulderR", CubeListBuilder.create().texOffs(89, 111).mirror().addBox(-2.0F, -3.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition rightArmJoint = rightArm.addOrReplaceChild("rightArmJoint", CubeListBuilder.create().texOffs(66, 103).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.1F)), PartPose.offset(0.0F, 3.0F, 0.0F));

		PartDefinition rightForeArm = rightArm.addOrReplaceChild("rightForeArm", CubeListBuilder.create().texOffs(93, 121).addBox(-2.25F, 3.0F, -2.25F, 4.5F, 2.0F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(98, 98).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(91, 93).addBox(-2.1033F, 6.1352F, -1.807F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(91, 93).addBox(-2.1033F, 6.1352F, -0.507F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(91, 93).addBox(-2.1033F, 6.1352F, 0.793F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, 0.0F));

		PartDefinition stick = rightForeArm.addOrReplaceChild("stick", CubeListBuilder.create(), PartPose.offset(-0.75F, 6.1397F, -1.4387F));

		PartDefinition cube_r17 = stick.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(124, 112).addBox(-3.0F, -0.75F, -0.625F, 1.0F, 13.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, -2.767F, 2.3349F, 1.5708F, 0.0F, -1.5708F));

		PartDefinition cube_r18 = stick.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(112, 122).addBox(-3.0F, -2.75F, -0.625F, 1.0F, 1.0F, 4.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, -2.767F, 3.3349F, 1.5708F, 0.0F, -1.5708F));

		PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create(), PartPose.offset(5.9967F, 2.4352F, 0.093F));

		PartDefinition shoulderL = leftArm.addOrReplaceChild("shoulderL", CubeListBuilder.create().texOffs(107, 111).mirror().addBox(-1.9967F, -3.4352F, -2.093F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 2.0F, 0.0F));

		PartDefinition leftArmJoint = leftArm.addOrReplaceChild("leftArmJoint", CubeListBuilder.create().texOffs(116, 104).addBox(-0.9967F, -1.9352F, -1.593F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.1F)), PartPose.offset(0.0F, 5.0F, 0.0F));

		PartDefinition leftForeArm = leftArm.addOrReplaceChild("leftForeArm", CubeListBuilder.create().texOffs(80, 98).addBox(-1.9967F, -0.4352F, -2.093F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(74, 121).addBox(-2.2467F, 2.5648F, -2.343F, 4.5F, 2.0F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(91, 93).addBox(1.1F, 5.7F, -1.9F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(91, 93).addBox(1.1F, 5.7F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(91, 93).addBox(1.1F, 5.7F, 0.8F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 5.0F, 0.0F));

		PartDefinition torso = body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(17, 112).addBox(-4.0F, -11.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 12.0F, 0.0F));

		PartDefinition booba_r1 = torso.addOrReplaceChild("booba_r1", CubeListBuilder.create().texOffs(43, 123).addBox(-4.0F, -0.85F, -0.4F, 8.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -10.0F, -1.75F, -0.1309F, 0.0F, 0.0F));

		PartDefinition badges = torso.addOrReplaceChild("badges", CubeListBuilder.create(), PartPose.offset(0.0F, -11.0F, 0.0F));

		PartDefinition right_badge = badges.addOrReplaceChild("right_badge", CubeListBuilder.create().texOffs(9, 117).addBox(-0.675F, 1.125F, -0.125F, 1.75F, 1.75F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(10, 115).addBox(-0.425F, 1.375F, -0.225F, 1.25F, 1.25F, 0.25F, new CubeDeformation(0.0F)), PartPose.offset(1.875F, 8.125F, -2.125F));

		PartDefinition cube_r19 = right_badge.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(9, 121).addBox(-0.875F, -0.875F, -0.125F, 1.75F, 1.75F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.2F, 2.0F, 0.0F, 0.0F, 0.0F, 0.48F));

		PartDefinition cube_r20 = right_badge.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(1, 125).addBox(-0.875F, -0.875F, -0.125F, 1.75F, 1.75F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.2F, 2.0F, 0.0F, 0.0F, 0.0F, -0.48F));

		PartDefinition left_badge = badges.addOrReplaceChild("left_badge", CubeListBuilder.create().texOffs(1, 117).addBox(-1.075F, 0.425F, -0.125F, 1.75F, 1.75F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(5, 115).addBox(-0.825F, 0.675F, -0.225F, 1.25F, 1.25F, 0.25F, new CubeDeformation(0.0F)), PartPose.offset(-1.875F, 8.125F, -2.125F));

		PartDefinition cube_r21 = left_badge.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(1, 121).addBox(-0.875F, -0.875F, -0.125F, 1.75F, 1.75F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.2F, 1.3F, 0.0F, 0.0F, 0.0F, -0.48F));

		PartDefinition cube_r22 = left_badge.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(9, 125).addBox(-0.875F, -0.875F, -0.125F, 1.75F, 1.75F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.2F, 1.3F, 0.0F, 0.0F, 0.0F, 0.48F));

		PartDefinition skarf = badges.addOrReplaceChild("skarf", CubeListBuilder.create().texOffs(118, 18).mirror().addBox(-1.0254F, -1.0352F, -5.1596F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-0.0921F, -0.2327F, -0.5742F));

		PartDefinition cube_r23 = skarf.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(110, 22).mirror().addBox(-0.625F, 0.3007F, -0.4308F, 2.0F, 1.0F, 1.5F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.3859F, -0.7486F, 2.1031F, -1.2993F, 1.2956F, -1.1671F));

		PartDefinition cube_r24 = skarf.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(119, 50).mirror().addBox(-1.875F, 0.0507F, -0.9692F, 3.5F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-4.9368F, -0.6486F, -0.9224F, 1.1776F, 1.266F, 0.9673F));

		PartDefinition cube_r25 = skarf.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(110, 18).mirror().addBox(-1.375F, 0.3007F, -0.4308F, 2.0F, 1.0F, 1.5F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-4.9368F, -0.7486F, 2.1031F, -1.2993F, -1.2956F, 1.1671F));

		PartDefinition cube_r26 = skarf.addOrReplaceChild("cube_r26", CubeListBuilder.create().texOffs(106, 40).mirror().addBox(0.375F, 0.0507F, -0.7808F, 2.5F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-4.9368F, -0.6486F, 2.1031F, -0.9776F, -1.0054F, 0.822F));

		PartDefinition cube_r27 = skarf.addOrReplaceChild("cube_r27", CubeListBuilder.create().texOffs(120, 58).mirror().addBox(-1.875F, -1.3453F, -0.4315F, 3.0F, 1.5F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.9368F, 0.3514F, -2.9224F, 0.7798F, 0.6653F, 0.6947F));

		PartDefinition cube_r28 = skarf.addOrReplaceChild("cube_r28", CubeListBuilder.create().texOffs(120, 25).mirror().addBox(-1.125F, -1.3453F, -0.4315F, 3.0F, 1.5F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(4.3859F, 0.3514F, -2.9224F, 0.7798F, -0.6653F, -0.6947F));

		PartDefinition cube_r29 = skarf.addOrReplaceChild("cube_r29", CubeListBuilder.create().texOffs(108, 50).mirror().addBox(-1.625F, 0.3007F, -0.9692F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.9368F, 0.3514F, -2.9224F, 1.1113F, 0.5248F, 0.8169F));

		PartDefinition cube_r30 = skarf.addOrReplaceChild("cube_r30", CubeListBuilder.create().texOffs(120, 22).mirror().addBox(-1.375F, 0.3007F, -0.9692F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(4.3859F, 0.3514F, -2.9224F, 1.1113F, -0.5248F, -0.8169F));

		PartDefinition cube_r31 = skarf.addOrReplaceChild("cube_r31", CubeListBuilder.create().texOffs(107, 31).mirror().addBox(-1.875F, -1.3453F, -0.6815F, 3.5F, 1.5F, 1.5F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-4.9368F, -0.6486F, -0.9224F, 0.6998F, 1.3295F, 0.7432F));

		PartDefinition cube_r32 = skarf.addOrReplaceChild("cube_r32", CubeListBuilder.create().texOffs(105, 45).mirror().addBox(-1.625F, -1.3453F, -0.6815F, 3.5F, 1.5F, 1.5F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.3859F, -0.6486F, -0.9224F, 0.6998F, -1.3295F, -0.7432F));

		PartDefinition cube_r33 = skarf.addOrReplaceChild("cube_r33", CubeListBuilder.create().texOffs(119, 28).mirror().addBox(-1.625F, 0.0507F, -0.9692F, 3.5F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.3859F, -0.6486F, -0.9224F, 1.1776F, -1.266F, -0.9673F));

		PartDefinition cube_r34 = skarf.addOrReplaceChild("cube_r34", CubeListBuilder.create().texOffs(117, 45).mirror().addBox(-1.875F, -1.3453F, -1.0685F, 3.5F, 1.5F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.3859F, -0.6486F, 2.1031F, -0.5527F, 1.3607F, -0.6155F));

		PartDefinition cube_r35 = skarf.addOrReplaceChild("cube_r35", CubeListBuilder.create().texOffs(117, 40).mirror().addBox(-1.625F, -1.3453F, -1.0685F, 3.5F, 1.5F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-4.9368F, -0.6486F, 2.1031F, -0.5527F, -1.3607F, 0.6155F));

		PartDefinition cube_r36 = skarf.addOrReplaceChild("cube_r36", CubeListBuilder.create().texOffs(119, 31).mirror().addBox(-2.875F, 0.0507F, -0.7808F, 2.5F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.3859F, -0.6486F, 2.1031F, -1.0136F, 0.9688F, -0.8651F));

		PartDefinition cube_r37 = skarf.addOrReplaceChild("cube_r37", CubeListBuilder.create().texOffs(108, 0).mirror().addBox(-4.0F, -1.5F, 3.0F, 8.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.2245F, -0.5352F, 0.0903F, 2.7925F, 0.0F, 3.1416F));

		PartDefinition cube_r38 = skarf.addOrReplaceChild("cube_r38", CubeListBuilder.create().texOffs(107, 53).mirror().addBox(-4.25F, -2.0F, 3.0F, 8.5F, 1.5F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.2245F, -0.5352F, 0.0903F, 2.1817F, 0.0F, -3.1416F));

		PartDefinition cube_r39 = skarf.addOrReplaceChild("cube_r39", CubeListBuilder.create().texOffs(105, 35).mirror().addBox(-4.75F, -1.3F, 3.25F, 9.5F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.2245F, -1.0352F, -0.1597F, -0.1309F, 0.0F, 0.0F));

		PartDefinition cube_r40 = skarf.addOrReplaceChild("cube_r40", CubeListBuilder.create().texOffs(106, 62).mirror().addBox(-4.75F, -2.0F, 3.35F, 9.5F, 1.5F, 1.5F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.2245F, -1.0352F, -0.1597F, -0.7418F, 0.0F, 0.0F));

		PartDefinition scarfEdge2 = skarf.addOrReplaceChild("scarfEdge2", CubeListBuilder.create(), PartPose.offset(0.2421F, 24.2327F, 0.5742F));

		PartDefinition bone3 = scarfEdge2.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offset(0.8062F, -22.2937F, -2.9448F));

		PartDefinition cube_r41 = bone3.addOrReplaceChild("cube_r41", CubeListBuilder.create().texOffs(110, 6).mirror().addBox(-1.6168F, -2.9284F, -0.3173F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.1797F, 1.4146F, -0.2322F, -0.0393F, -0.0522F, -0.0357F));

		PartDefinition bone4 = bone3.addOrReplaceChild("bone4", CubeListBuilder.create(), PartPose.offset(-0.2982F, 2.878F, -0.0816F));

		PartDefinition cube_r42 = bone4.addOrReplaceChild("cube_r42", CubeListBuilder.create().texOffs(110, 12).mirror().addBox(-1.0704F, -0.07F, -0.4068F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.2721F, -1.4634F, -0.1506F, 0.0133F, -0.0905F, 0.1006F));

		PartDefinition scarfEdge1 = skarf.addOrReplaceChild("scarfEdge1", CubeListBuilder.create(), PartPose.offset(1.3726F, 3.5453F, -4.6596F));

		PartDefinition bone = scarfEdge1.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(-0.8658F, -2.4516F, -0.6376F));

		PartDefinition cube_r43 = bone.addOrReplaceChild("cube_r43", CubeListBuilder.create().texOffs(120, 6).mirror().addBox(-1.44F, -2.9284F, -0.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0024F, 1.4283F, -0.0624F, -0.0436F, -0.0019F, -0.0436F));

		PartDefinition bone2 = bone.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offset(-0.2822F, -0.8788F, 6.1376F));

		PartDefinition cube_r44 = bone2.addOrReplaceChild("cube_r44", CubeListBuilder.create().texOffs(120, 12).mirror().addBox(-1.4353F, -0.0718F, -0.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.2846F, 2.3071F, -6.2F, -0.0436F, 0.0019F, 0.0436F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}