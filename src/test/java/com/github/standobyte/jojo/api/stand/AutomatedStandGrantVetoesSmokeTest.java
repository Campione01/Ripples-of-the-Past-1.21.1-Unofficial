package com.github.standobyte.jojo.api.stand;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class AutomatedStandGrantVetoesSmokeTest {
	private AutomatedStandGrantVetoesSmokeTest() {}

	public static void run() {
		AutomatedStandGrantVetoes.resetForTests();
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		ResourceLocation source = id("source");
		List<ResourceLocation> calls = new ArrayList<>();
		AutomatedStandGrantVetoes.register(first, query -> {
			calls.add(first);
			throw new IllegalStateException("isolated test failure");
		});
		AutomatedStandGrantVetoes.register(second, query -> {
			calls.add(second);
			return query.source().equals(source);
		});
		check(AutomatedStandGrantVetoes.evaluate(
						new AutomatedStandGrantQuery(source, null)),
				"Automated Stand grant vetoes did not OR results");
		check(calls.equals(List.of(first, second)),
				"Automated Stand grant veto order changed");
		check(AutomatedStandGrantVetoes.registeredOwners()
						.equals(List.of(first, second)),
				"Automated Stand grant veto owner order changed");
		expectIllegalState(() -> AutomatedStandGrantVetoes.register(
				first, query -> false));
		AutomatedStandGrantVetoes.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
			throw new AssertionError("Duplicate veto was accepted");
		}
		catch (IllegalStateException expected) {
			// Expected.
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
