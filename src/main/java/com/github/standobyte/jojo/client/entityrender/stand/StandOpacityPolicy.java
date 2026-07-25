package com.github.standobyte.jojo.client.entityrender.stand;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.config.client.ClientModSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class StandOpacityPolicy {
	private StandOpacityPolicy() {}

	public static float opacityFromTransparency(float transparency) {
		return 1.0F - Mth.clamp(transparency, 0.0F, 100.0F) / 100.0F;
	}

	public static float opacityMultiplierForUser(@Nullable LivingEntity user) {
		ClientModSettings.Settings settings = ClientModSettings.getSettingsReadOnly();
		float transparency = isOwnStandUser(user) ? settings.standTransparency : settings.standOthersTransparency;
		return opacityFromTransparency(transparency);
	}

	public static float apply(float lifecycleAlpha, @Nullable LivingEntity user) {
		return Mth.clamp(lifecycleAlpha * opacityMultiplierForUser(user), 0.0F, 1.0F);
	}

	public static boolean isOwnStandUser(@Nullable LivingEntity user) {
		return user != null && user == Minecraft.getInstance().player;
	}
}
