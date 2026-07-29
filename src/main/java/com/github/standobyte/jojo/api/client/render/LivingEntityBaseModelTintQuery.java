package com.github.standobyte.jojo.api.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;

public record LivingEntityBaseModelTintQuery(
		LivingEntity entity,
		EntityModel<?> model,
		float partialTick,
		int originalColor) {}
