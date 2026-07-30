package com.github.standobyte.jojoimpl.powers.hamon;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerData;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.movement_input_sync.PlayerMovementInputData;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonResetSkillsButtonPacket.HamonSkillsTab;
import com.github.standobyte.jojoimpl.powers.hamon.HamonSkillDefinition.HamonSkillBranch;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonSunlightYellowOverdriveAbility.SYOverdrive;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonTrainingHudFeedback;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.network.PacketDistributor;

public class HamonData extends PlayerPowerData {
	private static final int MAX_TECHNIQUE_NAME_LENGTH = 128;
	private static final int MAX_ABILITY_NAME_LENGTH = 256;
	private static final int MAX_ABILITY_COOLDOWNS = 1024;
	public enum HamonStat {
		STRENGTH,
		CONTROL
	}

	public static final int MAX_STAT_LEVEL = 60;
	public static final float MAX_BREATHING_LEVEL = 100.0F;
	public static final float BASE_MAX_ENERGY = 1000.0F;
	public static final float MAX_BREATH_STABILITY = BASE_MAX_ENERGY;
	public static final float ENERGY_TICK_DOWN_AMOUNT = 20.0F;
	private static final int[] POINTS_AT_LEVEL = new int[MAX_STAT_LEVEL + 1];
	public static final int MAX_HAMON_POINTS;
	private static final float ENERGY_PER_POINT = 750F;
	private static final float NO_ENERGY_EFFICIENCY = 0.5F;
	private static final float ENERGY_STABILITY_USAGE_RATIO = 2.5F;
	public static final float ALL_EXERCISES_EFFICIENCY_ADD_MULTIPLIER = 0.05F;
	public static final float MAX_HAMON_STRENGTH_MULTIPLIER;
	public static final int[] TECHNIQUE_SKILL_REQUIREMENTS = {20, 30, 40};
	public static final boolean MIX_HAMON_TECHNIQUES = false;
	private static final int BREATH_STABILITY_RECOVERY_INTERVAL = 20;
	private static final int MEDITATION_INC_START = 40;
	public static final int MAX_EXERCISES_NEEDED = 4;
	public static final int CAN_SKIP_DAYS = 2;
	public static final AttributeModifier RUNNING_COMPLETED = new AttributeModifier(
			JojoMod.resLoc("hamon_exercise.running_completed"), 0.1D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	public static final AttributeModifier MINING_COMPLETED = new AttributeModifier(
			JojoMod.resLoc("hamon_exercise.mining_completed"), 0.05D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	public static final float SWIMMING_COMPLETED_MAX_ENERGY_MULTIPLIER = 1.1F;
	public static final float MEDITATION_COMPLETED_ENERGY_REGEN_TIME_REDUCTION = 20.0F;
	private static final AttributeModifier BREATHING_TRAINING_ATTACK_DAMAGE = new AttributeModifier(
			JojoMod.resLoc("hamon_training.attack_damage"), 0.03D, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier BREATHING_TRAINING_ATTACK_SPEED = new AttributeModifier(
			JojoMod.resLoc("hamon_training.attack_speed"), 0.015D, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier BREATHING_TRAINING_MOVEMENT_SPEED = new AttributeModifier(
			JojoMod.resLoc("hamon_training.movement_speed"), 0.0005D, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier BREATHING_TRAINING_SWIMMING_SPEED = new AttributeModifier(
			JojoMod.resLoc("hamon_training.swimming_speed"), 0.01D, AttributeModifier.Operation.ADD_VALUE);

	static {
		int diff = 0;
		POINTS_AT_LEVEL[0] = 0;
		POINTS_AT_LEVEL[1] = 2;
		for (int i = 2; i < POINTS_AT_LEVEL.length; i++) {
			diff += 3 + (i - 1) / 20;
			POINTS_AT_LEVEL[i] = POINTS_AT_LEVEL[i - 1] + POINTS_AT_LEVEL[1] + diff;
		}
		MAX_HAMON_POINTS = pointsAtLevel(MAX_STAT_LEVEL);
		MAX_HAMON_STRENGTH_MULTIPLIER = dmgFormula(MAX_STAT_LEVEL);
	}

	private float breathingLevel;
	private float breathStability;
	private int trainingTicks;
	private float hamonEnergy;
	private int noEnergyDecayTicks;
	private int hamonStrengthPoints;
	private int hamonStrengthLevel;
	private int hamonControlPoints;
	private int hamonControlLevel;
	private float hamonDamageFactor = 1.0F;
	private float pointsIncFrac;
	private OptionalInt regenImpliedDuration = OptionalInt.empty();
	private boolean hamonProtection;
	private String characterTechnique;
	private boolean isMeditating;
	private int meditationTicks;
	private int meditationPoseTicks;
	private int breathStabilityIncTicks;
	private int ticksMaskWithNoHamonBreath;
	private int ticksNoBreathStabilityInc;
	private float prevBreathStability;
	private int prevAir = 300;
	private boolean isBeingSuffocated;
	private final int[] exerciseTicks = new int[Exercise.values().length];
	private float trainingBonus;
	private int canSkipTrainingDays;
	private long lastTrainingDay = Long.MIN_VALUE;
	private boolean incExerciseLastTick;
	private boolean incExerciseThisTick;
	private boolean exerciseCompleted;
	private Vec3 prevExercisePos;
	private int blocksMiningDelay;
	private final EnumSet<HamonStat> pendingStatSync = EnumSet.noneOf(HamonStat.class);
	private final EnumSet<HamonStat> pendingStatIncrease = EnumSet.noneOf(HamonStat.class);
	private final Map<HamonStat, Set<String>> pendingNewlyLearnableSkills = new EnumMap<>(HamonStat.class);
	private boolean pendingBreathingSync;
	private boolean pendingBreathingIncrease;
	private boolean pendingHamonMasteryCheck;
	private int lastAppliedBreathingBuffLevel = Integer.MIN_VALUE;
	private int lastAppliedExerciseMask = Integer.MIN_VALUE;
	private final Set<UUID> newLearners = new HashSet<>();
	private Set<String> teacherSkills;
	private final Map<String, Integer> abilityCooldowns = new HashMap<>();
	private final Map<String, Integer> abilityCooldownTotals = new HashMap<>();
	private boolean waterWalkingPrevTick;
	private boolean waterWalkingThisTick;
	private boolean trWaterWalking;
	private boolean clWaterWalkingTickSpark = true;
	private boolean clWaterWalkingLargeSpark;
	private boolean doubleShiftPress;
	private boolean shiftSynced;
	private boolean wallClimbing;
	private boolean wallClimbHamon;
	private boolean wallClimbMoving;
	private float wallClimbSpeed;
	private boolean wallClimbYRotSet;
	private float wallClimbYRot;
	private String wallClimbAnimationName = "wall_climb_up";
	private float wallClimbAnimationTicks = 1.0F;
	private float wallClimbAnimationSpeed;
	private boolean wallClimbAnimationStopping;
	private float wallClimbAnimationStopTick;
	private boolean josephRunAwayActive;
	private long cheatDeathRefreshDay = Long.MIN_VALUE;
	private boolean cheatDeathSkillPrev;
	private boolean satiporojaScarfSkillPrev;
	private boolean satiporojaScarfGranted;
	private HamonAuraColor auraColor = HamonAuraColor.ORANGE;
	private String lastAuraAbility;

	public HamonData() {
		super(HamonPowerType.HAMON.get());
		breathStability = getMaxBreathStability();
	}

	public float getBreathingLevel() {
		return breathingLevel;
	}

	public void setBreathingLevel(float value) {
		setBreathingLevel(value, true);
	}

	public void applyBreathingLevelFromServer(float value) {
		setBreathingLevel(value, false);
	}

	private void setBreathingLevel(float value, boolean queueServerFeedback) {
		float oldLevel = breathingLevel;
		breathingLevel = Mth.clamp(value, 0.0F, MAX_BREATHING_LEVEL);
		if ((int) oldLevel != (int) breathingLevel) {
			recalcHamonDamage();
		}
		if (queueServerFeedback && oldLevel != breathingLevel) {
			pendingBreathingSync = true;
			if ((int) breathingLevel > (int) oldLevel) {
				pendingBreathingIncrease = true;
			}
		}
	}

	public float getBreathStability() {
		return breathStability;
	}

	public void setBreathStability(float value) {
		setBreathStability(value, 0);
	}

	private void setBreathStability(float value, int noIncTicks) {
		breathStability = Mth.clamp(value, 0.0F, getMaxBreathStability());
		prevBreathStability = breathStability;
		ticksNoBreathStabilityInc = Math.max(ticksNoBreathStabilityInc, noIncTicks);
		if (hamonEnergy > getMaxEnergy()) {
			setEnergy(hamonEnergy);
		}
	}

	private void reduceBreathStability(float value) {
		setBreathStability(value, 80);
	}

	private void tickAirSupply(LivingEntity user) {
		if (!isBeingSuffocated) {
			int air = user.getAirSupply();
			if (air < user.getMaxAirSupply() - 1 && air > 0) {
				int airRegainChancePerc = (int) (getBreathingLevel() * getBreathStability() / getMaxBreathStability()) - 1;
				if (user.tickCount % 100 < airRegainChancePerc) {
					user.setAirSupply(air + 1);
				}
			}
		}
		isBeingSuffocated = false;

		if (user.getAirSupply() <= -19) {
			reduceBreathStability(0.0F);
		}
	}

	public void suffocateTick(float suffocationSpeed) {
		reduceBreathStability(Math.max(getBreathStability() - getMaxBreathStability() * suffocationSpeed, 1.0F));
		isBeingSuffocated = true;
	}

	public int getTrainingTicks() {
		return trainingTicks;
	}

	public void setTrainingTicks(int value) {
		trainingTicks = Math.max(value, 0);
	}

	@Override
	public void tick(Power<?> userPower) {
		super.tick(userPower);
		LivingEntity user = userPower.getUser();
		tickHamonEnergy(user);
		tickBreathStability(user);
		tickDoubleShift(user);
		tickAbilityCooldowns();
		if (user.level().isClientSide()) {
			tickHamonProtection(user);
			tickHamonAura(user);
			tickClientPoseAndWallClimbEffects(user);
			if (user instanceof Player player && player == ClientProxy.getClientPlayer()) {
				tickExercises(player);
			}
			postTickWaterWalking(user);
			waterWalkingThisTick = false;
			return;
		}
		tickAirSupply(user);
		setTrainingTicks(trainingTicks + 1);
		giveBreathingTrainingBuffs(user);
		updateExerciseAttributes(user);
		if (user instanceof Player player) {
			tickBreathingTrainingDay(player);
			tickExercises(player);
		}
		tickNewPlayerLearners(user);
		tickCharacterTechniqueSideEffects(user);
		tickJosephRunAway(user);
		tickWallClimbing(userPower, user);
		flushStatFeedback(user);
		postTickWaterWalking(user);
		waterWalkingThisTick = false;
	}

	private void tickHamonAura(LivingEntity user) {
		EntityActionInstance currentAction = LivingComponentAction.getCurEntityAction(user);
		String actionName = getActionName(currentAction);
		auraColor = getThisTickAuraColor(user, actionName);

		float energy = getEnergy();
		if ("sunlight_yellow_overdrive".equals(actionName)) {
			if (!(user instanceof Player player) || !player.getAbilities().instabuild) {
				if (currentAction instanceof SYOverdrive syo) {
					energy += syo.getSpentEnergyForAura(this);
				}
			}
			energy *= 2.0F;
		}
		float maxStability = Math.max(getMaxBreathStability(), 1.0F);
		float particlesPerTick = energy / maxStability * getHamonDamageMultiplier();
		ParticleOptions particleType = auraColor.particleType();
		int particles = MathUtil.fractionRandomInc(particlesPerTick);
		for (int i = 0; i < particles; i++) {
			CustomParticlesHelper.createHamonAuraParticle(particleType, user,
					user.getX() + (user.getRandom().nextDouble() - 0.5D) * (user.getBbWidth() + 0.5F),
					user.getY() + user.getRandom().nextDouble() * (user.getBbHeight() * 0.5F),
					user.getZ() + (user.getRandom().nextDouble() - 0.5D) * (user.getBbWidth() + 0.5F));
		}
		if (user == ClientProxy.getCameraEntity()) {
			CustomParticlesHelper.summonHamonAuraParticlesFirstPerson(particleType, user, particlesPerTick / 5.0F);
		}
	}

	private HamonAuraColor getThisTickAuraColor(LivingEntity user, String actionName) {
		HamonAuraColor actionColor = HamonAuraColor.fromAction(actionName);
		if (actionColor != null) {
			lastAuraAbility = actionName;
			return actionColor;
		}
		if (getEnergy() <= 0.0F) {
			lastAuraAbility = null;
		}
		else {
			HamonAuraColor lastColor = HamonAuraColor.fromAction(lastAuraAbility);
			if (lastColor != null) {
				return lastColor;
			}
		}
		if (isSkillLearned(ModHamonSkills.METAL_SILVER_OVERDRIVE.get())
				&& HamonAbilityHelpers.isItemWeapon(user.getMainHandItem())) {
			return HamonAuraColor.SILVER;
		}
		if (isSkillLearned(ModHamonSkills.TURQUOISE_BLUE_OVERDRIVE.get())
				&& user.isEyeInFluid(FluidTags.WATER)) {
			return HamonAuraColor.BLUE;
		}
		return HamonAuraColor.ORANGE;
	}

	private static String getActionName(EntityActionInstance action) {
		return action != null && action.ability != null && action.ability.getAbilityId() != null
				? action.ability.getAbilityId().nameInMoveset()
				: null;
	}

	private enum HamonAuraColor {
		ORANGE(),
		BLUE("turquoise_blue_overdrive"),
		YELLOW("sunlight_yellow_overdrive", "sunlight_yellow_overdrive_barrage"),
		RED("scarlet_overdrive"),
		SILVER("metal_silver_overdrive", "metal_silver_overdrive_weapon");

		private final Set<String> abilities;

		HamonAuraColor(String... abilities) {
			this.abilities = Set.of(abilities);
		}

		private boolean matches(String ability) {
			return ability != null && abilities.contains(ability);
		}

		private static HamonAuraColor fromAction(String ability) {
			for (HamonAuraColor color : values()) {
				if (color.matches(ability)) {
					return color;
				}
			}
			return null;
		}

		private ParticleOptions particleType() {
			return switch (this) {
			case BLUE -> ModParticles.HAMON_AURA_BLUE.get();
			case YELLOW -> ModParticles.HAMON_AURA_YELLOW.get();
			case RED -> ModParticles.HAMON_AURA_RED.get();
			case SILVER -> ModParticles.HAMON_AURA_SILVER.get();
			default -> ModParticles.HAMON_AURA.get();
			};
		}
	}

	private void tickExercises(Player user) {
		boolean clientSide = user.level().isClientSide();
		Vec3 pos = user.position();
		boolean positionChanged = prevExercisePos == null || prevExercisePos.x != pos.x || prevExercisePos.y != pos.y || prevExercisePos.z != pos.z;
		prevExercisePos = pos;
		incExerciseThisTick = false;
		exerciseCompleted = false;

		if (isMiningExercise(user)) {
			incExerciseTicks(Exercise.MINING, 1.0F, clientSide);
		}
		if (positionChanged && user.isSwimming() && playerHasMovementInput(user)) {
			incExerciseTicks(Exercise.SWIMMING, 1.0F, clientSide);
		}
		else if (positionChanged && user.isSprinting() && user.onGround() && !user.isSwimming()) {
			incExerciseTicks(Exercise.RUNNING, 1.0F, clientSide);
		}
		if (isMeditating()) {
			if (++meditationTicks >= MEDITATION_INC_START) {
				incExerciseTicks(Exercise.MEDITATION, 1.0F, clientSide);
				breathStabilityIncTicks++;
			}
			if (!clientSide) {
				user.getFoodData().addExhaustion(-0.0025F);
				if (user.tickCount % 200 == 0
						&& user.getHealth() < user.getMaxHealth()
						&& user.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
					user.heal(1.0F);
				}
			}
		}
		boolean syncExercises = (incExerciseLastTick && !incExerciseThisTick) || exerciseCompleted;
		boolean showCompletionFeedback = !clientSide && breathingLevel < MAX_BREATHING_LEVEL
				&& exerciseCompleted && getCompleteExercisesCount() <= MAX_EXERCISES_NEEDED;
		if (showCompletionFeedback) {
			updateExerciseAttributes(user);
			if (user instanceof ServerPlayer serverPlayer) {
				sendExerciseCompletionFeedback(serverPlayer);
			}
		}
		else if (!clientSide && syncExercises && user instanceof ServerPlayer serverPlayer) {
			PacketDistributor.sendToPlayer(serverPlayer, HamonExercisesPacket.exercisesOnly(this));
		}
		incExerciseLastTick = incExerciseThisTick;
	}

	public float getEnergy() {
		return hamonEnergy;
	}

	public float getMaxEnergy() {
		return getBreathStability();
	}

	public boolean hasEnergy(float amount) {
		return hasEnergy(amount, null);
	}

	public boolean hasEnergy(float amount, LivingEntity user) {
		return amount == 0.0F || getHamonEnergyUsageEfficiency(amount, false, user) > 0.0F;
	}

	public void addEnergy(float amount) {
		setEnergy(hamonEnergy + amount);
	}

	private static boolean canBreatheForHamon(LivingEntity user) {
		return user.canBreatheUnderwater() || !user.isEyeInFluid(FluidTags.WATER);
	}

	public float tickHamonBreath(LivingEntity user) {
		if (!canBreatheForHamon(user)) {
			return 0.0F;
		}
		ticksMaskWithNoHamonBreath = 0;
		updateNoEnergyDecayTicks(user);
		float energyAdded = getMaxBreathStability() / getFullEnergyTicks();
		addEnergy(energyAdded);
		return energyAdded;
	}

	private boolean isHamonBreathingAction(LivingEntity user) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(user);
		return action != null && action.ability.getAbilityId() != null
				&& "hamon_breath".equals(action.ability.getAbilityId().nameInMoveset());
	}

	private void tickHamonEnergy(LivingEntity user) {
		if (JojoDefinitions.isDyingBody(user)) {
			setEnergy(0.0F);
			setBreathStability(0.0F);
			return;
		}
		if (isHamonBreathingAction(user) && canBreatheForHamon(user)) {
			tickHamonBreath(user);
			return;
		}
		if (isUserWearingBreathMask(user) && !isMeditating()) {
			ticksMaskWithNoHamonBreath++;
		}
		else {
			ticksMaskWithNoHamonBreath = 0;
		}
		if (hamonEnergy <= 0.0F) {
			setHamonProtection(false);
		}
		if (noEnergyDecayTicks > 0) {
			noEnergyDecayTicks--;
			return;
		}
		if (JojoModConfig.getCommonConfigInstance(user.level().isClientSide()).hamonEnergyTicksDown.get()) {
			setEnergy(hamonEnergy - ENERGY_TICK_DOWN_AMOUNT);
		}
	}

	private void tickBreathStability(LivingEntity user) {
		if (JojoDefinitions.isDyingBody(user)) {
			breathStability = 0.0F;
			prevBreathStability = 0.0F;
			prevAir = user.getAirSupply();
			return;
		}

		boolean canBreath = canBreatheForHamon(user);
		float maxStability = getMaxBreathStability();
		float inc;
		boolean maskNoBreath = false;

		if (isUserWearingBreathMask(user)) {
			float ticksCanBreatheWithMask = 400.0F + getBreathingLevel() * 16.0F;
			float breathMaskHandicap = 0.0F;
			if (getBreathingLevel() < MAX_BREATHING_LEVEL) {
				breathMaskHandicap = Mth.clamp((ticksCanBreatheWithMask - ticksMaskWithNoHamonBreath) / (ticksCanBreatheWithMask / 2.0F), -1.0F, 1.0F);
			}
			if (breathMaskHandicap >= 0.0F) {
				inc = maxStability / getFullBreathStabilityTicks() * breathMaskHandicap;
			}
			else {
				inc = maxStability / 1200.0F * breathMaskHandicap;
				float stabilityLowerCap = 0.2F;
				if ((breathStability + inc) / maxStability < stabilityLowerCap) {
					inc = Mth.clamp(inc, stabilityLowerCap * maxStability - breathStability, 0.0F);
				}
				maskNoBreath = true;
			}
		}
		else {
			inc = maxStability / getFullBreathStabilityTicks();
		}

		if (inc >= 0.0F && isMeditating() && breathStabilityIncTicks > 0) {
			inc *= Mth.sqrt((float) Math.min(breathStabilityIncTicks, 100));
		}
		if (!canBreath) {
			inc = Math.min(inc, 0.0F);
		}
		if (inc > 0.0F && ticksNoBreathStabilityInc > 0) {
			ticksNoBreathStabilityInc--;
			inc = 0.0F;
		}

		float beforeStability = breathStability;
		setBreathStability(breathStability + inc);
		int air = user.getAirSupply();
		if (!user.level().isClientSide() && (breathStability == 0.0F && beforeStability > 0.0F || air == 0 && prevAir > 0)) {
			outOfBreath(user, maskNoBreath && air > 0);
		}
		prevBreathStability = breathStability;
		prevAir = air;
	}

	private void updateNoEnergyDecayTicks(LivingEntity user) {
		float scaledExtraTicks = 150.0F * getBreathingLevel() / MAX_BREATHING_LEVEL;
		int wholeTicks = Mth.floor(scaledExtraTicks);
		float partialTick = scaledExtraTicks - wholeTicks;
		noEnergyDecayTicks = 50 + wholeTicks + (user.getRandom().nextFloat() < partialTick ? 1 : 0);
	}

	public boolean consumeEnergy(float amount) {
		return getHamonEnergyUsageEfficiency(amount, true) > 0.0F;
	}

	public boolean consumeEnergy(float amount, LivingEntity user) {
		return getHamonEnergyUsageEfficiency(amount, true, user) > 0.0F;
	}

	public void setEnergy(float amount) {
		hamonEnergy = Mth.clamp(amount, 0.0F, Math.max(getMaxEnergy(), 0.0F));
	}

	public float getMaxBreathStability() {
		float max = getMaxBreathStabilityAt(hamonControlLevel);
		if (isExerciseComplete(Exercise.SWIMMING)) {
			max *= SWIMMING_COMPLETED_MAX_ENERGY_MULTIPLIER;
		}
		return max;
	}

	public static float getMaxBreathStabilityAt(int controlLevel) {
		return BASE_MAX_ENERGY * (1.0F + Math.max(controlLevel, 0) * 0.1F);
	}

	public float getFullEnergyTicks() {
		float ticks = 80.0F - 40.0F * getBreathingLevel() / MAX_BREATHING_LEVEL;
		if (isExerciseComplete(Exercise.MEDITATION)) {
			ticks -= MEDITATION_COMPLETED_ENERGY_REGEN_TIME_REDUCTION;
		}
		return Math.max(ticks, 1.0F);
	}

	private float getFullBreathStabilityTicks() {
		return Math.max(1200.0F - 600.0F * getBreathingLevel() / MAX_BREATHING_LEVEL, 1.0F);
	}

	public float getActionEfficiency(float energyCost, boolean handSwingTimer) {
		return getActionEfficiency(energyCost, handSwingTimer, null);
	}

	public float getActionEfficiency(float energyCost, boolean handSwingTimer, HamonSkill hamonSkill) {
		return getActionEfficiency(energyCost, handSwingTimer, hamonSkill, null);
	}

	public float getActionEfficiency(float energyCost, boolean handSwingTimer, HamonSkill hamonSkill, LivingEntity user) {
		float efficiency = getHamonEnergyUsageEfficiency(energyCost, false, user);
		if (efficiency > 0.0F) {
			efficiency *= getBloodstreamEfficiency(user);
			float multiplier = 1.0F;
			if (getCompleteExercisesCount() >= MAX_EXERCISES_NEEDED) {
				multiplier += ALL_EXERCISES_EFFICIENCY_ADD_MULTIPLIER;
			}
			if (hamonSkill != null) {
				HamonTechnique technique = getCharacterTechnique();
				if (technique != null) {
					multiplier += technique.getAddSkillEfficiency(hamonSkill);
				}
			}
			efficiency *= multiplier;
			if (handSwingTimer && user instanceof Player player) {
				float swingStrengthScale = player.getAttackStrengthScale(1.0F);
				efficiency *= 0.2F + swingStrengthScale * swingStrengthScale * 0.8F;
			}
			float stab = getBreathStability();
			float maxStab = getMaxBreathStability();
			if (stab < maxStab) {
				efficiency *= 0.5F + 0.5F * stab / maxStab;
			}
		}
		return efficiency;
	}

	public float getHamonEnergyUsageEfficiency(float energyNeeded, boolean doConsume) {
		return getHamonEnergyUsageEfficiency(energyNeeded, doConsume, null);
	}

	public float getHamonEnergyUsageEfficiency(float energyNeeded, boolean doConsume, LivingEntity user) {
		energyNeeded = Math.max(energyNeeded, 0.0F);
		energyNeeded = reduceEnergyConsumed(energyNeeded, user);
		doConsume = shouldConsumeHamonEnergy(doConsume, user);
		if (hamonEnergy >= energyNeeded || energyNeeded == 0.0F) {
			if (doConsume) {
				setEnergy(hamonEnergy - energyNeeded);
			}
			return 1.0F;
		}
		else if (hamonEnergy > 0.0F) {
			float energyRatio = hamonEnergy / energyNeeded;
			if (doConsume) {
				setEnergy(0.0F);
			}
			return NO_ENERGY_EFFICIENCY + (1.0F - NO_ENERGY_EFFICIENCY) * energyRatio;
		}
		else {
			float energyFromStability = getBreathStability();
			if (!isUserWearingBreathMask(user)) {
				energyFromStability *= ENERGY_STABILITY_USAGE_RATIO;
			}
			float energyRatio = Math.min(energyFromStability / energyNeeded, 1.0F);
			if (doConsume) {
				sendHamonUiEffect(user, HamonUiEffectPacket.Type.NO_ENERGY);
				if (energyFromStability < energyNeeded) {
					reduceBreathStability(0.0F);
					outOfBreath(user, false);
				}
				else {
					reduceBreathStability((energyFromStability - energyNeeded) / ENERGY_STABILITY_USAGE_RATIO);
				}
			}
			return NO_ENERGY_EFFICIENCY * energyRatio;
		}
	}

	private boolean shouldConsumeHamonEnergy(boolean doConsume, LivingEntity user) {
		if (!doConsume) {
			return false;
		}
		if (user == null) {
			return true;
		}
		return !user.level().isClientSide()
				&& (!(user instanceof Player player) || !player.getAbilities().instabuild);
	}

	private void sendHamonUiEffect(LivingEntity user, HamonUiEffectPacket.Type type) {
		if (user instanceof ServerPlayer serverPlayer) {
			PacketDistributor.sendToPlayer(serverPlayer, new HamonUiEffectPacket(type));
		}
	}

	private void outOfBreath(LivingEntity user, boolean mask) {
		if (user == null) {
			return;
		}
		user.setAirSupply(0);
		sendHamonUiEffect(user, mask ? HamonUiEffectPacket.Type.OUT_OF_BREATH_MASK : HamonUiEffectPacket.Type.OUT_OF_BREATH);
	}

	private float reduceEnergyConsumed(float amount, LivingEntity user) {
		if (user != null && user.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SATIPOROJA_SCARF.get())) {
			return amount * 0.6F;
		}
		return amount;
	}

	public float getBloodstreamEfficiency() {
		return 1.0F;
	}

	public float getBloodstreamEfficiency(LivingEntity user) {
		if (user == null) {
			return getBloodstreamEfficiency();
		}
		float efficiency = 1.0F;

		MobEffectInstance bleedingEffect = user.getEffect(ModStatusEffects.BLEEDING);
		if (bleedingEffect != null) {
			float bleeding = Math.min((bleedingEffect.getAmplifier() + 1) * 0.2F, 0.8F);
			efficiency *= 1.0F - bleeding;
		}

		MobEffectInstance freezeEffect = user.getEffect(ModStatusEffects.FREEZE);
		if (freezeEffect != null) {
			float freeze = Math.min((freezeEffect.getAmplifier() + 1) * 0.25F, 1.0F);
			efficiency *= 1.0F - freeze;
		}

		return efficiency;
	}

	public int getAbilityCooldown(String abilityName) {
		return abilityCooldowns.getOrDefault(abilityName, 0);
	}

	public boolean isAbilityOnCooldown(String abilityName) {
		return getAbilityCooldown(abilityName) > 0;
	}

	public float getAbilityCooldownRatio(String abilityName, float partialTick) {
		int cooldown = getAbilityCooldown(abilityName);
		int totalCooldown = abilityCooldownTotals.getOrDefault(abilityName, cooldown);
		if (cooldown <= 0 || totalCooldown <= 0) {
			return 0.0F;
		}
		return Mth.clamp((cooldown - partialTick) / totalCooldown, 0.0F, 1.0F);
	}

	public void setAbilityCooldown(String abilityName, int cooldown) {
		setAbilityCooldown(abilityName, cooldown, cooldown);
	}

	public void setAbilityCooldown(String abilityName, int cooldown, int totalCooldown) {
		int newCooldown = Math.max(cooldown, 0);
		int newTotalCooldown = Math.max(totalCooldown, newCooldown);
		if (newCooldown > 0) {
			abilityCooldowns.put(abilityName, newCooldown);
			abilityCooldownTotals.put(abilityName, newTotalCooldown);
		}
		else {
			abilityCooldowns.remove(abilityName);
			abilityCooldownTotals.remove(abilityName);
		}
	}

	private void tickAbilityCooldowns() {
		for (Iterator<Map.Entry<String, Integer>> iterator = abilityCooldowns.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<String, Integer> cooldown = iterator.next();
			int ticksLeft = cooldown.getValue() - 1;
			if (ticksLeft > 0) {
				cooldown.setValue(ticksLeft);
			}
			else {
				abilityCooldownTotals.remove(cooldown.getKey());
				iterator.remove();
			}
		}
	}

	@Override
	public void tickWhileTemporarilySuspended(
			com.github.standobyte.jojo.powersystem.playerpower.PlayerPower power) {
		tickAbilityCooldowns();
	}

	public float getHamonDamageMultiplier() {
		return hamonDamageFactor;
	}

	public static int pointsAtLevel(int level) {
		level = Mth.clamp(level, 0, MAX_STAT_LEVEL);
		return POINTS_AT_LEVEL[level];
	}

	public static int levelFromPoints(int points) {
		points = Mth.clamp(points, 0, MAX_HAMON_POINTS);
		int low = 0;
		int high = MAX_STAT_LEVEL;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			if (POINTS_AT_LEVEL[mid] <= points) {
				low = mid + 1;
			}
			else {
				high = mid - 1;
			}
		}
		return Mth.clamp(high, 0, MAX_STAT_LEVEL);
	}

	public void setHamonStatPoints(HamonStat stat, int points, boolean ignoreTraining, boolean allowLesserValue) {
		setHamonStatPoints(stat, points, ignoreTraining, allowLesserValue, true);
	}

	public void applyStatPointsFromServer(HamonStat stat, int points) {
		setHamonStatPoints(stat, points, true, true, false);
	}

	private void setHamonStatPoints(HamonStat stat, int points, boolean ignoreTraining,
			boolean allowLesserValue, boolean queueServerFeedback) {
		int oldPoints = getStatPoints(stat);
		int oldLevel = getStatLevel(stat);
		if (!allowLesserValue && points <= oldPoints) {
			return;
		}
		int newPoints = Mth.clamp(points, 0, MAX_HAMON_POINTS);
		int newLevel = levelFromPoints(newPoints);
		Set<String> learnableBefore = queueServerFeedback && newLevel > oldLevel
				? getLearnableSkillsTeacherIrrelevant() : Set.of();
		switch (stat) {
		case STRENGTH:
			hamonStrengthPoints = newPoints;
			hamonStrengthLevel = newLevel;
			break;
		case CONTROL:
			hamonControlPoints = newPoints;
			hamonControlLevel = newLevel;
			break;
		}
		if (oldLevel != newLevel) {
			switch (stat) {
			case STRENGTH:
				recalcHamonDamage();
				break;
			case CONTROL:
				float oldMax = getMaxBreathStabilityAt(oldLevel);
				float ratio = oldMax > 0.0F ? getMaxBreathStabilityAt(hamonControlLevel) / oldMax : 1.0F;
				setBreathStability(getBreathStability() * ratio);
				setEnergy(getEnergy() * ratio);
				break;
			}
		}
		if (queueServerFeedback && oldPoints != newPoints) {
			if (newLevel != oldLevel) {
				pendingHamonMasteryCheck = true;
			}
			pendingStatSync.add(stat);
			if (newLevel > oldLevel) {
				pendingStatIncrease.add(stat);
				Set<String> newlyLearnable = getLearnableSkillsTeacherIrrelevant();
				newlyLearnable.removeAll(learnableBefore);
				if (!newlyLearnable.isEmpty()) {
					pendingNewlyLearnableSkills
							.computeIfAbsent(stat, __ -> new LinkedHashSet<>())
							.addAll(newlyLearnable);
				}
			}
		}
	}

	public void hamonPointsFromAction(HamonStat stat, float energyCost) {
		if (energyCost <= 0.0F) {
			return;
		}
		if (isSkillLearned(ModHamonSkills.NATURAL_TALENT.get())) {
			energyCost *= 2.0F;
		}
		energyCost *= JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get().floatValue();
		int points = (int) (energyCost / ENERGY_PER_POINT);
		pointsIncFrac += (energyCost % ENERGY_PER_POINT) / ENERGY_PER_POINT;
		if (pointsIncFrac >= 1.0F) {
			points++;
			pointsIncFrac--;
		}
		setHamonStatPoints(stat, getStatPoints(stat) + points, false, false);
	}

	public OptionalInt getRegenImpliedDuration() {
		return regenImpliedDuration;
	}

	public void setRegenImpliedDuration(int duration) {
		regenImpliedDuration = OptionalInt.of(Math.max(duration, 0));
	}

	public void clearRegenImpliedDuration() {
		regenImpliedDuration = OptionalInt.empty();
	}

	public int getHamonStrengthPoints() {
		return hamonStrengthPoints;
	}

	public int getHamonStrengthLevel() {
		return hamonStrengthLevel;
	}

	public int getHamonControlPoints() {
		return hamonControlPoints;
	}

	public int getHamonControlLevel() {
		return hamonControlLevel;
	}

	public int getStatPoints(HamonStat stat) {
		return switch (stat) {
		case STRENGTH -> hamonStrengthPoints;
		case CONTROL -> hamonControlPoints;
		};
	}

	public int getStatLevel(HamonStat stat) {
		return switch (stat) {
		case STRENGTH -> hamonStrengthLevel;
		case CONTROL -> hamonControlLevel;
		};
	}

	public int getSpentSkillPoints(HamonStat stat) {
		return (int) unlockedSkills.stream()
				.map(ModHamonSkills::definitionFor)
				.filter(definition -> definition != null && !definition.startingSkill())
				.filter(definition -> statForSkillBranch(definition.branch()) == stat)
				.count();
	}

	public int getSkillPoints(HamonStat stat) {
		return Mth.clamp(getStatLevel(stat), 0, MAX_STAT_LEVEL) / 5 - getSpentSkillPoints(stat);
	}

	public int nextSkillPointLvl(HamonStat stat) {
		return Mth.clamp(getStatLevel(stat), 0, MAX_STAT_LEVEL - 1) / 5 * 5 + 5;
	}

	public static HamonStat statForSkillBranch(HamonSkillBranch branch) {
		return switch (branch) {
		case OVERDRIVE, INFUSION, FLEXIBILITY -> HamonStat.STRENGTH;
		case HEALING, ATTRACTANT_REPELLENT, BODY_MANIPULATION -> HamonStat.CONTROL;
		case CHARACTER_TECHNIQUE -> null;
		};
	}

	private Set<String> getLearnableSkillsTeacherIrrelevant() {
		Set<String> learnable = new LinkedHashSet<>();
		for (String skillName : getAllSkills().keySet()) {
			if (canLearnSkillTeacherIrrelevant(skillName)) {
				learnable.add(skillName);
			}
		}
		return learnable;
	}

	private boolean canLearnSkillTeacherIrrelevant(String skillName) {
		if (isSkillUnlocked(skillName)) {
			return false;
		}
		HamonSkillDefinition definition = ModHamonSkills.definitionFor(skillName);
		if (definition == null || definition.prerequisiteSkills().stream().anyMatch(skill -> !isSkillUnlocked(skill))) {
			return false;
		}
		if (definition.branch() != HamonSkillBranch.CHARACTER_TECHNIQUE) {
			if (!definition.startingSkill()) {
				HamonStat stat = statForSkillBranch(definition.branch());
				if (stat != null && getSkillPoints(stat) <= 0) {
					return false;
				}
			}
			return true;
		}

		HamonTechnique technique = getCharacterTechnique();
		if (technique == null || !MIX_HAMON_TECHNIQUES && !technique.isTechniqueSkill(skillName)) {
			return false;
		}
		int learnedTechniqueSkills = getLearnedTechniqueSkillCount();
		return learnedTechniqueSkills < techniqueSlotsCount() && hasTechniqueLevel(learnedTechniqueSkills);
	}

	private boolean hasHamonMastery() {
		return getHamonStrengthLevel() >= MAX_STAT_LEVEL && getHamonControlLevel() >= MAX_STAT_LEVEL;
	}

	public void checkHamonMastery(ServerPlayer player) {
		pendingHamonMasteryCheck = false;
		if (!player.level().isClientSide() && hasHamonMastery()) {
			ModCriteriaTriggers.triggerHamonStats(player, this);
		}
	}

	private void flushStatFeedback(LivingEntity user) {
		for (HamonStat stat : HamonStat.values()) {
			if (pendingStatSync.remove(stat)) {
				Set<String> newSkills = pendingNewlyLearnableSkills.remove(stat);
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, HamonStatFeedbackPacket.stat(
						user.getId(), stat, getStatPoints(stat), pendingStatIncrease.remove(stat),
						newSkills != null ? List.copyOf(newSkills) : List.of()));
			}
		}
		if (pendingBreathingSync) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, HamonStatFeedbackPacket.breathing(
					user.getId(), getBreathingLevel(), pendingBreathingIncrease));
			pendingBreathingSync = false;
			pendingBreathingIncrease = false;
		}
		if (pendingHamonMasteryCheck) {
			if (user instanceof ServerPlayer player) {
				checkHamonMastery(player);
			}
			else {
				pendingHamonMasteryCheck = false;
			}
		}
	}

	public boolean isSkillLearned(HamonSkill skill) {
		return skill != null && isSkillUnlocked(skill.getRegistryKey().getPath());
	}

	public boolean learnSkill(HamonSkill skill) {
		return skill != null && _setSkillUnlocked(skill.getRegistryKey().getPath(), true, true);
	}

	public boolean removeSkill(HamonSkill skill) {
		return skill != null && _setSkillUnlocked(skill.getRegistryKey().getPath(), false, true);
	}

	private void tickCharacterTechniqueSideEffects(LivingEntity user) {
		boolean cheatDeathLearned = isSkillLearned(ModHamonSkills.CHEAT_DEATH.get());
		if (cheatDeathLearned) {
			long day = hamonDay(user);
			if (!cheatDeathSkillPrev) {
				HamonUtil.updateCheatDeathEffect(user);
				cheatDeathRefreshDay = day;
			}
			else if (!user.hasEffect(ModStatusEffects.CHEAT_DEATH) && cheatDeathRefreshDay != day) {
				HamonUtil.updateCheatDeathEffect(user);
				cheatDeathRefreshDay = day;
			}
		}
		else if (cheatDeathSkillPrev) {
			user.removeEffect(ModStatusEffects.CHEAT_DEATH);
			cheatDeathRefreshDay = Long.MIN_VALUE;
		}
		cheatDeathSkillPrev = cheatDeathLearned;

		boolean satiporojaLearned = isSkillLearned(ModHamonSkills.SATIPOROJA_SCARF.get());
		if (satiporojaLearned && !satiporojaScarfSkillPrev && !satiporojaScarfGranted && user instanceof Player player) {
			ItemStack scarf = new ItemStack(ModItems.SATIPOROJA_SCARF.get());
			if (!player.addItem(scarf)) {
				player.spawnAtLocation(scarf);
			}
			satiporojaScarfGranted = true;
		}
		satiporojaScarfSkillPrev = satiporojaLearned;
	}

	private void tickJosephRunAway(LivingEntity user) {
		boolean runAway = characterIs(ModHamonSkills.CHARACTER_JOSEPH.get())
				&& user.isSprinting() && hasRunAwayThreatBehind(user);
		if (!runAway) {
			josephRunAwayActive = false;
			return;
		}

		MobEffectInstance speed = user.getEffect(MobEffects.MOVEMENT_SPEED);
		int speedAmplifier = speed != null && speed.getDuration() > 100 ? speed.getAmplifier() + 2 : 1;
		user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, speedAmplifier, false, false, true));
		if (!josephRunAwayActive) {
			JojoModUtil.sayVoiceLine(user, ModSoundEvents.JOSEPH_RUN_AWAY);
		}
		josephRunAwayActive = true;
	}

	private static boolean hasRunAwayThreatBehind(LivingEntity user) {
		Vec3 vecBehind = Vec3.directionFromRotation(0.0F, 180.0F + user.getYRot()).scale(8.0D);
		AABB aabb = new AABB(user.position().subtract(0.0D, 2.0D, 0.0D),
				user.position().add(vecBehind.x, 2.0D, vecBehind.z));
		return !user.level().getEntitiesOfClass(LivingEntity.class, aabb,
				entity -> entity != user && !(entity instanceof StandEntity)).isEmpty();
	}

	public void markCheatDeathConsumed(LivingEntity user) {
		cheatDeathRefreshDay = hamonDay(user);
		cheatDeathSkillPrev = true;
	}

	private static long hamonDay(LivingEntity user) {
		return user.level().getDayTime() / 24000L;
	}

	public boolean techniquesEnabled() {
		return techniqueSlotsCount() > 0;
	}

	public static int techniqueSlotsCount() {
		return TECHNIQUE_SKILL_REQUIREMENTS.length;
	}

	public static int techniqueSkillRequirement(int slot) {
		if (slot < 0 || slot >= TECHNIQUE_SKILL_REQUIREMENTS.length) {
			return Integer.MAX_VALUE;
		}
		return TECHNIQUE_SKILL_REQUIREMENTS[slot];
	}

	public boolean hasTechniqueLevel(int techniqueSkillSlot) {
		if (techniqueSkillSlot < 0 || techniqueSkillSlot >= techniqueSlotsCount()) {
			return false;
		}
		int requiredLevel = techniqueSkillRequirement(techniqueSkillSlot);
		return getHamonStrengthLevel() >= requiredLevel && getHamonControlLevel() >= requiredLevel;
	}

	public boolean pickHamonTechnique(LivingEntity user, HamonTechnique technique) {
		if (user.level().isClientSide() || technique == null || !technique.canPick(this)) {
			return false;
		}
		characterTechnique = technique.getRegistryKey().getPath();
		applyTechniquePerks(true);
		playHamonPickSound(user, technique);
		syncOnUpdate(user);
		return true;
	}

	public void resetCharacterTechnique(LivingEntity user) {
		if (characterTechnique != null) {
			HamonTechnique technique = getCharacterTechnique();
			if (technique != null) {
				technique.getPerksOnPick().forEach(perk -> _setSkillUnlocked(perk, false, true));
			}
			characterTechnique = null;
			if (user != null) {
				syncOnUpdate(user);
			}
		}
	}

	public HamonTechnique getCharacterTechnique() {
		return characterTechnique != null ? ModHamonSkills.techniqueByName(characterTechnique) : null;
	}

	public String getCharacterTechniqueName() {
		return characterTechnique != null ? characterTechnique : "";
	}

	public boolean characterIs(HamonTechnique technique) {
		return technique != null && technique.getRegistryKey().getPath().equals(characterTechnique);
	}

	public int getLearnedTechniqueSkillCount() {
		HamonTechnique technique = getCharacterTechnique();
		return (int) unlockedSkills.stream()
				.filter(ModHamonSkills::isTechniqueSkill)
				.filter(skillName -> technique == null || !technique.isTechniquePerk(skillName))
				.count();
	}

	private void applyTechniquePerks(boolean inGameplay) {
		HamonTechnique technique = getCharacterTechnique();
		if (technique != null) {
			technique.getPerksOnPick().forEach(perk -> _setSkillUnlocked(perk, true, inGameplay));
		}
	}

	private void playHamonPickSound(LivingEntity user, HamonTechnique technique) {
		Holder<SoundEvent> music = technique.getMusicOnPick();
		if (music != null) {
			user.level().playSound(null, user.getX(), user.getY(), user.getZ(), music.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	@Override
	public void resetUnlockedSkills(Power<?> userPower) {
		super.resetUnlockedSkills(userPower);
		resetCharacterTechnique(userPower.getUser());
	}

	public static boolean canResetTab(Player user, HamonSkillsTab type) {
		return user != null && user.getAbilities().instabuild;
	}

	public void resetHamonSkills(LivingEntity user, HamonSkillsTab type) {
		if (type == null || user instanceof Player player && !canResetTab(player, type)) {
			return;
		}

		for (String skillName : java.util.List.copyOf(unlockedSkills)) {
			HamonSkillDefinition definition = ModHamonSkills.definitionFor(skillName);
			if (definition != null && !definition.startingSkill() && shouldResetSkill(definition, type)) {
				_setSkillUnlocked(skillName, false, true);
			}
		}
		if (type == HamonSkillsTab.TECHNIQUE) {
			resetCharacterTechnique(user);
		}
		syncOnUpdate(user);
	}

	private static boolean shouldResetSkill(HamonSkillDefinition definition, HamonSkillsTab type) {
		return switch (type) {
		case STRENGTH -> statForSkillBranch(definition.branch()) == HamonStat.STRENGTH;
		case CONTROL -> statForSkillBranch(definition.branch()) == HamonStat.CONTROL;
		case TECHNIQUE -> definition.branch() == HamonSkillBranch.CHARACTER_TECHNIQUE;
		};
	}

	public void setIsMeditating(LivingEntity user, boolean isMeditating) {
		if (this.isMeditating != isMeditating) {
			this.isMeditating = isMeditating;
			this.meditationTicks = 0;
			this.meditationPoseTicks = 0;
			this.breathStabilityIncTicks = 0;
			if (!user.level().isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrHamonMeditationPacket(user.getId(), isMeditating));
			}
			if (isMeditating) {
				user.yBodyRot = user.getYRot();
			}
		}
	}

	public boolean isMeditating() {
		return isMeditating;
	}

	public int getMeditationTicks() {
		return meditationTicks;
	}

	public int getMeditationPoseTicks() {
		return meditationPoseTicks;
	}

	public int getExerciseTicks(Exercise exercise) {
		return Math.min(exerciseTicks[exercise.ordinal()], exercise.getMaxTicks(this));
	}

	public boolean isExerciseComplete(Exercise exercise) {
		return getExerciseTicks(exercise) >= exercise.getMaxTicks(this);
	}

	public int getCompleteExercisesCount() {
		int count = 0;
		for (Exercise exercise : Exercise.values()) {
			if (isExerciseComplete(exercise) && exercise.ordinal() < MAX_EXERCISES_NEEDED) {
				count++;
			}
		}
		return count;
	}

	public float getMaxIncompleteExercise() {
		float max = 0.0F;
		for (Exercise exercise : Exercise.values()) {
			if (exercise.ordinal() >= MAX_EXERCISES_NEEDED) {
				continue;
			}
			int maxTicks = exercise.getMaxTicks(this);
			if (maxTicks > 0 && getExerciseTicks(exercise) < maxTicks) {
				max = Math.max(max, (float) getExerciseTicks(exercise) / (float) maxTicks);
			}
		}
		return max;
	}

	public boolean has4ExercisesBonus() {
		return getCompleteExercisesCount() >= MAX_EXERCISES_NEEDED;
	}

	private void updateExerciseAttributes(LivingEntity user) {
		if (user == null || user.level().isClientSide()) {
			return;
		}
		int exerciseMask = exerciseCompletionMask();
		if (lastAppliedExerciseMask == exerciseMask) {
			return;
		}
		setAttributeModifier(user, Attributes.MOVEMENT_SPEED, RUNNING_COMPLETED, isExerciseComplete(Exercise.RUNNING));
		setAttributeModifier(user, Attributes.ATTACK_SPEED, MINING_COMPLETED, isExerciseComplete(Exercise.MINING));
		setBreathStability(getBreathStability());
		lastAppliedExerciseMask = exerciseMask;
	}

	private int exerciseCompletionMask() {
		int mask = 0;
		for (Exercise exercise : Exercise.values()) {
			if (exercise.ordinal() < MAX_EXERCISES_NEEDED && isExerciseComplete(exercise)) {
				mask |= 1 << exercise.ordinal();
			}
		}
		return mask;
	}

	private void giveBreathingTrainingBuffs(LivingEntity user) {
		if (user == null || user.level().isClientSide()) {
			return;
		}
		int level = (int) getBreathingLevel();
		if (lastAppliedBreathingBuffLevel == level) {
			return;
		}
		updateAttributeModifier(user, Attributes.ATTACK_DAMAGE, BREATHING_TRAINING_ATTACK_DAMAGE, level);
		updateAttributeModifier(user, Attributes.ATTACK_SPEED, BREATHING_TRAINING_ATTACK_SPEED, level);
		updateAttributeModifier(user, Attributes.MOVEMENT_SPEED, BREATHING_TRAINING_MOVEMENT_SPEED, level);
		updateAttributeModifier(user, NeoForgeMod.SWIM_SPEED, BREATHING_TRAINING_SWIMMING_SPEED, level);
		lastAppliedBreathingBuffLevel = level;
	}

	private static void updateAttributeModifier(LivingEntity user, Holder<Attribute> attribute, AttributeModifier modifier, int multiplier) {
		AttributeInstance instance = user.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(modifier.id());
		if (multiplier != 0) {
			instance.addTransientModifier(new AttributeModifier(
					modifier.id(), modifier.amount() * multiplier, modifier.operation()));
		}
	}

	private static void setAttributeModifier(LivingEntity user, Holder<Attribute> attribute, AttributeModifier modifier, boolean enabled) {
		AttributeInstance instance = user.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(modifier.id());
		if (enabled) {
			instance.addTransientModifier(modifier);
		}
	}

	private boolean isMiningExercise(Player user) {
		boolean isMining;
		if (user.level().isClientSide()) {
			isMining = ClientProxy.isDestroyingBlock();
		}
		else if (user instanceof ServerPlayer serverPlayer) {
			ServerPlayerGameMode gameMode = serverPlayer.gameMode;
			isMining = gameMode.isDestroyingBlock;
		}
		else {
			isMining = false;
		}
		if (isMining) {
			blocksMiningDelay = 6;
		}
		else {
			isMining = blocksMiningDelay-- > 0;
		}
		return isMining;
	}

	private static boolean playerHasMovementInput(Player user) {
		PlayerMovementInputData input = PlayerMovementInputData.get(user);
		return input == null || input.jumping || Math.abs(input.left) > 1.0E-4F || Math.abs(input.forward) > 1.0E-4F;
	}

	private void incExerciseTicks(Exercise exercise, float multiplier, boolean clientSide) {
		int ticks = exerciseTicks[exercise.ordinal()];
		int maxTicks = exercise.getMaxTicks(this);
		if (ticks < maxTicks) {
			int inc = multiplier > 1.0F ? Mth.ceil(multiplier) : 1;
			int newTicks = Math.min(ticks + inc, maxTicks);
			if (newTicks == maxTicks) {
				if (clientSide) {
					return;
				}
				exerciseCompleted = true;
			}
			setExerciseValue(exercise, newTicks, clientSide);
			incExerciseThisTick = true;
		}
	}

	public void setExerciseTicks(int[] ticks, boolean clientSide) {
		Exercise[] exercises = Exercise.values();
		for (int i = 0; i < exercises.length && i < ticks.length; i++) {
			setExerciseValue(exercises[i], ticks[i], clientSide);
		}
	}

	private void setExerciseValue(Exercise exercise, int value, boolean clientSide) {
		int index = exercise.ordinal();
		int newValue = Mth.clamp(value, 0, exercise.getMaxTicks(this));
		if (exerciseTicks[index] != newValue) {
			exerciseTicks[index] = newValue;
			if (clientSide) {
				HamonTrainingHudFeedback.onExerciseValueChanged(exercise);
			}
		}
	}

	public int[] exerciseTicksArray() {
		return Arrays.stream(Exercise.values()).mapToInt(this::getExerciseTicks).toArray();
	}

	public float getTrainingBonus(boolean perksAndConfigMult) {
		return getTrainingBonus(null, perksAndConfigMult);
	}

	private float getTrainingBonus(Player user, boolean perksAndConfigMult) {
		if (user != null && !isUserWearingBreathMask(user)) {
			return 0.0F;
		}
		return perksAndConfigMult ? multiplyPositiveBreathingTraining(trainingBonus) : trainingBonus;
	}

	private float multiplyPositiveBreathingTraining(float training) {
		if (training > 0.0F) {
			if (isSkillLearned(ModHamonSkills.NATURAL_TALENT.get())) {
				training *= 2.0F;
			}
			training *= JojoModConfig.getCommonConfigInstance(false).breathingTrainingMultiplier.get().floatValue();
		}
		return training;
	}

	public void setTrainingBonus(float trainingBonus) {
		this.trainingBonus = Math.max(0.0F, trainingBonus);
	}

	public int getCanSkipTrainingDays() {
		return canSkipTrainingDays;
	}

	public void setCanSkipTrainingDays(int canSkipTrainingDays) {
		this.canSkipTrainingDays = Math.max(0, canSkipTrainingDays);
	}

	private void tickBreathingTrainingDay(Player user) {
		long day = hamonDay(user);
		if (lastTrainingDay == Long.MIN_VALUE) {
			lastTrainingDay = day;
			return;
		}
		if (lastTrainingDay != day) {
			lastTrainingDay = day;
			breathingTrainingDay(user);
		}
	}

	public void breathingTrainingDay(Player user) {
		if (user == null || user.level().isClientSide()) {
			return;
		}
		float oldLevel = getBreathingLevel();
		float levelIncrease = getBreathingIncrease(user, true);
		setBreathingLevel(oldLevel + levelIncrease);
		if (isSkillLearned(ModHamonSkills.CHEAT_DEATH.get())) {
			HamonUtil.updateCheatDeathEffect(user);
		}
		clearExerciseTicks(false);
		updateExerciseAttributes(user);
		giveBreathingTrainingBuffs(user);
		if (user instanceof ServerPlayer serverPlayer) {
			ModCriteriaTriggers.triggerHamonStats(serverPlayer, this);
			PacketDistributor.sendToPlayer(serverPlayer, HamonExercisesPacket.allData(this));
			sendBreathingTrainingDayFeedback(serverPlayer, levelIncrease);
		}
		syncOnUpdate(user);
	}

	public boolean breathingCanGoDown(Player user) {
		return JojoModConfig.getCommonConfigInstance(false).breathingTrainingDeterioration.get()
				&& breathingLevel < MAX_BREATHING_LEVEL;
	}

	public float getBreathingIncrease(Player user, boolean newTrainingDay) {
		float completedExercises = getCompleteExercisesCount() + getMaxIncompleteExercise();
		float levelIncrease = Mth.clamp(completedExercises - 2.0F, -1.0F, 1.0F);
		float bonusIncrease = levelIncrease * 0.25F;
		boolean keepLevelThisDay = canSkipTrainingDays > 0;

		if (levelIncrease <= 0.0F) {
			if (!breathingCanGoDown(user) || keepLevelThisDay) {
				levelIncrease = 0.0F;
			}
			else {
				levelIncrease *= 0.25F;
			}
			bonusIncrease = 0.0F;
		}
		else {
			levelIncrease = multiplyPositiveBreathingTraining(levelIncrease + getTrainingBonus(user, false));
		}

		if (newTrainingDay) {
			if (levelIncrease <= 0.0F && !keepLevelThisDay) {
				trainingBonus = 0.0F;
			}
			else if (isUserWearingBreathMask(user)) {
				trainingBonus += bonusIncrease;
			}
			if (canSkipTrainingDays > 0) {
				canSkipTrainingDays--;
			}
			if (completedExercises >= MAX_EXERCISES_NEEDED) {
				canSkipTrainingDays = Math.max(canSkipTrainingDays, CAN_SKIP_DAYS);
			}
		}

		return Mth.clamp(levelIncrease, -breathingLevel, MAX_BREATHING_LEVEL - breathingLevel);
	}

	private void clearExerciseTicks(boolean clientSide) {
		for (Exercise exercise : Exercise.values()) {
			setExerciseValue(exercise, 0, clientSide);
		}
		incExerciseLastTick = false;
		incExerciseThisTick = false;
		exerciseCompleted = false;
	}

	private boolean isUserWearingBreathMask(LivingEntity user) {
		ItemStack headItem = user != null ? user.getItemBySlot(EquipmentSlot.HEAD) : ItemStack.EMPTY;
		return !headItem.isEmpty() && headItem.is(ModItems.BREATH_CONTROL_MASK.get());
	}

	private void sendExerciseCompletionFeedback(ServerPlayer serverPlayer) {
		int completed = getCompleteExercisesCount();
		String breathingIncrease = completed == 3
				? new DecimalFormat("#.##").format(getBreathingIncrease(serverPlayer, false)) : "";
		PacketDistributor.sendToPlayer(serverPlayer,
				HamonExercisesPacket.exerciseCompleted(this, completed, breathingIncrease));
	}

	private void sendBreathingTrainingDayFeedback(ServerPlayer user, float levelIncrease) {
		if (levelIncrease > 0.0F) {
			user.displayClientMessage(Component.translatable("hamon.exercise.all.day_end_increase", formatTrainingAmount(levelIncrease)), true);
		}
		else if (levelIncrease < 0.0F) {
			user.displayClientMessage(Component.translatable("hamon.exercise.all.day_end_decrease", formatTrainingAmount(-levelIncrease)), true);
		}
		else if (canSkipTrainingDays > 0) {
			user.displayClientMessage(Component.translatable("hamon.exercise.can_skip", canSkipTrainingDays), true);
		}
	}

	private static String formatTrainingAmount(float value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	public Set<String> getTeacherSkills() {
		return teacherSkills;
	}

	public void setTeacherSkills(Collection<String> teacherSkills) {
		this.teacherSkills = teacherSkills != null ? new HashSet<>(teacherSkills) : null;
	}

	public boolean playerWantsToLearn(Player player) {
		return player != null && newLearners.contains(player.getUUID());
	}

	public void addNewPlayerLearner(LivingEntity teacher, Player learnerPlayer) {
		if (learnerPlayer == null) {
			return;
		}
		newLearners.add(learnerPlayer.getUUID());
		if (!teacher.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(teacher,
					new TrHamonSyncPlayerLearnerPacket(teacher.getId(), learnerPlayer.getId(), true));
		}
	}

	public void setNewPlayerLearner(Player learnerPlayer, boolean wantsToLearn) {
		if (learnerPlayer == null) {
			return;
		}
		if (wantsToLearn) {
			newLearners.add(learnerPlayer.getUUID());
		}
		else {
			newLearners.remove(learnerPlayer.getUUID());
		}
	}

	private void tickNewPlayerLearners(LivingEntity user) {
		if (!(user.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		newLearners.removeIf(uuid -> {
			Player learner = serverLevel.getPlayerByUUID(uuid);
			return learner == null || !learner.isAlive() || user.distanceToSqr(learner) > 64.0D;
		});
	}

	public boolean interactWithNewLearner(Player teacher, Player learnerPlayer) {
		if (teacher == null || learnerPlayer == null || !newLearners.contains(learnerPlayer.getUUID())) {
			return false;
		}
		if (!learnerPlayer.level().isClientSide()) {
			HamonUtil.startLearningHamon(learnerPlayer.level(), learnerPlayer,
					com.github.standobyte.jojo.powersystem.playerpower.PlayerPower.get(learnerPlayer), teacher, this);
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(teacher,
					new TrHamonSyncPlayerLearnerPacket(teacher.getId(), learnerPlayer.getId(), false));
		}
		newLearners.remove(learnerPlayer.getUUID());
		return true;
	}

	public void removeNewLearner(Player player) {
		if (player != null) {
			newLearners.remove(player.getUUID());
		}
	}

	public boolean toggleHamonProtection() {
		setHamonProtection(!hamonProtection);
		return hamonProtection;
	}

	public void setHamonProtection(boolean isEnabled) {
		this.hamonProtection = isEnabled;
	}

	public boolean isProtectionEnabled() {
		return hamonProtection;
	}

	private void tickHamonProtection(LivingEntity user) {
		if (!hamonProtection) {
			return;
		}
		HamonSparksLoopSound.playSparkSound(user, user.getBoundingBox().getCenter(), 1.0F, true);
		CustomParticlesHelper.createHamonSparkParticles(user,
				user.getRandomX(0.5D), user.getRandomY(), user.getRandomZ(0.5D), 2);
	}

	public void setWaterWalkingThisTick() {
		waterWalkingThisTick = true;
	}

	public void postTickWaterWalking(LivingEntity user) {
		if (!user.level().isClientSide()) {
			if (waterWalkingPrevTick != waterWalkingThisTick) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user,
						new TrHamonLiquidWalkingPacket(user.getId(), waterWalkingThisTick));
			}
		}
		else {
			if ((!waterWalkingPrevTick && waterWalkingThisTick) || clWaterWalkingLargeSpark) {
				CustomParticlesHelper.createHamonSparkParticles(null, user.position(), 10);
				clWaterWalkingTickSpark = false;
				clWaterWalkingLargeSpark = false;
			}
			if ((trWaterWalking || waterWalkingThisTick) && clWaterWalkingTickSpark) {
				HamonSparksLoopSound.playSparkSound(user, user.position(), 1.0F);
				CustomParticlesHelper.createHamonSparkParticles(user,
						user.getRandomX(0.5D), user.getY(user.getRandom().nextDouble() * 0.1D),
						user.getRandomZ(0.5D), 1);
			}
			clWaterWalkingTickSpark = true;
		}
		waterWalkingPrevTick = waterWalkingThisTick;
	}

	public void trSetWaterWalking(boolean waterWalking) {
		this.trWaterWalking = waterWalking;
		if (waterWalking && !waterWalkingThisTick && !waterWalkingPrevTick) {
			clWaterWalkingLargeSpark = true;
		}
	}

	public boolean isWaterWalking() {
		return trWaterWalking || waterWalkingPrevTick;
	}

	public float waterWalkingTickCost() {
		return waterWalkingPrevTick ? 1.0F : 50.0F;
	}

	public void setDoubleShiftPress(LivingEntity user) {
		doubleShiftPress = true;
		shiftSynced = user != null && user.isShiftKeyDown();
	}

	private void tickDoubleShift(LivingEntity user) {
		if (!doubleShiftPress || user == null) {
			return;
		}
		if (!shiftSynced) {
			if (user.isShiftKeyDown()) {
				shiftSynced = true;
			}
		}
		else if (!user.isShiftKeyDown()) {
			doubleShiftPress = false;
			shiftSynced = false;
		}
	}

	public boolean getDoubleShiftPress() {
		return doubleShiftPress;
	}

	public boolean isWallClimbing() {
		return wallClimbing;
	}

	public boolean isHamonWallClimbing() {
		return wallClimbing && wallClimbHamon;
	}

	public boolean isWallClimbMoving() {
		return wallClimbMoving;
	}

	public void setWallClimbMoving(boolean moving) {
		setWallClimbMotion(moving, 0.0D, 0.0D, moving ? 1.0F : 0.0F);
	}

	public void setWallClimbMotion(boolean moving, double movementUp, double movementLeft, float speed) {
		boolean wasMoving = wallClimbMoving;
		wallClimbMoving = moving;
		if (moving) {
			if (movementUp > 1.0E-7D) {
				wallClimbAnimationName = "wall_climb_up";
			}
			else if (movementUp < -1.0E-7D) {
				wallClimbAnimationName = "wall_climb_down";
			}
			else if (movementLeft > 1.0E-7D) {
				wallClimbAnimationName = "wall_climb_left";
			}
			else if (movementLeft < -1.0E-7D) {
				wallClimbAnimationName = "wall_climb_right";
			}
			float maxSpeed = Math.abs(movementUp) > 1.0E-7D ? 2.5F : 1.25F;
			wallClimbAnimationSpeed = Mth.clamp(speed, 1.0F, maxSpeed);
			wallClimbAnimationStopping = false;
		}
		else if (wasMoving) {
			float loopTick = wallClimbAnimationTicks % 24.0F;
			if (loopTick < 0.0F) {
				loopTick += 24.0F;
			}
			float ticksUntilRest = loopTick < 12.0F ? 12.0F - loopTick : 24.0F - loopTick;
			wallClimbAnimationStopTick = wallClimbAnimationTicks + ticksUntilRest;
			wallClimbAnimationStopping = true;
		}
	}

	public String getWallClimbAnimationName() {
		return wallClimbAnimationName;
	}

	public float getWallClimbAnimationTicks(float partialTick) {
		float ticks = wallClimbAnimationTicks;
		if (wallClimbMoving || wallClimbAnimationStopping) {
			ticks += wallClimbAnimationSpeed * partialTick;
			if (wallClimbAnimationStopping) {
				ticks = Math.min(ticks, wallClimbAnimationStopTick);
			}
		}
		return ticks;
	}

	public boolean hasWallClimbYRot() {
		return wallClimbYRotSet;
	}

	public float getWallClimbYRot(float fallback) {
		return wallClimbYRotSet ? wallClimbYRot : fallback;
	}

	public float getWallClimbSpeed(LivingEntity user) {
		if (wallClimbHamon && user != null) {
			return (float) ((1.2D + getBreathingLevel() * 0.004D + getHamonControlLevel() * 0.00667D)
					* getActionEfficiency(getMaxEnergy() / 2.0F, false, ModHamonSkills.WALL_CLIMBING.get(), user));
		}
		return wallClimbSpeed;
	}

	public void startWallClimbing(LivingEntity user, float yBodyRot) {
		setWallClimbing(user, true, true, -1.0F, true, yBodyRot);
	}

	public void stopWallClimbing(LivingEntity user) {
		setWallClimbing(user, false, false, 0.0F, false, 0.0F);
	}

	public void setWallClimbing(LivingEntity user, boolean value, boolean hamon, float climbSpeed,
			boolean hasBodyRot, float yBodyRot) {
		trSetWallClimbing(value, hamon, climbSpeed, hasBodyRot, yBodyRot);
		if (user != null && !user.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrHamonWallClimbingPacket(
					user.getId(), value, hamon, climbSpeed, hasBodyRot, yBodyRot));
		}
	}

	public void trSetWallClimbing(boolean value, boolean hamon, float climbSpeed, boolean hasBodyRot, float yBodyRot) {
		boolean wasWallClimbing = wallClimbing;
		wallClimbing = value;
		wallClimbHamon = hamon;
		wallClimbSpeed = climbSpeed;
		wallClimbYRotSet = hasBodyRot;
		wallClimbYRot = hasBodyRot ? yBodyRot : 0.0F;
		if (value && !wasWallClimbing) {
			wallClimbAnimationName = "wall_climb_up";
			wallClimbAnimationTicks = 1.0F;
			wallClimbAnimationSpeed = 0.0F;
			wallClimbAnimationStopping = false;
		}
		else if (!value) {
			wallClimbMoving = false;
			wallClimbAnimationSpeed = 0.0F;
			wallClimbAnimationStopping = false;
		}
	}

	private void tickClientPoseAndWallClimbEffects(LivingEntity user) {
		if (isMeditating()) {
			meditationPoseTicks++;
		}
		if (!isHamonWallClimbing()) {
			return;
		}
		if (wallClimbMoving || wallClimbAnimationStopping) {
			float nextTick = wallClimbAnimationTicks + wallClimbAnimationSpeed;
			if (wallClimbAnimationStopping && nextTick >= wallClimbAnimationStopTick) {
				wallClimbAnimationTicks = wallClimbAnimationStopTick;
				wallClimbAnimationStopping = false;
				wallClimbAnimationSpeed = 0.0F;
			}
			else {
				wallClimbAnimationTicks = nextTick;
			}
		}
		HamonSparksLoopSound.playSparkSound(user,
				new Vec3(user.getX(), user.getY(0.75D), user.getZ()), 1.0F, true);
	}

	private void tickWallClimbing(Power<?> userPower, LivingEntity user) {
		if (!isHamonWallClimbing()) {
			return;
		}
		if (!isSkillLearned(ModHamonSkills.WALL_CLIMBING.get())) {
			stopWallClimbing(user);
			return;
		}
		if (!isHamonBreathingAction(user)) {
			float energyCost = 10.0F * (wallClimbMoving ? 1.0F : 0.25F);
			float points = Math.min(energyCost, getEnergy() * getActionEfficiency(energyCost, false, ModHamonSkills.WALL_CLIMBING.get(), user));
			if (hasEnergy(energyCost, user)) {
				consumeEnergy(energyCost, user);
				if (wallClimbMoving) {
					hamonPointsFromAction(HamonStat.CONTROL, points);
				}
				if (user.tickCount % 5 == 0) {
					syncOnUpdate(user);
				}
			}
			else {
				stopWallClimbing(user);
			}
		}
	}

	private void recalcHamonDamage() {
		hamonDamageFactor = dmgFormula(hamonStrengthLevel);
	}

	private static float dmgFormula(float strength) {
		return 1.0F + strength * 0.1F;
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = super.serializeNBT(provider);
		nbt.putFloat("BreathingLevel", breathingLevel);
		nbt.putFloat("BreathStability", breathStability);
		nbt.putInt("TrainingTicks", trainingTicks);
		nbt.putFloat("HamonEnergy", hamonEnergy);
		nbt.putInt("EnergyTicks", noEnergyDecayTicks);
		nbt.putInt("StrengthPoints", hamonStrengthPoints);
		nbt.putInt("ControlPoints", hamonControlPoints);
		nbt.putFloat("PointsIncFrac", pointsIncFrac);
		nbt.putBoolean("HamonProtection", hamonProtection);
		if (characterTechnique != null) {
			nbt.putString("CharacterTechnique", characterTechnique);
		}
		nbt.putBoolean("Meditating", isMeditating);
		nbt.putInt("MeditationTicks", meditationTicks);
		nbt.putInt("BreathStabilityIncTicks", breathStabilityIncTicks);
		nbt.putInt("MaskNoBreathTicks", ticksMaskWithNoHamonBreath);
		nbt.putInt("NoBreathStabilityIncTicks", ticksNoBreathStabilityInc);
		nbt.putIntArray("ExerciseTicks", exerciseTicksArray());
		nbt.putFloat("TrainingBonus", trainingBonus);
		nbt.putInt("CanSkipDays", canSkipTrainingDays);
		nbt.putLong("LastTrainingDay", lastTrainingDay);
		nbt.putBoolean("WallClimb", wallClimbing);
		nbt.putBoolean("WallClimbHamon", wallClimbHamon);
		nbt.putBoolean("WallClimbMoving", wallClimbMoving);
		nbt.putFloat("WallClimbSpeed", wallClimbSpeed);
		nbt.putLong("CheatDeathRefreshDay", cheatDeathRefreshDay);
		nbt.putBoolean("SatiporojaScarfGranted", satiporojaScarfGranted);
		if (wallClimbYRotSet) {
			nbt.putFloat("WallClimbRot", wallClimbYRot);
		}
		if (!abilityCooldowns.isEmpty()) {
			CompoundTag cooldowns = new CompoundTag();
			abilityCooldowns.forEach((abilityName, cooldown) -> cooldowns.putIntArray(abilityName,
					new int[] { cooldown, abilityCooldownTotals.getOrDefault(abilityName, cooldown) }));
			nbt.put("HamonAbilityCooldowns", cooldowns);
		}
		return nbt;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		super.deserializeNBT(provider, nbt);
		setBreathingLevel(nbt.getFloat("BreathingLevel"), false);
		hamonStrengthPoints = Mth.clamp(nbt.getInt("StrengthPoints"), 0, MAX_HAMON_POINTS);
		hamonStrengthLevel = levelFromPoints(hamonStrengthPoints);
		hamonControlPoints = Mth.clamp(nbt.getInt("ControlPoints"), 0, MAX_HAMON_POINTS);
		hamonControlLevel = levelFromPoints(hamonControlPoints);
		pendingHamonMasteryCheck = hasHamonMastery();
		recalcHamonDamage();
		setBreathStability(nbt.contains("BreathStability") ? nbt.getFloat("BreathStability") : getMaxBreathStability());
		setTrainingTicks(nbt.getInt("TrainingTicks"));
		setEnergy(nbt.getFloat("HamonEnergy"));
		noEnergyDecayTicks = nbt.getInt("EnergyTicks");
		pointsIncFrac = nbt.getFloat("PointsIncFrac");
		hamonProtection = nbt.getBoolean("HamonProtection");
		characterTechnique = nbt.contains("CharacterTechnique") && ModHamonSkills.techniqueDefinitionFor(nbt.getString("CharacterTechnique")) != null
				? nbt.getString("CharacterTechnique")
				: null;
		isMeditating = nbt.getBoolean("Meditating");
		meditationTicks = nbt.getInt("MeditationTicks");
		breathStabilityIncTicks = nbt.getInt("BreathStabilityIncTicks");
		ticksMaskWithNoHamonBreath = nbt.getInt("MaskNoBreathTicks");
		ticksNoBreathStabilityInc = nbt.getInt("NoBreathStabilityIncTicks");
		if (nbt.contains("ExerciseTicks")) {
			setExerciseTicks(nbt.getIntArray("ExerciseTicks"), false);
		}
		trainingBonus = nbt.getFloat("TrainingBonus");
		canSkipTrainingDays = nbt.getInt("CanSkipDays");
		lastTrainingDay = nbt.contains("LastTrainingDay") ? nbt.getLong("LastTrainingDay") : Long.MIN_VALUE;
		wallClimbing = nbt.getBoolean("WallClimb");
		wallClimbHamon = nbt.getBoolean("WallClimbHamon");
		wallClimbMoving = nbt.getBoolean("WallClimbMoving");
		wallClimbSpeed = nbt.getFloat("WallClimbSpeed");
		cheatDeathRefreshDay = nbt.contains("CheatDeathRefreshDay") ? nbt.getLong("CheatDeathRefreshDay") : Long.MIN_VALUE;
		satiporojaScarfGranted = nbt.getBoolean("SatiporojaScarfGranted");
		wallClimbYRotSet = nbt.contains("WallClimbRot");
		wallClimbYRot = wallClimbYRotSet ? nbt.getFloat("WallClimbRot") : 0.0F;
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		if (nbt.get("HamonAbilityCooldowns") instanceof CompoundTag cooldowns) {
			for (String abilityName : cooldowns.getAllKeys()) {
				int[] values = cooldowns.getIntArray(abilityName);
				int cooldown = values.length > 0 ? values[0] : 0;
				int totalCooldown = values.length > 1 ? values[1] : cooldown;
				if (cooldown > 0) {
					abilityCooldowns.put(abilityName, cooldown);
					abilityCooldownTotals.put(abilityName, Math.max(totalCooldown, cooldown));
				}
			}
		}
		applyTechniquePerks(false);
	}

	@Override
	public void toBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.toBuf(buf, isSentToTracking);
		buf.writeFloat(breathingLevel);
		buf.writeFloat(breathStability);
		buf.writeInt(trainingTicks);
		buf.writeFloat(hamonEnergy);
		buf.writeVarInt(noEnergyDecayTicks);
		buf.writeInt(hamonStrengthPoints);
		buf.writeInt(hamonControlPoints);
		buf.writeFloat(pointsIncFrac);
		buf.writeBoolean(hamonProtection);
		buf.writeBoolean(characterTechnique != null);
		if (characterTechnique != null) {
			buf.writeUtf(characterTechnique, MAX_TECHNIQUE_NAME_LENGTH);
		}
		buf.writeBoolean(isMeditating);
		buf.writeInt(meditationTicks);
		buf.writeInt(breathStabilityIncTicks);
		buf.writeVarInt(ticksMaskWithNoHamonBreath);
		buf.writeVarInt(ticksNoBreathStabilityInc);
		int[] ticks = exerciseTicksArray();
		buf.writeVarInt(ticks.length);
		for (int tick : ticks) {
			buf.writeVarInt(tick);
		}
		buf.writeFloat(trainingBonus);
		buf.writeVarInt(canSkipTrainingDays);
		buf.writeLong(lastTrainingDay);
		NetworkPayloadValidation.requireOutboundCollectionSize(
				abilityCooldowns.size(), MAX_ABILITY_COOLDOWNS,
				"Hamon ability cooldown");
		buf.writeVarInt(abilityCooldowns.size());
		for (Map.Entry<String, Integer> cooldown : abilityCooldowns.entrySet()) {
			String abilityName = cooldown.getKey();
			buf.writeUtf(abilityName, MAX_ABILITY_NAME_LENGTH);
			buf.writeVarInt(cooldown.getValue());
			buf.writeVarInt(abilityCooldownTotals.getOrDefault(abilityName, cooldown.getValue()));
		}
		buf.writeBoolean(isWaterWalking());
		buf.writeBoolean(doubleShiftPress);
		buf.writeBoolean(shiftSynced);
		buf.writeBoolean(wallClimbing);
		buf.writeBoolean(wallClimbHamon);
		buf.writeBoolean(wallClimbMoving);
		buf.writeFloat(wallClimbSpeed);
		buf.writeBoolean(wallClimbYRotSet);
		if (wallClimbYRotSet) {
			buf.writeFloat(wallClimbYRot);
		}
	}

	@Override
	public void fromBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.fromBuf(buf, isSentToTracking);
		float newBreathingLevel = buf.readFloat();
		float newBreathStability = buf.readFloat();
		int newTrainingTicks = buf.readInt();
		float newHamonEnergy = buf.readFloat();
		int newNoEnergyDecayTicks = buf.readVarInt();
		int newStrengthPoints = buf.readInt();
		int newControlPoints = buf.readInt();
		float newPointsIncFrac = buf.readFloat();
		boolean newHamonProtection = buf.readBoolean();
		String newCharacterTechnique = buf.readBoolean()
				? buf.readUtf(MAX_TECHNIQUE_NAME_LENGTH)
				: null;
		boolean newMeditating = buf.readBoolean();
		int newMeditationTicks = buf.readInt();
		int newBreathStabilityIncTicks = buf.readInt();
		int newTicksMaskWithNoHamonBreath = buf.readVarInt();
		int newTicksNoBreathStabilityInc = buf.readVarInt();
		int exerciseCount = buf.readVarInt();
		if (exerciseCount != Exercise.values().length) {
			throw new DecoderException("Invalid Hamon exercise count: " + exerciseCount
					+ " (expected " + Exercise.values().length + ")");
		}
		int[] newExerciseTicks = new int[exerciseCount];
		for (int i = 0; i < exerciseCount; i++) {
			newExerciseTicks[i] = buf.readVarInt();
		}
		float newTrainingBonus = buf.readFloat();
		int newCanSkipTrainingDays = buf.readVarInt();
		long newLastTrainingDay = buf.readLong();
		Map<String, int[]> newCooldowns = new HashMap<>();
		int cooldownCount = NetworkPayloadValidation.requireCollectionSize(
				buf.readVarInt(), MAX_ABILITY_COOLDOWNS,
				"Hamon ability cooldown");
		for (int i = 0; i < cooldownCount; i++) {
			String abilityName = buf.readUtf(MAX_ABILITY_NAME_LENGTH);
			int cooldown = buf.readVarInt();
			int totalCooldown = buf.readVarInt();
			newCooldowns.put(abilityName, new int[] { cooldown, totalCooldown });
		}
		boolean newWaterWalking = buf.readBoolean();
		boolean newDoubleShiftPress = buf.readBoolean();
		boolean newShiftSynced = buf.readBoolean();
		boolean newWallClimbing = buf.readBoolean();
		boolean newWallClimbHamon = buf.readBoolean();
		boolean newWallClimbMoving = buf.readBoolean();
		float newWallClimbSpeed = buf.readFloat();
		boolean newWallClimbYRotSet = buf.readBoolean();
		float newWallClimbYRot = newWallClimbYRotSet ? buf.readFloat() : 0.0F;

		applyBreathingLevelFromServer(newBreathingLevel);
		hamonStrengthPoints = Mth.clamp(newStrengthPoints, 0, MAX_HAMON_POINTS);
		hamonStrengthLevel = levelFromPoints(hamonStrengthPoints);
		hamonControlPoints = Mth.clamp(newControlPoints, 0, MAX_HAMON_POINTS);
		hamonControlLevel = levelFromPoints(hamonControlPoints);
		recalcHamonDamage();
		setBreathStability(newBreathStability);
		setTrainingTicks(newTrainingTicks);
		setEnergy(newHamonEnergy);
		noEnergyDecayTicks = newNoEnergyDecayTicks;
		pointsIncFrac = newPointsIncFrac;
		hamonProtection = newHamonProtection;
		characterTechnique = newCharacterTechnique != null && ModHamonSkills.techniqueDefinitionFor(newCharacterTechnique) != null
				? newCharacterTechnique
				: null;
		isMeditating = newMeditating;
		meditationTicks = newMeditationTicks;
		breathStabilityIncTicks = newBreathStabilityIncTicks;
		ticksMaskWithNoHamonBreath = newTicksMaskWithNoHamonBreath;
		ticksNoBreathStabilityInc = newTicksNoBreathStabilityInc;
		setExerciseTicks(newExerciseTicks, false);
		trainingBonus = newTrainingBonus;
		canSkipTrainingDays = newCanSkipTrainingDays;
		lastTrainingDay = newLastTrainingDay;
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		for (Map.Entry<String, int[]> entry : newCooldowns.entrySet()) {
			int[] values = entry.getValue();
			int cooldown = values.length > 0 ? values[0] : 0;
			int totalCooldown = values.length > 1 ? values[1] : cooldown;
			if (cooldown > 0) {
				abilityCooldowns.put(entry.getKey(), cooldown);
				abilityCooldownTotals.put(entry.getKey(), Math.max(totalCooldown, cooldown));
			}
		}
		trWaterWalking = newWaterWalking;
		waterWalkingPrevTick = newWaterWalking;
		doubleShiftPress = newDoubleShiftPress;
		shiftSynced = newShiftSynced;
		wallClimbing = newWallClimbing;
		wallClimbHamon = newWallClimbHamon;
		wallClimbMoving = newWallClimbMoving;
		wallClimbSpeed = newWallClimbSpeed;
		wallClimbYRotSet = newWallClimbYRotSet;
		wallClimbYRot = newWallClimbYRot;
		applyTechniquePerks(false);
	}

	public enum Exercise {
		MINING(75.0F),
		RUNNING(67.5F),
		SWIMMING(60.0F),
		MEDITATION(37.5F),
		PLACEHOLDER_1(60.0F),
		PLACEHOLDER_2(60.0F);

		public static final boolean TMP_HAS_PLACEHOLDERS = true;
		private final float maxTicks;

		Exercise(float seconds) {
			this.maxTicks = seconds * 20.0F;
		}

		public int getMaxTicks(HamonData hamon) {
			float multiplier = hamon != null
					? (MAX_BREATHING_LEVEL - hamon.getBreathingLevel()) / MAX_BREATHING_LEVEL * 0.75F + 0.25F
					: 1.0F;
			return Mth.floor(maxTicks * multiplier);
		}

		public Component getName() {
			return Component.translatable("hamon." + name().toLowerCase(Locale.ROOT) + "_exercise");
		}
	}

}
