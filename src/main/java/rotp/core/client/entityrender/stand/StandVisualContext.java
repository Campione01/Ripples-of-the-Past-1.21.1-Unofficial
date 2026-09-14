package rotp.core.client.entityrender.stand;

import rotp.core.config.client.ClientModSettings;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.LivingEntity;

public record StandVisualContext(
		float lifecycleAlpha,
		float opacityMultiplier,
		float effectiveAlpha,
		boolean ownStand,
		boolean classicObstruction,
		boolean classicOutline) {

	public static StandVisualContext world(StandEntity entity, float lifecycleAlpha) {
		ClientModSettings.Settings settings = ClientModSettings.getSettingsReadOnly();
		LivingEntity user = entity.getUser();
		float multiplier = StandOpacityPolicy.opacityMultiplierForUser(user);
		float effectiveAlpha = StandOpacityPolicy.apply(lifecycleAlpha, user);
		boolean classicEnabled = settings.classicStandObstruction;
		return new StandVisualContext(
				lifecycleAlpha,
				multiplier,
				effectiveAlpha,
				StandOpacityPolicy.isOwnStandUser(user),
				classicEnabled,
				classicEnabled && settings.standOutline);
	}

	public static StandVisualContext preview() {
		return new StandVisualContext(1.0F, 1.0F, 1.0F, false, false, false);
	}
}
