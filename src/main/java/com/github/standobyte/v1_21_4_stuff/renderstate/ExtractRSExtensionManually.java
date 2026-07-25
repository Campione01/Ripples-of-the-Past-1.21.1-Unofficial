package com.github.standobyte.v1_21_4_stuff.renderstate;

import com.github.standobyte.jojo.mechanics.clothes.client.layer.HumanoidClothesRSExtension;

import net.minecraft.world.entity.LivingEntity;

public class ExtractRSExtensionManually {

	public static void extractClothes(LivingEntity entity) {
		HumanoidClothesRSExtension.reusedInstance.extract(entity);
	}

	public static void resetClothes() {
		HumanoidClothesRSExtension.reusedInstance.hasClothesComponent = false;
	}
}
