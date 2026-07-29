package com.github.standobyte.jojo.api.client.render;

import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.client.player.LocalPlayer;

public record FirstPersonStandRenderQuery(
		LocalPlayer viewer,
		StandEntity stand,
		float partialTick) {}
