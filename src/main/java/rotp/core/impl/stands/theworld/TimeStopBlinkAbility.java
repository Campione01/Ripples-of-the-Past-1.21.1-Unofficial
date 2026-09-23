package rotp.core.impl.stands.theworld;

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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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

	public TimeStopBlinkAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		isSubAbility = true;
		spriteName = "time_stop";
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
		ConditionCheck staminaCheck = StandAbilityStamina.check(context, effectiveTimeStopCost(standPower, getStaminaCost(standPower)));
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
		if (!StandAbilityStamina.consumeOrMessage(this, power, user, effectiveTimeStopCost(power, getStaminaCost(power)))) {
			return false;
		}
		power.consumeStamina(effectiveTimeStopCost(power, impliedTicks * getStaminaCostTicking(power, learningName)));
		Vec3 soundPos = user.position();
		makeNearbyMobsLoseTarget(user, blinkPos);
		user.teleportTo(blinkPos.x, blinkPos.y, blinkPos.z);
		faceEntityTarget(user, target);
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

	private static int getMaxImpliedTicks(StandPower power, String learningName) {
		int timeStopTicks = TimeStopLearning.getTimeStopTicks(power, learningName);
		if (StandUtil.standIgnoresStaminaDebuff(power)) {
			return timeStopTicks;
		}
		float tickingCost = effectiveTimeStopCost(power, getStaminaCostTicking(power, learningName));
		if (tickingCost <= 0.0F) {
			return timeStopTicks;
		}
		float staminaAfterBaseCost = power.getStamina() - effectiveTimeStopCost(power, getStaminaCost(power));
		int affordableTicks = Mth.floor(staminaAfterBaseCost / tickingCost);
		return Mth.clamp(affordableTicks, TimeStopLearning.MIN_TIME_STOP_TICKS, timeStopTicks);
	}

	private static float effectiveTimeStopCost(StandPower power, float amount) {
		return amount * PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power);
	}

	private static float getStaminaCost(StandPower power) {
		return TimeStopLearning.getTimeStopBlinkStaminaCost(power);
	}

	private static float getStaminaCostTicking(StandPower power, String learningName) {
		return TimeStopLearning.getTimeStopBlinkStaminaCostTicking(power, learningName);
	}

	private static Holder<SoundEvent> getTimeStopBlinkSound(StandPower power) {
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

	private static void faceEntityTarget(LivingEntity user, ActionTarget target) {
		if (target.getType() != ActionTarget.TargetType.ENTITY || target.getEntity() == null) {
			return;
		}
		Vec3 toTarget = target.getEntity().position().subtract(user.position());
		if (toTarget.lengthSqr() > 1e-6) {
			float yRot = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180F / Math.PI)) - 90F;
			user.setYRot(yRot);
			user.yRotO = yRot;
		}
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
