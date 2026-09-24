package rotp.core.impl.stands.theworld;

import java.util.EnumSet;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStands;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class TimeStopBlinkAbility extends Ability {
	private static final double MAX_BLINK_DISTANCE = 192;
	private boolean teleportBehindEntity;
	private String timeStopAbilityName = "time_stop";
	// 1.16 TimeStop.Builder staminaCost / staminaCostTick of the base time stop (The World's by default)
	private float baseStaminaCost = TimeStopLearning.BASE_STAMINA_COST;
	private float baseStaminaCostTick = TimeStopLearning.BASE_STAMINA_COST_TICK;
	@Nullable private Holder<SoundEvent> blinkSound;
	@Nullable private BiFunction<LivingEntity, Entity, Vec3> entityTargetTeleportPos;

	public TimeStopBlinkAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		isSubAbility = true;
		// own wheel slot: the 1.16 ts_blink icon (falls back to time_stop)
		spriteName = "time_stop_blink";
		partsRequired(StandPart.MAIN_BODY);
	}

	@Override
	public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
		return false;
	}

	@Override
	public boolean canBeUsedInStoppedTime(Power<?> context) {
		return true;
	}

	@Override
	public boolean canUserSeeInStoppedTime(LivingEntity user, Power<?> context) {
		return true;
	}

	public TimeStopBlinkAbility setTeleportBehindEntity() {
		teleportBehindEntity = true;
		return this;
	}

	public TimeStopBlinkAbility setTimeStopAbilityName(String abilityName) {
		if (abilityName == null || abilityName.isBlank()) {
			throw new IllegalArgumentException("Time Stop ability name must not be blank");
		}
		timeStopAbilityName = abilityName;
		return this;
	}

	public String getTimeStopAbilityName() {
		return timeStopAbilityName;
	}

	/**
	 * 1.16 TimeStopInstant costs 0.8 of its base time stop's staminaCost and staminaCostTick, so a blink
	 * bound to a time stop built with other values than The World's 225 / 9 is given them here.
	 */
	public TimeStopBlinkAbility setBaseTimeStopStaminaCosts(float staminaCost, float staminaCostTick) {
		if (!Float.isFinite(staminaCost) || staminaCost < 0.0F
				|| !Float.isFinite(staminaCostTick) || staminaCostTick < 0.0F) {
			throw new IllegalArgumentException("Time Stop stamina costs must be finite and non-negative");
		}
		baseStaminaCost = staminaCost;
		baseStaminaCostTick = staminaCostTick;
		return this;
	}

	/** 1.16 TimeStopInstant took its blink sound as a constructor argument. */
	public TimeStopBlinkAbility setBlinkSound(Holder<SoundEvent> sound) {
		blinkSound = sound;
		return this;
	}

	/**
	 * 1.16 TimeStopInstant#getEntityTargetTeleportPos was overridden by add-on blinks (Shadow The World
	 * lands behind the target's whole look vector, pitch included). Takes the user and the target entity;
	 * null keeps the core rule.
	 */
	public TimeStopBlinkAbility setEntityTargetTeleportPos(@Nullable BiFunction<LivingEntity, Entity, Vec3> teleportPos) {
		entityTargetTeleportPos = teleportPos;
		return this;
	}

	public float getBlinkStaminaCost(StandPower power) {
		return TimeStopLearning.getTimeStopBlinkStaminaCost(power, baseStaminaCost);
	}

	public float getBlinkStaminaCostTicking(StandPower power, String learningName) {
		return TimeStopLearning.getTimeStopBlinkStaminaCostTicking(power, learningName, baseStaminaCostTick);
	}

	@Override
	public boolean isAbilityUnlocked(Power<?> context) {
		return getUnlockConditionCheck(context).isPositive();
	}

	@Override
	public ConditionCheck getUnlockConditionCheck(Power<?> context) {
		Ability timeStop = context != null ? context.getMoveset().getAbility(timeStopAbilityName) : null;
		return timeStop != null && timeStop != this
				? timeStop.getUnlockConditionCheck(context) : ConditionCheck.createNegative("not_unlocked");
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		return isAbilityUnlocked(context);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		LivingEntity user = standPower != null ? standPower.getUser() : null;
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (isTimeStopped(user.level(), user)) {
			return ConditionCheck.NEGATIVE;
		}
		StandEntity stand = standPower.getSummonedStandEntity();
		if (stand != null && LivingComponentAction.getCurEntityAction(stand) != null) {
			return ConditionCheck.NEGATIVE;
		}
		ConditionCheck staminaCheck = StandAbilityStamina.check(context, effectiveTimeStopCost(standPower, getBlinkStaminaCost(standPower)));
		if (!staminaCheck.isPositive()) {
			return staminaCheck;
		}
		return super.checkSpecificConditions(context);
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		bufferingState.isActionSuccess = false;
		if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
			StandPower standPower = PowerClass.STAND.get(user);
			if (standPower != null && checkConditions(standPower).isPositive()) {
				bufferingState.isActionSuccess = performBlink(serverLevel, user, standPower);
			}
		}
		return null;
	}

	private boolean performBlink(ServerLevel serverLevel, LivingEntity user, StandPower power) {
		// 1.16 TimeStopInstant read its base time stop's training for reach and per-tick cost
		String learningName = TimeStopLearning.getLearningName(power, timeStopAbilityName);
		int timeStopTicks = getMaxImpliedTicks(power, learningName);
		double playerSpeed = getDistancePerTick(user);
		double maxDistance = Math.min(playerSpeed * timeStopTicks, MAX_BLINK_DISTANCE);
		ActionTarget target = rayTraceBlinkTarget(user, maxDistance);
		Vec3 blinkPos = calcBlinkPos(serverLevel, user, target, maxDistance);
		int impliedTicks = getImpliedTicks(user, blinkPos, playerSpeed, timeStopTicks);
		if (!StandAbilityStamina.consumeOrMessage(this, power, user, effectiveTimeStopCost(power, getBlinkStaminaCost(power)))) {
			return false;
		}
		power.consumeStamina(effectiveTimeStopCost(power, impliedTicks * getBlinkStaminaCostTicking(power, learningName)));
		Vec3 soundPos = user.position();
		// 1.16 set the yaw toward the target from blinkPos before teleporting
		Float facingYaw = getFacingYaw(target, blinkPos);
		makeNearbyMobsLoseTarget(user, blinkPos);
		teleportFacing(user, blinkPos, facingYaw);
		skipTicksForStandAndUser(power, impliedTicks);
		double soundRadius = 16.0D * 5.0D;
		StandUtil.broadcastSoundWithCondition(serverLevel, soundPos, getTimeStopBlinkSound(power),
				false, power, SoundSource.AMBIENT, 5.0F, 1.0F,
				player -> TimeStopState.canPlayerSeeInStoppedTime(player)
						&& player.position().distanceToSqr(soundPos) < soundRadius * soundRadius);
		TimeStopCooldowns.setTimeStopBlinkCooldowns(power, this, impliedTicks);
		TimeStopLearning.markUsedTimeStopToday(power);
		return true;
	}

	private int getMaxImpliedTicks(StandPower power, String learningName) {
		int timeStopTicks = TimeStopLearning.getTimeStopTicks(power, learningName);
		if (StandUtil.standIgnoresStaminaDebuff(power)) {
			return timeStopTicks;
		}
		float tickingCost = effectiveTimeStopCost(power, getBlinkStaminaCostTicking(power, learningName));
		if (tickingCost <= 0.0F) {
			return timeStopTicks;
		}
		float staminaAfterBaseCost = power.getStamina() - effectiveTimeStopCost(power, getBlinkStaminaCost(power));
		int affordableTicks = Mth.floor(staminaAfterBaseCost / tickingCost);
		return Mth.clamp(affordableTicks, TimeStopLearning.MIN_TIME_STOP_TICKS, timeStopTicks);
	}

	private static float effectiveTimeStopCost(StandPower power, float amount) {
		return amount * PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power);
	}

	private Holder<SoundEvent> getTimeStopBlinkSound(StandPower power) {
		if (blinkSound != null) {
			return blinkSound;
		}
		return power != null && power.getPowerType() == ModStands.STAR_PLATINUM.get()
				? ModSoundEvents.STAR_PLATINUM_TIME_STOP_BLINK
				: ModSoundEvents.THE_WORLD_TIME_STOP_BLINK;
	}

	private static int getImpliedTicks(LivingEntity user, Vec3 blinkPos, double playerSpeed, int timeStopTicks) {
		if (playerSpeed <= 0) {
			return 0;
		}
		double ticksForDistance = blinkPos.subtract(user.position()).length() / playerSpeed;
		return Mth.clamp(Mth.ceil(ticksForDistance), 0, timeStopTicks);
	}

	private static double getDistancePerTick(LivingEntity entity) {
		return entity.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.1585;
	}

	private static ActionTarget rayTraceBlinkTarget(LivingEntity user, double maxDistance) {
		return HitResultUtil.clip(user.getEyePosition(), user.getLookAngle(),
				maxDistance, maxDistance, user.level(),
				entity -> entity != user && canBlinkTarget(user, entity),
				user, 0);
	}

	private static boolean canBlinkTarget(LivingEntity user, Entity entity) {
		if (!(entity instanceof LivingEntity)) {
			return false;
		}
		if (entity instanceof StandEntity stand && stand.getUser() == user) {
			return false;
		}
		return true;
	}

	private Vec3 calcBlinkPos(ServerLevel level, LivingEntity user, ActionTarget target, double maxDistance) {
		if (target.isEmpty(level)) {
			return getMissTeleportPos(level, user, maxDistance);
		}
		return switch (target.getType()) {
			case ENTITY -> getEntityTargetTeleportPos(user, target.getEntity());
			case BLOCK -> getBlockTargetTeleportPos(level, user, target);
			default -> getMissTeleportPos(level, user, maxDistance);
		};
	}

	private Vec3 getEntityTargetTeleportPos(LivingEntity user, Entity targetEntity) {
		if (entityTargetTeleportPos != null) {
			return entityTargetTeleportPos.apply(user, targetEntity);
		}
		if (teleportBehindEntity) {
			return targetEntity.position().subtract(Vec3.directionFromRotation(0, targetEntity.getYRot())
					.scale(targetEntity.getBbWidth() + user.getBbWidth()));
		}
		double distance = targetEntity.getBbWidth() + user.getBbWidth();
		return user.distanceToSqr(targetEntity) > distance * distance
				? targetEntity.position().subtract(user.getLookAngle().scale(distance))
				: user.position();
	}

	private static Vec3 getBlockTargetTeleportPos(ServerLevel level, LivingEntity user, ActionTarget target) {
		BlockPos blockPos = target.getBlockPos();
		BlockPos standPos = level.isEmptyBlock(blockPos.above()) ? blockPos.above() : blockPos.relative(target.getFace());
		return Vec3.atBottomCenterOf(standPos);
	}

	private static Vec3 getMissTeleportPos(ServerLevel level, LivingEntity user, double maxDistance) {
		Vec3 pos = user.getEyePosition().add(user.getLookAngle().scale(maxDistance));
		BlockPos blockPos = BlockPos.containing(pos);
		while (blockPos.getY() > level.getMinBuildHeight() && level.isEmptyBlock(blockPos.below())) {
			blockPos = blockPos.below();
		}
		double y = blockPos.getY() > level.getMinBuildHeight() ? blockPos.getY() : user.position().y;
		return new Vec3(pos.x, y, pos.z);
	}

	/** The yaw that faces an entity target from blinkPos, or null (no entity target: keep the yaw). */
	@Nullable
	public static Float getFacingYaw(ActionTarget target, Vec3 blinkPos) {
		if (target.getType() != ActionTarget.TargetType.ENTITY || target.getEntity() == null) {
			return null;
		}
		return getFacingYaw(target.getEntity().position(), blinkPos);
	}

	/** 1.16 MathUtil.yRotDegFromVec(targetPos - blinkPos); null straight above or below. */
	@Nullable
	public static Float getFacingYaw(Vec3 targetPos, Vec3 blinkPos) {
		Vec3 toTarget = targetPos.subtract(blinkPos);
		if (toTarget.horizontalDistanceSqr() < 1.0E-6D) {
			return null;
		}
		return (float) -Mth.atan2(toTarget.x, toTarget.z) * Mth.RAD_TO_DEG;
	}

	/**
	 * Teleports the user, turned to yaw when it is not null. ServerPlayer.teleportTo(x, y, z) sends the
	 * rotation as relative (unchanged), so a server-side yaw never reached the client; 1.16's teleport
	 * was absolute. A player gets an absolute yaw (pitch stays the client's own); others are turned
	 * before Entity.teleportTo, which keeps the entity's rotation.
	 */
	public static void teleportFacing(LivingEntity user, Vec3 pos, @Nullable Float yaw) {
		if (yaw != null && user instanceof ServerPlayer player && !player.isFakePlayer() && player.connection != null) {
			player.connection.teleport(pos.x, pos.y, pos.z, yaw, player.getXRot(), EnumSet.of(RelativeMovement.X_ROT));
			player.setYHeadRot(yaw);
			return;
		}
		if (yaw != null) {
			user.setYRot(yaw);
			user.yRotO = yaw;
			user.setYHeadRot(yaw);
			user.yHeadRotO = yaw;
			user.setYBodyRot(yaw);
			user.yBodyRotO = yaw;
		}
		user.teleportTo(pos.x, pos.y, pos.z);
	}

	private static void skipTicksForStandAndUser(StandPower power, int ticks) {
		LivingEntity user = power.getUser();
		if (user != null) {
			syncNoLerpPosition(user, user.position());
			skipTicks(user, ticks);
		}
		StandEntity stand = power.getSummonedStandEntity();
		if (stand != null) {
			syncNoLerpPosition(stand, stand.position());
			skipTicks(stand, ticks);
			if (ticks > 0) {
				stand.overlayTickCount += ticks;
			}
		}
	}

	private static void skipTicks(LivingEntity entity, int ticks) {
		if (ticks > 0) {
			entity.tickCount += ticks;
		}
	}

	private static void makeNearbyMobsLoseTarget(LivingEntity user, Vec3 blinkPos) {
		user.level().getEntitiesOfClass(Mob.class, user.getBoundingBox().inflate(8),
				mob -> mob.getTarget() == user
						&& mob.getLookAngle().dot(mob.getEyePosition(1).subtract(blinkPos)) >= 0)
		.forEach(mob -> loseTarget(mob, user));
	}

	private static void loseTarget(Mob mob, LivingEntity target) {
		if (mob.getTarget() == target) {
			mob.setTarget(null);
			for (WrappedGoal goal : mob.targetSelector.getAvailableGoals()) {
				if (goal.isRunning()) {
					goal.stop();
				}
			}
		}
	}

	private static void syncNoLerpPosition(LivingEntity entity, Vec3 pos) {
		if (!entity.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TrDirectEntityPosPacket(entity.getId(), pos));
		}
	}

	static boolean isTimeStopped(Level level, LivingEntity user) {
		ChunkPos chunkPos = new ChunkPos(user.blockPosition());
		if (level.isClientSide()) {
			return TimeStopState.getClientDisplayInstance(chunkPos).isPresent();
		}
		if (level instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.isTimeStopped(chunkPos);
		}
		return false;
	}
}
