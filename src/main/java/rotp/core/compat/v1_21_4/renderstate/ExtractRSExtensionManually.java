package rotp.core.compat.v1_21_4.renderstate;

import rotp.core.mechanics.clothes.client.layer.HumanoidClothesRSExtension;

import net.minecraft.world.entity.LivingEntity;

public class ExtractRSExtensionManually {

	public static void extractClothes(LivingEntity entity) {
		HumanoidClothesRSExtension.reusedInstance.extract(entity);
	}

	public static void resetClothes() {
		HumanoidClothesRSExtension.reusedInstance.hasClothesComponent = false;
	}
}
