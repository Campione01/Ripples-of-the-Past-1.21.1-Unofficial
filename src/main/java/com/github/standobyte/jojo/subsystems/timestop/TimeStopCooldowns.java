package com.github.standobyte.jojo.subsystems.timestop;

import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

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
		int cooldown = timeStopCooldown(power, ticksPassed);
		power.setAbilityCooldown(TIME_STOP, cooldown);
		int blinkCooldown = timeStopBlinkCooldown(power, ticksPassed);
		if (power.getMoveset().getAbility(TIME_STOP_BLINK) != null) {
			power.setAbilityCooldown(TIME_STOP_BLINK, blinkCooldown);
		}
	}

	public static void setTimeStopBlinkCooldowns(StandPower power, int impliedTicks) {
		int cooldown = timeStopBlinkCooldown(power, impliedTicks);
		if (cooldown > 0) {
			power.setAbilityCooldown(TIME_STOP_BLINK, cooldown);
			if (!power.isAbilityOnCooldown(TIME_STOP)) {
				power.setAbilityCooldown(TIME_STOP, cooldown);
			}
		}
	}

	private static float timeStopCooldownFloat(StandPower power, int ticks) {
		if (power.isUserCreative()) {
			return 0;
		}
		float cooldown = TIME_STOP_COOLDOWN_PER_TICK * Math.max(ticks, 0);
		LivingEntity user = power.getUser();
		if (user != null && user.hasEffect(ModStatusEffects.RESOLVE)) {
			cooldown /= 3F;
		}
		return cooldown;
	}
}
