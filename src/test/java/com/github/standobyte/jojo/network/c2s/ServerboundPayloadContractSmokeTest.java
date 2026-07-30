package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.DebugItem;
import com.github.standobyte.jojo.network.c2s.ClWalkmanControlsPacket.Action;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class ServerboundPayloadContractSmokeTest {
	private ServerboundPayloadContractSmokeTest() {}

	public static void run() {
		check(ClDebugCommandPacket.Handler.holdsDebugItemTypes(
				DebugItem.class, Item.class),
				"legitimate creative debug tool in the main hand must remain authorized");
		check(ClDebugCommandPacket.Handler.holdsDebugItemTypes(
				Item.class, DebugItem.class),
				"legitimate creative debug tool in the off hand must remain authorized");
		check(!ClDebugCommandPacket.Handler.holdsDebugItemTypes(
				Item.class, Item.class),
				"ordinary item must not authorize debug commands");

		check(ClWalkmanControlsPacket.Handler.isValidControl(
				Action.VOLUME, 0.5F, 0),
				"normal Walkman volume must be accepted");
		check(ClWalkmanControlsPacket.Handler.isValidControl(
				Action.PLAY, -1.0F, 0),
				"non-volume Walkman controls must preserve sentinel volume");
		check(ClWalkmanControlsPacket.Handler.isValidControl(
				Action.POSITION, -1.0F, 0),
				"first cassette track must be accepted");
		check(!ClWalkmanControlsPacket.Handler.isValidControl(
				Action.VOLUME, Float.NaN, 0),
				"NaN Walkman volume must be rejected");
		check(!ClWalkmanControlsPacket.Handler.isValidControl(
				Action.POSITION, -1.0F, -1),
				"negative cassette track must be rejected");

		ResourceLocation currentStand =
				ResourceLocation.fromNamespaceAndPath("jojo_ripples", "star_platinum");
		ResourceLocation otherStand =
				ResourceLocation.fromNamespaceAndPath("jojo_ripples", "the_world");
		check(ClSetStandSkinPacket.Handler.matchesStandId(currentStand, currentStand),
				"skin request for the player's current Stand must be accepted");
		check(!ClSetStandSkinPacket.Handler.matchesStandId(currentStand, otherStand),
				"skin request claiming another Stand must be rejected");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
