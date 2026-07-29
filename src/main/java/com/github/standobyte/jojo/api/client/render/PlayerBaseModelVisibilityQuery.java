package com.github.standobyte.jojo.api.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Frame-local context for one third-person player base-model draw.
 */
public record PlayerBaseModelVisibilityQuery(
		AbstractClientPlayer player,
		PlayerModel<?> model,
		float partialTick) {}
