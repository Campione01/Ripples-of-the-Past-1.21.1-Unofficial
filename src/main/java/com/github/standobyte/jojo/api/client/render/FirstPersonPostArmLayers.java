package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.client.firstperson.FirstPersonModelLayer;
import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only registration for addon-owned layers rendered after the vanilla
 * first-person arm and all core arm layers.
 *
 * <p>Each contributor receives an isolated pose stack and a bounded context
 * that can render only the current arm. Lower order values run first; ties are
 * resolved by the registration ID, so ordering does not depend on addon load
 * order.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FirstPersonPostArmLayers {
	private static final Object LOCK = new Object();
	private static final Map<ResourceLocation, Entry> REGISTERED =
			new HashMap<>();
	private static final Comparator<Entry> ORDERING =
			Comparator.comparingInt(Entry::order)
					.thenComparing(entry -> entry.id().toString());
	private static volatile List<Entry> snapshot = List.of();

	/**
	 * Registers one addon-owned post-arm contributor.
	 *
	 * @throws IllegalStateException if the ID is already registered
	 */
	public static Registration register(
			ResourceLocation id,
			int order,
			Contributor contributor) {
		Entry entry = new Entry(
				Objects.requireNonNull(id, "id"),
				order,
				Objects.requireNonNull(contributor, "contributor"));
		synchronized (LOCK) {
			if (REGISTERED.putIfAbsent(id, entry) != null) {
				throw new IllegalStateException(
						"A first-person post-arm layer is already "
								+ "registered as "
								+ id);
			}
			publishSnapshot();
		}
		return new Registration(entry);
	}

	private static void publishSnapshot() {
		List<Entry> next = new ArrayList<>(REGISTERED.values());
		next.sort(ORDERING);
		snapshot = List.copyOf(next);
	}

	@ApiStatus.Internal
	public static void render(
			LivingEntity entity,
			HumanoidModel<?> model,
			HumanoidArm handSide,
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			float partialTick) {
		List<Entry> entries = snapshot;
		if (entries.isEmpty()) {
			return;
		}
		FirstPersonModelLayer.setupForFirstPersonRender(
				model, entity, partialTick);
		boolean preserveCurrentPose =
				FirstPersonModelLayer.isRipplesAnimPlaying(model);
		for (Entry entry : entries) {
			Context context = new Context(
					entity,
					handSide,
					copyTopPose(poseStack),
					buffer,
					packedLight,
					partialTick,
					model,
					preserveCurrentPose);
			invoke(entry, context, FirstPersonPostArmLayers::logFailure);
		}
	}

	private static PoseStack copyTopPose(PoseStack source) {
		PoseStack copy = new PoseStack();
		copy.last().pose().set(source.last().pose());
		copy.last().normal().set(source.last().normal());
		return copy;
	}

	private static void logFailure(
			ResourceLocation id,
			Throwable throwable) {
		JojoMod.getLogger().error(
				"First-person post-arm layer {} failed; "
						+ "continuing with later layers.",
				id,
				throwable);
	}

	static void dispatchForTest(
			Context context,
			BiConsumer<ResourceLocation, Throwable> failureHandler) {
		for (Entry entry : snapshot) {
			invoke(entry, context, failureHandler);
		}
	}

	private static void invoke(
			Entry entry,
			Context context,
			BiConsumer<ResourceLocation, Throwable> failureHandler) {
		try {
			entry.contributor().render(context);
		}
		catch (VirtualMachineError error) {
			throw error;
		}
		catch (Throwable throwable) {
			if (throwable.getClass().getName()
					.equals("java.lang.ThreadDeath")) {
				throw (Error) throwable;
			}
			failureHandler.accept(entry.id(), throwable);
		}
	}

	@FunctionalInterface
	public interface Contributor {
		void render(Context context);
	}

	/**
	 * Immutable metadata plus an isolated pose stack for one contributor.
	 * The model is intentionally not exposed.
	 */
	public static final class Context {
		private final LivingEntity entity;
		private final HumanoidArm handSide;
		private final PoseStack poseStack;
		private final MultiBufferSource buffer;
		private final int packedLight;
		private final float partialTick;
		private final HumanoidModel<?> model;
		private final boolean preserveCurrentPose;

		private Context(
				LivingEntity entity,
				HumanoidArm handSide,
				PoseStack poseStack,
				MultiBufferSource buffer,
				int packedLight,
				float partialTick,
				HumanoidModel<?> model,
				boolean preserveCurrentPose) {
			this.entity = entity;
			this.handSide = handSide;
			this.poseStack = poseStack;
			this.buffer = buffer;
			this.packedLight = packedLight;
			this.partialTick = partialTick;
			this.model = model;
			this.preserveCurrentPose = preserveCurrentPose;
		}

		public LivingEntity entity() {
			return entity;
		}

		public HumanoidArm handSide() {
			return handSide;
		}

		public PoseStack poseStack() {
			return poseStack;
		}

		public MultiBufferSource buffer() {
			return buffer;
		}

		public int packedLight() {
			return packedLight;
		}

		public float partialTick() {
			return partialTick;
		}

		public void renderArm(VertexConsumer vertices) {
			renderArm(
					vertices,
					OverlayTexture.NO_OVERLAY,
					0xFFFFFFFF);
		}

		public void renderArm(
				VertexConsumer vertices,
				int packedOverlay,
				int color) {
			ModelPart arm =
					FirstPersonModelLayer.getArm(model, handSide);
			PartPose armPose = arm.storePose();
			boolean armVisible = arm.visible;
			ModelPart outer = model instanceof PlayerModel<?> playerModel
					? FirstPersonModelLayer.getArmOuter(
							playerModel, handSide)
					: null;
			PartPose outerPose =
					outer != null ? outer.storePose() : null;
			boolean outerVisible =
					outer != null && outer.visible;
			try {
				FirstPersonModelLayer.renderArmAndOuter(
						model,
						handSide,
						poseStack,
						Objects.requireNonNull(vertices, "vertices"),
						packedLight,
						packedOverlay,
						color,
						preserveCurrentPose);
			}
			finally {
				arm.loadPose(armPose);
				arm.visible = armVisible;
				if (outer != null) {
					outer.loadPose(outerPose);
					outer.visible = outerVisible;
				}
			}
		}
	}

	public static final class Registration implements AutoCloseable {
		private final Entry entry;
		private boolean closed;

		private Registration(Entry entry) {
			this.entry = entry;
		}

		public ResourceLocation id() {
			return entry.id();
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
				REGISTERED.remove(entry.id(), entry);
				publishSnapshot();
			}
		}
	}

	private record Entry(
			ResourceLocation id,
			int order,
			Contributor contributor) {}

	private FirstPersonPostArmLayers() {}
}
