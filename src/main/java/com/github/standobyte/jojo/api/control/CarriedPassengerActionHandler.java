package com.github.standobyte.jojo.api.control;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface CarriedPassengerActionHandler {
	boolean handle(
			ServerPlayer player,
			ServerboundPlayerActionPacket.Action action);
}
