package com.github.standobyte.jojo.subsystems.timestop;

import com.github.standobyte.jojo.api.timestop.TimeStopBehaviorPolicies;
import com.github.standobyte.jojo.api.timestop.TimeStopProgressionPolicy;
import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.type.StandTypePersistentData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismData;
import com.github.standobyte.jojoimpl.powers.zombie.ZombieData;
import com.github.standobyte.jojoimpl.powers.zombie.ZombiePowerType;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class TimeStopLearning {
	public static final String TIME_STOP = "time_stop";
	public static final int MIN_RELEASE_TIME_STOP_TICKS = 1;
	public static final int MIN_TIME_STOP_TICKS = 5;
	public static final int HUMAN_MAX_TIME_STOP_TICKS = 100;
	public static final int VAMPIRE_MAX_TIME_STOP_TICKS = 180;
	public static final int PILLARMAN_MAX_TIME_STOP_TICKS = 180;
	public static final int PILLARMAN_STAGE_TIME_STOP_BONUS_TICKS = 40;
	public static final int ZOMBIE_MAX_TIME_STOP_TICKS = 180;
	public static final int CREATIVE_TIME_STOP_TICKS = Integer.MAX_VALUE - 1200;
	public static final float STAR_PLATINUM_LEARNING_PER_TICK = 0.25F;
	public static final float THE_WORLD_LEARNING_PER_TICK = 0.1F;
	public static final float TIME_STOP_DECAY_PER_DAY = 0.0F;
	public static final float BASE_STAMINA_COST = 225F;
	public static final float BASE_STAMINA_COST_TICK = 9F;
	public static final float BLINK_STAMINA_RATIO = 0.8F;
	public static final float TS_PUNCH_BLINK_STAMINA_RATIO = 0.5F;

	private TimeStopLearning() {}

	public static int getTimeStopTicks(StandPower power) {
		if (isCreativeTimeStopTemplate(power)) {
			return CREATIVE_TIME_STOP_TICKS;
		}
		return getSavedTimeStopTicks(power);
	}

	public static int getSavedTimeStopTicks(StandPower power) {
		if (power == null) {
			return MIN_TIME_STOP_TICKS;
		}
		StandTypePersistentData data = power.getCurTypeData();
		float points = data != null ? data.getAbilityLearningProgressPoints(TIME_STOP) : 0.0F;
		return getLearnedTimeStopTicks(points, getNormalMaxTimeStopTicks(power));
	}

	static int getLearnedTimeStopTicks(float points, int maxTicks) {
		if (points < 0.0F) {
			points = 0.0F;
		}
		maxTicks = Math.max(maxTicks, MIN_TIME_STOP_TICKS);
		return Mth.clamp(Mth.floor(points) + MIN_TIME_STOP_TICKS, MIN_TIME_STOP_TICKS, maxTicks);
	}

	public static int getReleasedTimeStopTicks(int maxTicks, float chargeRatio) {
		int clampedMax = Math.max(maxTicks, MIN_RELEASE_TIME_STOP_TICKS);
		float clampedRatio = Mth.clamp(chargeRatio, 0.0F, 1.0F);
		return Mth.clamp(Mth.ceil(clampedMax * clampedRatio),
				MIN_RELEASE_TIME_STOP_TICKS, clampedMax);
	}

	public static float getTimeStopChargeRatio(int chargeTicks, float chargeLength) {
		if (chargeLength <= 0) {
			return 1.0F;
		}
		return Mth.clamp((float) Math.max(chargeTicks, 1) / (float) chargeLength,
				0.0F, 1.0F);
	}

	public static int getReleasedTimeStopTicks(
			int maxTicks, int chargeTicks, float chargeLength) {
		return getReleasedTimeStopTicks(
				maxTicks, getTimeStopChargeRatio(chargeTicks, chargeLength));
	}

	public static int getMaxTrainingPoints(StandPower power) {
		return getNormalMaxTimeStopTicks(power) - MIN_TIME_STOP_TICKS;
	}

	public static int getMaxTimeStopTicks(StandPower power) {
		if (isCreativeTimeStopTemplate(power)) {
			return CREATIVE_TIME_STOP_TICKS;
		}
		return getNormalMaxTimeStopTicks(power);
	}

	public static boolean isCreativeTimeStopTemplate(StandPower power) {
		return power != null && power.isUserCreative();
	}

	private static int getNormalMaxTimeStopTicks(StandPower power) {
		LivingEntity user = power != null ? power.getUser() : null;
		TimeStopProgressionPolicy policy =
				TimeStopBehaviorPolicies.progression(power);
		int humanMaxTicks = policy != null
				? policy.humanMaxTicks()
				: HUMAN_MAX_TIME_STOP_TICKS;
		int enhancedMaxTicks = policy != null
				? policy.enhancedMaxTicks()
				: VAMPIRE_MAX_TIME_STOP_TICKS;
		if (isHighSaturationZombie(user)) {
			return policy != null
					? enhancedMaxTicks
					: ZOMBIE_MAX_TIME_STOP_TICKS;
		}
		int pillarmanTicks =
				getPillarmanTimeStopTicks(user, enhancedMaxTicks);
		if (pillarmanTicks > 0) {
			return pillarmanTicks;
		}
		if (isHighBloodVampire(user)) {
			return enhancedMaxTicks;
		}
		return humanMaxTicks;
	}

	public static float getTimeStopStaminaCostTick(StandPower power) {
		return BASE_STAMINA_COST_TICK * HUMAN_MAX_TIME_STOP_TICKS / getTimeStopTicks(power);
	}

	public static float getTimeStopStaminaCost(StandPower power, int ticks) {
		int maxTicks = getTimeStopTicks(power);
		int clampedTicks = Mth.clamp(ticks, MIN_RELEASE_TIME_STOP_TICKS, maxTicks);
		return BASE_STAMINA_COST * clampedTicks / maxTicks;
	}

	public static float getTimeStopBlinkStaminaCost(StandPower power) {
		return BASE_STAMINA_COST * BLINK_STAMINA_RATIO;
	}

	public static float getTimeStopBlinkStaminaCostTicking(StandPower power) {
		return getTimeStopStaminaCostTick(power) * BLINK_STAMINA_RATIO;
	}

	public static float getTsPunchTimeStopBaseStaminaCost(StandPower power) {
		return getTimeStopBlinkStaminaCost(power) * TS_PUNCH_BLINK_STAMINA_RATIO;
	}

	public static float getTsPunchTimeStopStaminaCostTicking(StandPower power) {
		return getTimeStopBlinkStaminaCostTicking(power);
	}

	public static int getAffordableTsPunchTimeStopTicks(StandPower power) {
		int timeStopTicks = getTimeStopTicks(power);
		if (StandUtil.standIgnoresStaminaDebuff(power)) {
			return timeStopTicks;
		}
		float costMultiplier = PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power);
		float tickingCost = getTsPunchTimeStopStaminaCostTicking(power) * costMultiplier;
		if (tickingCost <= 0.0F) {
			return timeStopTicks;
		}
		int affordableTicks = Mth.floor((power.getStamina() - getTsPunchTimeStopBaseStaminaCost(power) * costMultiplier) / tickingCost);
		return Mth.clamp(affordableTicks, 0, timeStopTicks);
	}

	public static float getTsPunchTimeStopStaminaCost(StandPower power, int ticks) {
		return getTsPunchTimeStopBaseStaminaCost(power) + Math.max(ticks, 0) * getTsPunchTimeStopStaminaCostTicking(power);
	}

	public static void consumeTsPunchTimeStopStamina(StandPower power, int ticks) {
		if (power != null) {
			power.consumeStamina(getTsPunchTimeStopStaminaCost(power, ticks)
					* PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power));
		}
	}

	public static float learningPerTick(StandPower standPower) {
		TimeStopProgressionPolicy policy =
				TimeStopBehaviorPolicies.progression(standPower);
		if (policy != null) {
			return policy.learningPerTick();
		}
		if (standPower != null && standPower.getPowerType() == ModStands.STAR_PLATINUM.get()) {
			return STAR_PLATINUM_LEARNING_PER_TICK;
		}
		if (standPower != null && standPower.getPowerType() == ModStands.THE_WORLD.get()) {
			return THE_WORLD_LEARNING_PER_TICK;
		}
		return THE_WORLD_LEARNING_PER_TICK;
	}

	public static void onTimeStopEnded(StandPower standPower, int ticksPassed) {
		if (!canLearnFromEndedTimeStop(standPower)) {
			return;
		}
		addTimeStopLearning(standPower,
				getLearningPoints(learningPerTick(standPower), ticksPassed));
	}

	private static boolean canLearnFromEndedTimeStop(StandPower standPower) {
		return standPower != null && standPower.hasPower()
				&& standPower.isAbilityUnlocked(TIME_STOP);
	}

	public static void onTsPunchTimeSkip(StandPower standPower, int ticksPassed) {
		addTimeStopLearning(standPower,
				getLearningPoints(learningPerTick(standPower), ticksPassed));
	}

	static float getLearningPoints(float learningPerTick, int ticksPassed) {
		return learningPerTick * Math.max(ticksPassed, 0);
	}

	private static void addTimeStopLearning(StandPower standPower, float points) {
		if (isCreativeTimeStopTemplate(standPower)
				|| standPower == null || !standPower.hasPower() || standPower.getCurTypeData() == null
				|| standPower.getCurTypeData().getAbilityLearningProgressPoints(TIME_STOP) < 0.0F) {
			return;
		}
		standPower.getCurTypeData().addAbilityLearningProgressPoints(TIME_STOP, points,
				getMaxTrainingPoints(standPower), standPower);
	}

	public static void markUsedTimeStopToday(StandPower standPower) {
		if (standPower != null && standPower.getCurTypeData() != null) {
			standPower.getCurTypeData().markUsedTimeStopToday(standPower);
		}
	}

	public static void applyDailyDecay(StandTypePersistentData data, StandPower standPower) {
		TimeStopProgressionPolicy policy =
				TimeStopBehaviorPolicies.progression(standPower);
		float decayPerDay = policy != null
				? policy.decayPerDay()
				: TIME_STOP_DECAY_PER_DAY;
		if (decayPerDay > 0.0F) {
			data.addAbilityLearningProgressPoints(TIME_STOP, -decayPerDay,
					getMaxTrainingPoints(standPower), standPower);
		}
	}

	private static boolean isHighBloodVampire(LivingEntity user) {
		return user != null && PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM)
				.map((VampirismData data) -> data.isHighOnBlood(user))
				.orElse(false);
	}

	private static int getPillarmanTimeStopTicks(
			LivingEntity user, int enhancedBaseTicks) {
		return user != null ? PlayerPower.getPowerData(user, PillarmanPowerType.PILLAR_MAN)
				.map(PillarmanData::getEvolutionStage)
				.map(stage -> (int) Math.min(
						(long) enhancedBaseTicks
								+ (long) Math.max(stage - 1, 0)
										* PILLARMAN_STAGE_TIME_STOP_BONUS_TICKS,
						CREATIVE_TIME_STOP_TICKS))
				.orElse(0) : 0;
	}

	private static boolean isHighSaturationZombie(LivingEntity user) {
		return user != null && PlayerPower.getPowerData(user, ZombiePowerType.ZOMBIE)
				.map(data -> data.isHighSaturation(user))
				.orElse(false);
	}
}
