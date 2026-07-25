package com.github.standobyte.jojoimpl.stands.theworld;

import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.network.s2c.TrDirectEntityPosPacket;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.AbilityUsageGroup;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.HeldInput;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopLearning;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityHeavyPunchAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class TheWorldTSPunchAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier TS_PUNCH_ANIM = ActionAnimIdentifier.getOrCreate("ts_punch", false);
	private static final float STAMINA_COST = 50F;
	private static final int RESOLVE_LEVEL_TO_UNLOCK = 3;

	public TheWorldTSPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, TheWorldTSPunch::new);
		usageGroup = AbilityUsageGroup.COMBAT;
		setDefaultPhaseLength(ActionPhase.WINDUP, 5);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
		noFinisherBarDecay = true;
		partsRequired(StandPart.MAIN_BODY, StandPart.ARMS);
		cooldown(50);
		resolveLevelToUnlock(RESOLVE_LEVEL_TO_UNLOCK);
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && performer instanceof StandEntity stand) {
			action.phasesLength.put(ActionPhase.RECOVERY,
					StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), 0));
		}
	}

	@Override
	public String getSpriteName(Power<?> context) {
		LivingEntity user = context != null ? context.getUser() : null;
		return doesBackshot(user) ? "ts_punch_back" : super.getSpriteName(context);
	}

	private static boolean doesBackshot(LivingEntity user) {
		return user != null && user.isShiftKeyDown();
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower == null) {
			return ConditionCheck.NEGATIVE;
		}

		LivingEntity user = standPower.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}

		if (!user.level().isClientSide() && user.level() instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			if (state.isTimeStopped(user)) {
				return ConditionCheck.NEGATIVE;
			}
		}

		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
	}

	@Override
	protected ConditionCheck checkStandEntityConditions(StandPower standPower, StandEntity standEntity) {
		ConditionCheck check = super.checkStandEntityConditions(standPower, standEntity);
		if (!check.isPositive()) {
			return check;
		}
		if (standEntity.isBeingRetracted() || !standEntity.canAttackMelee()) {
			return ConditionCheck.NEGATIVE;
		}
		EntityActionInstance curAction = LivingComponentAction.getComponent(standEntity).getAction();
		if (curAction != null && !curAction.canBeCancelledInto(this)) {
			return ConditionCheck.NEGATIVE;
		}
		return ConditionCheck.POSITIVE;
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		if (level.isClientSide()) {
			return null;
		}

		return super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
	}

	@Override
	public boolean noAdheringToUserOffset(StandPower standPower, StandEntity standEntity) {
		return true;
	}

	@Override
	public boolean noAdheringToUserOffsetClientFallback(StandEntity standEntity) {
		return true;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return TS_PUNCH_ANIM;
	}

	public static class TheWorldTSPunch extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch {
		private ActionTarget targetAfterBlink = ActionTarget.EMPTY;

		public TheWorldTSPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			super.onActionSet(prevAction);
			if (performer instanceof StandEntity stand && !stand.level().isClientSide()) {
				stand.summonLockTicks = 0;
				ActionTarget target = blinkStandTowardTarget(stand, prevAction, getActionTargetSnapshot(stand.level()));
				targetAfterBlink = setActionTargetSnapshot(target);
				if (!target.isEmpty(stand.level())) {
					standRotationTarget = target;
				}
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (performer instanceof StandEntity stand) {
				ActionTarget target = getPunchTarget(stand);
				if (!level.isClientSide()) {
					StandPower standPower = StandPower.get(getPowerUser());
					if (getPowerUser() == null
							|| !StandAbilityStamina.consumeOrMessage(ability, standPower, getPowerUser(), STAMINA_COST)) {
						return;
					}

					if (StandEntityPunchAbility.playHitSound(target, level)) {
						StandUtil.broadcastSound((ServerLevel) level, target.getCenterPos(),
								ModSoundEvents.THE_WORLD_PUNCH_HEAVY, true, standPower,
								stand.getSoundSource(), 1, 1);
					}
					DamageSource dmgSource = makePunchDamageSource();
					float dmgAmount = StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage());
					float explRadius = StandEntityHeavyPunchAbility.calcExplosionRadius(stand);

					boolean deflectedTarget = StandEntityPunchAbility.deflectSilverChariotProjectiles(stand, target);
					switch (target.getType()) {
						case ENTITY -> {
							if (!deflectedTarget) {
								hitEntity(target, level, stand, dmgSource, dmgAmount, explRadius);
							}
						}
						case BLOCK -> hitBlock(target, level, stand, dmgSource, dmgAmount, explRadius);
						default -> {}
					}

					punchedTarget = target;
					if (standPower != null) {
						TimeStopLearning.markUsedTimeStopToday(standPower);
					}
				}
				if (target.getType() == TargetType.ENTITY) {
					standRotationTarget = target;
				}
				else {
					aimAs = AimingEntity.CAMERA_ENTITY;
				}
			}
		}

		@Override
		protected void addKnockback(DamageSource dmgSource) {
			DamageSourceModified knockback = (DamageSourceModified) dmgSource;
			knockback.jojo_ripples$modifyKnockback(4F, 1);
		}

		@Override
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand,
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			Entity targetEntity = target.getMainEntity();
			disableTargetStandBlocking(stand, targetEntity);
			((DamageSourceModified) dmgSource).jojo_ripples$setStandInvulTicks(10);
			if (targetEntity instanceof LivingEntity livingTarget) {
				boolean wasAlive = livingTarget.isAlive();
				super.hitEntity(target, level, stand, dmgSource, dmgAmount, explRadius);
				if (wasAlive && !livingTarget.isAlive()) {
					playVoiceLineOnKill(stand);
				}
			}
			else {
				super.hitEntity(target, level, stand, dmgSource, dmgAmount, explRadius);
			}
		}

		private void disableTargetStandBlocking(StandEntity stand, Entity targetEntity) {
			if (targetEntity instanceof StandEntity targetStand && stand.getRandom().nextFloat() < 1.0F) {
				targetStand.breakStandBlocking(StandStatFormulas.getGuardBreakTicks(targetStand.getDurability()));
			}
		}

		private void playVoiceLineOnKill(StandEntity stand) {
			LivingEntity user = stand.getUser();
			if (user != null && stand.distanceToSqr(user) > 16) {
				JojoModUtil.sayVoiceLine(user, ModSoundEvents.DIO_THIS_IS_THE_WORLD);
			}
		}

		@Override
		protected ActionTarget getPunchTarget(StandEntity stand) {
			if (targetAfterBlink != ActionTarget.EMPTY && !targetAfterBlink.isEmpty(stand.level())) {
				return targetAfterBlink;
			}
			return super.getPunchTarget(stand);
		}

		private static ActionTarget blinkStandTowardTarget(StandEntity stand, EntityActionInstance prevAction,
				ActionTarget inputTarget) {
			LivingEntity user = stand.getUser();
			if (user == null || !(stand.level() instanceof ServerLevel serverLevel)) {
				return ActionTarget.EMPTY;
			}

			LivingEntity aimingEntity = stand.isManuallyControlled() ? stand : user;
			ActionTarget target = inputTarget != null ? inputTarget.resolveEntityId(stand.level()) : ActionTarget.EMPTY;
			if (target.isEmpty(stand.level())) {
				target = rayTraceTsPunchTarget(stand, aimingEntity);
			}
			Vec3 blinkPos = calcBlinkPos(stand, aimingEntity, target);

			StandPower standPower = StandPower.get(user);
			int timeStopTicks = standPower != null
					? TimeStopLearning.getAffordableTsPunchTimeStopTicks(standPower)
					: TimeStopLearning.MIN_TIME_STOP_TICKS;
			int ticksForWindup = 10 + (prevAction != null ? 20 : 0);
			double speed = getDistancePerTick(stand);
			if (speed > 0) {
				double ticksForDistance = blinkPos.subtract(stand.position()).length() / speed;
				if (timeStopTicks < ticksForDistance + ticksForWindup) {
					if (timeStopTicks > ticksForWindup && ticksForDistance > 0) {
						blinkPos = blinkPos.subtract(stand.position())
								.scale((double) timeStopTicks - ticksForWindup / ticksForDistance)
								.add(stand.position());
					}
					else {
						blinkPos = stand.position();
					}
				}
				else {
					timeStopTicks = Mth.ceil(ticksForDistance) + ticksForWindup;
				}
			}
			else {
				timeStopTicks = ticksForWindup;
			}

			blinkPos = stand.collideNextPos(blinkPos);
			stand.moveTo(blinkPos.x, blinkPos.y, blinkPos.z);

			skipTicksForStandAndUser(standPower, stand, timeStopTicks);
			playTimeSkipBlinkSound(serverLevel, stand, standPower);
			if (standPower != null) {
				TimeStopLearning.consumeTsPunchTimeStopStamina(standPower, timeStopTicks);
				TimeStopLearning.onTsPunchTimeSkip(standPower, timeStopTicks);
			}
			return target;
		}

		private static void playTimeSkipBlinkSound(ServerLevel level, StandEntity stand, StandPower standPower) {
			Vec3 pos = stand.position();
			if (standPower == null || standPower.getStandInstance().isEmpty()) {
				level.playSound(null, pos.x, pos.y, pos.z, ModSoundEvents.THE_WORLD_TIME_STOP_BLINK.get(),
						SoundSource.AMBIENT, 1.0F, 1.0F);
				return;
			}

			double soundRadius = 16.0D;
			StandUtil.broadcastSoundWithCondition(level, pos, ModSoundEvents.THE_WORLD_TIME_STOP_BLINK,
					false, standPower, SoundSource.AMBIENT, 1.0F, 1.0F,
					player -> TimeStopState.canPlayerSeeInStoppedTime(player)
							&& player.position().distanceToSqr(pos) < soundRadius * soundRadius);
			StandUtil.broadcastSoundWithCondition(level, pos, ModSoundEvents.THE_WORLD_TIME_STOP_UNREVEALED,
					false, standPower, SoundSource.AMBIENT, 1.0F, 1.0F,
					player -> !TimeStopState.canPlayerSeeInStoppedTime(player)
							&& player.position().distanceToSqr(pos) < soundRadius * soundRadius);
		}

		private static ActionTarget rayTraceTsPunchTarget(StandEntity stand, LivingEntity aimingEntity) {
			return HitResultUtil.clip(aimingEntity.getEyePosition(), aimingEntity.getLookAngle(),
					stand.getMaxRange(), stand.getMaxRange(), stand.level(),
					entity -> StandEntityPunchAbility.canStandHit(stand, entity),
					aimingEntity, stand.getPrecision() / 16F);
		}

		private static Vec3 calcBlinkPos(StandEntity stand, LivingEntity aimingEntity, ActionTarget target) {
			return switch (target.getType()) {
				case ENTITY -> getEntityTargetTeleportPos(stand, aimingEntity, target.getEntity(), target);
				case BLOCK -> getBlockTargetTeleportPos(stand, aimingEntity, target);
				default -> aimingEntity.position().add(stand.getLookAngle().scale(stand.getMaxRange()));
			};
		}

		private static Vec3 getEntityTargetTeleportPos(StandEntity stand, LivingEntity aimingEntity,
				Entity targetEntity, ActionTarget target) {
			Vec3 targetPos = targetEntity.getEyePosition();
			double offset = 0.5 + stand.getBbWidth() + targetEntity.getBoundingBox().getXsize() / 2;
			return offsetFromTargetPosition(stand, aimingEntity, targetPos, offset);
		}

		private static Vec3 getBlockTargetTeleportPos(StandEntity stand, LivingEntity aimingEntity, ActionTarget target) {
			Vec3 blockHitPos = Vec3.atCenterOf(target.getBlockPos());
			double offset = 0.5 + stand.getBbWidth();
			return offsetFromTargetPosition(stand, aimingEntity, blockHitPos, offset);
		}

		private static Vec3 offsetFromTargetPosition(StandEntity stand, LivingEntity aimingEntity,
				Vec3 targetPos, double offset) {
			Vec3 offsetFromTarget = aimingEntity.getEyePosition().subtract(targetPos);
			offsetFromTarget = offsetFromTarget.normalize().scale(offset);
			LivingEntity user = stand.getUser();
			if (TheWorldTSPunchAbility.doesBackshot(user)) {
				offsetFromTarget = offsetFromTarget.reverse();
			}
			return targetPos.add(offsetFromTarget).subtract(0, stand.getEyeHeight(), 0);
		}

		private static double getDistancePerTick(LivingEntity entity) {
			return entity.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.1585;
		}

		private static void skipTicksForStandAndUser(StandPower standPower, StandEntity stand, int ticks) {
			if (standPower != null && standPower.getUser() != null) {
				LivingEntity user = standPower.getUser();
				syncNoLerpPosition(user, user.position());
				skipTicks(user, ticks);
			}
			syncNoLerpPosition(stand, stand.position());
			skipTicks(stand, ticks);
			if (ticks > 0) {
				stand.overlayTickCount += ticks;
			}
		}

		private static void skipTicks(LivingEntity entity, int ticks) {
			if (ticks > 0) {
				entity.tickCount += ticks;
			}
		}

		private static void syncNoLerpPosition(LivingEntity entity, Vec3 pos) {
			if (!entity.level().isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TrDirectEntityPosPacket(entity.getId(), pos));
			}
		}
	}
}
