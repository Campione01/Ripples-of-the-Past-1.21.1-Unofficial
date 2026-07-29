package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.standobyte.jojo.api.client.render.ScopedPlayerModelVisibility.Part;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

public final class PlayerBaseModelVisibilityPoliciesSmokeTest {
	private PlayerBaseModelVisibilityPoliciesSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		PlayerBaseModelVisibilityPolicies.resetForTests();
		PlayerModel<?> defaultModel = model(false);
		PlayerModel<?> slimModel = model(true);

		try (PlayerBaseModelVisibilityPolicies.RenderFrame ignoredRender =
						PlayerBaseModelVisibilityPolicies.enterRenderFrame(
								null, defaultModel, 0.0F);
				PlayerBaseModelVisibilityPolicies.FrameScope ignoredDraw =
						PlayerBaseModelVisibilityPolicies.beginFrame(
								null, defaultModel, 0.0F)) {
			check(defaultModel.hat.visible,
					"no-provider frame changed the model");
		}

		ResourceLocation failing = id("failing");
		ResourceLocation outerSkin = id("outer_skin");
		AtomicInteger successfulCalls = new AtomicInteger();
		try (PlayerBaseModelVisibilityPolicies.Registration ignoredFailing =
						PlayerBaseModelVisibilityPolicies.register(
								failing,
								query -> {
									query.model().head.visible = false;
									throw new IllegalStateException(
											"expected smoke failure");
								});
				PlayerBaseModelVisibilityPolicies.Registration ignoredPolicy =
						PlayerBaseModelVisibilityPolicies.register(
								outerSkin,
								query -> {
									successfulCalls.incrementAndGet();
									return Set.of(
											Part.HAT,
											Part.JACKET,
											Part.LEFT_SLEEVE,
											Part.RIGHT_SLEEVE,
											Part.LEFT_PANTS,
											Part.RIGHT_PANTS);
								})) {
			check(PlayerBaseModelVisibilityPolicies.registeredOwners()
							.equals(List.of(failing, outerSkin)),
					"owner registration order changed");
			expectIllegalState(() ->
					PlayerBaseModelVisibilityPolicies.register(
							outerSkin, query -> Set.of()));

			assertDefaultAndSlim(defaultModel, slimModel);
			assertCaptureNestedAndInterleaved(
					defaultModel, slimModel, successfulCalls);
			assertExceptionalAbortRestores(defaultModel);
			check(successfulCalls.get() == 5,
					"policy was not evaluated once per frame");
		}

