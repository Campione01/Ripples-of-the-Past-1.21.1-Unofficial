package com.github.standobyte.jojo.api.playerpower;

import java.util.Optional;

import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerDataLifecycleSmokeTest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PlayerPowerTransitionsSmokeTest {
	private PlayerPowerTransitionsSmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println(
				"PlayerPower transitions focused smoke passed.");
	}

	public static void run() {
		PlayerPowerDataLifecycleSmokeTest.run();

		ResourceLocation typeId =
				ResourceLocation.fromNamespaceAndPath(
						"rotp_test", "retained_power");
		CompoundTag data = new CompoundTag();
		data.putInt("Energy", 37);
		PlayerPowerTransitions.Snapshot snapshot =
				new PlayerPowerTransitions.Snapshot(typeId, data);

		data.putInt("Energy", 99);
		check(snapshot.powerDataNbt().getInt("Energy") == 37,
				"snapshot retained caller-owned NBT");
		CompoundTag exposed = snapshot.powerDataNbt();
		exposed.putInt("Energy", 11);
		check(snapshot.powerDataNbt().getInt("Energy") == 37,
				"snapshot exposed mutable internal NBT");

		Optional<PlayerPowerTransitions.Snapshot> loaded =
				PlayerPowerTransitions.Snapshot.load(
						snapshot.save());
		check(loaded.isPresent(),
				"valid temporary transition snapshot did not load");
		check(loaded.get().powerTypeId().equals(typeId),
				"snapshot type ID changed during save/load");
		check(loaded.get().powerDataNbt().getInt("Energy") == 37,
				"snapshot data changed during save/load");

		check(PlayerPowerTransitions.Snapshot.load(
						new CompoundTag()).isEmpty(),
				"incomplete snapshot was accepted");
		CompoundTag malformed = snapshot.save();
		malformed.putString("PowerType", "invalid id");
		check(PlayerPowerTransitions.Snapshot.load(
						malformed).isEmpty(),
				"malformed snapshot type ID was accepted");
	}

	private static void check(
			boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
