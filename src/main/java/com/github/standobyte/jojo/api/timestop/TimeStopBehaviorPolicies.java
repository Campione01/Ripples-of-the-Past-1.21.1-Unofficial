package com.github.standobyte.jojo.api.timestop;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import net.minecraft.resources.ResourceLocation;

/**
 * Exclusive, owner-keyed bindings between Stand types and time-stop behavior.
 * One owner may register one binding and one Stand type may have one policy;
 * duplicate owners and target conflicts are rejected at registration time.
 */
public final class TimeStopBehaviorPolicies {
	private static final Map<ResourceLocation, Binding> BY_OWNER =
			new LinkedHashMap<>();
	private static final Map<ResourceLocation, Binding> BY_STAND =
			new HashMap<>();

	private TimeStopBehaviorPolicies() {}

	public static synchronized void register(
			ResourceLocation owner,
			ResourceLocation standType,
			TimeStopBehaviorPolicy policy) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(standType, "standType");
		Objects.requireNonNull(policy, "policy");
		if (BY_OWNER.containsKey(owner)) {
			throw new IllegalStateException(
					"Duplicate time-stop behavior owner: " + owner);
		}
		Binding existing = BY_STAND.get(standType);
		if (existing != null) {
			throw new IllegalStateException(
					"Time-stop behavior for " + standType
							+ " is already owned by " + existing.owner());
		}
		Binding binding = new Binding(owner, standType, policy);
		BY_OWNER.put(owner, binding);
		BY_STAND.put(standType, binding);
	}

	@ApiStatus.Internal
	public static TimeStopStartupCostDecision resolveStartupCost(
			StandPower power,
			AbilityId abilityId,
			@Nullable TimeStopState.Instance instance,
			float defaultCost) {
		TimeStopBehaviorPolicy policy = find(power);
		if (policy == null) {
			return TimeStopStartupCostDecision.pass();
		}
		try {
			TimeStopStartupCostDecision decision = policy.startupCost(
					new TimeStopStartupCostContext(
							power, abilityId, instance, defaultCost));
			return Objects.requireNonNull(
					decision, "Time-stop startup-cost decision");
		}
		catch (RuntimeException error) {
			logFailure(power.getPowerType().getId(), "startup cost", error);
			return TimeStopStartupCostDecision.pass();
		}
	}

	@ApiStatus.Internal
	public static TimeStopAudioDecision resolveAudio(
			@Nullable ResourceLocation standType,
			TimeStopAudioContext context) {
		TimeStopBehaviorPolicy policy = find(standType);
		if (policy == null) {
			return TimeStopAudioDecision.pass();
		}
		try {
			TimeStopAudioDecision decision = policy.audio(context);
			return Objects.requireNonNull(
					decision, "Time-stop audio decision");
		}
		catch (RuntimeException error) {
			logFailure(standType, "audio", error);
			return TimeStopAudioDecision.pass();
		}
	}

	@ApiStatus.Internal
	@Nullable
	public static TimeStopProgressionPolicy progression(
			@Nullable StandPower power) {
		TimeStopBehaviorPolicy policy = find(power);
		if (policy == null) {
			return null;
		}
		try {
			return policy.progression();
		}
		catch (RuntimeException error) {
			logFailure(power.getPowerType().getId(), "progression", error);
			return null;
		}
	}

	@Nullable
	private static TimeStopBehaviorPolicy find(@Nullable StandPower power) {
		if (power == null || power.getPowerType() == null) {
			return null;
		}
		return find(power.getPowerType().getId());
	}

	@Nullable
	static synchronized TimeStopBehaviorPolicy find(
			@Nullable ResourceLocation standType) {
		Binding binding = standType != null ? BY_STAND.get(standType) : null;
		return binding != null ? binding.policy() : null;
	}

	private static void logFailure(
			ResourceLocation standType,
			String surface,
			RuntimeException error) {
		JojoMod.getLogger().error(
				"Time-stop behavior policy for {} failed while resolving {}.",
				standType,
				surface,
				error);
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(BY_OWNER.keySet());
	}

	static synchronized List<ResourceLocation> registeredStandTypes() {
		return List.copyOf(BY_STAND.keySet());
	}

	static synchronized void resetForTests() {
		BY_OWNER.clear();
		BY_STAND.clear();
	}

	private record Binding(
			ResourceLocation owner,
			ResourceLocation standType,
			TimeStopBehaviorPolicy policy) {}
}
