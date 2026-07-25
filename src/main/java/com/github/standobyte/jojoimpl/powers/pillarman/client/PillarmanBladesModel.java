package com.github.standobyte.jojoimpl.powers.pillarman.client;

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

public class PillarmanBladesModel<T extends LivingEntity> extends HumanoidModel<T> {
	public final ModelPart bladeRight;
	public final ModelPart bladeLeft;

	public PillarmanBladesModel(ModelPart root) {
		super(root);
		this.bladeRight = rightArm.getChild("bladeRight");
		this.bladeLeft = leftArm.getChild("bladeLeft");
	}

	public static LayerDefinition createBodyLayer(CubeDeformation deformation) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

		rightArm.addOrReplaceChild("bladeRight", bladeCubes(deformation), PartPose.offsetAndRotation(-0.9F, 9.0F, 5.9F, 0.0F, 3.1416F, 0.0F));
		leftArm.addOrReplaceChild("bladeLeft", bladeCubes(deformation), PartPose.offsetAndRotation(2.3F, 9.0F, 5.9F, 0.0F, 3.1416F, 0.0F));
		return LayerDefinition.create(mesh, 16, 16);
	}

	private static CubeListBuilder bladeCubes(CubeDeformation deformation) {
		return CubeListBuilder.create()
				.texOffs(0, 0).addBox(0.2F, -2.8F, -1.0F, 1.0F, 3.0F, 5.0F, deformation)
				.texOffs(0, 8).addBox(0.2F, -2.8F, -4.0F, 1.0F, 2.0F, 3.0F, deformation)
				.texOffs(10, 0).addBox(0.2F, -2.8F, -6.0F, 1.0F, 1.0F, 2.0F, deformation)
				.texOffs(6, 11).addBox(0.2F, -3.8F, -7.0F, 1.0F, 1.0F, 4.0F, deformation);
	}

	@Override
	protected Iterable<ModelPart> headParts() {
		return Collections.emptyList();
	}

	@Override
	protected Iterable<ModelPart> bodyParts() {
		return List.of(rightArm, leftArm);
	}
}
