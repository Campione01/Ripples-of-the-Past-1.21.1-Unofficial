package com.github.standobyte.jojo.client.render.armor.model;

import java.util.Collections;
import java.util.List;

import com.github.standobyte.jojo.client.entityrender.entities.BladeHatEntityModel;
import com.github.standobyte.jojo.init.ModItems;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

public class BladeHatArmorModel<T extends LivingEntity> extends HumanoidModel<T> {
	public BladeHatArmorModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		BladeHatEntityModel.addHat(head, "blade_hat", PartPose.offset(0.0F, -4.0F, 0.0F));
		root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	protected Iterable<ModelPart> headParts() {
		return List.of(head);
	}

	@Override
	protected Iterable<ModelPart> bodyParts() {
		return Collections.emptyList();
	}

	public static void modifyOuterLayer(PlayerModel<?> playerModel, LivingEntity entity) {
		if (entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.BLADE_HAT.get())) {
			playerModel.hat.visible = false;
		}
	}
}
