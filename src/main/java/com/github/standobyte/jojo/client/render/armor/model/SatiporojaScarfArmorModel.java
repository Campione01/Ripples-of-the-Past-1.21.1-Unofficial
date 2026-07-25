package com.github.standobyte.jojo.client.render.armor.model;

import java.util.Collections;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

public class SatiporojaScarfArmorModel<T extends LivingEntity> extends HumanoidModel<T> {
	public SatiporojaScarfArmorModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 7)
				.addBox(-4.5F, -1.2F, -2.5F, 9.0F, 1.0F, 5.0F, CubeDeformation.NONE)
				.texOffs(0, 0)
				.addBox(-4.5F, 0.0F, -2.6F, 9.0F, 2.0F, 5.0F, new CubeDeformation(0.2F))
				.texOffs(0, 13)
				.addBox(-4.1F, -0.5F, -3.5F, 3.0F, 11.0F, 1.0F, new CubeDeformation(-0.3F)),
				PartPose.offsetAndRotation(0.0F, 0.5F, 0.0F, 0.0873F, 0.0F, 0.0F));
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
		return Collections.emptyList();
	}

	@Override
	protected Iterable<ModelPart> bodyParts() {
		return List.of(head);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
			int packedOverlay, int color) {
		head.xRot = body.xRot + 0.0873F;
		head.yRot = body.yRot;
		head.zRot = body.zRot;
		super.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}
