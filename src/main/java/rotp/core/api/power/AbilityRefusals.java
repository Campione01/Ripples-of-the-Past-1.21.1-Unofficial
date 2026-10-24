package rotp.core.api.power;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed refusals of abilities in the core's condition check, as a 1.16 add-on refused an action at the head of
 * its Action.checkConditions (Gold Experience Requiem's DisablerMixin on StandAction and NonStandAction).
 *
 * <p>Asked on both logical sides wherever the core checks an ability's conditions: the HUD's slot check (AvailableAbilities),
 * the press on the user's client and on the server (Ability#checkConditions), every held tick and before a released hold
 * fires (EntityActionAbility#checkHeldActionConditions, which ends a refused hold with the refusal's message, as 1.16's
 * held tick re-ran checkConditions). In the press check it comes right after the silent refusal by the power's held action
 * and before every check of the ability itself (its player action gate, Stand parts, stopped time, cooldown, performer
 * stun, specific conditions); in the held recheck after its performer checks, where 1.16's StandAction checks began.
 * Only an ability that was a 1.16 action is asked (Ability#endsOtherHoldOnPress): never the manual control key or the grab
 * release, which 1.16 did not run as actions (its remote control key sent its own packet). Refusals run in registration
 * order and the first negative answer is the check's result. A refusal that fails is logged and skipped. Register from
 * common setup, so that clients have it too. Feature flag:
 * {@link rotp.core.api.RotpAddonApi#FEATURE_ABILITY_CONDITION_REFUSALS_V1}.</p>
 */
public final class AbilityRefusals {
	private static final Map<ResourceLocation, AbilityRefusal> REFUSALS = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private AbilityRefusals() {}

	public static synchronized void register(ResourceLocation owner, AbilityRefusal refusal) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(refusal, "refusal");
		if (REFUSALS.putIfAbsent(owner, refusal) != null) {
			throw new IllegalStateException("Duplicate ability refusal: " + owner);
		}
		refreshSnapshot();
	}

	/** @return whether a refusal was registered for the owner */
	public static synchronized boolean unregister(ResourceLocation owner) {
		boolean removed = REFUSALS.remove(Objects.requireNonNull(owner, "owner")) != null;
		if (removed) {
			refreshSnapshot();
		}
		return removed;
	}

	private static void refreshSnapshot() {
		List<Registration> registrations = new ArrayList<>(REFUSALS.size());
		REFUSALS.forEach((key, value) -> registrations.add(new Registration(key, value)));
		snapshot = List.copyOf(registrations);
	}

	/** @return the first registered refusal of the ability, or null when none refuses it */
	@ApiStatus.Internal
	@Nullable
	public static ConditionCheck refusal(Ability ability, @Nullable Power<?> context) {
		LivingEntity user = context != null ? context.getUser() : null;
		if (user == null || !ability.endsOtherHoldOnPress()) {
			return null;
		}
		for (Registration registration : snapshot) {
			try {
				ConditionCheck check = registration.refusal().refuse(ability, context, user);
				if (check != null && !check.isPositive()) {
					return check;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Ability refusal {} failed.", registration.owner(), error);
			}
		}
		return null;
	}

	private record Registration(ResourceLocation owner, AbilityRefusal refusal) {}
}
