package com.github.standobyte.jojo.client.entityrender.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.shader.StandSurfaceDraw;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class StandSurfaceDiagnosticSmokeTest {
	private static final String TARGET = "test:stand";

	private StandSurfaceDiagnosticSmokeTest() {}

	public static void main(String[] args) {
		verifyDefaultOff();
		verifyExactTarget();
		verifyRenderGuards();
		verifyNearestSurfaceBlend();
		verifyIndependentSurfaceMerge();
		verifySubmissionIndependentOrder();
		verifyScratchScopeContract();
		System.out.println("Stand surface policy, blend math and source-scope checks passed; live draw validation remains required");
	}

	private static void verifyDefaultOff() {
		String previous = System.getProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY);
		try {
			System.clearProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY);
			check(StandSurfaceDiagnosticPolicy.configuredTarget().isEmpty(),
					"an unset diagnostic target must default to empty");
			check(!allows(StandSurfaceDiagnosticPolicy.configuredTarget(), TARGET, 0.8F, true, 0),
					"the diagnostic must remain disabled without an explicit target");
			System.setProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY, TARGET);
			check(StandSurfaceDiagnosticPolicy.configuredTarget().equals(TARGET),
					"the explicit system property was not read");
		}
		finally {
			if (previous == null) {
				System.clearProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY);
			}
			else {
				System.setProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY, previous);
			}
		}
	}

	private static void verifyExactTarget() {
		check(allows(TARGET, TARGET, 0.8F, true, 0), "an exact target must opt in");
		for (String target : new String[] { null, "", " ", "test", "stand", "test:*", "test:stand ", "TEST:stand" }) {
			check(!allows(target, TARGET, 0.8F, true, 0), "a non-exact target enabled the diagnostic: " + target);
		}
		check(!allows(TARGET, "other:stand", 0.8F, true, 0), "a different namespace was admitted");
		check(!allows(TARGET, "test:stand_other", 0.8F, true, 0), "an entity prefix was admitted");
		check(!allows(TARGET, null, 0.8F, true, 0), "a missing entity type was admitted");
	}

	private static void verifyRenderGuards() {
		for (float alpha : new float[] { Float.NEGATIVE_INFINITY, -0.1F, 0.0F, 1.0F, 1.1F, Float.POSITIVE_INFINITY, Float.NaN }) {
			check(!allows(TARGET, TARGET, alpha, true, 0), "an unsupported alpha was admitted: " + alpha);
		}
		check(allows(TARGET, TARGET, Float.MIN_VALUE, true, 0), "a positive fractional alpha was rejected");
		check(allows(TARGET, TARGET, Math.nextDown(1.0F), true, 0), "a fractional alpha below one was rejected");
		check(!allows(TARGET, TARGET, 0.8F, false, 0), "an invisible body was admitted");
		for (int exclusions = 1; exclusions < 16; exclusions++) {
			check(!allows(TARGET, TARGET, 0.8F, true, exclusions),
					"mask, classic obstruction, afterimage or barrage exclusion failed: " + exclusions);
		}
	}

	private static boolean allows(String target, String entityType, float alpha, boolean bodyVisible, int exclusions) {
		return StandSurfaceDiagnosticPolicy.useNearestSurface(target, entityType, alpha, bodyVisible,
				(exclusions & 1) != 0, (exclusions & 2) != 0, (exclusions & 4) != 0, (exclusions & 8) != 0);
	}

	private static void verifyNearestSurfaceBlend() {
		// A split front plane can straddle its back plane's QUADS sorting key.
		double alpha = 0.8;
		double frontGray = 0.8;
		double backGray = 0.2;
		double frontOnlyAlpha = alpha;
		double backThenFrontAlpha = alpha + alpha * (1.0 - alpha);
		check(close(frontOnlyAlpha, 0.8) && close(backThenFrontAlpha, 0.96),
				"over blending must expose the one-layer versus two-layer counterexample");
		double frontOnlyColor = frontGray * alpha;
		double backThenFrontColor = frontGray * alpha + backGray * alpha * (1.0 - alpha);
		check(!close(frontOnlyColor, backThenFrontColor), "back-face color contamination was not reproduced");
		double nearestColor = frontGray * alpha + backGray * alpha * 0.0;
		double nearestAlpha = alpha + alpha * 0.0;
		check(close(nearestColor, frontOnlyColor) && close(nearestAlpha, frontOnlyAlpha),
				"SRC_ALPHA/ZERO and ONE/ZERO must replace the previous surface with premultiplied RGBA");
	}

	private static void verifyIndependentSurfaceMerge() {
		double nearColor = 0.8 * 0.8;
		double farColor = 0.2 * 0.8;
		double combinedColor = nearColor + farColor * (1.0 - 0.8);
		double combinedAlpha = 0.8 + 0.8 * (1.0 - 0.8);
		check(close(combinedColor, 0.672) && close(combinedAlpha, 0.96),
				"independent bodies must over-composite instead of replacing the farther body");
		check(close(Math.min(0.2, 0.6), Math.min(0.6, 0.2)),
				"the depth-only resolve must keep the nearest depth in either draw order");
		check(close(0.8 + combinedAlpha * (1.0 - 0.8), 0.992),
				"a third body must preserve both earlier bodies' alpha contributions");
		check(0.6 < 0.95 && 0.6 > 0.2,
				"a body behind another Stand but in front of the scene requires an independent scene depth seed");
	}

	private static void verifySubmissionIndependentOrder() {
		record Surface(int owner, double depth, double gray) {}
		Surface near = new Surface(7, 2.0, 0.8);
		Surface far = new Surface(3, 6.0, 0.2);
		for (List<Surface> submitted : List.of(List.of(near, far), List.of(far, near))) {
			List<Surface> draws = new ArrayList<>(submitted);
			draws.sort((left, right) -> StandSurfaceDraw.compareBackToFront(
					left.depth(), left.owner(), right.depth(), right.owner()));
			double color = 0;
			double alpha = 0;
			for (Surface draw : draws) {
				color = draw.gray() * 0.8 + color * 0.2;
				alpha = 0.8 + alpha * 0.2;
			}
			check(draws.equals(List.of(far, near)) && close(color, 0.672) && close(alpha, 0.96),
					"body color changed with Iris submission order");
		}
		check(StandSurfaceDraw.compareBackToFront(4.0, 2, 4.0, 9) < 0,
				"equal-depth bodies need a stable owner tie break");
	}

	// This check needs a real NeoForge client; plain Java cannot initialize RenderType's registries.
	public static void verifyBodyBatchBoundaryInBootstrappedClient() {
		ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("test", "textures/stand.png");
		RenderType surface = ModRenderTypes.standSurfaceDiagnostic(texture, false);
		RenderType nextSurface = ModRenderTypes.standSurfaceDiagnostic(texture, false);
		RenderType ordinary = ModRenderTypes.standTranslucent(texture);
		check(surface != nextSurface && !surface.equals(nextSurface),
				"same-texture bodies need distinct RenderType keys for Iris's map-based batching");
		check(!surface.canConsolidateConsecutiveGeometry() && ordinary.canConsolidateConsecutiveGeometry(),
				"only the surface resolve material may disable consecutive body consolidation");
		check(surface.outline().equals(ordinary.outline()) && surface.isOutline() == ordinary.isOutline(),
				"the surface wrapper must preserve the ordinary material's outline mapping");
		check(ModRenderTypes.standSurfaceDiagnostic(texture, true).outline()
				.equals(ModRenderTypes.standTranslucentCull(texture).outline()),
				"the first-person surface wrapper must preserve the culled outline mapping");
		try (ByteBufferBuilder storage = new ByteBufferBuilder(512)) {
			InspectingBufferSource buffers = new InspectingBufferSource(storage);
			appendQuad(buffers.getBuffer(surface));
			appendQuad(buffers.getBuffer(surface));
			check(buffers.vertexCounts.equals(List.of(4)), "a second body did not flush the first body's four vertices");
			buffers.endBatch();
			check(buffers.vertexCounts.equals(List.of(4, 4)), "two same-material bodies were not submitted independently");
		}
		try (ByteBufferBuilder storage = new ByteBufferBuilder(512)) {
			InspectingBufferSource buffers = new InspectingBufferSource(storage);
			appendQuad(buffers.getBuffer(surface));
			appendQuad(buffers.getBuffer(nextSurface));
			buffers.endBatch();
			check(buffers.vertexCounts.equals(List.of(4, 4)), "distinct same-texture body keys did not retain separate batches");
		}
		try (ByteBufferBuilder storage = new ByteBufferBuilder(512)) {
			InspectingBufferSource buffers = new InspectingBufferSource(storage);
			appendQuad(buffers.getBuffer(ordinary));
			appendQuad(buffers.getBuffer(ordinary));
			check(buffers.vertexCounts.isEmpty(), "ordinary Stand geometry stopped consolidating");
			buffers.endBatch();
			check(buffers.vertexCounts.equals(List.of(8)), "the public ordinary material's batching changed");
		}
	}

	private static void appendQuad(VertexConsumer consumer) {
		for (int[] position : new int[][] { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } }) {
			consumer.addVertex(position[0], position[1], 0, 0xCCFFFFFF, 0, 0, 0, 0, 0, 0, 1);
		}
	}

	private static final class InspectingBufferSource extends MultiBufferSource.BufferSource {
		private final List<Integer> vertexCounts = new ArrayList<>();

		private InspectingBufferSource(ByteBufferBuilder storage) {
			super(storage, new LinkedHashMap<>());
		}

		@Override
		public void endBatch(RenderType renderType, BufferBuilder builder) {
			try (MeshData mesh = builder.build()) {
				if (mesh != null) {
					vertexCounts.add(mesh.drawState().vertexCount());
				}
			}
			if (renderType.equals(lastSharedType)) {
				lastSharedType = null;
			}
		}
	}

	private static void verifyScratchScopeContract() {
		Path root = Path.of(System.getProperty("user.dir"));
		String framebuffer = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/shader/StandTranslucencyFramebuffer.java"));
		int captureStart = framebuffer.indexOf("public void drawBodySurface(");
		int drainStart = framebuffer.indexOf("private void drainBodySurfaces()");
		int resolveStart = framebuffer.indexOf("private void resolveBodySurface()");
		int quadStart = framebuffer.indexOf("private void drawSurfaceQuad()");
		check(captureStart >= 0 && drainStart > captureStart && resolveStart > drainStart && quadStart > resolveStart,
				"body capture and resolve scopes are missing");
		String capture = framebuffer.substring(captureStart, drainStart);
		String drain = framebuffer.substring(drainStart, resolveStart);
		String resolve = framebuffer.substring(resolveStart, quadStart);
		require(framebuffer, "private final RenderTargetState surfaceTargetState = new RenderTargetState();");
		require(capture, "try (meshData)");
		require(capture, "StandSurfaceDraw.capture(");
		require(capture, "pendingSurfaces.add(draw);");
		check(!capture.contains("resolveBodySurface();") && !capture.contains("copyDepthFrom("),
				"arrival-time draws must not resolve before ordering and final scene depth");
		require(drain, "StandSurfaceDraw.compareBackToFront(");
		require(drain, "int sourceFramebuffer = surfaceTargetState.drawFramebuffer();");
		require(drain, "sourceFramebuffer == buffer.frameBufferId");
		require(drain, "copyDepthFrom(surfaceBuffer, sourceFramebuffer,");
		check(!drain.contains("copyDepthFrom(surfaceBuffer, buffer.frameBufferId"),
				"another Stand's accumulated depth became the next body's scene seed");
		check(drain.indexOf("surfaceBuffer.clear(") < drain.indexOf("copyDepthFrom(surfaceBuffer,")
				&& drain.indexOf("copyDepthFrom(surfaceBuffer,") < drain.indexOf("draw.draw();")
				&& drain.indexOf("draw.draw();") < drain.indexOf("resolveBodySurface();"),
				"scratch clear, scene seed, body draw and resolve are out of order");
		require(drain, "surfaceTargetState.restoreAfterLogicalMainTarget(");
		require(drain, "surfaceTargetState.restore();");
		require(drain, "RenderSystem.restoreGlState(glState);");
		require(drain, "RenderSystem.setShader(() -> previousShader);");
		require(drain, "clearPendingSurfaces();");
		String bodyLoop = drain.substring(drain.indexOf("for (List<StandSurfaceDraw> group : groups)"));
		check(bodyLoop.indexOf("RenderSystem.colorMask(true, true, true, true);") < bodyLoop.indexOf("surfaceBuffer.clear("),
				"the previous depth-only pass left the next scratch clear/color draw masked out");
		require(bodyLoop, "RenderSystem.enableCull();");
		check(bodyLoop.indexOf("surfaceBuffer.clear(") < bodyLoop.indexOf("for (StandSurfaceDraw draw : group)"),
				"same-body overlays must keep body depth rather than clearing their own scratch");
		require(drain, "drawsByGroup.computeIfAbsent(draw.groupKey()");
		require(drain, "Comparator.comparing(StandSurfaceDraw::bodyPass).reversed()");
		require(drain, ".thenComparingInt(StandSurfaceDraw::emissionOrder)");
		String ownedDraw = read(root.resolve("src/main/java/com/github/standobyte/jojo/client/shader/StandSurfaceDraw.java"));
		require(ownedDraw, "vertexBuffer.upload(meshData);");
		require(ownedDraw, "new Matrix4f(RenderSystem.getModelViewMatrix())");
		require(ownedDraw, "new Matrix4f(RenderSystem.getProjectionMatrix())");
		require(ownedDraw, "RenderSystem.getShaderColor().clone()");
		require(ownedDraw, "snapshot.apply(shader);");
		require(ownedDraw, "surfaceMaterial.clearRenderState();");
		require(resolve, "RenderSystem.colorMask(true, true, true, true);");
		require(resolve, "RenderSystem.disableDepthTest();");
		require(resolve, "RenderSystem.depthMask(false);");
		require(resolve, "RenderSystem.colorMask(false, false, false, false);");
		require(resolve, "RenderSystem.depthFunc(GL11.GL_LEQUAL);");
		require(resolve, "RenderSystem.depthMask(true);");
		check(resolve.split("drawSurfaceQuad\\(\\);", -1).length == 3,
				"the body resolve needs one RGBA pass and one depth-only pass");
		require(framebuffer, "ModShaders.loadPrivateTargetCoreShader(event, JojoMod.resLoc(\"stand_surface_resolve\")");
		String renderTypes = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/rendertype/ModRenderTypes.java"));
		require(renderTypes, ".setOutputState(renderState.isolatedOutput ? MAIN_TARGET : STAND_TRANSLUCENCY_TARGET)");
		require(renderTypes, "return queuedStandTranslucent(texture, outline, false);");
		require(renderTypes, "return queuedStandTranslucent(texture, true, true);");
		require(framebuffer, "return acceptingSurfaceDraws && compositeShader != null && surfaceResolveShader != null;");
		require(renderTypes, "material.setupRenderState();");
		require(renderTypes, "material.clearRenderState();");
		require(renderTypes, "ClientRenderCompatibility.isIrisShadowPass() ? shadowMaterial : fallbackMaterial");
		require(renderTypes, "!ClientRenderCompatibility.isIrisShadowPass()");
		String shader = read(root.resolve(
				"src/main/resources/assets/jojo_ripples/shaders/core/stand_surface_resolve.fsh"));
		require(shader, "if (surfaceColor.a <= 0.0)");
		require(shader, "fragColor = surfaceColor;");
		require(shader, "gl_FragDepth = texture(SurfaceDepthSampler, texCoord).r;");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void require(String source, String expected) {
		check(source.contains(expected), "missing surface resolve contract: " + expected);
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) < 1.0E-10;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
