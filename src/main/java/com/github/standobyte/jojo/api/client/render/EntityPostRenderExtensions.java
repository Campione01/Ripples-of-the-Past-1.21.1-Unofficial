package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Owner-keyed client extension point after each registered entity renderer.
 *
 * <p>Callbacks are ordered deterministically, receive independent pose stacks,
 * and are exception-isolated. This extension point never replaces or cancels
 * the registered renderer.</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class EntityPostRenderExtensions {
	private static final Object LOCK = new Object();
	private static final Map<ResourceLocation, Entry> REGISTERED =
			new HashMap<>();
	private static final Comparator<Entry> ORDERING =
			Comparator.comparingInt(Entry::order)
					.thenComparing(entry -> entry.owner().toString());
	private static volatile List<Entry> snapshot = List.of();
	private static volatile long frameId;

	/**
	 * Registers one owner. Lower order values run first.
	 *
	 * @throws IllegalStateException if the owner is already registered
	 */
	public static Registration register(
			ResourceLocation owner,
			int order,
			EntityPostRenderExtension extension) {
		Entry entry = new Entry(
				Objects.requireNonNull(owner, "owner"),
				order,
				Objects.requireNonNull(extension, "extension"));
		synchronized (LOCK) {
			if (REGISTERED.putIfAbsent(owner, entry) != null) {
				throw new IllegalStateException(
						"An entity post-render extension is already "
								+ "registered as "
								+ owner);
			}
			publishSnapshot();
		}
		return new Registration(entry);
	}

	public static long currentFrameId() {
		return frameId;
	}

	@ApiStatus.Internal
	public static void afterEntityRender(
			Entity entity,
			EntityRenderer<?> renderer,
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			float entityYaw,
			float partialTick) {
		List<Entry> entries = snapshot;
		if (entries.isEmpty()) {
			return;
		}
		long currentFrame = frameId;
		for (Entry entry : entries) {
			EntityPostRenderContext context =
					new EntityPostRenderContext(
							entity,
							renderer,
							copyTopPose(poseStack),
							buffer,
							packedLight,
							entityYaw,
							partialTick,
							currentFrame);
			invokeAfterEntity(
					entry,
					context,
					EntityPostRenderExtensions::logFailure);
		}
	}

	@SubscribeEvent
	public static void afterEntityStage(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			finishFrame(EntityPostRenderExtensions::logFailure);
		}
	}

	private static PoseStack copyTopPose(PoseStack source) {
		PoseStack copy = new PoseStack();
		copy.last().pose().set(source.last().pose());
		copy.last().normal().set(source.last().normal());
		return copy;
	}

	private static void publishSnapshot() {
		List<Entry> next = new ArrayList<>(REGISTERED.values());
		next.sort(ORDERING);
		snapshot = List.copyOf(next);
	}

	private static void finishFrame(
			BiConsumer<ResourceLocation, Throwable> failureHandler) {
		long completedFrame = frameId;
		for (Entry entry : snapshot) {
			invokeEndFrame(entry, completedFrame, failureHandler);
		}
		frameId = completedFrame + 1L;
	}

	private static void logFailure(
			ResourceLocation owner,
			Throwable throwable) {
		JojoMod.getLogger().error(
				"Entity post-render extension {} failed; "
						+ "continuing with later extensions.",
				owner,
				throwable);
	}

	static void dispatchForTest(
			EntityPostRenderContext context,
			BiConsumer<ResourceLocation, Throwable> failureHandler) {
		for (Entry entry : snapshot) {
			invokeAfterEntity(entry, context, failureHandler);
		}
	}

	static void finishFrameForTest(
			BiConsumer<ResourceLocation, Throwable> failureHandler) {
		finishFrame(failureHandler);
	}

	private static void invokeAfterEntity(
			Entry entry,
			EntityPostRenderContext context,
			BiConsumer<ResourceLocation, Throwable> failureHandler) {
		try {
			entry.extension().afterEntityRender(context);
		}
		catch (VirtualMachineError fatal) {
			throw fatal;
		}
		catch (Throwable throwable) {
			if (throwable.getClass().getName()
					.equals("java.lang.ThreadDeath")) {
				throw (Error) throwable;
			}
			failureHandler.accept(entry.owner(), throwable);
		}
	}

	private static void invokeEndFrame(
			Entry entry,
			long completedFrame,
			BiConsumer<ResourceLocation, Throwable> failureHandler) {
		try {
			entry.extension().endFrame(completedFrame);
		}
		catch (VirtualMachineError fatal) {
			throw fatal;
		}
		catch (Throwable throwable) {
			if (throwable.getClass().getName()
					.equals("java.lang.ThreadDeath")) {
				throw (Error) throwable;
			}
			failureHandler.accept(entry.owner(), throwable);
		}
	}

	public static final class Registration implements AutoCloseable {
		private final Entry entry;
		private boolean closed;

		private Registration(Entry entry) {
			this.entry = entry;
		}

		public ResourceLocation owner() {
			return entry.owner();
		}

		public int order() {
			return entry.order();
		}

		@Override
		public void close() {
			synchronized (LOCK) {
				if (closed) {
					return;
				}
				closed = true;
				REGISTERED.remove(entry.owner(), entry);
				publishSnapshot();
			}
		}
	}

	private record Entry(
			ResourceLocation owner,
			int order,
			EntityPostRenderExtension extension) {}

	private EntityPostRenderExtensions() {}
}
