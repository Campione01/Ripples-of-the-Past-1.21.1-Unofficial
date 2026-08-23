package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.resources.ResourceLocation;

public final class EntityMaskPostEffectSmokeTest {
	private EntityMaskPostEffectSmokeTest() {}

	public static void run() {
		verifyOwnerLifecycle();
		verifyCaptureGuard();
		verifyCaptureRenderTypes();
		verifyFailureEpisodeLatch();
		verifyUvMapping();
		verifySourceBoundary();
		verifyStateNeutralPrivateCaptureBoundary();
	}

	public static void main(String[] args) {
		run();
	}

	private static void verifyOwnerLifecycle() {
		ResourceLocation first = id("first");
		ResourceLocation tied = id("tied");
		ResourceLocation last = id("last");
		try (EntityMaskPostEffect ignoredLast =
						EntityMaskPostEffect.register(
								last, 20, context -> {});
				EntityMaskPostEffect ignoredTied =
						EntityMaskPostEffect.register(
								tied, 0, context -> {});
				EntityMaskPostEffect ignoredFirst =
						EntityMaskPostEffect.register(
								first, 0, context -> {})) {
			check(EntityMaskPostEffect.registeredOwnersForTest()
							.equals(List.of(first, tied, last)),
					"entity mask owner order drifted");
			boolean duplicateRejected = false;
			try {
				EntityMaskPostEffect.register(
						first, 99, context -> {});
			}
			catch (IllegalStateException expected) {
				duplicateRejected = true;
			}
			check(duplicateRejected, "duplicate mask owner was accepted");
		}
		check(EntityMaskPostEffect.registeredOwnersForTest().isEmpty(),
				"closed entity mask effects remained registered");
	}

	private static void verifyCaptureGuard() {
		check(!EntityMaskPostEffect.isCapturePass(),
				"capture guard leaked before a capture");
		boolean failed = false;
		try {
			EntityMaskPostEffect.runCapturePassForTest(() -> {
				check(EntityMaskPostEffect.isCapturePass(),
						"capture guard was not visible");
				EntityMaskPostEffect.runCapturePassForTest(() ->
						check(EntityMaskPostEffect.isCapturePass(),
								"nested capture lost its guard"));
				throw new IllegalStateException("expected");
			});
		}
		catch (IllegalStateException expected) {
			failed = true;
		}
		check(failed, "capture exception did not propagate");
		check(!EntityMaskPostEffect.isCapturePass(),
				"capture guard leaked after an exception");

		AtomicBoolean visibleOnOtherThread =
				new AtomicBoolean(true);
		EntityMaskPostEffect.runCapturePassForTest(() -> {
			Thread otherThread = new Thread(
					() -> visibleOnOtherThread.set(
							EntityMaskPostEffect.isCapturePass()));
			otherThread.start();
			try {
				otherThread.join();
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new AssertionError(
						"capture thread check was interrupted",
						error);
			}
		});
		check(!visibleOnOtherThread.get(),
				"capture guard escaped its render thread");
	}

	private static void verifyUvMapping() {
		check(EntityMaskPostEffect.clipCoordinate(0.0F) == -1.0F,
				"framebuffer U/V zero no longer maps to clip minus one");
		check(EntityMaskPostEffect.clipCoordinate(0.5F) == 0.0F,
				"framebuffer U/V midpoint no longer maps to clip zero");
		check(EntityMaskPostEffect.clipCoordinate(1.0F) == 1.0F,
				"framebuffer U/V one no longer maps to clip one");
	}

	private static void verifyCaptureRenderTypes() {
		check(EntityMaskPostEffect.supportsCaptureFormatForTest(
						DefaultVertexFormat.NEW_ENTITY),
				"NEW_ENTITY geometry is not captured");
		check(EntityMaskPostEffect.supportsCaptureFormatForTest(
						DefaultVertexFormat.BLOCK),
				"solid/translucent-moving BLOCK geometry is not captured");
		check(!EntityMaskPostEffect.supportsCaptureFormatForTest(
						DefaultVertexFormat.POSITION_COLOR_NORMAL),
				"unrelated line geometry entered the entity mask");
	}

	private static void verifyFailureEpisodeLatch() {
		EntityMaskPostEffect.FailureEpisodeLatch latch =
				new EntityMaskPostEffect.FailureEpisodeLatch();

		check(latch.tryReportFailure(),
				"first failed group did not report");
		latch.settleBatch(true, true);
		check(latch.isReported(),
				"mixed success/failure batch cleared the failure episode");
		check(!latch.tryReportFailure(),
				"persistent failed group reported again");

		latch.settleBatch(false, false);
		check(latch.isReported(),
				"empty batch cleared the failure episode");
		latch.settleBatch(true, false);
		check(!latch.isReported(),
				"fully successful batch did not recover the latch");
		check(latch.tryReportFailure(),
				"new failure after recovery did not report");
	}

