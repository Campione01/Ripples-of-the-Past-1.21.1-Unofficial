package rotp.core.api.client.render;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;

public record AbilitySelectionVisualQuery(
		Ability ability,
		@Nullable Power<?> power,
		ConditionCheck conditionCheck,
		AbilitySelectionSurface surface) {}
