package com.github.standobyte.jojo.api.block;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class BlockSignalSuppressorsSmokeTest {
	private BlockSignalSuppressorsSmokeTest() {}

	public static void run() {
		BlockSignalSuppressors.resetForTests();
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		AtomicInteger calls = new AtomicInteger();

		BlockSignalSuppressors.register(first, query -> {
			calls.incrementAndGet();
			return false;
		});
		BlockSignalSuppressors.register(second, query -> {
			calls.incrementAndGet();
			return query.kind()
					== BlockSignalQuery.Kind.PRESSURE_PLATE_OUTPUT;
		});

		check(BlockSignalSuppressors.registeredOwners()
						.equals(List.of(first, second)),
				"signal suppressor order changed");
		check(BlockSignalSuppressors.shouldSuppress(
						null,
						BlockPos.ZERO,
						null,
						BlockSignalQuery.Kind.PRESSURE_PLATE_OUTPUT),
				"registered pressure-plate suppression was ignored");
		check(calls.get() == 2,
				"signal suppressors did not run in registration order");
		expectIllegalState(() -> BlockSignalSuppressors.register(
				first, query -> true));
		BlockSignalSuppressors.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate signal suppressor registration was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
