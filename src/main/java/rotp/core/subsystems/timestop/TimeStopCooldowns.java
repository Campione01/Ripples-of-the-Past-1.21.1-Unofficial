package rotp.core.subsystems.timestop;

import javax.annotation.Nullable;

import rotp.core.api.timestop.TimeStopBehaviorPolicies;
import rotp.core.api.timestop.TimeStopProgressionPolicy;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.world.entity.LivingEntity;

public final class TimeStopCooldowns {
	public static final String TIME_STOP = "time_stop";
	public static final String TIME_STOP_BLINK = "time_stop_blink";
	public static final float TIME_STOP_COOLDOWN_PER_TICK = 3F;
	public static final float TIME_STOP_BLINK_COOLDOWN_RATIO = 1F / 6F;

	private TimeStopCooldowns() {}

	public static int timeStopCooldown(StandPower power, int ticks) {
		return (int) timeStopCooldownFloat(power, ticks);
	}

	public static int timeStopBlinkCooldown(StandPower power, int ticks) {
		return (int) (timeStopCooldownFloat(power, ticks) * TIME_STOP_BLINK_COOLDOWN_RATIO);
	}

	public static void setTimeStopCooldownsOnTimeStopEnd(StandPower power, int ticksPassed) {
		setTimeStopCooldownsOnTimeStopEnd(power, TIME_STOP, ticksPassed);
	}

	/**
	 * 1.16 TimeStopInstance.onRemoved: the ended time stop action and its instant
	 * variation (the blink bound to it) get the cooldown, whatever their moveset names.
	 */
	public static void setTimeStopCooldownsOnTimeStopEnd(StandPower power, String timeStopAbilityName, int ticksPassed) {
		int cooldown = timeStopCooldown(power, ticksPassed);
		power.setAbilityCooldown(timeStopAbilityName, cooldown);
		int blinkCooldown = timeStopBlinkCooldown(power, ticksPassed);
		String blinkAbilityName = getTimeStopBlinkAbilityName(power, timeStopAbilityName);
		if (blinkAbilityName != null) {
			power.setAbilityCooldown(blinkAbilityName, blinkCooldown);
		}
	}

	@Nullable
	private static String getTimeStopBlinkAbilityName(StandPower power, String timeStopAbilityName) {
		if (TIME_STOP.equals(timeStopAbilityName)) {
			return power.getMoveset().getAbility(TIME_STOP_BLINK) != null ? TIME_STOP_BLINK : null;
		}
		for (Ability ability : power.getMoveset().abilities.values()) {
			if (ability instanceof TimeStopBlinkAbility blink
					&& timeStopAbilityName.equals(blink.getTimeStopAbilityName())) {
				return ability.name();
			}
		}
		return null;
	}

	public static void setTimeStopBlinkCooldowns(StandPower power, int impliedTicks) {
		setTimeStopBlinkCooldowns(power, TIME_STOP_BLINK, TIME_STOP, impliedTicks);
	}

	/**
	 * 1.16 TimeStopInstant.perform: the blink, and its base time stop unless that is already
	 * cooling down, get the blink cooldown, whatever their moveset names.
	 */
	public static void setTimeStopBlinkCooldowns(StandPower power, TimeStopBlinkAbility blink, int impliedTicks) {
		setTimeStopBlinkCooldowns(power, blink.name(), blink.getTimeStopAbilityName(), impliedTicks);
	}

	private static void setTimeStopBlinkCooldowns(StandPower power, String blinkAbilityName,
			String timeStopAbilityName, int impliedTicks) {
		int cooldown = timeStopBlinkCooldown(power, impliedTicks);
		if (cooldown > 0) {
			power.setAbilityCooldown(blinkAbilityName, cooldown);
			if (!power.isAbilityOnCooldown(timeStopAbilityName)) {
				power.setAbilityCooldown(timeStopAbilityName, cooldown);
			}
		}
	}

	private static float timeStopCooldownFloat(StandPower power, int ticks) {
		if (power.isUserCreative()) {
			return 0;
		}
		TimeStopProgressionPolicy policy =
				TimeStopBehaviorPolicies.progression(power);
		float cooldownPerTick = policy != null
				? policy.cooldownPerTick()
				: TIME_STOP_COOLDOWN_PER_TICK;
		float cooldown = cooldownPerTick * Math.max(ticks, 0);
		LivingEntity user = power.getUser();
		if (user != null && user.hasEffect(ModStatusEffects.RESOLVE)) {
			cooldown /= 3F;
		}
		return cooldown;
	}
}
