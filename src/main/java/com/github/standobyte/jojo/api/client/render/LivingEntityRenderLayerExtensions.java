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

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed client extension for living model passes after vanilla layers.
 *
 * <p>Lower order values run first. Ties are resolved by owner ID. Each
 * provider receives a fresh frame-scoped context with an isolated pose stack,
 * and one provider failure does not prevent later providers from running.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class LivingEntityRenderLayerExtensions {
	private static final Object LOCK = new Object();
	private static final Map<ResourceLocation, Entry> REGISTERED =
			new HashMap<>();
	private static final Comparator<Entry> ORDERING =
			Comparator.comparingInt(Entry::order)
					.thenComparing(entry -> entry.owner().toString());
	private static volatile List<Entry> snapshot = List.of();

	/**
	 * Registers one addon-owned living render layer.
	 *
	 * @throws IllegalStateException if the owner is already registered
	 */
	public static Registration register(
			ResourceLocation owner,
			int order,
			LivingEntityRenderLayerProvider provider) {
		Entry entry = new Entry(
				Objects.requireNonNull(owner, "owner"),
				order,
				Objects.requireNonNull(provider, "provider"));
		synchronized (LOCK) {
			if (REGISTERED.putIfAbsent(owner, entry) != null) {
				throw new IllegalStateException(
						"A living entity render layer is already "
								+ "registered as "
								+ owner);
			}
			publishSnapshot();
		}
		return new Registration(entry);
	}

	@ApiStatus.Internal
	public static void renderAfterVanillaLayers(
			LivingEntity entity,
			EntityModel<?> model,
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			float partialTick) {
		for (Entry entry : snapshot) {
			LivingEntityRenderLayerContext context =
					new LivingEntityRenderLayerContext(
							entity,
							model,
							copyTopPose(poseStack),
							buffer,
							packedLight,
							partialTick);
			invoke(
					entry,
					context,
					LivingEntityRenderLayerExtensions::logFailure);
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

	private static void logFailure(
			ResourceLocation owner,
			Throwable throwable) {
		JojoMod.getLogger().error(
				"Living entity render layer {} failed; "
						+ "continuing with later layers.",
				owner,
				throwable);
	}

	static void dispatchForTest(
			LivingEntityRenderLayerContext context,
			BiConsumer<ResourceLocation, Throwable> failureHandler) {
		for (Entry entry : snapshot) {
			invoke(entry, context, failureHandler);
		}
	}

	private static void invoke(
			Entry entry,
			LivingEntityRenderLayerContext context,
			BiConsumer<ResourceLocation, Throwable> failureHandler) {
		try {
			entry.provider().render(context);
		}
		catch (VirtualMachineError | ThreadDeath fatal) {
			throw fatal;
		}
		catch (Throwable throwable) {
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
			LivingEntityRenderLayerProvider provider) {}

	private LivingEntityRenderLayerExtensions() {}
}