	private static void verifySourceBoundary() {
		Path root = Path.of(System.getProperty("user.dir"));
		String source = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/api/"
						+ "client/render/EntityMaskPostEffect.java"));
		int frameMethod = source.indexOf(
				"public static void onFrame(");
		int drained = source.indexOf(
				"effect.drainQueue()", frameMethod);
		int depthCheck = source.indexOf(
				"canSampleSceneDepth(", drained);
		check(source.contains(
						"new IdentityHashMap<>()")
						&& frameMethod >= 0
						&& drained > frameMethod
						&& depthCheck > drained,
				"mask requests are not identity keyed and drained fail-closed");
		check(source.contains("renderer.render(")
						&& source.contains("poseStack.translate(")
						&& source.contains(
								"renderX + renderOffset.x")
						&& source.contains(
								"renderY + renderOffset.y")
						&& source.contains(
								"renderZ + renderOffset.z")
						&& !source.contains("dispatcher.render("),
				"capture no longer calls only the registered renderer");
		check(source.contains(
						"format == DefaultVertexFormat.NEW_ENTITY")
						&& source.contains(
								"format == DefaultVertexFormat.BLOCK")
						&& source.contains(
								"return DISCARDING_VERTICES;"),
				"capture vertex-format ownership drifted");
		int sourceRenderState = source.indexOf(
				"source.setupRenderState();");
		int privateTargetBind = source.indexOf(
				"target.bindWrite(false);", sourceRenderState);
		check(sourceRenderState >= 0
						&& privateTargetBind > sourceRenderState
						&& source.contains(
								"source::clearRenderState")
						&& source.contains(
								"delegate.endBatch();"),
				"BLOCK capture no longer preserves source cutout state");
		check(source.contains("ModShaders"
						+ "::privateTargetEntityMask")
						&& !source.contains("GameRenderer"
								+ "::getRendertypeEntitySolidShader"),
				"mask drawing can still let Iris replace the private FBO");
		String compactSource = compact(source);
		check(count(
						compactSource,
						"renderer.getRenderOffset(entity,partialTick)")
						== 1
						&& compactSource.contains(
								"newPreparedRequest("
										+ "request,renderer,renderOffset)")
						&& compactSource.contains(
								".move(request.renderOffset())")
						&& compactSource.contains(
								"renderX+renderOffset.x")
						&& compactSource.contains(
								"renderY+renderOffset.y")
						&& compactSource.contains(
								"renderZ+renderOffset.z"),
				"renderer offset is not shared by bounds and capture");
		check(!source.contains("renderFlame(")
						&& !source.contains("renderShadow(")
						&& !source.contains("renderHitbox("),
				"dispatcher-owned auxiliary passes entered the mask path");
		check(source.contains(
						"renderedAny |= context.drewComposite()")
				&& source.contains("if (renderedAny) {")
				&& source.contains("blitAuraToCapturedTarget("),
				"a no-draw compositor can still touch the captured target");
		int blitMethod = compactSource.indexOf(
				"privatestaticvoidblitAuraToCapturedTarget(");
		int blitSampler = compactSource.indexOf(
				"shader.setSampler(\"Sampler0\","
						+ "aura.getColorTextureId());",
				blitMethod);
		int blitDraw = compactSource.indexOf(
				"drawClipQuad(", blitSampler);
		check(blitMethod >= 0
						&& blitSampler > blitMethod
						&& blitDraw > blitSampler,
				"raw private-target blit lost its V0 aura sampler");
		check(source.contains("finally {\n\t\t\tstate.restore();")
						&& source.contains(
								"RenderSystem.setProjectionMatrix(")
						&& source.contains(
								"RenderSystem.getModelViewStack().set(")
						&& source.contains(
								"GlStateManager._activeTexture(")
						&& source.contains(
								"RenderSystem.restoreGlState(glState)")
						&& source.contains(
								"RenderSystem.setShader(() -> shader)"),
				"mask state restoration coverage drifted");
		check(source.contains(
						"shader.safeGetUniform(\"uMaskUvMin\")"
								+ ".set(0.0F, 0.0F)")
						&& source.contains(
								"shader.safeGetUniform(\"uMaskUvMax\")"
										+ ".set(1.0F, 1.0F)")
						&& source.contains(
								".setUv(minU, minV)")
						&& source.contains(
								".setUv(maxU, maxV)"),
				"full-size mask UV or framebuffer Y mapping drifted");
	}

	private static void verifyStateNeutralPrivateCaptureBoundary() {
		Path root = Path.of(System.getProperty("user.dir"));
		String renderState = compact(read(root.resolve(
				"src/main/java/com/github/standobyte/v1_21_4_stuff/"
						+ "renderstate/EntityRenderState.java")));
		check(renderState.contains(
						"reusedState.displayFireAnimation="
								+ "EntityMaskPostEffect.isCapturePass()"
								+ "?false:entity.displayFireAnimation();"),
				"mask capture can consume entity fire-animation state");

		String standRenderer = compact(read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/"
						+ "entityrender/stand/StandEntityRenderer.java")));
		check(count(standRenderer, "entity.setNoFireAnimFrame();") == 1
						&& standRenderer.contains(
								"if(!EntityMaskPostEffect.isCapturePass())"
										+ "{entity.setNoFireAnimFrame();}"),
				"mask capture can mutate Stand fire-animation state");

		String obstructionLayer = compact(read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/"
						+ "entityrender/stand/"
						+ "StandClassicObstructionLayer.java")));
		int captureBranch = obstructionLayer.indexOf(
				"if(EntityMaskPostEffect.isCapturePass()){");
		int gameplayBranch = obstructionLayer.indexOf(
				"}else{renderGameplayOutline(", captureBranch);
		int suppliedBuffer = obstructionLayer.indexOf(
				"renderClassicOutline(poseStack,bufferSource,",
				captureBranch);
		int globalOutline = obstructionLayer.indexOf(
				"outlineBufferSource()");
		check(captureBranch >= 0
						&& suppliedBuffer > captureBranch
						&& suppliedBuffer < gameplayBranch
						&& gameplayBranch < globalOutline
						&& count(obstructionLayer,
								"outlineBufferSource()") == 1
						&& count(obstructionLayer,
								"requestOutlineEffect()") == 1,
				"mask capture can write to the global outline buffer");
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_mask_test", path);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static String compact(String source) {
		return source.replaceAll("\\s+", "");
	}

	private static int count(String source, String needle) {
		int matches = 0;
		int from = 0;
		while ((from = source.indexOf(needle, from)) >= 0) {
			matches++;
			from += needle.length();
		}
		return matches;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
