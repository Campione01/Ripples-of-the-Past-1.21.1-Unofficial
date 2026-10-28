package rotp.core.impl.stands.theworld;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.CollisionHelper;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;

public class TheWorldTSPunchAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier TS_PUNCH_ANIM = ActionAnimIdentifier.getOrCreate("ts_punch", false);
	private static final float STAMINA_COST = 50F;
	private static final int RESOLVE_LEVEL_TO_UNLOCK = 3;
	private float staminaCost = STAMINA_COST;
	private boolean trainsTimeStop = true;
	private boolean skipCappedByBlinkImpliedTicks = false;
	private boolean timeSkipSoundHeardByAll = false;
	private ActionAnimIdentifier entityAnim = TS_PUNCH_ANIM;

	public TheWorldTSPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, TheWorldTSPunch::new);
		usageGroup = AbilityUsageGroup.COMBAT;
		setDefaultPhaseLength(ActionPhase.WINDUP, 5);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
		noFinisherBarDecay = true;
		partsRequired(StandPart.MAIN_BODY, StandPart.ARMS);
		cooldown(50);
		resolveLevelToUnlock(RESOLVE_LEVEL_TO_UNLOCK);
	}

	/** 1.16 StandEntityAction.Builder#staminaCost of an add-on TS punch (The World's is 50). */
	public TheWorldTSPunchAbility setStaminaCost(float staminaCost) {
		if (!Float.isFinite(staminaCost) || staminaCost < 0.0F) {
			throw new IllegalArgumentException("TS punch stamina cost must be finite and non-negative");
		}
		this.staminaCost = staminaCost;
		return this;
	}

	public float getStaminaCost() {
		return staminaCost;
	}

	/** 1.16 TheWorldTSHeavyAttack trained its time stop by the skipped ticks; an add-on TS punch that did not passes false. */
	public TheWorldTSPunchAbility setTrainsTimeStop(boolean trainsTimeStop) {
		this.trainsTimeStop = trainsTimeStop;
		return this;
	}

	public boolean trainsTimeStop() {
		return trainsTimeStop;
	}

	/**
	 * Diego's 1.16 THEWORLDTSHeavyAttack started its time skip from the blink's getMaxImpliedTicks (full blink
	 * start reserved, at least 5 ticks); the core TS punch starts from the trained ticks.
	 */
	public TheWorldTSPunchAbility setSkipCappedByBlinkImpliedTicks(boolean skipCappedByBlinkImpliedTicks) {
		this.skipCappedByBlinkImpliedTicks = skipCappedByBlinkImpliedTicks;
		return this;
	}

	public boolean skipCappedByBlinkImpliedTicks() {
		return skipCappedByBlinkImpliedTicks;
	}

	/**
	 * Diego's 1.16 THEWORLDTSHeavyAttack played its blink to every player in range; the core TS punch gives
	 * players who cannot see in stopped time the unrevealed sound instead.
	 */
	public TheWorldTSPunchAbility setTimeSkipSoundHeardByAll(boolean timeSkipSoundHeardByAll) {
		this.timeSkipSoundHeardByAll = timeSkipSoundHeardByAll;
		return this;
	}

	public boolean timeSkipSoundHeardByAll() {
		return timeSkipSoundHeardByAll;
	}

	/** Time skip sound a listener within range hears. */
	public static Holder<SoundEvent> getTimeSkipSound(ServerPlayer listener, boolean heardByAll) {
		return heardByAll || TimeStopState.canPlayerSeeInStoppedTime(listener)
				? ModSoundEvents.THE_WORLD_TIME_STOP_BLINK
				: ModSoundEvents.THE_WORLD_TIME_STOP_UNREVEALED;
	}

	/** Ticks this TS punch's time skip can last at the user's stamina. */
	public int getAffordableTimeSkipTicks(StandPower power) {
		return TimeStopLearning.getAffordableTsPunchTimeStopTicks(power, skipCappedByBlinkImpliedTicks);
	}

	/**
	 * Stand clip of an add-on TS punch (1.16 StandEntityAction.Builder#standPose; Diego's was
	 * HEAVY_ATTACK_FINISHER, its "kick" clip). Null keeps "ts_punch".
	 */
	public TheWorldTSPunchAbility setEntityAnim(String animId) {
		if (animId != null && animId.isBlank()) {
			throw new IllegalArgumentException("TS punch clip id must not be blank");
		}
		this.entityAnim = animId != null ? ActionAnimIdentifier.getOrCreate(animId, false) : TS_PUNCH_ANIM;
		return this;
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

		return super.checkSpecificConditions(context);
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
		if (!canStartDuring(curAction)) {
			return ConditionCheck.NEGATIVE;
		}
		return ConditionCheck.POSITIVE;
	}

	private boolean canStartDuring(EntityActionInstance current) {
		return current == null || current.ability != this
				&& (current.canBeCancelledInto(this) || current.getPhase() == ActionPhase.RECOVERY);
	}

	@Override
	public boolean shouldBufferInput(LivingComponentAction performerAction) {
		return !canStartDuring(performerAction.getAction());
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
		return entityAnim;
	}

	static double getBlinkDistanceRatio(int timeStopTicks, int ticksForWindup, double ticksForDistance) {
		if (timeStopTicks <= ticksForWindup || !Double.isFinite(ticksForDistance) || ticksForDistance < 0) {
			return 0;
		}
		if (ticksForDistance == 0) {
			return 1;
		}
		return Mth.clamp(((double) timeStopTicks - ticksForWindup) / ticksForDistance, 0, 1);
	}

	static double getEntityBlinkFeetY(double alignedFeetY, double targetFeetY) {
		return Math.max(alignedFeetY, targetFeetY);
	}

	public static class TheWorldTSPunch extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch {
		private ActionTarget targetAfterBlink = ActionTarget.EMPTY;
		private boolean gravityOverridden;
		private boolean previousNoGravity;
		private boolean recoveryReread;

		public TheWorldTSPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected boolean usesHeavyFinisherSetup() {
			return false;
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			super.onActionSet(prevAction);
			if (performer instanceof StandEntity stand) {
				// Following normally masks old velocity; a stationary blink must not inherit it.
				stand.setDeltaMovement(Vec3.ZERO);
				// 1.16 TheWorldTSHeavyAttack.preTaskInit
				if (isWherePressed()) {
					stand.summonLockTicks = 0;
				}
				if (!takesTargetsFromServer()) {
					previousNoGravity = stand.isNoGravity();
					gravityOverridden = true;
					stand.setNoGravity(true);
					stand.hurtMarked = true;
					// a failed input that puts this punch back keeps the blink, time skip and stamina it took
					if (isRestoringInputLifecycle()) {
						setActionTargetSnapshot(targetAfterBlink);
						return;
					}
					boolean trainsTimeStop = !(ability instanceof TheWorldTSPunchAbility tsPunch) || tsPunch.trainsTimeStop;
					TheWorldTSPunchAbility punchAbility = ability instanceof TheWorldTSPunchAbility tsPunchAbility ? tsPunchAbility : null;
					ActionTarget target = blinkStandTowardTarget(stand, prevAction, trainsTimeStop, punchAbility);
					targetAfterBlink = setActionTargetSnapshot(target);
					if (!target.isEmpty(stand.level())) {
						standRotationTarget = target;
					}
					else {
						// 1.16 targetBeforePerform put the blink's ray in place of the pressed target: no task target to turn to
						storeTaskTarget(null);
					}
				}
			}
		}

		// 1.16 StandEntityAction.startedHolding and perform ran preTaskInit where the press ran (PowerBaseImpl.clickAction):
		// on the server and on the pressing user's client, not on another player's
		private boolean isWherePressed() {
			return !takesTargetsFromServer() || getPowerUser() instanceof Player player && player.isLocalPlayer();
		}

		@Override
		public void setPhase(ActionPhase newPhase, int tick) {
			super.setPhase(newPhase, tick);
			if (recoveryReread) {
				recoveryReread = false;
				syncPhaseChanges();
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			// 1.16 TheWorldTSHeavyAttack.getStandRecoveryTicks, read by StandEntityTask as the recovery starts: finisher 0
			// at the Stand's attack speed of that moment
			if (newPhase == ActionPhase.RECOVERY && phase != null && phase != newPhase
					&& performer instanceof StandEntity stand && !stand.level().isClientSide()) {
				float before = phasesLength.getFloat(ActionPhase.RECOVERY);
				phasesLength.put(ActionPhase.RECOVERY, StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), 0));
				recoveryReread = Float.compare(before, phasesLength.getFloat(ActionPhase.RECOVERY)) != 0;
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			if (performer instanceof StandEntity stand) {
				stand.setDeltaMovement(Vec3.ZERO);
				if (!stand.level().isClientSide()) {
					if (gravityOverridden) {
						stand.setNoGravity(previousNoGravity);
						gravityOverridden = false;
					}
					stand.hurtMarked = true;
				}
			}
			super.onActionCleared(newAction);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (performer instanceof StandEntity stand) {
				ActionTarget target = getPunchTarget(stand);
				// judged before the hit, which may break the block or remove the entity, as 1.16 set the task target first
				boolean keepsStruck = !takesTargetsFromServer() && keepsStrikeTarget(stand, target);
				if (!level.isClientSide()) {
					StandPower standPower = StandPower.get(getPowerUser());
					float staminaCost = ability instanceof TheWorldTSPunchAbility tsPunch ? tsPunch.staminaCost : STAMINA_COST;
					if (standPower == null || getPowerUser() == null) {
						return;
					}
					stand.turnTowardsStrike(isGrabVariation() ? target
							: StandEntityPunchAbility.strikeAim(stand, target, StandEntityPunchAbility.CrosshairRule.NONE));

					boolean entityTarget = target.getType() == TargetType.ENTITY;
					Vec3 impactSoundPos = target.isEmpty(level) ? null : target.getCenterPos();
					// 1.16 HeavyPunchBlockInstance.playImpactSound: a block always sounds, whatever its hardness
					if (target.getType() == TargetType.BLOCK && impactSoundPos != null) {
						StandUtil.broadcastSound((ServerLevel) level, impactSoundPos,
								ModSoundEvents.THE_WORLD_PUNCH_HEAVY, true, standPower,
								stand.getSoundSource(), 1, 1);
					}
					DamageSource dmgSource = makePunchDamageSource();
					float dmgAmount = StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage());
					float explRadius = StandEntityHeavyPunchAbility.calcExplosionRadius(stand);

					boolean deflectedTarget = StandEntityPunchAbility.deflectSilverChariotProjectiles(stand, target);
					impactSoundDue = null;
					switch (target.getType()) {
						case ENTITY -> {
							if (!deflectedTarget) {
								hitEntity(target, level, stand, dmgSource, dmgAmount, explRadius);
							}
						}
						case BLOCK -> hitBlock(target, level, stand, dmgSource, dmgAmount, explRadius);
						default -> {}
					}
					// 1.16 StandEntity.attackTarget: an entity's impact sound comes after the hit, if it landed
					if (entityTarget && impactSoundPos != null && (impactSoundDue == null || impactSoundDue)) {
						StandUtil.broadcastSound((ServerLevel) level, impactSoundPos,
								ModSoundEvents.THE_WORLD_PUNCH_HEAVY, true, standPower,
								stand.getSoundSource(), 1, 1);
					}

					punchedTarget = target;
					if (standPower != null) {
						TimeStopLearning.markUsedTimeStopToday(standPower);
					}
					ModCriteriaTriggers.triggerTimeAbility(getPowerUser(), standPower);
					// The donor task charges after the strike, even when the blink exhausted stamina.
					standPower.consumeStamina(staminaCost);
				}
				// the server's strike set the targets by 1.16's task rule (getPunchTarget); a client sets them from the
				// target it holds
				if (takesTargetsFromServer() || isGrabVariation()) {
					if (target.getType() == TargetType.ENTITY) {
						standRotationTarget = target;
					}
					else {
						aimAs = AimingEntity.CAMERA_ENTITY;
					}
				}
				else {
					takeStrikeTarget(target, keepsStruck);
				}
			}
		}

		// 1.16 TheWorldTSHeavyAttack.standKeepsTarget kept every target, a block too, as the light attack did
		@Override
		protected StandEntityPunchAbility.CrosshairRule keptTargetRule() {
			return StandEntityPunchAbility.CrosshairRule.BLOCK_OR_ENTITY;
		}

		// 1.16 TheWorldTSHeavyPunch was a HeavyPunchInstance: the native hurt, then 2 on a landed hit, then a 2 tail
		@Override
		protected boolean usesDetachedPunchKnockback() {
			return true;
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

		// 1.16 TheWorldTSHeavyAttack disableBlocking(1.0F), between the landed hit's push and the tail
		@Override
		protected void afterDetachedPunchHurt(StandEntity stand, LivingEntity target, DamageSource source, float rawDamage, boolean hurt) {
			super.afterDetachedPunchHurt(stand, target, source, rawDamage, hurt);
			StandEntityHeavyPunchAbility.disableShieldOnHit(target, hurt, 1.0F);
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
			// the blink's target is the server's: a client holds what the server sent
			if (takesTargetsFromServer() || isGrabVariation()) {
				return super.getPunchTarget(stand);
			}
			// the blink's target while the task keeps it (StandEntityTask.tick dropped it on any tick it failed keepTarget)
			ActionTarget target = strikeTarget(stand, getActionTargetSnapshot(stand.level()));
			if (target.isEmpty(stand.level())) {
				// 1.16 StandEntity.punch: a target the strike cannot reach is replaced by a fresh aim
				target = strikeTarget(stand, freshAim(stand));
			}
			if (isStartingPerform()) {
				takeStrikeTarget(target, keepsStrikeTarget(stand, target));
			}
			return target;
		}

		// 1.16 aimWithThisOrUser: the precision ray, walls blocking
		private ActionTarget freshAim(StandEntity stand) {
			ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(
					stand, getActionTargetSnapshot(stand.level()),
					StandEntityPunchAbility.shouldRetainPunchTarget(stand, ability), true);
			// at the strike, the task rule decides what is kept (takeStrikeTarget)
			if (!isStartingPerform()) {
				setActionTargetSnapshot(target);
			}
			return target;
		}

		private static ActionTarget strikeTarget(StandEntity stand, ActionTarget candidate) {
			ActionTarget target = StandEntityPunchAbility.validatePunchTarget(stand, candidate);
			if (target.getType() == TargetType.ENTITY && StandSkillWalls.wallsHideEntities(stand)
					&& !stand.hasLineOfSight(target.getMainEntity())) {
				return ActionTarget.EMPTY;
			}
			return target;
		}

		private static ActionTarget blinkStandTowardTarget(StandEntity stand, EntityActionInstance prevAction,
				boolean trainsTimeStop, TheWorldTSPunchAbility punchAbility) {
			LivingEntity user = stand.getUser();
			if (user == null || !(stand.level() instanceof ServerLevel serverLevel)) {
				return ActionTarget.EMPTY;
			}

			LivingEntity aimingEntity = stand.isManuallyControlled() ? stand : user;
			// 1.16 TheWorldTSHeavyAttack.targetBeforePerform aimed again and never used the clicked target
			ActionTarget target = rayTraceTsPunchTarget(stand, aimingEntity, punchAbility);
			Vec3 blinkPos = calcBlinkPos(stand, aimingEntity, target);

			StandPower standPower = StandPower.get(user);
			int timeStopTicks = standPower == null ? TimeStopLearning.MIN_TIME_STOP_TICKS
					: punchAbility != null ? punchAbility.getAffordableTimeSkipTicks(standPower)
					: TimeStopLearning.getAffordableTsPunchTimeStopTicks(standPower);
			int ticksForWindup = 10 + (prevAction != null ? 20 : 0);
			double speed = getDistancePerTick(stand);
			if (Double.isFinite(speed) && speed > 0) {
				double ticksForDistance = blinkPos.subtract(stand.position()).length() / speed;
				double distanceRatio = getBlinkDistanceRatio(timeStopTicks, ticksForWindup, ticksForDistance);
				if (distanceRatio <= 0) {
					blinkPos = stand.position();
				}
				else if (distanceRatio < 1) {
					blinkPos = stand.position().lerp(blinkPos, distanceRatio);
				}
				else {
					timeStopTicks = Mth.ceil(ticksForDistance) + ticksForWindup;
				}
			}
			else {
				blinkPos = stand.position();
				timeStopTicks = Math.min(timeStopTicks, ticksForWindup);
			}

			if (target.getType() == TargetType.BLOCK) {
				// Eye-aligned block targets can put the feet into the floor; collide the full body.
				CollisionHelper.BlockCollisionResult collision = CollisionHelper.collideBoundingBox(
						blinkPos.subtract(stand.position()), stand.getBoundingBox(), serverLevel, CollisionContext.of(stand));
				blinkPos = stand.position().add(collision.x, collision.y, collision.z);
			}
			else {
				blinkPos = stand.collideNextPos(blinkPos);
			}
			stand.moveTo(blinkPos.x, blinkPos.y, blinkPos.z);

			skipTicksForStandAndUser(standPower, stand, timeStopTicks);
			playTimeSkipBlinkSound(serverLevel, stand, standPower,
					punchAbility != null && punchAbility.timeSkipSoundHeardByAll);
			if (standPower != null) {
				TimeStopLearning.consumeTsPunchTimeStopStamina(standPower, timeStopTicks);
				if (trainsTimeStop) {
					TimeStopLearning.onTsPunchTimeSkip(standPower, timeStopTicks);
				}
			}
			return target;
		}

		private static void playTimeSkipBlinkSound(ServerLevel level, StandEntity stand, StandPower standPower,
				boolean heardByAll) {
			Vec3 pos = stand.position();
			if (standPower == null || standPower.getStandInstance().isEmpty()) {
				level.playSound(null, pos.x, pos.y, pos.z, ModSoundEvents.THE_WORLD_TIME_STOP_BLINK.get(),
						SoundSource.AMBIENT, 1.0F, 1.0F);
				return;
			}

			double soundRadius = 16.0D;
			// each listener in range gets one of the two; heardByAll sends the blink to all
			StandUtil.broadcastSoundWithCondition(level, pos, ModSoundEvents.THE_WORLD_TIME_STOP_BLINK,
					false, standPower, SoundSource.AMBIENT, 1.0F, 1.0F,
					player -> player.position().distanceToSqr(pos) < soundRadius * soundRadius
							&& getTimeSkipSound(player, heardByAll) == ModSoundEvents.THE_WORLD_TIME_STOP_BLINK);
			StandUtil.broadcastSoundWithCondition(level, pos, ModSoundEvents.THE_WORLD_TIME_STOP_UNREVEALED,
					false, standPower, SoundSource.AMBIENT, 1.0F, 1.0F,
					player -> player.position().distanceToSqr(pos) < soundRadius * soundRadius
							&& getTimeSkipSound(player, heardByAll) == ModSoundEvents.THE_WORLD_TIME_STOP_UNREVEALED);
		}

		// 1.16 precisionRayTrace(aiming, getMaxRange(), getPrecision() / 16, false); entities behind a wall stay hidden
		// unless this skill is marked through walls
		private static ActionTarget rayTraceTsPunchTarget(StandEntity stand, LivingEntity aimingEntity,
				TheWorldTSPunchAbility punchAbility) {
			boolean wallsHideEntities = StandSkillWalls.wallsHideEntities(stand.level(), punchAbility);
			if (!StandEntityPunchAbility.aimAssist(stand.getUser())) {
				return StandPrecisionRay.crosshair(aimingEntity, stand, stand.getMaxRange(), false, wallsHideEntities);
			}
			return StandPrecisionRay.trace(aimingEntity, stand, stand.getMaxRange(), stand.getPrecision() / 16D, false,
					wallsHideEntities);
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
			Vec3 blinkPos = offsetFromTargetPosition(stand, aimingEntity, targetPos, offset);
			return new Vec3(blinkPos.x, getEntityBlinkFeetY(blinkPos.y, targetEntity.getY()), blinkPos.z);
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

	@Override
	public float getStaminaCost(Power<?> context) {
		return getStaminaCost();
	}
}
