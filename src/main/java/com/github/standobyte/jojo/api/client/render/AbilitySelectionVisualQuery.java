package com.github.standobyte.jojo.api.client.render;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;

public record AbilitySelectionVisualQuery(
		Ability ability,
		@Nullable Power<?> power,
		ConditionCheck conditionCheck,
		AbilitySelectionSurface surface) {}
