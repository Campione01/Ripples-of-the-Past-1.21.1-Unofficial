package com.github.standobyte.jojo.api.control;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owner-keyed, deny-dominant policies for server-owned player operations.
 *
 * <p>Providers must be side-effect-free. The core owns cancellation and any
 * denial feedback.</p>
 */
public final class PlayerOperationPolicies {
	private static final Map<ResourceLocation, PlayerOperationPolicy> POLICIES =
			new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private PlayerOperationPolicies() {}

	public static synchronized void register(
			ResourceLocation owner,
			PlayerOperationPolicy policy) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(policy, "policy");
		if (POLICIES.putIfAbsent(owner, policy) != null) {
			throw new IllegalStateException(
					"Duplicate player operation policy: " + owner);
		}
		List<Registration> registrations =
				new ArrayList<>(POLICIES.size());
		POLICIES.forEach((id, registered) ->
				registrations.add(new Registration(id, registered)));
		snapshot = List.copyOf(registrations);
	}

	@ApiStatus.Internal
	public static boolean intercept(
			ServerPlayer player,
			PlayerOperation operation) {
		PlayerOperationDecision decision =
				evaluate(new PlayerOperationQuery(player, operation));
		if (!decision.denied()) {
			return false;
		}
		if (decision.denialMessage() != null) {
			player.displayClientMessage(decision.denialMessage(), true);
		}
		return true;
	}

	private static PlayerOperationDecision evaluate(
			PlayerOperationQuery query) {
		for (Registration registration : snapshot) {
			try {
				PlayerOperationDecision decision =
						Objects.requireNonNull(
								registration.policy().decide(query),
								"policy decision");
				if (decision.denied()) {
					return decision;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Player operation policy {} failed.",
						registration.owner(),
						error);
			}
		}
		return PlayerOperationDecision.pass();
	}

	static PlayerOperationDecision evaluateForTests(
			PlayerOperation operation) {
		return evaluate(new PlayerOperationQuery(null, operation));
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
			PlayerOperationPolicy policy) {}
}
