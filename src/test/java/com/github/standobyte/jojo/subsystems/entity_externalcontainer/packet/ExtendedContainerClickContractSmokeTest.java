package com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.item.ItemStack;

public final class ExtendedContainerClickContractSmokeTest {
	private ExtendedContainerClickContractSmokeTest() {}

	public static void run() {
		Int2ObjectOpenHashMap<ItemStack> changedSlots = new Int2ObjectOpenHashMap<>();
		changedSlots.put(0, null);
		changedSlots.put(2, null);
		check(ClExtendedContainerClickPacket.Handler.changedSlotsAreValid(
				changedSlots, slot -> slot >= 0 && slot < 3),
				"valid changed-slot indices must be accepted");

		changedSlots.put(-1, null);
		check(!ClExtendedContainerClickPacket.Handler.changedSlotsAreValid(
				changedSlots, slot -> slot >= 0 && slot < 3),
				"negative changed-slot index must be rejected");

		changedSlots.remove(-1);
		changedSlots.put(3, null);
		check(!ClExtendedContainerClickPacket.Handler.changedSlotsAreValid(
				changedSlots, slot -> slot >= 0 && slot < 3),
				"changed-slot index at menu size must be rejected");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
