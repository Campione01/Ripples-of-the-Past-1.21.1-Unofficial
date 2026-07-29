package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed vetoes for the core's first-person Stand arm pass.
 *
 * <p>Policies run in registration order until one suppresses the pass. They
 * do not replace the Stand renderer and retain no frame-scoped state.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FirstPersonStandRenderPolicies {
	private static final Map<ResourceLocation,
			FirstPersonStandRenderPolicy> POLICIES =
					new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private FirstPersonStandRenderPolicies() {}

	public static synchronized void register(
			ResourceLocation owner,
			FirstPersonStandRenderPolicy policy) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(policy, "policy");
		if (POLICIES.putIfAbsent(owner, policy) != null) {
			throw new IllegalStateException(
					"Duplicate first-person Stand render policy: "
							+ owner);
		}
		publishSnapshot();
	}

	@ApiStatus.Internal
	public static boolean shouldSuppress(
			LocalPlayer viewer,
			StandEntity stand,
			float partialTick) {
		return shouldSuppress(new FirstPersonStandRenderQuery(
				viewer, stand, partialTick));
	}

	static boolean shouldSuppress(
			FirstPersonStandRenderQuery query) {
		for (Registration registration : snapshot) {
			try {
				if (registration.policy().shouldSuppress(query)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"First-person Stand render policy {} failed.",
						registration.owner(),
						error);
			}
		}
		return false;
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
			FirstPersonStandRenderPolicy policy) {}
}
