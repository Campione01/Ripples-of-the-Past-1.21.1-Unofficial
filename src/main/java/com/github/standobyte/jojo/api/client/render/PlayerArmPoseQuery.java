package com.github.standobyte.jojo.api.client.render;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public record PlayerArmPoseQuery(
		AbstractClientPlayer player,
		InteractionHand hand,
		ItemStack stack) {}
