package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed material tints for the existing Stand renderer pipeline.
 *
 * <p>Every matching tint composes in registration order. The core wraps the
 * renderer's buffer source, preserving the selected model, render type,
 * feature layers, alpha routing, and shader compatibility.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class StandMaterialTintPolicies {
	private static final Map<ResourceLocation,
			StandMaterialTintPolicy> POLICIES =
					new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private StandMaterialTintPolicies() {}

	public static synchronized void register(
			ResourceLocation owner,
			StandMaterialTintPolicy policy) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(policy, "policy");
		if (POLICIES.putIfAbsent(owner, policy) != null) {
			throw new IllegalStateException(
					"Duplicate Stand material tint policy: " + owner);
		}
		publishSnapshot();
	}

	@ApiStatus.Internal
	public static MultiBufferSource wrap(
			MultiBufferSource delegate,
			StandEntity stand,
			float partialTick) {
		Objects.requireNonNull(delegate, "delegate");
		List<ResolvedTint> resolved = resolve(
				new StandMaterialTintQuery(stand, partialTick));
		return resolved.isEmpty()
				? delegate
				: new TintingBufferSource(delegate, resolved);
	}

	static int transformForTests(
			StandMaterialTintQuery query,
			int originalArgb) {
		int transformed = originalArgb;
		for (ResolvedTint resolved : resolve(query)) {
			transformed = transform(resolved, transformed);
		}
		return transformed;
	}

	private static List<ResolvedTint> resolve(
			StandMaterialTintQuery query) {
		List<ResolvedTint> resolved = new ArrayList<>();
		for (Registration registration : snapshot) {
			try {
				StandMaterialTint tint =
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
				"Stand material tint policy {} failed during {}.",
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
			StandMaterialTintPolicy policy) {}

	private record ResolvedTint(
			ResourceLocation owner,
			StandMaterialTint tint) {}

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
