package com.github.standobyte.jojoimpl.stands._entitybase;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.world.entity.LivingEntity;

public final class StandAbilityStamina {
	private static final String NOT_ENOUGH_STAMINA = "not_enough_stamina";

	private StandAbilityStamina() {}

	public static ConditionCheck check(Power<?> context, float amount) {
		if (!(context instanceof StandPower standPower)) {
			return ConditionCheck.NEGATIVE;
		}
		return canPay(standPower, amount) ? ConditionCheck.POSITIVE : ConditionCheck.createNegative(NOT_ENOUGH_STAMINA);
	}

	public static boolean canPay(@Nullable StandPower standPower, float amount) {
		if (standPower == null) {
			return false;
		}
		amount = effectiveCost(standPower, amount);
		if (amount <= 0 || standPower.isStaminaInfinite() || standPower.getStamina() >= amount) {
			return true;
		}
		LivingEntity user = standPower.getUser();
		return user != null && ResolveModeEffect.getResolveEffectLvl(user) >= 0;
	}

	public static float effectiveCost(@Nullable StandPower standPower, float amount) {
		return amount;
	}

	public static boolean consume(@Nullable Ability ability, @Nullable StandPower standPower, float amount, boolean ticking) {
		return standPower != null && standPower.consumeStamina(effectiveCost(standPower, amount), ticking);
	}

	public static boolean consume(@Nullable EntityActionType ability, @Nullable StandPower standPower, float amount, boolean ticking) {
		return consume(ability instanceof Ability abilityObj ? abilityObj : null, standPower, amount, ticking);
	}

	public static boolean consumeOrMessage(EntityActionType ability, @Nullable StandPower standPower,
			LivingEntity user, float amount) {
		return consumeOrMessage(ability, standPower, user, amount, false);
	}

	public static boolean consumeOrMessage(@Nullable Ability ability, @Nullable StandPower standPower,
			LivingEntity user, float amount) {
		return consumeOrMessage(ability, standPower, user, amount, false);
	}

	public static boolean consumeOrMessage(@Nullable Ability ability, @Nullable StandPower standPower,
			LivingEntity user, float amount, boolean ticking) {
		if (consume(ability, standPower, amount, ticking)) {
			return true;
		}
		ConditionCheck.sendActionFailedMessage(ability, ConditionCheck.createNegative(NOT_ENOUGH_STAMINA), user);
		return false;
	}

	public static boolean consumeOrMessage(EntityActionType ability, @Nullable StandPower standPower,
			LivingEntity user, float amount, boolean ticking) {
		return consumeOrMessage(
				ability instanceof Ability abilityObj ? abilityObj : null,
				standPower,
				user,
				amount,
				ticking);
	}
}
