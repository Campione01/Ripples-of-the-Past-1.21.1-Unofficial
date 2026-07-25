package com.github.standobyte.jojo.client.entityanim.playerbend;

import net.minecraft.client.model.geom.ModelPart;

public interface IPlayerBendModel {
	ModelPart jojo_ripples$animMainBody();
	ModelPart jojo_ripples$animTorso();
	ModelPart jojo_ripples$animTorsoBend();
	ModelPart jojo_ripples$animRightArmBend();
	ModelPart jojo_ripples$animLeftArmBend();
	ModelPart jojo_ripples$animRightLegBend();
	ModelPart jojo_ripples$animLeftLegBend();
	ModelPart jojo_ripples$animRightItem();
	ModelPart jojo_ripples$animLeftItem();
	ModelPart jojo_ripples$animCapeBend();
	ModelPart jojo_ripples$leftArm();
	ModelPart jojo_ripples$rightArm();
	void jojo_ripples_v1_21_1$onResetPose();
}
