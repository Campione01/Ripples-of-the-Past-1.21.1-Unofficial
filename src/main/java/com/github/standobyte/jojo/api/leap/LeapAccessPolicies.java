package com.github.standobyte.jojo.api.leap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed, deny-dominant powered-leap policies.
 */
public final class LeapAccessPolicies {
	private static final Map<ResourceLocation, LeapAccessPolicy> POLICIES =
			new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private LeapAccessPolicies() {}

	public static synchronized void register(
			ResourceLocation owner,
			LeapAccessPolicy policy) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(policy, "policy");
		if (POLICIES.putIfAbsent(owner, policy) != null) {
			throw new IllegalStateException(
					"Duplicate leap access policy: " + owner);
		}
		List<Registration> registrations =
				new ArrayList<>(POLICIES.size());
		POLICIES.forEach((id, registered) ->
				registrations.add(new Registration(id, registered)));
		snapshot = List.copyOf(registrations);
	}

	@ApiStatus.Internal
	public static boolean allowsExecution(
			LivingEntity user,
			LeapSource source) {
		return allows(new LeapAccessQuery(
				user, source, LeapSurface.EXECUTION));
	}

	/**
	 * Future HUD surfaces should use this helper instead of duplicating policy
	 * predicates client-side.
	 */
	public static boolean allowsHud(
			LivingEntity user,
			LeapSource source) {
		return allows(new LeapAccessQuery(user, source, LeapSurface.HUD));
	}

	private static boolean allows(LeapAccessQuery query) {
		for (Registration registration : snapshot) {
			try {
				if (registration.policy().denies(query)) {
					return false;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Leap access policy {} failed.",
						registration.owner(),
						error);
			}
		}
		return true;
	}

	static boolean allowsForTests(
			LeapSource source,
			LeapSurface surface) {
		return allows(new LeapAccessQuery(
				null, source, surface));
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
			LeapAccessPolicy policy) {}
}
