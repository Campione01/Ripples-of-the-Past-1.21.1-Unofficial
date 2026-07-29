package com.github.standobyte.jojoimpl.powers.vampirism.client;

import com.github.standobyte.jojo.api.client.vampirism.HungryZombiePoseProviders;
import com.github.standobyte.jojoimpl.powers.vampirism.entity.HungryZombieEntity;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class HungryZombieModel
		extends ZombieModel<HungryZombieEntity> {
	public HungryZombieModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(
			HungryZombieEntity entity,
			float limbSwing,
			float limbSwingAmount,
			float ageInTicks,
			float netHeadYaw,
			float headPitch) {
		super.setupAnim(
				entity,
				limbSwing,
				limbSwingAmount,
				ageInTicks,
				netHeadYaw,
				headPitch);
		if (!HungryZombiePoseProviders.isWaiting(entity)) {
			return;
		}

		float attack = Mth.sin(attackTime * (float) Math.PI);
		float easedAttack = Mth.sin(
				(1.0F - (1.0F - attackTime)
						* (1.0F - attackTime))
						* (float) Math.PI);
		rightArm.zRot = 0.0F;
		leftArm.zRot = 0.0F;
		rightArm.yRot = -(0.1F - attack * 0.6F);
		leftArm.yRot = 0.1F - attack * 0.6F;
		rightArm.xRot = -0.9308423F;
		leftArm.xRot = -0.9308423F;
		rightArm.xRot += attack * 1.2F
				- easedAttack * 0.4F;
		leftArm.xRot += attack * 1.2F
				- easedAttack * 0.4F;
		AnimationUtils.bobArms(
				rightArm, leftArm, ageInTicks);
	}
}
