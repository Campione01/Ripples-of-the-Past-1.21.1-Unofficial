package rotp.core.api.client.render;

import rotp.core.powersystem.standpower.entity.StandEntity;

public record StandMaterialTintQuery(
		StandEntity stand,
		float partialTick) {}
