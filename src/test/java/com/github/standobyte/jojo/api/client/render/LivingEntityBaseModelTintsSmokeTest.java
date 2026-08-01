package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;

public final class LivingEntityBaseModelTintsSmokeTest {
	private LivingEntityBaseModelTintsSmokeTest() {}

	public static void run() {
		LivingEntityBaseModelTints.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation tint = id("tint");
		AtomicInteger calls = new AtomicInteger();

		LivingEntityBaseModelTints.register(failed, query -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected smoke failure");
		});
		LivingEntityBaseModelTints.register(tint, query -> {
			calls.incrementAndGet();
			return OptionalInt.of(0xFF123456);
		});

		check(LivingEntityBaseModelTints.apply(
						null, null, 0.5F, 0xFFFFFFFF)
				== 0xFF123456,
				"living base-model tint was not applied");
		check(calls.get() == 2,
				"living tint providers did not run in registration order");
		check(LivingEntityBaseModelTints.registeredOwners()
						.equals(List.of(failed, tint)),
				"living tint provider order changed");
		expectIllegalState(() -> LivingEntityBaseModelTints.register(
				tint, query -> OptionalInt.empty()));
		LivingEntityBaseModelTints.resetForTests();
		verifyMixinContract();
	}

	private static void verifyMixinContract() {
		String source = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "client/v1_21_1_modelanim/"
						+ "LivingEntityRendererMixin.java");
		check(source.contains(
				"@Local(argsOnly = true) LivingEntity entity"),
				"living tint must capture entity through MixinExtras");
		check(source.contains(
				"@Local(argsOnly = true, ordinal = 1)\n"
						+ "\t\t\tfloat partialTick"),
				"living tint must capture partial tick through MixinExtras");
		check(!source.contains(
				"int originalColor,\n"
						+ "\t\t\tLivingEntity entity,"),
				"living tint @ModifyArg must use single-argument mode");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path)).replace("\r\n", "\n");
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + path,
					exception);
		}
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate living tint registration was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
