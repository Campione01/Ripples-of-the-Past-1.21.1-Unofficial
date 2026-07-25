package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.List;

import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonMasterEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class HamonMasterModel extends HumanoidModel<HamonMasterEntity> {
	public final ModelPart leftSleeve;
	public final ModelPart rightSleeve;
	public final ModelPart leftPants;
	public final ModelPart rightPants;
	public final ModelPart jacket;

	private final ModelPart rightCapeBinding;
	private final ModelPart rightCape;
	private final ModelPart lowRightCape;
	private final ModelPart leftCapeBinding;
	private final ModelPart leftCape;
	private final ModelPart lowLeftCape;

	public HamonMasterModel(ModelPart root) {
		super(root);
		leftSleeve = root.getChild("left_sleeve");
		rightSleeve = root.getChild("right_sleeve");
		leftPants = root.getChild("left_pants");
		rightPants = root.getChild("right_pants");
		jacket = root.getChild("jacket");

		rightCapeBinding = body.getChild("right_cape_binding");
		rightCape = body.getChild("right_cape");
		lowRightCape = rightCape.getChild("low_right_cape");
		leftCapeBinding = body.getChild("left_cape_binding");
		leftCape = body.getChild("left_cape");
		lowLeftCape = leftCape.getChild("low_left_cape");
	}

	public static LayerDefinition createBodyLayer() {
		return createLayer(false);
	}

	public static LayerDefinition createExtraLayer() {
		return createLayer(true);
	}

	private static LayerDefinition createLayer(boolean extraLayer) {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition root = mesh.getRoot();

		if (extraLayer) {
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		}
		else {
			root.addOrReplaceChild("left_arm",
					CubeListBuilder.create().texOffs(32, 48)
							.addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
					PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_leg",
					CubeListBuilder.create().texOffs(16, 48)
							.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
					PartPose.offset(1.9F, 12.0F, 0.0F));
		}

		CubeDeformation outer = new CubeDeformation(0.25F);
		root.addOrReplaceChild("left_sleeve", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(48, 48)
						.addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
				PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_sleeve", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(40, 32)
						.addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
				PartPose.offset(-5.0F, 2.0F, 10.0F));
		root.addOrReplaceChild("left_pants", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(0, 48)
						.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
				PartPose.offset(1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("right_pants", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(0, 32)
						.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
				PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("jacket", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(16, 32)
						.addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, outer),
				PartPose.ZERO);

		PartDefinition body = root.getChild("body");
		CubeDeformation bindingDeformation = new CubeDeformation(0.225F);
		body.addOrReplaceChild("right_cape_binding", extraLayer
				? CubeListBuilder.create().texOffs(36, 36)
						.addBox(-2.0F, -0.8F, -2.0F, 4.0F, 3.0F, 4.0F, bindingDeformation)
				: CubeListBuilder.create(),
				PartPose.offsetAndRotation(-2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0873F));
		PartDefinition rightCape = body.addOrReplaceChild("right_cape", extraLayer
				? CubeListBuilder.create()
						.texOffs(36, 45).addBox(-2.0F, 0.0F, -0.35F, 4.0F, 4.0F, 0.0F)
						.texOffs(44, 40).addBox(-2.0F, 0.0F, -5.35F, 0.0F, 4.0F, 5.0F)
				: CubeListBuilder.create(),
				PartPose.offsetAndRotation(-2.0F, 12.0F, 2.0F, 0.2182F, 0.0F, 0.1745F));
		rightCape.addOrReplaceChild("low_right_cape", extraLayer
				? CubeListBuilder.create()
						.texOffs(44, 44).addBox(0.0F, 0.0F, -5.0F, 0.0F, 7.0F, 5.0F)
						.texOffs(36, 49).addBox(0.0F, 0.0F, 0.0F, 4.0F, 7.0F, 0.0F)
				: CubeListBuilder.create(),
				PartPose.offset(-2.0F, 4.0F, -0.35F));

		body.addOrReplaceChild("left_cape_binding", extraLayer
				? CubeListBuilder.create().texOffs(4, 36)
						.addBox(-2.0F, -0.8F, -2.0F, 4.0F, 3.0F, 4.0F, bindingDeformation)
				: CubeListBuilder.create(),
				PartPose.offsetAndRotation(2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.0873F));
		PartDefinition leftCape = body.addOrReplaceChild("left_cape", extraLayer
				? CubeListBuilder.create()
						.texOffs(2, 40).addBox(2.0F, 0.0F, -5.35F, 0.0F, 4.0F, 5.0F)
						.texOffs(12, 45).addBox(-2.0F, 0.0F, -0.35F, 4.0F, 4.0F, 0.0F)
				: CubeListBuilder.create(),
				PartPose.offsetAndRotation(2.0F, 12.0F, 2.0F, 0.2182F, 0.0F, -0.1745F));
		leftCape.addOrReplaceChild("low_left_cape", extraLayer
				? CubeListBuilder.create()
						.texOffs(2, 44).addBox(0.0F, 0.0F, -5.0F, 0.0F, 7.0F, 5.0F)
						.texOffs(12, 49).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 7.0F, 0.0F)
				: CubeListBuilder.create(),
				PartPose.offset(2.0F, 4.0F, -0.35F));

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(HamonMasterEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch) {
		super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		setupOuterLayer();
	}

	public void copyPropertiesTo(HamonMasterModel model) {
		super.copyPropertiesTo(model);
		model.leftSleeve.copyFrom(leftSleeve);
		model.rightSleeve.copyFrom(rightSleeve);
		model.leftPants.copyFrom(leftPants);
		model.rightPants.copyFrom(rightPants);
		model.jacket.copyFrom(jacket);
		model.leftCapeBinding.copyFrom(leftCapeBinding);
		model.leftCape.copyFrom(leftCape);
		model.lowLeftCape.copyFrom(lowLeftCape);
		model.rightCapeBinding.copyFrom(rightCapeBinding);
		model.rightCape.copyFrom(rightCape);
		model.lowRightCape.copyFrom(lowRightCape);
	}

	@Override
	public void setAllVisible(boolean visible) {
		super.setAllVisible(visible);
		leftSleeve.visible = visible;
		rightSleeve.visible = visible;
		leftPants.visible = visible;
		rightPants.visible = visible;
		jacket.visible = visible;
	}

	@Override
	protected Iterable<ModelPart> bodyParts() {
		return List.of(body, rightArm, leftArm, rightLeg, leftLeg, hat,
				leftPants, rightPants, leftSleeve, rightSleeve, jacket);
	}

	private void setupOuterLayer() {
		leftPants.copyFrom(leftLeg);
		rightPants.copyFrom(rightLeg);
		leftSleeve.copyFrom(leftArm);
		rightSleeve.copyFrom(rightArm);
		jacket.copyFrom(body);
	}
}
