package rotp.core.api.client.render;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.client.player.LocalPlayer;

public record FirstPersonStandRenderQuery(
		LocalPlayer viewer,
		StandEntity stand,
		float partialTick) {}
