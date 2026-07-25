package com.github.standobyte.jojo.mrpresident.client;

import java.util.List;

import com.github.standobyte.jojo.mrpresident.CocoJumboTurtleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

public class CocoJumboTurtleModel extends EntityModel<CocoJumboTurtleEntity> {
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart eggBelly;
	private final ModelPart rightHindLeg;
	private final ModelPart leftHindLeg;
	private final ModelPart rightFrontLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart mrPresidentKey;
	private final List<ModelPart> bodyParts;
	private boolean keyOnly;

	public CocoJumboTurtleModel(ModelPart root) {
		super(RenderType::entityCutoutNoCull);
		this.root = root;
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.eggBelly = root.getChild("egg_belly");
		this.rightHindLeg = root.getChild("right_hind_leg");
		this.leftHindLeg = root.getChild("left_hind_leg");
		this.rightFrontLeg = root.getChild("right_front_leg");
		this.leftFrontLeg = root.getChild("left_front_leg");
		this.mrPresidentKey = root.getChild("mr_president_key");
		this.bodyParts = List.of(body, eggBelly, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(3, 0).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 5.0F, 6.0F),
				PartPose.offset(0.0F, 19.0F, -10.0F));
		root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(7, 37).addBox("shell", -9.5F, 3.0F, -10.0F, 19.0F, 20.0F, 6.0F)
				.texOffs(31, 1).addBox("belly", -5.5F, 3.0F, -13.0F, 11.0F, 18.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, 11.0F, -10.0F, Mth.HALF_PI, 0.0F, 0.0F));
		root.addOrReplaceChild("egg_belly", CubeListBuilder.create()
				.texOffs(70, 33).addBox(-4.5F, 3.0F, -14.0F, 9.0F, 18.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 11.0F, -10.0F, Mth.HALF_PI, 0.0F, 0.0F));
		root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create()
				.texOffs(1, 23).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 1.0F, 10.0F),
				PartPose.offset(-3.5F, 22.0F, 11.0F));
		root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create()
				.texOffs(1, 12).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 1.0F, 10.0F),
				PartPose.offset(3.5F, 22.0F, 11.0F));
		root.addOrReplaceChild("right_front_leg", CubeListBuilder.create()
				.texOffs(27, 30).addBox(-13.0F, 0.0F, -2.0F, 13.0F, 1.0F, 5.0F),
				PartPose.offset(-5.0F, 21.0F, -4.0F));
		root.addOrReplaceChild("left_front_leg", CubeListBuilder.create()
				.texOffs(27, 24).addBox(0.0F, 0.0F, -2.0F, 13.0F, 1.0F, 5.0F),
				PartPose.offset(5.0F, 21.0F, -4.0F));
		root.addOrReplaceChild("mr_president_key", CubeListBuilder.create()
				.texOffs(89, 1).addBox(-0.5F, 4.9F, 12.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(-0.1F))
				.texOffs(105, 7).addBox(-0.5F, 4.9F, 13.0F, 1.0F, 1.0F, 1.0F)
				.texOffs(98, 5).addBox(-1.25F, 4.9F, 16.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(-0.15F))
				.texOffs(64, 5).addBox(-1.5F, 4.45F, 8.35F, 3.0F, 1.0F, 3.0F, new CubeDeformation(-0.15F))
				.texOffs(76, 5).addBox(-1.5F, 4.15F, 8.35F, 3.0F, 1.0F, 3.0F, new CubeDeformation(-0.25F))
				.texOffs(80, 0).addBox(-2.0F, 4.9F, 7.85F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.1F))
				.texOffs(64, 0).addBox(-2.0F, 4.9F, 7.85F, 4.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 11.0F, -10.0F));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(CocoJumboTurtleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {
		for (ModelPart part : bodyParts) {
			part.resetPose();
		}
		head.resetPose();
		mrPresidentKey.resetPose();
		eggBelly.visible = false;
		head.xRot = headPitch * Mth.DEG_TO_RAD;
		head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
		float walk = Math.min(limbSwingAmount, 1.0F);
		rightHindLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 0.5F * walk;
		leftHindLeg.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 0.5F * walk;
		rightFrontLeg.zRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 0.5F * walk;
		leftFrontLeg.zRot = Mth.cos(limbSwing * 0.6662F) * 0.5F * walk;
		mrPresidentKey.xRot = body.xRot - Mth.HALF_PI;
		mrPresidentKey.yRot = body.yRot;
		mrPresidentKey.zRot = body.zRot;
	}

	public void renderKeyToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		keyOnly = true;
		renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		keyOnly = false;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		boolean headVisible = head.visible;
		boolean bodyVisible = body.visible;
		boolean eggBellyVisible = eggBelly.visible;
		boolean rightHindLegVisible = rightHindLeg.visible;
		boolean leftHindLegVisible = leftHindLeg.visible;
		boolean rightFrontLegVisible = rightFrontLeg.visible;
		boolean leftFrontLegVisible = leftFrontLeg.visible;
		boolean keyVisible = mrPresidentKey.visible;
		if (keyOnly) {
			setBodyVisible(false);
			mrPresidentKey.visible = true;
		}
		else {
			setBodyVisible(true);
			mrPresidentKey.visible = false;
		}
		root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		head.visible = headVisible;
		body.visible = bodyVisible;
		eggBelly.visible = eggBellyVisible;
		rightHindLeg.visible = rightHindLegVisible;
		leftHindLeg.visible = leftHindLegVisible;
		rightFrontLeg.visible = rightFrontLegVisible;
		leftFrontLeg.visible = leftFrontLegVisible;
		mrPresidentKey.visible = keyVisible;
	}

	private void setBodyVisible(boolean visible) {
		head.visible = visible;
		for (ModelPart part : bodyParts) {
			part.visible = visible;
		}
	}
}
