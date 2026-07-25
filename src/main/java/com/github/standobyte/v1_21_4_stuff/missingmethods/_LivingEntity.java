package com.github.standobyte.v1_21_4_stuff.missingmethods;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class _LivingEntity {

	public static ItemStack getItemHeldByArm(LivingEntity entity, HumanoidArm arm) {
		return entity.getMainArm() == arm ? entity.getMainHandItem() : entity.getOffhandItem();
	}
}
