package com.github.standobyte.jojo.api.control;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;

public record PlayerOperationQuery(
		@Nullable ServerPlayer player,
		PlayerOperation operation) {

	public PlayerOperationQuery {
		Objects.requireNonNull(operation, "operation");
	}
}
