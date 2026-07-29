package com.github.standobyte.jojo.api.timestop;

import net.minecraft.server.level.ServerPlayer;

/**
 * Supplies addon-defined time-stop awareness for one player. Implementations
 * must be server-safe and side-effect free.
 */
@FunctionalInterface
public interface TimeStopAwarenessProvider {
	TimeStopAwareness getAwareness(ServerPlayer player);
}
