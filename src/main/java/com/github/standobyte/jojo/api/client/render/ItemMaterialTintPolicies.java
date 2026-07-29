package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only, owner-keyed material policies for standard baked item models.
 *
 * <p>The core wraps the final vertex consumer after vanilla item and foil
 * routing. Custom item renderers are deliberately outside this contract.</p>
 */
public final class ItemMaterialTintPolicies {
	private static final Map<ResourceLocation, ItemMaterialTintProvider>
			PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private ItemMaterialTintPolicies() {}

	public static synchronized void register(
			ResourceLocation owner,
			ItemMaterialTintProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate item material tint policy: " + owner);
		}
		List<Registration> registrations =
				new ArrayList<>(PROVIDERS.size());
		PROVIDERS.forEach((id, registered) ->
				registrations.add(new Registration(id, registered)));
		snapshot = List.copyOf(registrations);
	}

	@ApiStatus.Internal
	public static VertexConsumer wrap(
			VertexConsumer delegate,
			ItemStack stack,
			ItemDisplayContext displayContext) {
		Objects.requireNonNull(delegate, "delegate");
		ResolvedTint resolved = resolve(stack, displayContext);
		return resolved == null
				? delegate
				: new TintingVertexConsumer(delegate, resolved);
	}

	static int transformForTests(
			ItemMaterialTintQuery query,
			int originalArgb) {
		ResolvedTint resolved = resolve(
				Objects.requireNonNull(query, "query"));
		return resolved == null
				? originalArgb
				: transform(resolved, originalArgb);
	}

	@Nullable
	private static ResolvedTint resolve(
			ItemStack stack,
			ItemDisplayContext displayContext) {
		return resolve(new ItemMaterialTintQuery(
				Objects.requireNonNull(stack, "stack"),
				Objects.requireNonNull(
						displayContext, "displayContext")));
	}

	@Nullable
	private static ResolvedTint resolve(ItemMaterialTintQuery query) {
		for (Registration registration : snapshot) {
			try {
				ItemMaterialTint tint =
						registration.provider().materialTint(query);
				if (tint != null) {
					return new ResolvedTint(registration.owner(), tint);
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Item material tint policy {} failed.",
						registration.owner(),
						error);
			}
		}
		return null;
	}

	private static int transform(ResolvedTint resolved, int originalArgb) {
		try {
			return resolved.tint().transformArgb(originalArgb);
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"Item material tint {} failed.",
					resolved.owner(),
					error);
			return originalArgb;
		}
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PROVIDERS.keySet());
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
		snapshot = List.of();
	}

	private record Registration(
			ResourceLocation owner,
			ItemMaterialTintProvider provider) {}

	private record ResolvedTint(
			ResourceLocation owner,
			ItemMaterialTint tint) {}

	private static final class TintingVertexConsumer
			implements VertexConsumer {
		private final VertexConsumer delegate;
		private final ResolvedTint resolved;
		private boolean tintFailed;

		private TintingVertexConsumer(
				VertexConsumer delegate,
				ResolvedTint resolved) {
			this.delegate = delegate;
			this.resolved = resolved;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(
				int red, int green, int blue, int alpha) {
			int original = alpha << 24
					| red << 16
					| green << 8
					| blue;
			int transformed = original;
			if (!tintFailed) {
				try {
					transformed = resolved.tint()
							.transformArgb(original);
				}
				catch (RuntimeException error) {
					tintFailed = true;
					JojoMod.getLogger().error(
							"Item material tint {} failed; "
									+ "the remaining model is untinted.",
							resolved.owner(),
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
