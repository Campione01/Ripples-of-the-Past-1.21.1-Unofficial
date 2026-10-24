package rotp.core.api.power;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;

import net.minecraft.world.entity.LivingEntity;

/**
 * An add-on's refusal of an ability in the core's condition check. See {@link AbilityRefusals}.
 */
@FunctionalInterface
public interface AbilityRefusal {
	/**
	 * Asked on the server and on the user's client alike: read the client's own copy of any state the refusal needs there.
	 * @param context the user's power the ability belongs to
	 * @return a negative check (with the message a refused press shows, or none) to refuse the ability, or null to leave it
	 *         to the next refusal and to its own checks; a positive check counts as null
	 */
	@Nullable
	ConditionCheck refuse(Ability ability, Power<?> context, LivingEntity user);
}
