package rotp.core.api.stand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed rules for the default rotation of a following Stand whose user is not a player: 1.16's
 * StandEntity.defaultRotation, which an add-on could replace for a mob user (Mobs with Powers faced the mob's target, or
 * set no rotation without one).
 *
 * <p>Asked on both logical sides on every tick a following Stand of such a user would copy its user's yaw, pitch and head,
 * and with no action its body ({@link StandEntity#updatePosition}). The Stand's action and its task's target turn it
 * after that, and the head goes on the yaw as usual. Rules run in registration order until one takes the rotation over;
 * with none, the core copies it. A player's Stand and the copy as a Stand is summoned are never asked. A rule that fails
 * leaves the copy to the core. Register from common setup, so that clients have the rule too.</p>
 */
public final class StandDefaultRotationRules {
	private static final Map<ResourceLocation, StandDefaultRotationRule> RULES = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private StandDefaultRotationRules() {}

	public static synchronized void register(ResourceLocation owner, StandDefaultRotationRule rule) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(rule, "rule");
		if (RULES.putIfAbsent(owner, rule) != null) {
			throw new IllegalStateException("Duplicate Stand default rotation rule: " + owner);
		}
		List<Registration> registrations = new ArrayList<>(RULES.size());
		RULES.forEach((key, value) -> registrations.add(new Registration(key, value)));
		snapshot = List.copyOf(registrations);
	}

	/** @return whether a rule took the default rotation over */
	@ApiStatus.Internal
	public static boolean rotate(StandEntity stand, LivingEntity user) {
		for (Registration registration : snapshot) {
			try {
				if (registration.rule().rotate(stand, user)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Stand default rotation rule {} failed.", registration.owner(), error);
			}
		}
		return false;
	}

	private record Registration(ResourceLocation owner, StandDefaultRotationRule rule) {}
}
