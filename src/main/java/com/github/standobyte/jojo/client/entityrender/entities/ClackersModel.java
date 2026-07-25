package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.customobjects.entity_projectile.ClackersEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class ClackersModel extends HierarchicalModel<ClackersEntity> {
	private final ModelPart root;
	private final ModelPart clackers;
	private final ModelPart string1;
	private final ModelPart string2;

	public ClackersModel(ModelPart root) {
		this.root = root;
		this.clackers = root.getChild("clackers");
		this.string1 = clackers.getChild("string1");
		this.string2 = clackers.getChild("string2");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition clackers = root.addOrReplaceChild("clackers",
				CubeListBuilder.create()
						.texOffs(18, 0)
						.addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.25F)),
				PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition string1 = clackers.addOrReplaceChild("string1",
				CubeListBuilder.create()
						.texOffs(24, 6)
						.addBox(-0.5F, -7.925F, -0.5F, 1.0F, 8.0F, 1.0F, new CubeDeformation(-0.075F)),
				PartPose.ZERO);
		string1.addOrReplaceChild("ball1",
				CubeListBuilder.create()
						.texOffs(0, 0)
						.addBox(-3.0F, -4.5F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(-1.5F)),
				PartPose.offset(0.0F, -7.85F, 0.0F));

		PartDefinition string2 = clackers.addOrReplaceChild("string2",
				CubeListBuilder.create()
						.texOffs(28, 6)
						.addBox(-0.5F, -0.075F, -0.5F, 1.0F, 8.0F, 1.0F, new CubeDeformation(-0.075F)),
				PartPose.ZERO);
		string2.addOrReplaceChild("ball2",
				CubeListBuilder.create()
						.texOffs(0, 12)
						.addBox(-3.0F, -1.2F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(-1.5F)),
				PartPose.offset(0.0F, 7.55F, 0.0F));

		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public ModelPart root() {
		return root;
	}

	@Override
	public void setupAnim(ClackersEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		resetPose();
		float xRotation = headPitch;
		if (!entity.isInGround()) {
			xRotation = (headPitch + ageInTicks * 18.0F * (float) entity.getDeltaMovement().length()) % 360.0F;
		}
		clackers.xRot = xRotation * MathUtil.DEG_TO_RAD;
		clackers.yRot = netHeadYaw * MathUtil.DEG_TO_RAD;
	}

	public ModelPart getMainPart() {
		return clackers;
	}

	public void resetPose() {
		clackers.xRot = 0.0F;
		clackers.yRot = 0.0F;
		clackers.zRot = 0.0F;
		setStringAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
	}

	public void setStringAngles(float xRot1, float yRot1, float zRot1, float xRot2, float yRot2, float zRot2) {
		string1.xRot = xRot1;
		string1.yRot = yRot1;
		string1.zRot = zRot1;
		string2.xRot = xRot2;
		string2.yRot = yRot2;
		string2.zRot = zRot2;
	}
}
