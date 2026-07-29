package com.github.standobyte.jojo.api.control;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

/**
 * A deny-only policy result. No provider can override another provider's deny.
 */
public record PlayerOperationDecision(
		boolean denied,
		@Nullable Component denialMessage) {

	private static final PlayerOperationDecision PASS =
			new PlayerOperationDecision(false, null);

	public static PlayerOperationDecision pass() {
		return PASS;
	}

	public static PlayerOperationDecision deny() {
		return new PlayerOperationDecision(true, null);
	}

	public static PlayerOperationDecision deny(Component message) {
		return new PlayerOperationDecision(true, message);
	}
}
