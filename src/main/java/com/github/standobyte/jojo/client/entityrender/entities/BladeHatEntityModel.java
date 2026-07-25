package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.customobjects.entity_projectile.BladeHatEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class BladeHatEntityModel extends HierarchicalModel<BladeHatEntity> {
	private final ModelPart root;
	private final ModelPart hat;

	public BladeHatEntityModel(ModelPart root) {
		this.root = root;
		this.hat = root.getChild("hat");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		addHat(mesh.getRoot(), "hat", PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static PartDefinition addHat(PartDefinition parent, String name, PartPose pose) {
		PartDefinition hat = parent.addOrReplaceChild(name, CubeListBuilder.create()
				.texOffs(0, 23).addBox(-4.0F, -6.0F, -4.0F, 8.0F, 6.0F, 8.0F, CubeDeformation.NONE)
				.texOffs(32, 23).addBox(-4.0F, -6.1F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.25F))
				.texOffs(0, 12).addBox(-4.5F, -2.85F, -4.5F, 9.0F, 2.0F, 9.0F, new CubeDeformation(-0.125F))
				.texOffs(36, 12).addBox(-3.0F, -2.85F, -4.6F, 6.0F, 2.0F, 1.0F, new CubeDeformation(-0.1F))
				.texOffs(0, 37).addBox(-1.5F, -2.85F, -4.75F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.1F))
				.texOffs(0, 0).addBox(-4.0F, 0.0F, -6.0F, 8.0F, 0.0F, 12.0F, CubeDeformation.NONE), pose);

		hat.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(32, 35).mirror()
				.addBox(-5.0F, 0.0F, -5.0F, 2.0F, 0.0F, 10.0F, CubeDeformation.NONE),
				PartPose.offsetAndRotation(8.6198F, -1.9135F, 0.0F, 0.0F, 0.0F, -0.3927F));
		hat.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(32, 35)
				.addBox(3.0F, 0.0F, -5.0F, 2.0F, 0.0F, 10.0F, CubeDeformation.NONE),
				PartPose.offsetAndRotation(-8.6198F, -1.9135F, 0.0F, 0.0F, 0.0F, 0.3927F));
		hat.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(8, 37).mirror()
				.addBox(0.0F, -2.5F, -1.0F, 0.0F, 4.0F, 2.0F, CubeDeformation.NONE),
				PartPose.offsetAndRotation(-4.5746F, -3.5345F, 0.9056F, -0.3927F, 0.0F, -0.2182F));
		hat.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(36, 15).mirror()
				.addBox(0.0F, -2.0F, -1.5F, 0.0F, 4.0F, 3.0F, CubeDeformation.NONE),
				PartPose.offsetAndRotation(-4.5F, -3.6F, 0.0F, -0.1745F, 0.0F, -0.2182F));
		hat.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(8, 37)
				.addBox(0.0F, -2.5F, -1.0F, 0.0F, 4.0F, 2.0F, CubeDeformation.NONE),
				PartPose.offsetAndRotation(4.5746F, -3.5345F, 0.9056F, -0.3927F, 0.0F, 0.2182F));
		hat.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(36, 15)
				.addBox(0.0F, -2.0F, -1.5F, 0.0F, 4.0F, 3.0F, CubeDeformation.NONE),
				PartPose.offsetAndRotation(4.5F, -3.6F, 0.0F, -0.1745F, 0.0F, 0.2182F));
		return hat;
	}

	@Override
	public ModelPart root() {
		return root;
	}

	@Override
	public void setupAnim(BladeHatEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {
		float yRotation = netHeadYaw;
		if (!entity.isInGround() && entity.getDeltaMovement().lengthSqr() > 1.0E-7D) {
			yRotation = (yRotation + ageInTicks * 36.0F) % 360.0F;
		}
		hat.yRot = yRotation * MathUtil.DEG_TO_RAD;
	}
}
