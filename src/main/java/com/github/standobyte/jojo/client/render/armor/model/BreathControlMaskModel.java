package com.github.standobyte.jojo.client.render.armor.model;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

public class BreathControlMaskModel<T extends LivingEntity> extends HumanoidModel<T> {
	public BreathControlMaskModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0)
				.addBox(-4.0F, -3.0F, -4.0F, 8.0F, 3.0F, 3.0F, new CubeDeformation(0.4F))
				.texOffs(22, 0)
				.addBox(-1.0F, -2.0F, -5.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.6F)),
				PartPose.ZERO);
		root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	protected Iterable<ModelPart> headParts() {
		return List.of(head);
	}

	@Override
	protected Iterable<ModelPart> bodyParts() {
		return Collections.emptyList();
	}
}
