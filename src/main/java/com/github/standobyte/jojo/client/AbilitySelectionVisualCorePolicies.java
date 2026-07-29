package com.github.standobyte.jojo.client;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.api.client.render.AbilitySelectionVisualPolicies;
import com.github.standobyte.jojo.api.client.render.AbilitySelectionVisualQuery;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonRebuffOverdriveAbility.HamonRebuffOverdrive;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.zombie.ZombieData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

final class AbilitySelectionVisualCorePolicies {
	static final int GREEN_TINT = 0xFF00FF00;
	static final ResourceLocation OWNER =
			JojoMod.resLoc("legacy_green_selection");
	private static final OptionalInt GREEN =
			OptionalInt.of(GREEN_TINT);
	private static final int STAND_POWER_CLASS = 0;
	private static final int PLAYER_POWER_CLASS = 1;

	private static final Set<RuleKey> CONDITION_POSITIVE_ABILITIES =
			Set.of(
					standKey("crazy_diamond", "disfiguring_punch"),
					standKey("crazy_diamond", "leave_object"),
					standKey("crazy_diamond", "fuse_with_rock"),
					standKey("gold_experience", "lifeshot"),
					standKey("gold_experience", "tooth_lifeform"));

	private static final Map<RuleKey, Rule> ACTIVE_STATE_ABILITIES =
			Map.of(
					playerKey("hamon", "rebuff_overdrive"),
					Rule.HAMON_REBUFF_ACTIVE,
					playerKey("hamon", "hamon_protection"),
					Rule.HAMON_PROTECTION_ENABLED,
					playerKey("hamon", "wall_climbing"),
					Rule.HAMON_WALL_CLIMBING,
					playerKey("pillarman", "pillarman_stone_form"),
					Rule.PILLARMAN_STONE_FORM_ENABLED,
					playerKey("zombie", "zombie_disguise"),
					Rule.ZOMBIE_DISGUISE_ENABLED);

	private AbilitySelectionVisualCorePolicies() {}

	static void register() {
		AbilitySelectionVisualPolicies.register(
				OWNER,
				AbilitySelectionVisualCorePolicies::selectionTint);
	}

	private static OptionalInt selectionTint(
			AbilitySelectionVisualQuery query) {
		return tintForRule(
				ruleFor(query.ability()),
				query.conditionCheck(),
				query.power());
	}

	static OptionalInt tintForRule(
			@Nullable Rule rule,
			ConditionCheck conditionCheck,
			@Nullable Power<?> power) {
		if (rule == null) {
			return OptionalInt.empty();
		}
		boolean green = rule == Rule.CONDITION_POSITIVE
				? conditionCheck.isPositive()
				: activeState(rule, power);
		return green ? GREEN : OptionalInt.empty();
	}

	@Nullable
	static Rule ruleFor(Ability ability) {
		if (ability.isStandFinisherOf != null) {
			return Rule.CONDITION_POSITIVE;
		}
		return ruleFor(ability.getAbilityId());
	}

	@Nullable
	static Rule ruleFor(AbilityId abilityId) {
		if (abilityId.powerClass() == null) {
			return null;
		}
		return ruleFor(
				abilityId.powerClass().ordinal(),
				abilityId.powerTypeId(),
				abilityId.nameInMoveset());
	}

	@Nullable
	static Rule ruleFor(
			int powerClass,
			ResourceLocation powerType,
			String abilityName) {
		RuleKey key = new RuleKey(
				powerClass,
				powerType,
				abilityName);
		if (CONDITION_POSITIVE_ABILITIES.contains(key)) {
			return Rule.CONDITION_POSITIVE;
		}
		return ACTIVE_STATE_ABILITIES.get(key);
	}

	private static boolean activeState(
			Rule rule,
			@Nullable Power<?> power) {
		PowerData data = power != null ? power.getCurTypeData() : null;
		return switch (rule) {
			case HAMON_REBUFF_ACTIVE -> {
				LivingEntity user = power != null ? power.getUser() : null;
				yield data instanceof HamonData
						&& user != null
						&& LivingComponentAction.getCurEntityAction(user)
								instanceof HamonRebuffOverdrive;
			}
			case HAMON_PROTECTION_ENABLED ->
					data instanceof HamonData hamon
							&& hamon.isProtectionEnabled();
			case HAMON_WALL_CLIMBING ->
					data instanceof HamonData hamon
							&& hamon.isWallClimbing();
			case PILLARMAN_STONE_FORM_ENABLED ->
					data instanceof PillarmanData pillarman
							&& pillarman.isStoneFormEnabled();
			case ZOMBIE_DISGUISE_ENABLED ->
					data instanceof ZombieData zombie
							&& zombie.isDisguiseEnabled();
			case CONDITION_POSITIVE -> false;
		};
	}

	private static RuleKey standKey(
			String powerType,
			String abilityName) {
		return new RuleKey(
				STAND_POWER_CLASS,
				JojoMod.resLoc(powerType),
				abilityName);
	}

	private static RuleKey playerKey(
			String powerType,
			String abilityName) {
		return new RuleKey(
				PLAYER_POWER_CLASS,
				JojoMod.resLoc(powerType),
				abilityName);
	}

	private record RuleKey(
			int powerClass,
			ResourceLocation powerType,
			String abilityName) {}

	enum Rule {
		CONDITION_POSITIVE,
		HAMON_REBUFF_ACTIVE,
		HAMON_PROTECTION_ENABLED,
		HAMON_WALL_CLIMBING,
		PILLARMAN_STONE_FORM_ENABLED,
		ZOMBIE_DISGUISE_ENABLED
	}
}
