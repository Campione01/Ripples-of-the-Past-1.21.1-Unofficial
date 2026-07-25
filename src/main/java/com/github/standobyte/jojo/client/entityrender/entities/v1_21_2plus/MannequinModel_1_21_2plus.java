package com.github.standobyte.jojo.client.entityrender.entities.v1_21_2plus;

import com.github.standobyte.v1_21_4_stuff.missingmethods._PartDefinition;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

//import com.github.standobyte.jojo.mechanics.clothes.mannequin.MannequinEntity;
//import com.github.standobyte.v1_21_4_stuff.missingmethods._PartDefinition;
//
//import net.minecraft.client.model.HumanoidModel;
//import net.minecraft.client.model.geom.ModelPart;
//import net.minecraft.client.model.geom.PartPose;
//import net.minecraft.client.model.geom.builders.CubeDeformation;
//import net.minecraft.client.model.geom.builders.CubeListBuilder;
//import net.minecraft.client.model.geom.builders.LayerDefinition;
//import net.minecraft.client.model.geom.builders.MeshDefinition;
//import net.minecraft.client.model.geom.builders.PartDefinition;

public class MannequinModel_1_21_2plus /*extends HumanoidModel<MannequinEntity>*/ {
	
//	public MannequinModel_1_21_2plus(ModelPart root) {
//		super(root);
//	}

	public static LayerDefinition createMesh(CubeDeformation cubeDeformation, boolean slim) {
		MeshDefinition mesh = HumanoidModel.createMesh(cubeDeformation, 0.0F);
		PartDefinition root = mesh.getRoot();
		
		if (slim) {
			PartDefinition leftArm = root.addOrReplaceChild(
					"left_arm",
					CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, cubeDeformation),
					PartPose.offset(5.0F, 2.0F, 0.0F));
			PartDefinition rightArm = root.addOrReplaceChild(
					"right_arm",
					CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, cubeDeformation),
					PartPose.offset(-5.0F, 2.0F, 0.0F));
			leftArm.addOrReplaceChild(
					"left_sleeve", 
					CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, cubeDeformation.extend(0.25F)), 
					PartPose.ZERO);
			rightArm.addOrReplaceChild(
					"right_sleeve", 
					CubeListBuilder.create().texOffs(40, 32).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, cubeDeformation.extend(0.25F)), 
					PartPose.ZERO);
		}
		else {
			PartDefinition leftArm = root.addOrReplaceChild(
					"left_arm",
					CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation),
					PartPose.offset(5.0F, 2.0F, 0.0F));
			PartDefinition rightArm = root.getChild("right_arm");
			leftArm.addOrReplaceChild(
					"left_sleeve", 
					CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(0.25F)), 
					PartPose.ZERO);
			rightArm.addOrReplaceChild(
					"right_sleeve", 
					CubeListBuilder.create().texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(0.25F)), 
					PartPose.ZERO);
		}
		_PartDefinition.clearChild(root, "hat");
		PartDefinition leftLeg = root.addOrReplaceChild(
				"left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation), 
				PartPose.offset(1.9F, 12.0F, 0.0F));
		PartDefinition rightLeg = root.getChild("right_leg");
		leftLeg.addOrReplaceChild(
				"left_pants", 
				CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(0.25F)), 
				PartPose.ZERO);
		rightLeg.addOrReplaceChild(
				"right_pants", 
				CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(0.25F)), 
				PartPose.ZERO);
		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild(
				"jacket", 
				CubeListBuilder.create().texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, cubeDeformation.extend(0.25F)), 
				PartPose.ZERO);
		
		return LayerDefinition.create(mesh, 64, 64);
	}

//	@Override
//	public void setupAnim(MannequinRenderState_1_21_2plus renderState) {
//		super.setupAnim(renderState);
//		this.head.xRot = (float) (Math.PI / 180.0) * renderState.headPose.getX();
//		this.head.yRot = (float) (Math.PI / 180.0) * renderState.headPose.getY();
//		this.head.zRot = (float) (Math.PI / 180.0) * renderState.headPose.getZ();
//		this.body.xRot = (float) (Math.PI / 180.0) * renderState.bodyPose.getX();
//		this.body.yRot = (float) (Math.PI / 180.0) * renderState.bodyPose.getY();
//		this.body.zRot = (float) (Math.PI / 180.0) * renderState.bodyPose.getZ();
//		this.leftArm.xRot = (float) (Math.PI / 180.0) * renderState.leftArmPose.getX();
//		this.leftArm.yRot = (float) (Math.PI / 180.0) * renderState.leftArmPose.getY();
//		this.leftArm.zRot = (float) (Math.PI / 180.0) * renderState.leftArmPose.getZ();
//		this.rightArm.xRot = (float) (Math.PI / 180.0) * renderState.rightArmPose.getX();
//		this.rightArm.yRot = (float) (Math.PI / 180.0) * renderState.rightArmPose.getY();
//		this.rightArm.zRot = (float) (Math.PI / 180.0) * renderState.rightArmPose.getZ();
//		this.leftLeg.xRot = (float) (Math.PI / 180.0) * renderState.leftLegPose.getX();
//		this.leftLeg.yRot = (float) (Math.PI / 180.0) * renderState.leftLegPose.getY();
//		this.leftLeg.zRot = (float) (Math.PI / 180.0) * renderState.leftLegPose.getZ();
//		this.rightLeg.xRot = (float) (Math.PI / 180.0) * renderState.rightLegPose.getX();
//		this.rightLeg.yRot = (float) (Math.PI / 180.0) * renderState.rightLegPose.getY();
//		this.rightLeg.zRot = (float) (Math.PI / 180.0) * renderState.rightLegPose.getZ();
//	}

}
