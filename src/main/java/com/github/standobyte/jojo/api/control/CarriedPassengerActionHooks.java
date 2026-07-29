package com.github.standobyte.jojo.api.control;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-authoritative handlers for drop actions while a player carries an
 * addon-owned passenger.
 *
 * <p>The client supplies only the vanilla action. Handlers receive the actual
 * connection player and must resolve any carried passenger from server state.
 * Returning {@code true} cancels the vanilla item drop.</p>
 */
public final class CarriedPassengerActionHooks {
	private static final Map<ResourceLocation,
			CarriedPassengerActionHandler> HANDLERS =
					new LinkedHashMap<>();

	private CarriedPassengerActionHooks() {}

	public static synchronized void register(
			ResourceLocation owner,
			CarriedPassengerActionHandler handler) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(handler, "handler");
		if (HANDLERS.putIfAbsent(owner, handler) != null) {
			throw new IllegalStateException(
					"Duplicate carried-passenger action handler: "
							+ owner);
		}
	}

	@ApiStatus.Internal
	public static boolean handle(
			ServerPlayer player,
			ServerboundPlayerActionPacket.Action action) {
		if (player == null
				|| !(player.level() instanceof ServerLevel)
				|| player.isRemoved()
				|| !player.isAlive()
				|| !isDropAction(action)) {
			return false;
		}
		List<Map.Entry<ResourceLocation,
				CarriedPassengerActionHandler>> snapshot;
		synchronized (CarriedPassengerActionHooks.class) {
			snapshot = new ArrayList<>(HANDLERS.entrySet());
		}
		for (Map.Entry<ResourceLocation,
				CarriedPassengerActionHandler> entry : snapshot) {
			try {
				if (entry.getValue().handle(player, action)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Carried-passenger action handler {} failed.",
						entry.getKey(),
						error);
			}
		}
		return false;
	}

	private static boolean isDropAction(
			ServerboundPlayerActionPacket.Action action) {
		return action
						== ServerboundPlayerActionPacket.Action.DROP_ITEM
				|| action
						== ServerboundPlayerActionPacket.Action
								.DROP_ALL_ITEMS;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(HANDLERS.keySet());
	}

	static synchronized void resetForTests() {
		HANDLERS.clear();
	}
}
