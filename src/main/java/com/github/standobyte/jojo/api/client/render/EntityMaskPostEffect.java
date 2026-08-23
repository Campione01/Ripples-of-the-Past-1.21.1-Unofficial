package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import com.github.standobyte.jojo.client.shader.ModShaders;
import com.github.standobyte.jojo.client.shader.core.RenderTargetState;
import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.GlStateBackup;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Owner-keyed exact-entity mask rerender and private-target compositor.
 *
 * <p>The core owns every offscreen target. Requests are identity-keyed,
 * drained before capture, and rerendered through the registered entity
 * renderer into a forced mask buffer. The compositor can sample only that
 * mask, its entity-depth attachment, and the public current main-target depth.
 * No Iris or Super Resolution target or texture is acquired or replaced.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class EntityMaskPostEffect implements AutoCloseable {
	private static final int TRACKED_TEXTURE_UNITS = 12;
	private static final int PRIVATE_BUFFER_SIZE = 786432;
	private static final ResourceLocation WHITE_TEXTURE =
			ResourceLocation.withDefaultNamespace(
					"textures/misc/white.png");
	private static final Object REGISTRY_LOCK = new Object();
	private static final Map<ResourceLocation, EntityMaskPostEffect>
			REGISTERED = new HashMap<>();
	private static final Comparator<EntityMaskPostEffect> ORDERING =
			Comparator.comparingInt(EntityMaskPostEffect::order)
					.thenComparing(effect -> effect.id().toString());
	private static volatile List<EntityMaskPostEffect> snapshot =
			List.of();
	private static final ThreadLocal<Integer> CAPTURE_DEPTH =
			ThreadLocal.withInitial(() -> 0);
	private static final VertexConsumer DISCARDING_VERTICES =
			new DiscardingVertexConsumer();

	@Nullable private static TextureTarget maskTarget;
	@Nullable private static TextureTarget auraTarget;
	private static int maskTargetWidth = -1;
	private static int maskTargetHeight = -1;
	private static int auraTargetWidth = -1;
	private static int auraTargetHeight = -1;

	private final ResourceLocation id;
	private final int order;
	private final Compositor compositor;
	private final Object queueLock = new Object();
	private final IdentityHashMap<Entity, QueuedRequest> queued =
			new IdentityHashMap<>();
	private final List<Entity> queueOrder = new ArrayList<>();
	private final FailureEpisodeLatch runtimeFailure =
			new FailureEpisodeLatch();
	private boolean closed;
	private volatile float outputScale = 1.0F;

	private EntityMaskPostEffect(
			ResourceLocation id,
			int order,
			Compositor compositor) {
		this.id = id;
		this.order = order;
		this.compositor = compositor;
	}

	/**
	 * Registers one owner. Lower order values composite first.
	 */
	public static EntityMaskPostEffect register(
			ResourceLocation id,
			int order,
			Compositor compositor) {
		EntityMaskPostEffect effect = new EntityMaskPostEffect(
				Objects.requireNonNull(id, "id"),
				order,
				Objects.requireNonNull(compositor, "compositor"));
		synchronized (REGISTRY_LOCK) {
			if (REGISTERED.putIfAbsent(id, effect) != null) {
				throw new IllegalStateException(
						"An entity mask post effect is already "
								+ "registered as "
								+ id);
			}
			publishSnapshot();
		}
		return effect;
	}

	public ResourceLocation id() {
		return id;
	}

	public int order() {
		return order;
	}

	public void setOutputScale(float outputScale) {
		requireOpen();
		if (!Float.isFinite(outputScale)) {
			throw new IllegalArgumentException(
					"Entity mask output scale must be finite");
		}
		this.outputScale = Mth.clamp(
				outputScale, 0.125F, 1.0F);
	}

	/**
	 * Queues the exact object identity for this frame.
	 *
	 * <p>A later request for the same object replaces its group and padding
	 * without moving its stable queue position.</p>
	 */
	public boolean queue(
			Entity entity,
			Object groupKey,
			float shapePadding) {
		requireOpen();
		Objects.requireNonNull(entity, "entity");
		Objects.requireNonNull(groupKey, "groupKey");
		if (isCapturePass()) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!canSampleSceneDepth(
				minecraft, minecraft.getMainRenderTarget())) {
			return false;
		}
		float boundedPadding =
				Mth.clamp(shapePadding, 0.0F, 1.0F);
		synchronized (queueLock) {
			if (!queued.containsKey(entity)) {
				queueOrder.add(entity);
			}
			queued.put(
					entity,
					new QueuedRequest(
							entity, groupKey, boundedPadding));
		}
		return true;
	}

	public boolean queue(Entity entity, Object groupKey) {
		return queue(entity, groupKey, 0.18F);
	}

	public static boolean isCapturePass() {
		return CAPTURE_DEPTH.get() > 0;
	}

	@Override
	public void close() {
		boolean release;
		synchronized (REGISTRY_LOCK) {
			if (closed) {
				return;
			}
			closed = true;
			REGISTERED.remove(id, this);
			publishSnapshot();
			release = REGISTERED.isEmpty();
		}
		clearQueue();
		if (release) {
			closeLoadedTargets();
		}
	}

	private void requireOpen() {
		if (closed) {
			throw new IllegalStateException(
					"Entity mask post effect " + id + " is closed");
		}
	}

	private static void publishSnapshot() {
		List<EntityMaskPostEffect> next =
				new ArrayList<>(REGISTERED.values());
		next.sort(ORDERING);
		snapshot = List.copyOf(next);
	}

	private List<QueuedRequest> drainQueue() {
		synchronized (queueLock) {
			if (queueOrder.isEmpty()) {
				return List.of();
			}
			List<QueuedRequest> drained =
					new ArrayList<>(queueOrder.size());
			for (Entity entity : queueOrder) {
				QueuedRequest request = queued.get(entity);
				if (request != null) {
					drained.add(request);
				}
			}
			queued.clear();
			queueOrder.clear();
			return List.copyOf(drained);
		}
	}

	private void clearQueue() {
		synchronized (queueLock) {
			queued.clear();
			queueOrder.clear();
		}
	}

	@ApiStatus.Internal
	public static void onFrame(RenderLevelStageEvent event) {
		if (event.getStage()
				!= RenderLevelStageEvent.Stage.AFTER_LEVEL) {
			return;
		}
		List<FrameEffect> frameEffects = new ArrayList<>();
		for (EntityMaskPostEffect effect : snapshot) {
			List<QueuedRequest> requests = effect.drainQueue();
			if (!requests.isEmpty()) {
				frameEffects.add(
						new FrameEffect(effect, requests));
			}
		}
		if (frameEffects.isEmpty()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget mainTarget = minecraft.getMainRenderTarget();
		if (!canSampleSceneDepth(minecraft, mainTarget)) {
			return;
		}

		RenderStateScope state = RenderStateScope.capture();
		try {
			ensureMaskTarget(
					mainTarget.viewWidth,
					mainTarget.viewHeight);
			for (FrameEffect frameEffect : frameEffects) {
				renderEffect(
						frameEffect,
						event,
						minecraft,
						mainTarget);
			}
		}
		catch (RuntimeException exception) {
			JojoMod.getLogger().error(
					"Entity mask post effects failed; "
							+ "the captured render target was restored.",
					exception);
		}
		finally {
			state.restore();
		}
	}

	private static boolean canSampleSceneDepth(
			Minecraft minecraft,
			RenderTarget mainTarget) {
		return minecraft.level != null
				&& mainTarget.useDepth
				&& mainTarget.getDepthTextureId() > 0
				&& mainTarget.viewWidth > 0
				&& mainTarget.viewHeight > 0;
	}

	private static void renderEffect(
			FrameEffect frameEffect,
			RenderLevelStageEvent event,
			Minecraft minecraft,
			RenderTarget mainTarget) {
		int outputWidth = Math.max(
				1,
				Math.round(
						mainTarget.viewWidth
								* frameEffect.effect().outputScale));
		int outputHeight = Math.max(
				1,
				Math.round(
						mainTarget.viewHeight
								* frameEffect.effect().outputScale));
		ensureAuraTarget(outputWidth, outputHeight);
		TextureTarget currentAuraTarget = auraTarget;
		if (currentAuraTarget == null) {
			return;
		}
		currentAuraTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
		currentAuraTarget.bindWrite(true);
		currentAuraTarget.clear(Minecraft.ON_OSX);

		Map<Object, RenderGroup> groups = groupRequests(
				frameEffect.requests(),
				event,
				mainTarget);
		boolean renderedAny = false;
		boolean attemptedGroup = false;
		boolean groupFailed = false;
		for (RenderGroup group : groups.values()) {
			if (group.bounds().isEmpty()) {
				continue;
			}
			attemptedGroup = true;
			try {
				renderGroupMask(
						minecraft,
						event,
						group.requests());
				EntityMaskCompositeContext context =
						new EntityMaskCompositeContext(
								group.key(),
								group.entities(),
								partialTick(event),
								group.auraThicknessScale(),
								group.bounds().minU(),
								group.bounds().minV(),
								group.bounds().maxU(),
								group.bounds().maxV(),
								currentAuraTarget.viewWidth,
								currentAuraTarget.viewHeight,
								mainTarget.viewWidth,
								mainTarget.viewHeight,
								Objects.requireNonNull(maskTarget),
								currentAuraTarget,
								mainTarget);
				frameEffect.effect().compositor.composite(context);
				renderedAny |= context.drewComposite();
			}
			catch (VirtualMachineError fatal) {
				throw fatal;
			}
			catch (Throwable throwable) {
				if (throwable.getClass().getName()
						.equals("java.lang.ThreadDeath")) {
					throw (Error) throwable;
				}
				groupFailed = true;
				frameEffect.effect().logRuntimeFailure(throwable);
			}
		}
		frameEffect.effect().runtimeFailure.settleBatch(
				attemptedGroup, groupFailed);
		if (renderedAny) {
			blitAuraToMain(currentAuraTarget, mainTarget);
		}
	}

	private void logRuntimeFailure(Throwable throwable) {
		if (!runtimeFailure.tryReportFailure()) {
			return;
		}
		JojoMod.getLogger().error(
				"Entity mask post effect {} failed; "
						+ "later effects will continue.",
				id,
				throwable);
	}

	private static Map<Object, RenderGroup> groupRequests(
			List<QueuedRequest> requests,
			RenderLevelStageEvent event,
			RenderTarget mainTarget) {
		Map<Object, MutableRenderGroup> mutable =
				new LinkedHashMap<>();
		EntityRenderDispatcher dispatcher =
				Minecraft.getInstance().getEntityRenderDispatcher();
		float partialTick = partialTick(event);
		for (QueuedRequest request : requests) {
			if (!canRender(request.entity())) {
				continue;
			}
			PreparedRequest prepared = prepareRequest(
					dispatcher, request, partialTick);
			ProjectedBounds projected = projectEntityBounds(
					prepared,
					event,
					mainTarget.viewWidth,
					mainTarget.viewHeight);
			if (projected.bounds().isEmpty()) {
				continue;
			}
			mutable.computeIfAbsent(
					request.groupKey(),
					MutableRenderGroup::new)
					.add(prepared, projected);
		}
		Map<Object, RenderGroup> groups = new LinkedHashMap<>();
		mutable.forEach((key, group) ->
				groups.put(key, group.freeze()));
		return groups;
	}

	private static boolean canRender(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		return !entity.isRemoved()
				&& entity.level() == minecraft.level;
	}

	private static void renderGroupMask(
			Minecraft minecraft,
			RenderLevelStageEvent event,
			List<PreparedRequest> requests) {
		TextureTarget currentMaskTarget = maskTarget;
		if (currentMaskTarget == null) {
			return;
		}
		currentMaskTarget.setClearColor(
				0.0F, 0.0F, 0.0F, 0.0F);
		currentMaskTarget.bindWrite(true);
		currentMaskTarget.clear(Minecraft.ON_OSX);

		RenderType maskRenderType =
				MaskRenderTypeHolder.MASK_RENDER_TYPE;
		try (ByteBufferBuilder storage =
				new ByteBufferBuilder(PRIVATE_BUFFER_SIZE)) {
			MultiBufferSource.BufferSource delegate =
					MultiBufferSource.immediate(storage);
			MultiBufferSource forcedMask =
					new ForcedMaskBuffer(
							delegate, maskRenderType);
			runCapturePass(() -> {
				Vec3 cameraPosition =
						event.getCamera().getPosition();
				float partialTick = partialTick(event);
				for (PreparedRequest request : requests) {
					Entity entity = request.entity();
					if (!canRender(entity)) {
						continue;
					}
					double renderX = Mth.lerp(
							partialTick,
							entity.xo,
							entity.getX()) - cameraPosition.x;
					double renderY = Mth.lerp(
							partialTick,
							entity.yo,
							entity.getY()) - cameraPosition.y;
					double renderZ = Mth.lerp(
							partialTick,
							entity.zo,
							entity.getZ()) - cameraPosition.z;
					float yaw = Mth.rotLerp(
							partialTick,
							entity.yRotO,
							entity.getYRot());
					renderRegisteredEntityOnly(
							request,
							renderX,
							renderY,
							renderZ,
							yaw,
							partialTick,
							copyTopPose(event.getPoseStack()),
							forcedMask);
				}
			});
			delegate.endBatch();
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static PreparedRequest prepareRequest(
			EntityRenderDispatcher dispatcher,
			QueuedRequest request,
			float partialTick) {
		Entity entity = request.entity();
		EntityRenderer renderer = dispatcher.getRenderer(entity);
		Vec3 renderOffset =
				renderer.getRenderOffset(entity, partialTick);
		return new PreparedRequest(
				request, renderer, renderOffset);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void renderRegisteredEntityOnly(
			PreparedRequest request,
			double renderX,
			double renderY,
			double renderZ,
			float yaw,
			float partialTick,
			PoseStack poseStack,
			MultiBufferSource forcedMask) {
		Entity entity = request.entity();
		EntityRenderer renderer = request.renderer();
		Vec3 renderOffset = request.renderOffset();
		poseStack.pushPose();
		try {
			poseStack.translate(
					renderX + renderOffset.x,
					renderY + renderOffset.y,
					renderZ + renderOffset.z);
			renderer.render(
					entity,
					yaw,
					partialTick,
					poseStack,
					forcedMask,
					LightTexture.FULL_BRIGHT);
		}
		finally {
			poseStack.popPose();
		}
	}

	private static PoseStack copyTopPose(PoseStack source) {
		PoseStack copy = new PoseStack();
		copy.last().pose().set(source.last().pose());
		copy.last().normal().set(source.last().normal());
		return copy;
	}

	static void drawComposite(
			EntityMaskCompositeContext context,
			ShaderInstance shader,
			EntityMaskCompositeContext.UniformUpdater
					uniformUpdater) {
		RenderTarget mask = context.maskTarget();
		RenderTarget aura = context.auraTarget();
		RenderTarget scene = context.sceneTarget();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.colorMask(true, true, true, true);

		shader.setSampler(
				"uMaskTex", mask.getColorTextureId());
		shader.setSampler(
				"uEntityDepthTex", mask.getDepthTextureId());
		shader.setSampler(
				"uSceneDepthTex", scene.getDepthTextureId());
		shader.safeGetUniform("uTexelSize").set(
				1.0F / Math.max(mask.viewWidth, 1),
				1.0F / Math.max(mask.viewHeight, 1));
		shader.safeGetUniform("uMaskUvMin").set(0.0F, 0.0F);
		shader.safeGetUniform("uMaskUvMax").set(1.0F, 1.0F);
		shader.safeGetUniform("uAspect").set(
				(float) context.sourceWidth()
						/ Math.max(context.sourceHeight(), 1));
		shader.safeGetUniform("uAntiAlias").set(Math.max(
				2.0F / Math.max(context.width(), 1),
				2.0F / Math.max(context.height(), 1)));
		uniformUpdater.update(shader, context);
		drawClipQuad(
				shader,
				() -> aura.bindWrite(true),
				context.minU(),
				context.minV(),
				context.maxU(),
				context.maxV());
		context.markDrewComposite();
	}

	private static void blitAuraToMain(
			RenderTarget aura,
			RenderTarget mainTarget) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.colorMask(true, true, true, true);
		RenderSystem.setShaderTexture(
				0, aura.getColorTextureId());
		ShaderInstance shader = ModShaders.privateTargetBlit();
		if (shader == null) {
			shader = GameRenderer.getPositionTexShader();
		}
		shader.setSampler("Sampler0", aura.getColorTextureId());
		drawClipQuad(
				shader,
				() -> mainTarget.bindWrite(true),
				0.0F,
				0.0F,
				1.0F,
				1.0F);
	}

	private static void drawClipQuad(
			ShaderInstance shader,
			Runnable bindDrawTarget,
			float minU,
			float minV,
			float maxU,
			float maxV) {
		Matrix4f projection =
				new Matrix4f(RenderSystem.getProjectionMatrix());
		VertexSorting sorting = RenderSystem.getVertexSorting();
		Matrix4fStack modelView =
				RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		try {
			modelView.identity();
			RenderSystem.applyModelViewMatrix();
			RenderSystem.setProjectionMatrix(
					new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
			float minX = clipCoordinate(minU);
			float minY = clipCoordinate(minV);
			float maxX = clipCoordinate(maxU);
			float maxY = clipCoordinate(maxV);
			try (ByteBufferBuilder storage =
					new ByteBufferBuilder(256)) {
				BufferBuilder builder = new BufferBuilder(
						storage,
						VertexFormat.Mode.QUADS,
						DefaultVertexFormat.POSITION_TEX);
				builder.addVertex(minX, minY, 0.0F)
						.setUv(minU, minV);
				builder.addVertex(maxX, minY, 0.0F)
						.setUv(maxU, minV);
				builder.addVertex(maxX, maxY, 0.0F)
						.setUv(maxU, maxV);
				builder.addVertex(minX, maxY, 0.0F)
						.setUv(minU, maxV);
				shader.setDefaultUniforms(
						VertexFormat.Mode.QUADS,
						RenderSystem.getModelViewMatrix(),
						RenderSystem.getProjectionMatrix(),
						Minecraft.getInstance().getWindow());
				shader.apply();
				try {
					bindDrawTarget.run();
					BufferUploader.draw(builder.buildOrThrow());
				}
				finally {
					shader.clear();
				}
			}
		}
		finally {
			RenderSystem.setProjectionMatrix(
					projection, sorting);
			modelView.popMatrix();
			RenderSystem.applyModelViewMatrix();
		}
	}

	static float clipCoordinate(float framebufferUv) {
		return framebufferUv * 2.0F - 1.0F;
	}

	private static ProjectedBounds projectEntityBounds(
			PreparedRequest request,
			RenderLevelStageEvent event,
			int width,
			int height) {
		Entity entity = request.entity();
		float partialTick = partialTick(event);
		double interpolatedX = Mth.lerp(
				partialTick, entity.xo, entity.getX());
		double interpolatedY = Mth.lerp(
				partialTick, entity.yo, entity.getY());
		double interpolatedZ = Mth.lerp(
				partialTick, entity.zo, entity.getZ());
		AABB box = entity.getBoundingBox()
				.move(
						interpolatedX - entity.getX(),
						interpolatedY - entity.getY(),
						interpolatedZ - entity.getZ())
				.move(request.renderOffset())
				.inflate(0.25D);
		ProjectionBounds projectionBounds =
				new ProjectionBounds();
		Vec3 camera = event.getCamera().getPosition();
		Matrix4f modelView = event.getModelViewMatrix();
		Matrix4f projection = event.getProjectionMatrix();
		double[] xs = {box.minX, box.maxX};
		double[] ys = {box.minY, box.maxY};
		double[] zs = {box.minZ, box.maxZ};
		for (double x : xs) {
			for (double y : ys) {
				for (double z : zs) {
					if (!projectCorner(
							projectionBounds,
							x,
							y,
							z,
							camera,
							modelView,
							projection)) {
						return new ProjectedBounds(
								ScreenRect.full(),
								estimateThicknessByDistance(
										box, camera));
					}
				}
			}
		}
		if (!projectionBounds.hasProjectedPoint) {
			return new ProjectedBounds(
					ScreenRect.empty(), 1.0F);
		}

		float sourceWidth = Math.max(width, 1);
		float sourceHeight = Math.max(height, 1);
		float aspect = sourceWidth / sourceHeight;
		float shapePadding = request.shapePadding();
		float paddingU = Math.max(
				shapePadding
						/ Math.max(aspect * 2.0F, 0.0001F),
				8.0F / sourceWidth)
				+ 300.0F / sourceWidth;
		float paddingV = Math.max(
				shapePadding * 0.5F,
				8.0F / sourceHeight)
				+ 300.0F / sourceHeight;
		float projectedHeightInShapeSpace = Math.max(
				(projectionBounds.maxV
						- projectionBounds.minV) * 2.0F,
				0.0001F);
		float worldHeight =
				Math.max((float) box.getYsize(), 0.0001F);
		float thicknessScale = Mth.clamp(
				projectedHeightInShapeSpace
						/ worldHeight * 8.0F,
				0.18F,
				2.6F);
		return new ProjectedBounds(
				ScreenRect.fromUnclamped(
						projectionBounds.minU - paddingU,
						projectionBounds.minV - paddingV,
						projectionBounds.maxU + paddingU,
						projectionBounds.maxV + paddingV),
				thicknessScale);
	}

	private static boolean projectCorner(
			ProjectionBounds bounds,
			double x,
			double y,
			double z,
			Vec3 camera,
			Matrix4f modelView,
			Matrix4f projection) {
		Vector4f corner = new Vector4f(
				(float) (x - camera.x),
				(float) (y - camera.y),
				(float) (z - camera.z),
				1.0F);
		modelView.transform(corner);
		projection.transform(corner);
		if (corner.w <= 0.0001F) {
			return false;
		}
		float inverseW = 1.0F / corner.w;
		float ndcX = corner.x * inverseW;
		float ndcY = corner.y * inverseW;
		float ndcZ = corner.z * inverseW;
		if (!Float.isFinite(ndcX)
				|| !Float.isFinite(ndcY)
				|| !Float.isFinite(ndcZ)) {
			return false;
		}
		if (ndcZ >= -1.0F && ndcZ <= 1.0F) {
			bounds.include(
					ndcX * 0.5F + 0.5F,
					ndcY * 0.5F + 0.5F);
		}
		return true;
	}

	private static float estimateThicknessByDistance(
			AABB box,
			Vec3 camera) {
		double distance = Math.sqrt(Math.max(
				box.getCenter().distanceToSqr(camera),
				0.0001D));
		return Mth.clamp(
				8.0F / Math.max((float) distance, 0.25F),
				0.18F,
				2.6F);
	}

	private static float partialTick(
			RenderLevelStageEvent event) {
		return event.getPartialTick()
				.getGameTimeDeltaTicks();
	}

	private static void ensureMaskTarget(int width, int height) {
		if (maskTarget != null
				&& maskTargetWidth == width
				&& maskTargetHeight == height) {
			return;
		}
		if (maskTarget != null) {
			maskTarget.destroyBuffers();
		}
		maskTarget = new TextureTarget(
				width, height, true, Minecraft.ON_OSX);
		maskTargetWidth = width;
		maskTargetHeight = height;
	}

	private static void ensureAuraTarget(int width, int height) {
		if (auraTarget != null
				&& auraTargetWidth == width
				&& auraTargetHeight == height) {
			return;
		}
		if (auraTarget != null) {
			auraTarget.destroyBuffers();
		}
		auraTarget = new TextureTarget(
				width, height, false, Minecraft.ON_OSX);
		auraTargetWidth = width;
		auraTargetHeight = height;
	}

	private static void releaseTargetsRaw() {
		if (maskTarget != null) {
			maskTarget.destroyBuffers();
			maskTarget = null;
		}
		if (auraTarget != null) {
			auraTarget.destroyBuffers();
			auraTarget = null;
		}
		maskTargetWidth = -1;
		maskTargetHeight = -1;
		auraTargetWidth = -1;
		auraTargetHeight = -1;
	}

	@ApiStatus.Internal
	public static void onClientTick() {
		if (Minecraft.getInstance().level != null) {
			return;
		}
		for (EntityMaskPostEffect effect : snapshot) {
			effect.clearQueue();
		}
		closeLoadedTargets();
	}

	@ApiStatus.Internal
	public static void onResourceManagerReload(
		ResourceManager resourceManager) {
		for (EntityMaskPostEffect effect : snapshot) {
			effect.runtimeFailure.reset();
			effect.clearQueue();
		}
		closeLoadedTargets();
	}

	@ApiStatus.Internal
	public static void resize(int width, int height) {
		closeLoadedTargets();
	}

	@ApiStatus.Internal
	public static void closeLoadedTargets() {
		if (maskTarget == null && auraTarget == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Runnable release = () -> {
			RenderStateScope state = RenderStateScope.capture();
			try {
				releaseTargetsRaw();
			}
			finally {
				state.restore();
			}
		};
		if (RenderSystem.isOnRenderThread()) {
			release.run();
		}
		else {
			minecraft.execute(release);
		}
	}

	private static void runCapturePass(Runnable action) {
		int previous = CAPTURE_DEPTH.get();
		CAPTURE_DEPTH.set(previous + 1);
		try {
			action.run();
		}
		finally {
			if (previous == 0) {
				CAPTURE_DEPTH.remove();
			}
			else {
				CAPTURE_DEPTH.set(previous);
			}
		}
	}

	static void runCapturePassForTest(Runnable action) {
		runCapturePass(action);
	}

	static List<ResourceLocation> registeredOwnersForTest() {
		List<ResourceLocation> owners = new ArrayList<>();
		for (EntityMaskPostEffect effect : snapshot) {
			owners.add(effect.id());
		}
		return List.copyOf(owners);
	}

	static boolean supportsCaptureFormatForTest(
			VertexFormat format) {
		return ForcedMaskBuffer.supportsFormat(format);
	}

	private static RenderType createMaskRenderType() {
		RenderStateShard.OutputStateShard output =
				new RenderStateShard.OutputStateShard(
						"jojo_ripples_entity_mask_target",
						() -> {
							TextureTarget target = maskTarget;
							if (target != null) {
								target.bindWrite(false);
							}
						},
						() -> {});
		RenderType.CompositeState state =
				RenderType.CompositeState.builder()
						.setTextureState(
								new RenderStateShard
										.TextureStateShard(
												WHITE_TEXTURE,
												false,
												false))
						.setShaderState(
								new RenderStateShard
										.ShaderStateShard(
												ModShaders::privateTargetEntityMask))
						.setTransparencyState(
								RenderStateShard.NO_TRANSPARENCY)
						.setDepthTestState(
								RenderStateShard.LEQUAL_DEPTH_TEST)
						.setCullState(RenderStateShard.NO_CULL)
						.setLightmapState(
								RenderStateShard.LIGHTMAP)
						.setOverlayState(RenderStateShard.OVERLAY)
						.setOutputState(output)
						.setWriteMaskState(
								RenderStateShard.COLOR_DEPTH_WRITE)
						.createCompositeState(false);
		return RenderType.create(
				"jojo_ripples_entity_mask",
				DefaultVertexFormat.NEW_ENTITY,
				VertexFormat.Mode.QUADS,
				RenderType.BIG_BUFFER_SIZE,
				false,
				false,
				state);
	}

	@FunctionalInterface
	public interface Compositor {
		void composite(EntityMaskCompositeContext context);
	}

	private record QueuedRequest(
			Entity entity,
			Object groupKey,
			float shapePadding) {}

	private record FrameEffect(
			EntityMaskPostEffect effect,
			List<QueuedRequest> requests) {}

	private record PreparedRequest(
			QueuedRequest request,
			EntityRenderer<?> renderer,
			Vec3 renderOffset) {
		private Entity entity() {
			return request.entity();
		}

		private Object groupKey() {
			return request.groupKey();
		}

		private float shapePadding() {
			return request.shapePadding();
		}
	}

	private record RenderGroup(
			Object key,
			List<PreparedRequest> requests,
			List<Entity> entities,
			ScreenRect bounds,
			float auraThicknessScale) {}

	private static final class MutableRenderGroup {
		private final Object key;
		private final List<PreparedRequest> requests =
				new ArrayList<>();
		private ScreenRect bounds = ScreenRect.empty();
		private float auraThicknessScale = 0.18F;

		private MutableRenderGroup(Object key) {
			this.key = key;
		}

		private void add(
				PreparedRequest request,
				ProjectedBounds projected) {
			requests.add(request);
			bounds = bounds.union(projected.bounds());
			auraThicknessScale = Math.max(
					auraThicknessScale,
					projected.auraThicknessScale());
		}

		private RenderGroup freeze() {
			List<Entity> entities = requests.stream()
					.map(PreparedRequest::entity)
					.toList();
			return new RenderGroup(
					key,
					List.copyOf(requests),
					entities,
					bounds,
					auraThicknessScale);
		}
	}

	private record ProjectedBounds(
			ScreenRect bounds,
			float auraThicknessScale) {}

	static final class FailureEpisodeLatch {
		private boolean reported;

		boolean tryReportFailure() {
			if (reported) {
				return false;
			}
			reported = true;
			return true;
		}

		void settleBatch(
				boolean attemptedGroup,
				boolean groupFailed) {
			if (attemptedGroup && !groupFailed) {
				reported = false;
			}
		}

		boolean isReported() {
			return reported;
		}

		void reset() {
			reported = false;
		}
	}

	private record ScreenRect(
			float minU,
			float minV,
			float maxU,
			float maxV) {
		private static ScreenRect full() {
			return new ScreenRect(
					0.0F, 0.0F, 1.0F, 1.0F);
		}

		private static ScreenRect empty() {
			return new ScreenRect(
					0.0F, 0.0F, 0.0F, 0.0F);
		}

		private static ScreenRect fromUnclamped(
				float minU,
				float minV,
				float maxU,
				float maxV) {
			if (maxU <= 0.0F
					|| minU >= 1.0F
					|| maxV <= 0.0F
					|| minV >= 1.0F) {
				return empty();
			}
			return new ScreenRect(
					Mth.clamp(minU, 0.0F, 1.0F),
					Mth.clamp(minV, 0.0F, 1.0F),
					Mth.clamp(maxU, 0.0F, 1.0F),
					Mth.clamp(maxV, 0.0F, 1.0F));
		}

		private boolean isEmpty() {
			return maxU <= minU || maxV <= minV;
		}

		private ScreenRect union(ScreenRect other) {
			if (isEmpty()) {
				return other;
			}
			if (other.isEmpty()) {
				return this;
			}
			return new ScreenRect(
					Math.min(minU, other.minU),
					Math.min(minV, other.minV),
					Math.max(maxU, other.maxU),
					Math.max(maxV, other.maxV));
		}
	}

	private static final class ProjectionBounds {
		private boolean hasProjectedPoint;
		private float minU = Float.POSITIVE_INFINITY;
		private float minV = Float.POSITIVE_INFINITY;
		private float maxU = Float.NEGATIVE_INFINITY;
		private float maxV = Float.NEGATIVE_INFINITY;

		private void include(float u, float v) {
			hasProjectedPoint = true;
			minU = Math.min(minU, u);
			minV = Math.min(minV, v);
			maxU = Math.max(maxU, u);
			maxV = Math.max(maxV, v);
		}
	}

	private static final class ForcedMaskBuffer
			implements MultiBufferSource {
		private final MultiBufferSource delegate;
		private final RenderType maskRenderType;
		private final IdentityHashMap<RenderType, RenderType>
				blockMaskRenderTypes = new IdentityHashMap<>();

		private ForcedMaskBuffer(
				MultiBufferSource delegate,
				RenderType maskRenderType) {
			this.delegate = delegate;
			this.maskRenderType = maskRenderType;
		}

		@Override
		public VertexConsumer getBuffer(RenderType renderType) {
			VertexFormat format = renderType.format();
			if (format == DefaultVertexFormat.NEW_ENTITY) {
				return delegate.getBuffer(maskRenderType);
			}
			if (format == DefaultVertexFormat.BLOCK) {
				RenderType redirected =
						blockMaskRenderTypes.computeIfAbsent(
								renderType,
								MaskRedirectRenderType::new);
				return delegate.getBuffer(redirected);
			}
			return DISCARDING_VERTICES;
		}

		private static boolean supportsFormat(
				VertexFormat format) {
			return format == DefaultVertexFormat.NEW_ENTITY
					|| format == DefaultVertexFormat.BLOCK;
		}
	}

	private static final class MaskRedirectRenderType
			extends RenderType {
		private MaskRedirectRenderType(RenderType source) {
			super(
					"jojo_ripples_entity_mask_block",
					source.format(),
					source.mode(),
					source.bufferSize(),
					source.affectsCrumbling(),
					source.sortOnUpload(),
					() -> {
						source.setupRenderState();
						TextureTarget target = maskTarget;
						if (target != null) {
							target.bindWrite(false);
						}
					},
					source::clearRenderState);
		}
	}

	private static final class DiscardingVertexConsumer
			implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(
				float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setColor(
				int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(
				float x, float y, float z) {
			return this;
		}
	}

	private static final class MaskRenderTypeHolder {
		private static final RenderType MASK_RENDER_TYPE =
				createMaskRenderType();
	}

	private static final class RenderStateScope {
		private final RenderTargetState targetState;
		private final GlStateBackup glState;
		private final float[] shaderColor;
		@Nullable private final ShaderInstance shader;
		private final Matrix4f projection;
		private final VertexSorting vertexSorting;
		private final Matrix4f modelView;
		private final int activeTexture;
		private final int[] textureBindings;
		private final int[] shaderTextures;

		private RenderStateScope(
				RenderTargetState targetState,
				GlStateBackup glState,
				float[] shaderColor,
				@Nullable ShaderInstance shader,
				Matrix4f projection,
				VertexSorting vertexSorting,
				Matrix4f modelView,
				int activeTexture,
				int[] textureBindings,
				int[] shaderTextures) {
			this.targetState = targetState;
			this.glState = glState;
			this.shaderColor = shaderColor;
			this.shader = shader;
			this.projection = projection;
			this.vertexSorting = vertexSorting;
			this.modelView = modelView;
			this.activeTexture = activeTexture;
			this.textureBindings = textureBindings;
			this.shaderTextures = shaderTextures;
		}

		private static RenderStateScope capture() {
			RenderTargetState targetState =
					new RenderTargetState();
			GlStateBackup glState = new GlStateBackup();
			float[] shaderColor =
					RenderSystem.getShaderColor().clone();
			ShaderInstance shader = RenderSystem.getShader();
			Matrix4f projection = new Matrix4f(
					RenderSystem.getProjectionMatrix());
			VertexSorting vertexSorting =
					RenderSystem.getVertexSorting();
			Matrix4f modelView = new Matrix4f(
					RenderSystem.getModelViewMatrix());
			int activeTexture =
					GlStateManager._getActiveTexture();
			int[] textureBindings =
					new int[TRACKED_TEXTURE_UNITS];
			int[] shaderTextures =
					new int[TRACKED_TEXTURE_UNITS];
			for (int i = 0;
					i < TRACKED_TEXTURE_UNITS;
					i++) {
				GlStateManager._activeTexture(
						GL13.GL_TEXTURE0 + i);
				textureBindings[i] = GL11.glGetInteger(
						GL11.GL_TEXTURE_BINDING_2D);
				shaderTextures[i] =
						RenderSystem.getShaderTexture(i);
			}
			GlStateManager._activeTexture(activeTexture);
			targetState.capture();
			RenderSystem.backupGlState(glState);
			return new RenderStateScope(
					targetState,
					glState,
					shaderColor,
					shader,
					projection,
					vertexSorting,
					modelView,
					activeTexture,
					textureBindings,
					shaderTextures);
		}

		private void restore() {
			targetState.restore();
			RenderSystem.restoreGlState(glState);
			RenderSystem.setProjectionMatrix(
					projection, vertexSorting);
			RenderSystem.getModelViewStack().set(modelView);
			RenderSystem.applyModelViewMatrix();
			RenderSystem.setShaderColor(
					shaderColor[0],
					shaderColor[1],
					shaderColor[2],
					shaderColor[3]);
			for (int i = 0;
					i < TRACKED_TEXTURE_UNITS;
					i++) {
				GlStateManager._activeTexture(
						GL13.GL_TEXTURE0 + i);
				GlStateManager._bindTexture(
						textureBindings[i]);
				RenderSystem.setShaderTexture(
						i, shaderTextures[i]);
			}
			GlStateManager._activeTexture(activeTexture);
			RenderSystem.setShader(() -> shader);
		}

	}

}
