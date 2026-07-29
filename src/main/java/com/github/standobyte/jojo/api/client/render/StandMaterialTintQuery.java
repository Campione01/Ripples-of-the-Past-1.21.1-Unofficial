package com.github.standobyte.jojo.api.client.render;

import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

public record StandMaterialTintQuery(
		StandEntity stand,
		float partialTick) {}
