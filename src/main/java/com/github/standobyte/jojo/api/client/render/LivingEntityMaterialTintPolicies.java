package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed material tints for complete living-entity render calls.
 *
 * <p>Every matching tint composes in registration order. The wrapped buffer
 * source is shared by the base model, armor, and feature layers without
 * mutating global render state.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class LivingEntityMaterialTintPolicies {
	private static final Map<ResourceLocation,
			LivingEntityMaterialTintPolicy> POLICIES =
					new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private LivingEntityMaterialTintPolicies() {}

	public static synchronized void register(
			ResourceLocation owner,
			LivingEntityMaterialTintPolicy policy) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(policy, "policy");
		if (POLICIES.putIfAbsent(owner, policy) != null) {
			throw new IllegalStateException(
					"Duplicate living entity material tint policy: "
							+ owner);
		}
		publishSnapshot();
	}

	@ApiStatus.Internal
	public static MultiBufferSource wrap(
			MultiBufferSource delegate,
			LivingEntity entity,
			float partialTick) {
		Objects.requireNonNull(delegate, "delegate");
		List<ResolvedTint> resolved = resolve(
				new LivingEntityMaterialTintQuery(entity, partialTick));
		return resolved.isEmpty()
				? delegate
				: new TintingBufferSource(delegate, resolved);
	}

	static int transformForTests(
			LivingEntityMaterialTintQuery query,
			int originalArgb) {
		int transformed = originalArgb;
		for (ResolvedTint resolved : resolve(query)) {
			transformed = transform(resolved, transformed);
		}
		return transformed;
	}

	private static List<ResolvedTint> resolve(
			LivingEntityMaterialTintQuery query) {
		Objects.requireNonNull(query, "query");
		List<ResolvedTint> resolved = new ArrayList<>();
		for (Registration registration : snapshot) {
			try {
				LivingEntityMaterialTint tint =
						registration.policy().materialTint(query);
				if (tint != null) {
					resolved.add(new ResolvedTint(
							registration.owner(), tint));
				}
			}
			catch (RuntimeException error) {
				logFailure(
						registration.owner(),
						"policy resolution",
						error);
			}
		}
		return List.copyOf(resolved);
	}

	private static int transform(
			ResolvedTint resolved,
			int originalArgb) {
		try {
			return resolved.tint().transformArgb(originalArgb);
		}
		catch (RuntimeException error) {
			logFailure(
					resolved.owner(),
					"color transform",
					error);
			return originalArgb;
		}
	}

	private static void logFailure(
			ResourceLocation owner,
			String phase,
			RuntimeException error) {
		JojoMod.getLogger().error(
				"Living entity material tint policy {} failed during {}.",
				owner,
				phase,
				error);
	}

	private static void publishSnapshot() {
		List<Registration> registrations =
				new ArrayList<>(POLICIES.size());
		POLICIES.forEach((owner, policy) ->
				registrations.add(new Registration(owner, policy)));
		snapshot = List.copyOf(registrations);
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(POLICIES.keySet());
	}

	static synchronized void resetForTests() {
		POLICIES.clear();
		snapshot = List.of();
	}

	private record Registration(
			ResourceLocation owner,
			LivingEntityMaterialTintPolicy policy) {}

	private record ResolvedTint(
			ResourceLocation owner,
			LivingEntityMaterialTint tint) {}

	private static final class TintingBufferSource
			implements MultiBufferSource {
		private final MultiBufferSource delegate;
		private final List<ResolvedTint> tints;

		private TintingBufferSource(
				MultiBufferSource delegate,
				List<ResolvedTint> tints) {
			this.delegate = delegate;
			this.tints = tints;
		}

		@Override
		public VertexConsumer getBuffer(RenderType renderType) {
			return new TintingVertexConsumer(
					delegate.getBuffer(renderType),
					tints);
		}
	}

	private static final class TintingVertexConsumer
			implements VertexConsumer {
		private final VertexConsumer delegate;
		private final List<ResolvedTint> tints;
		private final boolean[] failed;

		private TintingVertexConsumer(
				VertexConsumer delegate,
				List<ResolvedTint> tints) {
			this.delegate = delegate;
			this.tints = tints;
			this.failed = new boolean[tints.size()];
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(
				int red, int green, int blue, int alpha) {
			int transformed = alpha << 24
					| red << 16
					| green << 8
					| blue;
			for (int i = 0; i < tints.size(); i++) {
				if (failed[i]) {
					continue;
				}
				ResolvedTint tint = tints.get(i);
				try {
					transformed = tint.tint()
							.transformArgb(transformed);
				}
				catch (RuntimeException error) {
					failed[i] = true;
					logFailure(
							tint.owner(),
							"color transform",
							error);
				}
			}
			delegate.setColor(
					transformed >> 16 & 0xFF,
					transformed >> 8 & 0xFF,
					transformed & 0xFF,
					transformed >>> 24);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			delegate.setNormal(x, y, z);
			return this;
		}
	}
}
