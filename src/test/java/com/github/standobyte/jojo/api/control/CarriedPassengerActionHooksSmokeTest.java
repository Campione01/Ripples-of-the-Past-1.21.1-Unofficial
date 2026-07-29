package com.github.standobyte.jojo.api.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class CarriedPassengerActionHooksSmokeTest {
	private CarriedPassengerActionHooksSmokeTest() {}

	public static void run() {
		CarriedPassengerActionHooks.resetForTests();
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		CarriedPassengerActionHooks.register(
				first, (player, action) -> false);
		CarriedPassengerActionHooks.register(
				second, (player, action) -> true);
		check(CarriedPassengerActionHooks.registeredOwners().equals(
						List.of(first, second)),
				"carried-passenger handler order changed");
		expectIllegalState(() ->
				CarriedPassengerActionHooks.register(
						first, (player, action) -> false));

		Path root = Path.of(System.getProperty("user.dir"));
		String api = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/api/control/"
				+ "CarriedPassengerActionHooks.java"));
		check(api.contains("Action.DROP_ITEM")
						&& api.contains("DROP_ALL_ITEMS")
						&& api.contains("player.isRemoved()")
						&& api.contains("player.isAlive()")
						&& !api.contains(
								"SWAP_ITEM_WITH_OFFHAND"),
				"drop-action validation is incomplete");
		String mixin = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/control/"
				+ "ServerGamePacketListenerCarriedPassengerActionMixin.java"));
		for (String token : new String[] {
			"@Shadow public ServerPlayer player",
			"at = @At(\"HEAD\")",
			"cancellable = true",
			"packet.getAction()",
			"CarriedPassengerActionHooks.handle(",
			"ClientboundContainerSetSlotPacket",
			"-2,",
			"0,",
			"player.getInventory().selected",
			"player.connection.send(",
			"player.getInventory().getItem(selected)",
			"ci.cancel()"
		}) {
			check(mixin.contains(token),
					"server drop hook missing: " + token);
		}
		check(!mixin.contains("UUID")
						&& !mixin.contains("getPassengers"),
				"server drop hook trusts a client-selected entity");
		CarriedPassengerActionHooks.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate carried-passenger owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