		try (PlayerBaseModelVisibilityPolicies.FrameScope ignored =
				PlayerBaseModelVisibilityPolicies.beginFrame(
						null, defaultModel, 0.0F)) {
			check(defaultModel.hat.visible
							&& defaultModel.jacket.visible,
					"closed policies remained active");
		}
		PlayerBaseModelVisibilityPolicies.resetForTests();
		verifyMixinBoundary();
	}

	private static void assertDefaultAndSlim(
			PlayerModel<?> defaultModel,
			PlayerModel<?> slimModel) {
		for (PlayerModel<?> model : List.of(defaultModel, slimModel)) {
			model.rightSleeve.visible = false;
			try (PlayerBaseModelVisibilityPolicies.RenderFrame ignoredRender =
							PlayerBaseModelVisibilityPolicies.enterRenderFrame(
									null, model, 0.25F);
					PlayerBaseModelVisibilityPolicies.FrameScope ignoredDraw =
							PlayerBaseModelVisibilityPolicies.beginFrame(
									null, model, 0.25F)) {
				assertOuterHidden(model);
				check(model.head.visible
								&& model.body.visible
								&& model.leftArm.visible
								&& model.rightArm.visible
								&& model.leftLeg.visible
								&& model.rightLeg.visible,
						"base body geometry was hidden");
			}
			check(model.hat.visible
							&& model.jacket.visible
							&& model.leftSleeve.visible
							&& !model.rightSleeve.visible
							&& model.leftPants.visible
							&& model.rightPants.visible,
					"default/slim visibility was not restored exactly");
			model.rightSleeve.visible = true;
		}
	}

	private static void assertCaptureNestedAndInterleaved(
			PlayerModel<?> defaultModel,
			PlayerModel<?> slimModel,
			AtomicInteger calls) {
		try (PlayerBaseModelVisibilityPolicies.RenderFrame outerFrame =
				PlayerBaseModelVisibilityPolicies.enterRenderFrame(
						null, defaultModel, 0.5F)) {
			try (PlayerBaseModelVisibilityPolicies.FrameScope baseDraw =
					PlayerBaseModelVisibilityPolicies.beginFrame(
							null, defaultModel, 0.5F)) {
				assertOuterHidden(defaultModel);
			}
			check(defaultModel.hat.visible
							&& defaultModel.jacket.visible,
					"base visibility leaked into later layers");
		}
		int afterOuterDecision = calls.get();
		try {
			EntityMaskPostEffect.runCapturePassForTest(() -> {
				try (PlayerBaseModelVisibilityPolicies.RenderFrame capture =
									PlayerBaseModelVisibilityPolicies
											.enterRenderFrame(
													null,
													defaultModel,
													0.5F);
						PlayerBaseModelVisibilityPolicies.FrameScope
								captureDraw =
										PlayerBaseModelVisibilityPolicies
												.beginFrame(
														null,
														defaultModel,
														0.5F)) {
					check(calls.get() == afterOuterDecision,
							"capture rerender redispatched providers");
					assertOuterHidden(defaultModel);
					throw new TestAbort();
				}
			});
		}
		catch (TestAbort expected) {
			// Capture pass and both visibility scopes must unwind.
		}
		check(!EntityMaskPostEffect.isCapturePass()
						&& defaultModel.hat.visible
						&& defaultModel.jacket.visible,
				"capture exception leaked guard or visibility");
		PlayerModel<?> uncachedModel = model(false);
		EntityMaskPostEffect.runCapturePassForTest(() -> {
			try (PlayerBaseModelVisibilityPolicies.RenderFrame capture =
								PlayerBaseModelVisibilityPolicies
										.enterRenderFrame(
												null,
												uncachedModel,
												0.5F);
					PlayerBaseModelVisibilityPolicies.FrameScope captureDraw =
							PlayerBaseModelVisibilityPolicies.beginFrame(
									null, uncachedModel, 0.5F)) {
				check(calls.get() == afterOuterDecision
								&& uncachedModel.hat.visible,
						"uncached capture dispatched or invented a decision");
			}
		});

		try (PlayerBaseModelVisibilityPolicies.RenderFrame
						interleavedFrame =
								PlayerBaseModelVisibilityPolicies
										.enterRenderFrame(
												null,
												slimModel,
												0.5F);
				PlayerBaseModelVisibilityPolicies.FrameScope
						interleavedDraw =
								PlayerBaseModelVisibilityPolicies
										.beginFrame(
												null,
												slimModel,
												0.5F)) {
			check(calls.get() == afterOuterDecision + 1,
					"interleaved player did not resolve independently");
			assertOuterHidden(slimModel);
		}
		check(defaultModel.hat.visible
						&& defaultModel.jacket.visible
						&& slimModel.hat.visible
						&& slimModel.jacket.visible,
				"nested/interleaved frame was not restored");
		check(PlayerBaseModelVisibilityPolicies
						.activeRenderFrameDepthForTests() == 0,
				"capture render decision frame leaked");
	}

	private static void assertExceptionalAbortRestores(
			PlayerModel<?> model) {
		try {
			try (PlayerBaseModelVisibilityPolicies.RenderFrame ignoredRender =
							PlayerBaseModelVisibilityPolicies.enterRenderFrame(
									null, model, 0.75F);
					PlayerBaseModelVisibilityPolicies.FrameScope ignoredDraw =
							PlayerBaseModelVisibilityPolicies.beginFrame(
									null, model, 0.75F)) {
				assertOuterHidden(model);
				throw new TestAbort();
			}
		}
		catch (TestAbort expected) {
			// Simulates a later chained render wrapper aborting or failing.
		}
		check(model.hat.visible
						&& model.jacket.visible
						&& model.leftSleeve.visible
						&& model.rightSleeve.visible
						&& model.leftPants.visible
						&& model.rightPants.visible,
				"exceptional/aborted frame was not restored");
	}

	private static void assertOuterHidden(PlayerModel<?> model) {
		check(!model.hat.visible
						&& !model.jacket.visible
						&& !model.leftSleeve.visible
						&& !model.rightSleeve.visible
						&& !model.leftPants.visible
						&& !model.rightPants.visible,
				"outer player skin was not hidden");
	}

	private static PlayerModel<?> model(boolean slim) {
		return new PlayerModel<>(
				LayerDefinition.create(
						PlayerModel.createMesh(
								CubeDeformation.NONE, slim),
						64, 64)
						.bakeRoot(),
				slim);
	}

	private static void verifyMixinBoundary() {
		Path path = Path.of(
				System.getProperty("user.dir"),
				"src/main/java/com/github/standobyte/jojo/mixin/client/"
						+ "render/PlayerBaseModelVisibilityMixin.java");
		String source;
		try {
			source = Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
		check(source.contains("@WrapMethod")
						&& source.contains("enterRenderFrame(")
						&& source.contains("@WrapOperation")
						&& source.contains("renderToBuffer(")
						&& source.contains(
								"try (FrameScope ignored ="),
				"base-model call is no longer scoped with guaranteed restore");
		Path policyPath = Path.of(
				System.getProperty("user.dir"),
				"src/main/java/com/github/standobyte/jojo/api/client/"
						+ "render/PlayerBaseModelVisibilityPolicies.java");
		try {
			String policySource = Files.readString(policyPath);
			check(policySource.contains(
									"EntityMaskPostEffect.isCapturePass()")
							&& policySource.contains(
									"cachedFrameDecision(player, model)"),
					"entity-mask capture no longer reuses frame decisions");
		}
		catch (IOException error) {
			throw new AssertionError(
					"failed to read " + policyPath, error);
		}
		check(source.contains(
								"entity instanceof AbstractClientPlayer")
						&& source.contains(
								"renderedModel instanceof PlayerModel"),
				"base visibility hook is no longer player-only");
		check(!source.contains("RenderPlayerEvent")
						&& !source.contains("renderHand"),
				"policy leaked into full render or first-person arms");
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
		throw new AssertionError("duplicate owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class TestAbort extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
