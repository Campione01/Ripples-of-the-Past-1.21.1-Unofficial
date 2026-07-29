package com.github.standobyte.jojo.api.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

@FunctionalInterface
public interface HumanoidModelPostSetupCallback {
	void apply(LivingEntity entity, HumanoidModel<?> model);
}
