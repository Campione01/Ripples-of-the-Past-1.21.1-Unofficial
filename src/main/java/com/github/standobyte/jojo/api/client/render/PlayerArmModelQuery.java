package com.github.standobyte.jojo.api.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;

public record PlayerArmModelQuery(
		Player player,
		PlayerModel<?> model) {}
