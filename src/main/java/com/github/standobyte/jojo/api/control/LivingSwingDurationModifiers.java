package com.github.standobyte.jojo.api.control;

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
 * Owner-keyed modifiers for vanilla living-entity swing duration.
 *
 * <p>Modifiers compose in registration order. Each receives the duration
 * returned by the previous modifier. Non-positive results are ignored so a
 * faulty addon cannot break the vanilla swing state machine.</p>
 */
public final class LivingSwingDurationModifiers {
	private static final Map<ResourceLocation,
			LivingSwingDurationModifier> MODIFIERS =
					new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private LivingSwingDurationModifiers() {}

	public static synchronized void register(
			ResourceLocation owner,
			LivingSwingDurationModifier modifier) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(modifier, "modifier");
		if (MODIFIERS.putIfAbsent(owner, modifier) != null) {
			throw new IllegalStateException(
					"Duplicate living swing-duration modifier: " + owner);
		}
		publishSnapshot();
	}

	@ApiStatus.Internal
	public static int apply(
			LivingEntity entity,
			int originalDuration) {
		int duration = Math.max(1, originalDuration);
		for (Registration registration : snapshot) {
			try {
				int modified = registration.modifier().modifyDuration(
						new LivingSwingDurationQuery(entity, duration));
				if (modified > 0) {
					duration = modified;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Living swing-duration modifier {} failed.",
						registration.owner(),
						error);
			}
		}
		return duration;
	}

	private static void publishSnapshot() {
		List<Registration> registrations =
				new ArrayList<>(MODIFIERS.size());
		MODIFIERS.forEach((owner, modifier) ->
				registrations.add(new Registration(owner, modifier)));
		snapshot = List.copyOf(registrations);
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(MODIFIERS.keySet());
	}

	static synchronized void resetForTests() {
		MODIFIERS.clear();
		snapshot = List.of();
	}

	private record Registration(
			ResourceLocation owner,
			LivingSwingDurationModifier modifier) {}
}
