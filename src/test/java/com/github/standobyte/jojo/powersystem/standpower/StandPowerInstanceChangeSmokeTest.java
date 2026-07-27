package com.github.standobyte.jojo.powersystem.standpower;

import java.util.EnumSet;
import java.util.Optional;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import net.minecraft.resources.ResourceLocation;

public final class StandPowerInstanceChangeSmokeTest {
	private StandPowerInstanceChangeSmokeTest() {}

	public static void run() {
		ResourceLocation alphaId = id("jojo_ripples", "test_instance_change_alpha");
		ResourceLocation betaId = id("jojo_ripples", "test_instance_change_beta");
		StandInstance original = TestStandInstances.valid(alphaId);

		check(!StandPower.standInstanceChanged(
				Optional.of(original),
				Optional.of(original.copy())),
				"fully equal Stand instances must be a lifecycle no-op");

		StandInstance unresolved = TestStandInstances.invalid(alphaId);
		StandInstance resolved = TestStandInstances.valid(alphaId);
		check(unresolved.equals(resolved),
				"the resolution regression requires equal serialized Stand content");
		check(StandPower.standInstanceChanged(
				Optional.of(unresolved),
				Optional.of(resolved)),
				"same-ID unresolved to resolved replacement must refresh the power");

		StandInstance changedParts = original.copy();
		changedParts.removePart(StandPart.ARMS);
		check(StandPower.standInstanceChanged(
				Optional.of(original),
				Optional.of(changedParts)),
				"same-type part changes must refresh the manifestation");

		StandInstance changedSkin = original.copy();
		changedSkin.setCustomSkin(Optional.of(
				id("jojo_ripples", "test_instance_change_skin")));
		check(StandPower.standInstanceChanged(
				Optional.of(original),
				Optional.of(changedSkin)),
				"same-type skin changes must refresh the manifestation");

		check(StandPower.standInstanceChanged(
				Optional.of(original),
				Optional.of(TestStandInstances.valid(betaId))),
				"type changes must refresh the manifestation");
		check(!StandPower.standInstanceChanged(Optional.empty(), Optional.empty()),
				"repeated empty state must be a lifecycle no-op");
		check(StandPower.standInstanceChanged(Optional.of(original), Optional.empty()),
				"extraction must be treated as a state change");

		Optional<StandInstance> owned = StandPower.copyStandInstance(Optional.of(original));
		original.removePart(StandPart.LEGS);
		check(owned.orElseThrow().hasPart(StandPart.LEGS),
				"StandPower must store an owned Stand instance");

		StandInstance partsOwner = TestStandInstances.valid(alphaId);
		EnumSet<StandPart> exposedParts = partsOwner.getAllParts();
		exposedParts.clear();
		check(partsOwner.hasPart(StandPart.MAIN_BODY)
				&& partsOwner.hasPart(StandPart.ARMS)
				&& partsOwner.hasPart(StandPart.LEGS),
				"getAllParts must return a defensive copy");
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
