package com.github.standobyte.jojo.client.entityanim.playerbend;

import net.minecraft.client.model.geom.ModelPart;

public interface IPlayerLimbBend {
	void jojo_ripples$setBendBone(ModelPart bendBone, boolean invertBend);
	ModelPart jojo_ripples$getBendBone();
}
