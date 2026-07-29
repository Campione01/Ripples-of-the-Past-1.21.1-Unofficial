package com.github.standobyte.jojo.api.block;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.standobyte.jojo.subsystems.entity_soft_landing.EntitySoftLandingRuntimeSmokeTest;

import net.minecraft.resources.ResourceLocation;

public final class EntitySoftLandingProvidersSmokeTest {
	private static int assertions;

	private EntitySoftLandingProvidersSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		assertions = 0;
		EntitySoftLandingProviders.resetForTests();
		check(!EntitySoftLandingProviders
						.resolveProviders(provider -> provider.resolve(null))
						.isHandled(),
				"empty registry did not preserve vanilla behavior");

		ResourceLocation failingOwner = id("failing");
		ResourceLocation handlingOwner = id("handling");
		EntitySoftLandingProviders.register(
				failingOwner,
				query -> {
					throw new IllegalStateException("expected smoke failure");
				});
		EntitySoftLandingProviders.register(
				handlingOwner,
				query -> EntitySoftLandingDecision.softBounce(0.0F, 0.8D));

		EntitySoftLandingDecision decision =
				EntitySoftLandingProviders.resolveProviders(
						provider -> provider.resolve(null));
		check(decision.kind()
						== EntitySoftLandingDecision.Kind.SOFT_BOUNCE,
				"handled decision was not returned");
		check(decision.fallDamageMultiplier() == 0.0F,
				"fall-damage multiplier drifted");
		check(decision.verticalBounceMultiplier() == 0.8D,
				"vertical bounce multiplier drifted");
		check(EntitySoftLandingProviders.registeredOwners().equals(
						java.util.List.of(failingOwner, handlingOwner)),
				"owner registration order drifted");

		expectFailure(
				() -> EntitySoftLandingProviders.register(
						handlingOwner,
						query -> EntitySoftLandingDecision.pass()),
				"duplicate owner was accepted");
		expectFailure(
				() -> EntitySoftLandingDecision.softBounce(-1.0F, 0.8D),
				"negative fall-damage multiplier was accepted");
		expectFailure(
				() -> EntitySoftLandingDecision.softBounce(0.0F, -0.8D),
				"negative bounce multiplier was accepted");
		verifyContactHooks();
		EntitySoftLandingRuntimeSmokeTest.run();

		EntitySoftLandingProviders.resetForTests();
		check(EntitySoftLandingProviders.registeredOwners().isEmpty(),
				"test reset retained soft-landing providers");
		System.out.println(
				"Entity soft-landing provider smoke test passed with "
						+ assertions + " assertions.");
	}

	private static void verifyContactHooks() {
		String blockMixin = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "block/BlockEntitySoftLandingMixin.java");
		String entityMixin = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "directional_gravity/"
						+ "EntityDirectionalGravityMixin.java");
		String runtime = source(
				"src/main/java/com/github/standobyte/jojo/subsystems/"
						+ "entity_soft_landing/"
						+ "EntitySoftLandingRuntime.java");

		check(!blockMixin.contains("entity.blockPosition()"),
				"post-landing hook recomputes the entity block position");
		check(!blockMixin.contains(
						"method = \"updateEntityAfterFallOn\""),
				"post-landing hook still lacks the Entity contact locals");
		check(entityMixin.contains(
						"@Local(ordinal = 0) BlockPos position"),
				"landing contact position is not captured from Entity.move");
		check(entityMixin.contains(
						"@Local(ordinal = 0) BlockState state"),
				"landing contact state is not captured from Entity.move");
		check(entityMixin.contains(
						"EntitySoftLandingRuntime."
								+ "applyPostLandingMovement("),
				"ordinary and directional landings do not share "
						+ "the contact-aware runtime");
		check(!entityMixin.contains(
						"BlockPos pos = entity.getOnPosLegacy();"),
				"directional landing replay uses the global-down helper");
		int query = runtime.indexOf("new EntitySoftLandingQuery(");
		int position = runtime.indexOf("position,", query);
		int state = runtime.indexOf("state,", position);
		check(query >= 0 && position > query && state > position,
				"post-landing query does not retain the captured contact");
		check(runtime.contains(
						"if (!shouldApplyPostLandingMovement("
								+ "decision, movement))"),
				"post-landing runtime does not gate provider takeover "
						+ "on downward movement");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + path,
					exception);
		}
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("smoke", path);
	}

	private static void expectFailure(Runnable action, String message) {
		try {
			action.run();
		}
		catch (RuntimeException expected) {
			assertions++;
			return;
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
