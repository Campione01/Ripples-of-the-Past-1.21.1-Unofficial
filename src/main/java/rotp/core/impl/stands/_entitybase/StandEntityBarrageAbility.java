package rotp.core.impl.stands._entitybase;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.barrage.StandCrySoundHandler;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.entityattachment.syncheddata.SyncedDataHolderExtended;
import rotp.core.entityattachment.syncheddata.SynchedDataBuilder;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.network.s2c.TrBarrageHitSoundPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandUtil.StandStat;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.powersystem.standpower.entity.StandLinkDamageSource;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.core.core.JojoMod;

public class StandEntityBarrageAbility extends StandEntityAbility {
	@Nullable private Holder<SoundEvent> barrageHitSound;
	@Nullable private Holder<SoundEvent> barrageCrySound;
	private boolean barrageCryInArmsOnly;

	public StandEntityBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		this(abilityType, abilityId, StandEntityBarrage::new);
	}

	protected StandEntityBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId,
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		super(abilityType, abilityId, createActionObj);
		usageGroup = AbilityUsageGroup.COMBAT;
		setDefaultPhaseLength(ActionPhase.PERFORM, StandStatFormulas.getBarrageMaxDuration(8));
		setDefaultPhaseLength(ActionPhase.RECOVERY, 10);
		noFinisherBarDecay = true;
		standAutoSummonMode(AutoSummonMode.ARMS);
		partsRequired(StandPart.ARMS);
	}

	public StandEntityBarrageAbility barrageHitSound(Holder<SoundEvent> barrageHitSound) {
		this.barrageHitSound = barrageHitSound;
		return this;
	}

	public StandEntityBarrageAbility barrageCrySound(Holder<SoundEvent> barrageCrySound) {
		this.barrageCrySound = barrageCrySound;
		return this;
	}

	/**
	 * The barrage cry also plays while the Stand is arms-only, as a 1.16 cry registered with the one-argument
	 * standSound (playInArmsOnly true) did; the core Stands registered theirs with playInArmsOnly false.
	 */
	public StandEntityBarrageAbility barrageCryInArmsOnly() {
		this.barrageCryInArmsOnly = true;
		return this;
	}

	public StandEntityBarrageAbility initIsGrabVariation() {
		usageGroup = AbilityUsageGroup.GRAB;
		return this;
	}

	// 1.16 StandEntityMeleeBarrage.checkStandConditions, which PowerBaseImpl.tickHeldAction also ran on every held tick: a
	// Stand that can no longer attack in melee ends the hold, without a message
	@Override
	protected ConditionCheck checkStandEntityConditions(StandPower standPower, StandEntity standEntity) {
		ConditionCheck check = super.checkStandEntityConditions(standPower, standEntity);
		if (!check.isPositive()) {
			return check;
		}
		if (usageGroup != AbilityUsageGroup.GRAB
				&& LivingComponentGrab.getEntityGrabbedBy(standEntity) != null) {
			return ConditionCheck.NEGATIVE;
		}
		return ConditionCheck.noMessage(standEntity.canAttackMelee());
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		double standAttackSpeed = StandUtil.getPhysicalStatValue((StandPower) context, StandStat.ATTACK_SPEED);
		float hits = StandStatFormulas.getBarrageHitsPerSecond(standAttackSpeed);
		if (hits <= 0) {
			return ConditionCheck.createNegative("stand_too_slow");
		}
		return super.checkSpecificConditions(context);
	}

	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower != null) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null && LivingComponentGrab.getEntityGrabbedBy(standEntity) != null) {
				return abilities.getContextVariationOrDisable(
						name(), "grab_barrage");
			}
		}
		return super.replaceWithSubAbility(context, abilities);
	}
	
	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && performer instanceof StandEntity stand) {
			// 1.16 PowerBaseImpl.tickHeldAction read the hold maximum again on every tick, so PERFORM has no fixed
			// end here: StandEntityBarrage.actionTick ends it (getHoldDurationMax)
			action.phasesLength.put(ActionPhase.PERFORM, Integer.MAX_VALUE);
			int recovery = getBarrageRecoveryTicks(stand);
			action.phasesLength.put(ActionPhase.RECOVERY, recovery);
			if (action instanceof StandEntityBarrage barrage) {
				barrage.recoveryByBaseRule = recovery;
			}
		}
	}

	/** 1.16 StandEntityMeleeBarrage.getHoldDurationMax: no limit while the user has Resolve, otherwise 20 + 5 x durability. */
	public static int getBarrageHoldDurationMax(StandEntity stand) {
		return isResolveBarrage(stand) ? Integer.MAX_VALUE : StandStatFormulas.getBarrageMaxDuration(stand.getDurability());
	}

	/** 1.16 StandEntityMeleeBarrage.getStandRecoveryTicks, read when the barrage enters RECOVERY. */
	public static int getBarrageRecoveryTicks(StandEntity stand) {
		return stand.isArmsOnlyMode() ? 0 : StandStatFormulas.getBarrageRecovery(stand.getAttackSpeed());
	}

	// 1.16 StandEntityMeleeBarrage: a hit on the Stand of 4 or more, taken by the user through the health link.
	@Override
	public boolean cancelHeldOnGettingAttacked(EntityActionInstance action, DamageSource dmgSource, float dmgAmount) {
		return dmgAmount >= 4.0F && dmgSource instanceof StandLinkDamageSource;
	}

	/**
	 * 1.16 StandEntityMeleeBarrage.checkStandTarget: a barrage's task keeps an entity only while it is the Stand's barrage
	 * clash opponent, whether the barrage was pressed at it or a hit met it ({@link StandEntityBarrage#actionTick}); the
	 * Stand then faces it and its offset turns to it until the clash ends.
	 */
	@Override
	protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
		return target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() != null
				&& standEntity.barrageClashOpponent().orElse(null) == target.getEntity();
	}

	// The barrage is held until it goes into recovery.
	@Override
	public boolean isActionHeld(EntityActionInstance action) {
		ActionPhase phase = action.getPhase();
		return phase != null && phase != ActionPhase.RECOVERY;
	}

	@Override
	public boolean isHoldAction() {
		return true;
	}

	// 1.16 StandEntityMeleeBarrage.stopOnHeavyAttack: an enemy heavy attack that hurts the Stand stops the barrage.
	@Override
	public boolean stopsOnHeavyAttack(EntityActionInstance action) {
		return action instanceof StandEntityBarrage;
	}

	public static boolean isDirectionalBarrage(StandEntity stand, @Nullable EntityActionInstance action) {
		return action instanceof StandEntityBarrage
				&& action.ability instanceof StandEntityBarrageAbility
				&& action.ability.getAbilityUsageCategory() == AbilityUsageGroup.COMBAT
				&& stand.getUser() != null
				&& !StandEntityPunchAbility.shouldRetainPunchTarget(stand, action.ability);
	}

	/**
	 * Where the barrage places its Stand from: 1.16 StandEntityMeleeBarrage.getOffsetFromUser took front3dOffset of the
	 * task target, which for the default barrage stayed empty but for a barrage-clash opponent a hit had met during the
	 * clash, so the Stand stood along its user's look (StandEntityAction.front3dOffset) whatever each hit aimed at; a
	 * locked barrage places from its target.
	 */
	static ActionTarget placementTarget(StandEntity stand, EntityActionInstance action, ActionTarget target, boolean directional) {
		if (!directional) {
			return target;
		}
		ActionTarget opponent = heldClashOpponent(stand, action);
		return !opponent.isEmpty(stand.level()) && opponent.sameTarget(target) ? target : ActionTarget.EMPTY;
	}

	/**
	 * The barrage-clash opponent the default barrage stays on. 1.16 StandEntityMeleeBarrage.checkStandTarget let only the
	 * clash opponent become the task target, and only when a hit's aim met it while it was the Stand's clash opponent
	 * (StandEntity.punch: setTaskTarget of what aimWithThisOrUser found, before the hit); aimWithThisOrUser then kept it
	 * while the Stand could reach it. So it is held once the barrage's last hit met it during the clash, while it is alive
	 * and within the Stand's reach. The server and every client decide it from the same synced data of the action (1.16
	 * sent the task target, TrStandTaskTargetPacket).
	 */
	private static ActionTarget heldClashOpponent(StandEntity stand, EntityActionInstance action) {
		return action instanceof StandEntityBarrage barrage ? barrage.heldClashOpponentAsSynced(stand) : ActionTarget.EMPTY;
	}

	// the stamina rule of a player's default barrage: paid on the server and ended before an unpaid hit
	private static boolean paysAsDefaultBarrage(StandEntity stand, boolean directional) {
		return directional && stand.getUser() instanceof Player && !stand.aimsWithoutUser();
	}

	public static ActionTarget clipDirectionalBarrageTarget(
			StandEntity stand, EntityActionInstance action, float partialTick) {
		LivingEntity user = stand.getUser();
		if (user == null || !(action.ability instanceof StandEntityAbility ability)) {
			return ActionTarget.EMPTY;
		}
		ActionTarget opponent = heldClashOpponent(stand, action);
		if (!opponent.isEmpty(stand.level())) {
			return opponent;
		}
		// 1.16 StandEntityMeleeBarrage.checkStandTarget refused every pressed target, so each hit's StandEntity.punch aimed
		// from the user's eyes at the user's reach with the precision ray, then checked the Stand's own reach
		return StandEntityPunchAbility.aimWithThisOrUser(stand, user, partialTick,
				entity -> ability.canTargetEntityForAiming(stand, entity) && StandEntityPunchAbility.canStandHit(stand, entity),
				StandEntityPunchAbility.CrosshairRule.NONE, true);
	}
	
	public static class StandEntityBarrage extends EntityActionInstance implements SyncedDataHolderExtended {
		// apart, so that a barrage can be made without a bootstrapped game (the add-on API smoke tests)
		private static final class SyncedData {
			// the entity the last hit met if it was the Stand's clash opponent when the hit was thrown, -1 for none: the
			// clash lock is decided from it
			private static final EntityDataAccessor<Integer> CLASH_HIT_ENTITY_ID = SynchedEntityData.defineId(
					StandEntityBarrage.class, EntityDataSerializers.INT);
		}
		public int hitsThisTick;
		private boolean delayedHit;
		private int delayedHits;
		// server: the RECOVERY length the base rule set; an ability that puts its own length keeps it
		private float recoveryByBaseRule = Float.NaN;
		/**
		 * Server: whether this tick's entity hit makes its impact sound (1.16 playPunchImpactSound: the hit landed,
		 * the target is not living, or a barrage clash parried it). Null when a hitEntity override did not say: it plays.
		 */
		@Nullable protected Boolean impactSoundDue;
		// server: what the barrage was pressed at, the user's crosshair (StandEntityPunchAbility.pressTarget)
		@Nullable private ActionTarget pressedAt;

		public StandEntityBarrage(EntityActionType ability) {
			super(ability);
		}

		// 1.16 StandEntityTask.makeServerSideTask: the press target, which the task keeps only as the clash opponent
		@Override
		protected ActionTarget pressedTaskTarget() {
			return pressedAt != null ? pressedAt : super.pressedTaskTarget();
		}

		@Override
		public void defineSynchedData(SynchedDataBuilder builder) {
			builder.define(SyncedData.CLASH_HIT_ENTITY_ID, -1);
		}

		@Override
		public <T> void onSyncedDataUpdated(T oldValue, T newValue, EntityDataAccessor<T> dataAccessor) {}

		/**
		 * @param clashOpponentAtHit the Stand's clash opponent when the hit was thrown, before the hit's own parry could
		 *        change it
		 */
		private void setPunchedTarget(@Nullable ActionTarget target, @Nullable Entity clashOpponentAtHit) {
			punchedTarget = target;
			Entity entity = target != null && target.getType() == ActionTarget.TargetType.ENTITY
					? target.copy().resolveEntityId(level()).getEntity() : null;
			synchedData.set(SyncedData.CLASH_HIT_ENTITY_ID,
					entity != null && entity == clashOpponentAtHit ? entity.getId() : -1);
		}

		/**
		 * The clash opponent this barrage holds (heldClashOpponent), on the server and on every client alike: the Stand's
		 * clash opponent, if the last hit met it while it was the clash opponent, as the server sent it.
		 */
		public ActionTarget heldClashOpponentAsSynced(StandEntity stand) {
			Entity opponent = stand.barrageClashOpponent().orElse(null);
			if (opponent == null || synchedData.get(SyncedData.CLASH_HIT_ENTITY_ID) != opponent.getId()) {
				return ActionTarget.EMPTY;
			}
			return StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(opponent));
		}

		/** The live hold maximum, checked on every PERFORM tick on the server. */
		protected int getHoldDurationMax(StandEntity stand) {
			return getBarrageHoldDurationMax(stand);
		}

		private boolean samplesRecoveryAtEntry(ActionPhase newPhase) {
			return newPhase == ActionPhase.RECOVERY && phase != null && phase != newPhase
					&& performer instanceof StandEntity && !performer.level().isClientSide()
					&& Float.compare(phasesLength.getFloat(ActionPhase.RECOVERY), recoveryByBaseRule) == 0;
		}

		@Override
		public void setPhase(ActionPhase newPhase, int tick) {
			boolean samplesEntry = samplesRecoveryAtEntry(newPhase);
			float previousLength = samplesEntry ? phasesLength.getFloat(newPhase) : 0;
			super.setPhase(newPhase, tick);
			if (samplesEntry && Float.compare(previousLength, phasesLength.getFloat(newPhase)) != 0) {
				syncPhaseChanges();
			}
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			Level level = performer.level();
			if (performer instanceof StandEntity stand) {
				double minOffset = Math.min(0.5, stand.getEffectiveRange());
				double maxOffset = Math.min(1.5, stand.getEffectiveRange());
				boolean directional = isDirectionalBarrage(stand, this);
				pressedAt = StandEntityPunchAbility.pressTarget(stand, getActionTargetSnapshot(level));
				// the default barrage keeps no pressed target (1.16 checkStandTarget): it starts at what its first hit aims at
				ActionTarget target = setActionTargetSnapshot(directional && !level.isClientSide()
						? clipDirectionalBarrageTarget(stand, this, 1.0F) : pressedAt);
				setStandFrontOffsetFromTarget(stand, placementTarget(stand, this, target, directional), minOffset, maxOffset);
				keepStandAimedAtTarget(target);
				aimAs = AimingEntity.STAND;
				StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
				if (directional) {
					setActionTargetSnapshot(null);
				}
				if (level.isClientSide()) {
					if (ClientGlobals.canHearStand(stand) && playsBarrageCry(level, stand)) {
						BooleanSupplier stopCry = () -> this.isOver() || this.phase != ActionPhase.PERFORM;
						if (usesEndlessBarrageCry(stand)) {
							StandCrySoundHandler.create(stand, getBarrageCrySound(), 1, 1, stopCry);
						}
						else {
							// 1.16 StandEntityAction.playSoundAtStand: one clip, cut off when the barrage leaves PERFORM
							ClientsideSoundsHelper.playNonVanillaClassSound(new EntityStoppableSoundInstance(
									ClientsideSoundsHelper.withStandSkin(getBarrageCrySound(), stand), stand.getSoundSource(),
									1, 1, stand, stand.getRandom().nextLong(), stopCry));
						}
					}
				}
				onBarrageSet(level, stand);
			}
		}
		
		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (samplesRecoveryAtEntry(newPhase) && performer instanceof StandEntity stand) {
				int recovery = getBarrageRecoveryTicks(stand);
				phasesLength.put(ActionPhase.RECOVERY, recovery);
				recoveryByBaseRule = recovery;
			}
			if (level().isClientSide() && performer instanceof StandEntity stand && stand.clientStuff != null) {
				stand.clientStuff.barrageHitSounds.setIsBarraging(newPhase == ActionPhase.PERFORM);
			}
		}

		@Override
		public void actionTick() {
			Level level = performer.level();
			if (performer instanceof StandEntity stand) {
				StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
			}

			if (getPhase() == ActionPhase.PERFORM && performer instanceof StandEntity stand) {
				boolean directional = isDirectionalBarrage(stand, this);
				boolean paysAsDefault = paysAsDefaultBarrage(stand, directional);
				StandPower standPower = StandPower.get(getPowerUser());
				// 1.16's power counted the hold maximum from the press, through the Stand's summon lock
				if (!level.isClientSide() && (curPhaseTick + getTicksHeldInSummonLock() >= getHoldDurationMax(stand)
						|| paysAsDefault && (standPower == null || !standPower.consumeStamina(4, true)))) {
					hitsThisTick = 0;
					stand.setBarrageHitsThisTick(0);
					setPunchedTarget(null, null);
					startRecovery();
					return;
				}
				// 1.16 sent tracking clients a kept task target the server changed (StandEntity.setTaskTarget); the
				// default barrage's own aim at each hit was never one, and every client aims it from its user's look
				HeldTargets held = !directional && !isGrabVariation() && !takesTargetsFromServer() ? heldTargets() : null;
				ActionTarget target = getPunchTarget(stand);
				if (target.isEmpty(level)) {
					standRotationTarget = null;
					aimAs = AimingEntity.CAMERA_ENTITY;
				}
				else {
					standRotationTarget = target.copy().resolveEntityId(level);
					aimAs = AimingEntity.STAND;
				}
				StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
				if (held != null) {
					syncTargetsToTrackingIfChanged(held);
				}
				hitsThisTick = getHitsThisTick(level, stand);
				stand.setBarrageHitsThisTick(hitsThisTick);
				
				if (hitsThisTick > 0 && level.isClientSide()) {
					if (ClientGlobals.canHearStand(stand)) {
						level.playLocalSound(stand.getX(), stand.getEyeY(), stand.getZ(), ClientsideSoundsHelper.withStandSkin(
								getBarrageSwingSound(), stand),
								stand.getSoundSource(), getBarrageSwingVolume(stand), getBarrageSwingPitch(stand), false);
					}
				}
				else if (hitsThisTick > 0) {
					boolean entityTarget = target.getType() == ActionTarget.TargetType.ENTITY;
					boolean playSound = StandEntityPunchAbility.playHitSound(target, level);
					Vec3 soundPos = playSound ? target.getCenterPos() : null;
					Entity clashOpponentAtHit = stand.barrageClashOpponent().orElse(null);
					stand.turnTowardsStrike(strikeAim(stand, target, directional));
					// 1.16 StandEntity.punch: each hit's target, nothing for a miss, went to the task before the hit
					setTaskTarget(target);

					boolean deflectedTarget = StandEntityPunchAbility.deflectSilverChariotProjectiles(stand, target);
					impactSoundDue = null;
					switch (target.getType()) {
						case ENTITY -> {
							if (!deflectedTarget) {
								hitEntity(target, level, stand);
							}
						}
						case BLOCK -> hitBlock(target, level, stand);
						default -> {}
					}
					// 1.16 StandEntityMeleeBarrage.playPunchImpactSound: sent after the hit, for an entity only if it landed
					if (entityTarget && impactSoundDue != null && !impactSoundDue) {
						playSound = false;
					}
					TrBarrageHitSoundPacket.send(stand, playSound, getBarrageHitSound(), soundPos);
					setPunchedTarget(target, clashOpponentAtHit);
				}
				if (standPower != null && (!paysAsDefault || level.isClientSide())) {
					standPower.consumeStamina(4, true);
				}
			}
		}

		// what the hit's aim met, the target it strikes or one out of the Stand's reach, by the rule its target was aimed with
		private ActionTarget strikeAim(StandEntity stand, ActionTarget target, boolean directional) {
			if (isGrabVariation()) {
				return target;
			}
			if (directional && ability instanceof StandEntityAbility standAbility) {
				return StandEntityPunchAbility.strikeAim(stand, target, StandEntityPunchAbility.CrosshairRule.NONE,
						entity -> standAbility.canTargetEntityForAiming(stand, entity) && StandEntityPunchAbility.canStandHit(stand, entity));
			}
			return StandEntityPunchAbility.strikeAim(stand, target, StandEntityPunchAbility.CrosshairRule.BLOCK_OR_ENTITY);
		}

		/*
		 * 1.16 StandEntityMeleeBarrage.getOffsetFromUser, asked on every tick of the task before it ticked: front3dOffset of
		 * the task target, which the hits had left: the clash opponent the last hit met (placementTarget), for a locked
		 * barrage the target its last hit was aimed at, or before its first hit what it was pressed at
		 */
		@Override
		public void updateStandOffset(StandEntity stand) {
			if (canRefreshFrontOffset(stand)) {
				boolean directional = isDirectionalBarrage(stand, this);
				// the port's locked barrage (standAttackTargetLock, D-core-50) aims again for its place in each recovery tick
				refreshFrontOffset(stand, directional ? heldClashOpponent(stand, this)
						: getPhase() == ActionPhase.RECOVERY ? placementTarget(stand, this, getPunchTarget(stand), false)
						: getActionTargetSnapshot(stand.level()));
			}
		}

		private boolean canRefreshFrontOffset(StandEntity stand) {
			return StandEntityBarrageAbility.canRefreshFrontOffset(this, stand);
		}

		private void refreshFrontOffset(StandEntity stand, ActionTarget target) {
			StandEntityBarrageAbility.refreshFrontOffset(this, stand, target,
					Math.min(0.5, stand.getEffectiveRange()), Math.min(1.5, stand.getEffectiveRange()));
		}
		
		// 1.16 StandEntityMeleeBarrage was a holdType Stand action
		@Override
		public boolean isHoldTypeStandAction() {
			return true;
		}

		@Override
		public void onButtonStopHold() {
			startRecovery();
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			if (performer instanceof StandEntity stand && newAction != this) {
				stand.resetBarrageParry();
				// 1.16 StandEntityMeleeBarrage.onTaskStopped compared actions, after clearTask had already dropped this Stand's
				// side of the clash (StandEntity.onActionSet): another action or none synced its end, the same barrage pressed
				// again left the synced id; neither told the opponent
				if (!level().isClientSide() && (newAction == null || newAction.ability != ability)) {
					stand.barrageClashStopped();
				}
				else if (stand.clientStuff != null) {
					stand.clientStuff.barrageHitSounds.setIsBarraging(false);
				}
			}
		}
		
		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			if (performer instanceof StandEntity stand && stand.barrageClashOpponent().isPresent()) {
				return true;
			}
			if (getPhase() == ActionPhase.RECOVERY) {
				LivingEntity user = getPowerUser();
				return user != null && user.hasEffect(ModStatusEffects.RESOLVE)
						|| canFollowUpBarrage(cancellingAbility);
			}
			return recoveryNow() <= 0 && cancellingAbility != this.ability;
		}

		// 1.16 StandEntityMeleeBarrage was not chainable: a clash or a Resolve-free recovery took any action but itself
		@Override
		public boolean refusesOwnAbilityPressedAgain() {
			return true;
		}

		/**
		 * 1.16 StandEntityAction.isCancelable read getStandRecoveryTicks live during PERFORM. Server: the base rule's
		 * recovery is read again now, as when RECOVERY starts; an ability that put its own length keeps it.
		 */
		private float recoveryNow() {
			float recovery = phasesLength.getFloat(ActionPhase.RECOVERY);
			if (performer instanceof StandEntity stand && !stand.level().isClientSide()
					&& Float.compare(recovery, recoveryByBaseRule) == 0) {
				return getBarrageRecoveryTicks(stand);
			}
			return recovery;
		}

		private static boolean canFollowUpBarrage(EntityActionType cancellingAbility) {
			return cancellingAbility instanceof StandEntityHeavyPunchAbility;
		}

		protected int getHitsThisTick(Level level, StandEntity stand) {
			int hitsThisTick = 0;
			int hitsPerSecond = StandStatFormulas.getBarrageHitsPerSecond(stand.getAttackSpeed());
			int extraTickSwings = hitsPerSecond / 20;
			hitsThisTick += extraTickSwings;
			hitsPerSecond -= extraTickSwings * 20;
			
			if (popDelayedHit()) {
				hitsThisTick++;
			}
			else if (hitsPerSecond > 0) {
				double ticksInterval = 20D / hitsPerSecond;
				int intTicksInterval = (int) ticksInterval;
				if (((int) curPhaseLength - curPhaseTick + delayedHits) % intTicksInterval == 0) {
					if (!level.isClientSide()) {
						double delayProb = ticksInterval - intTicksInterval;
						if (stand.getRandom().nextDouble() < delayProb) {
							delayHit();
						}
						else {
							hitsThisTick++;
						}
					}
				}
			}
			if (isGrabVariation() && hitsThisTick > 0) {
				hitsThisTick = Math.max(1, hitsThisTick / 2);
			}
			return hitsThisTick;
		}

		private void delayHit() {
			delayedHit = true;
			delayedHits++;
		}

		private boolean popDelayedHit() {
			if (delayedHit) {
				delayedHit = false;
				return true;
			}
			return false;
		}

		protected SoundEvent getBarrageSwingSound() {
			return ModSoundEvents.STAND_PUNCH_BARRAGE_SWING.get();
		}

		// 1.16 StandEntityMeleeBarrage.clTtickSwingSound
		protected float getBarrageSwingVolume(StandEntity stand) {
			return 0.25F;
		}

		protected float getBarrageSwingPitch(StandEntity stand) {
			return 1.8F - (float) stand.getAttackDamage() * 0.05F + stand.getRandom().nextFloat() * 0.2F;
		}

		@Nullable
		protected Holder<SoundEvent> getBarrageHitSound() {
			if (ability instanceof StandEntityBarrageAbility barrageAbility && barrageAbility.barrageHitSound != null) {
				return barrageAbility.barrageHitSound;
			}
			// 1.16 StandEntityMeleeBarrage.Builder: hitSound = STAND_PUNCH_LIGHT
			return ModSoundEvents.STAND_PUNCH_LIGHT;
		}

		protected SoundEvent getBarrageCrySound() {
			if (ability instanceof StandEntityBarrageAbility barrageAbility && barrageAbility.barrageCrySound != null) {
				return barrageAbility.barrageCrySound.value();
			}
			return ModSoundEvents.STAND_BARRAGE_CRY.get();
		}

		protected void onBarrageSet(Level level, StandEntity stand) {}

		protected boolean shouldPlayBarrageCry(Level level, StandEntity stand) {
			return true;
		}

		// 1.16 StandEntityAction.getPhaseStandSounds: an arms-only Stand plays only the sounds registered to play there
		protected boolean playsBarrageCry(Level level, StandEntity stand) {
			return (!stand.isArmsOnlyMode()
					|| ability instanceof StandEntityBarrageAbility barrageAbility && barrageAbility.barrageCryInArmsOnly)
					&& shouldPlayBarrageCry(level, stand);
		}

		// 1.16 StandEntityMeleeBarrage.playSoundAtStand: the cry repeats clip after clip only for a user with Resolve
		protected boolean usesEndlessBarrageCry(StandEntity stand) {
			return isResolveBarrage(stand);
		}
		
		protected ActionTarget getPunchTarget(StandEntity stand) {
			if (isDirectionalBarrage(stand, this)) {
				setActionTargetSnapshot(null);
				return clipDirectionalBarrageTarget(stand, this, 1.0F);
			}
			if (isGrabVariation()) {
				return StandEntityPunchAbility.validatePunchTarget(
						stand,
						new ActionTarget(
								LivingComponentGrab.getEntityGrabbedBy(stand)));
			}
			// what the locked barrage keeps is the server's, as a 1.16 task target was: a client holds what it sent
			if (takesTargetsFromServer()) {
				return getActionTargetSnapshot(stand.level());
			}
			ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(
					stand, getActionTargetSnapshot(stand.level()),
					StandEntityPunchAbility.shouldRetainPunchTarget(stand, ability));
			setActionTargetSnapshot(target);
			return target;
		}
		
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand) {
			Entity targetEntity = target.getMainEntity();
			if (targetEntity != null) {
				boolean resolve = isResolveBarrage(stand);
				DamageSource dmgSource = makeBarrageDamageSource(stand, targetEntity);
				float dmgAmount = StandStatFormulas.getBarrageHitDamage(stand.getAttackDamage(), stand.getPrecision()) * hitsThisTick;
				boolean hurt = standEntityAttack(stand, targetEntity, dmgSource, dmgAmount);
				if (hurt && resolve) {
					pinResolveBarrageTarget(targetEntity);
				}
				// a barrage clash parry took punches out of this tick's source (1.16 playPunchSound = true)
				boolean parried = dmgSource instanceof DamageSourceModified modified
						&& modified.jojo_ripples$barrageHitsCount() < hitsThisTick;
				impactSoundDue = hurt || parried || !(targetEntity instanceof LivingEntity);

				addBarrageFinisher(stand, dmgSource);
			}
		}

		/** Barrage source with the 1.16 per-target knockback factor (see getBarrageKnockbackMultiplier). */
		protected DamageSource makeBarrageDamageSource(StandEntity stand, Entity target) {
			DamageSource dmgSource = makeBarrageDamageSource();
			((DamageSourceModified) dmgSource).jojo_ripples$modifyKnockback(0, getBarrageKnockbackMultiplier(stand, target));
			return dmgSource;
		}

		/**
		 * 1.16 BarrageEntityPunch.afterAttack: 0.005 per punch still in the damage source
		 * after the target's barrage-clash parry; a fully parried tick still gives 0.005.
		 */
		public void addBarrageFinisher(StandEntity stand, DamageSource dmgSource) {
			int hitsLeft = dmgSource instanceof DamageSourceModified modified
					? modified.jojo_ripples$barrageHitsCount() : hitsThisTick;
			stand.addFinisherMeter(0.005f * Math.max(hitsLeft, 1));
		}

		protected DamageSource makeBarrageDamageSource() {
			DamageSource dmgSource = makePunchDamageSource();
			DamageSourceModified modified = (DamageSourceModified) dmgSource;
			modified.jojo_ripples$modifyKnockback(0, 0.1f);
			modified.jojo_ripples$setBarrageHitsCount(hitsThisTick);
			return dmgSource;
		}
		
		protected void hitBlock(ActionTarget target, Level level, StandEntity stand) {
			StandEntityPunchAbility.StandEntityPunch.breakStandBlock(level, target.getBlockPos(), stand, !isUserCreative());
		}

	}

	static boolean canRefreshFrontOffset(EntityActionInstance action, StandEntity stand) {
		return !stand.level().isClientSide() && stand.isFollowingUser() && !stand.isArmsOnlyMode()
				&& action.ability.getAbilityUsageCategory() != AbilityUsageGroup.GRAB
				&& LivingComponentGrab.getEntityGrabbedBy(stand) == null;
	}

	/**
	 * 1.16 StandEntityAction.front3dOffset while the action runs: the Stand stays at its distance from the current
	 * target, measured to an entity's own position (offsetToTarget, ActionTarget.getTargetPos) rather than to the
	 * point where an aim ray met its surface. Clients get the offset only when it changes.
	 */
	public static void refreshFrontOffset(EntityActionInstance action, StandEntity stand, ActionTarget target,
			double minOffset, double maxOffset) {
		if (!canRefreshFrontOffset(action, stand)) {
			return;
		}
		Vec3 previous = action.getSyncedStandOffset();
		ActionTarget offsetTarget = target;
		if (target.getType() == ActionTarget.TargetType.ENTITY && target.getClipPos().isPresent()) {
			offsetTarget = target.copy().resolveEntityId(stand.level()).withClipPos(Optional.empty());
		}
		action.setStandFrontOffsetFromTarget(stand, offsetTarget, minOffset, maxOffset);
		if (!Objects.equals(previous, action.getSyncedStandOffset())) {
			stand.offsetFromUser.syncToTracking();
		}
	}

	/**
	 * 1.16 BarrageEntityPunch knockback factor: attack damage * 0.0075 of the normal knockback,
	 * none on a Stand or while the user has Resolve.
	 */
	public static float getBarrageKnockbackMultiplier(StandEntity stand, Entity target) {
		if (target instanceof StandEntity || isResolveBarrage(stand)) {
			return 0;
		}
		return Mth.clamp((float) stand.getAttackDamage() * 0.0075F, 0, 1);
	}

	public static boolean isResolveBarrage(StandEntity stand) {
		LivingEntity user = stand.getUser();
		return user != null && user.hasEffect(ModStatusEffects.RESOLVE);
	}

	public static final ResourceLocation RESOLVE_BARRAGE_NO_GRAVITY_ID = JojoMod.resLoc("resolve_barrage_no_gravity");
	public static final int RESOLVE_BARRAGE_NO_GRAVITY_TICKS = 3;
	// server thread only: target -> ticks of its own left before gravity returns
	private static final Map<LivingEntity, Integer> RESOLVE_NO_GRAVITY = new WeakHashMap<>();

	/**
	 * 1.16 BarrageEntityPunch.doHit with a Resolve user: a hit target hangs in the air
	 * (LivingUtilCap.setNoGravityFor(3)) and a mob drops its path.
	 */
	public static void pinResolveBarrageTarget(Entity target) {
		if (!(target instanceof LivingEntity living) || living.level().isClientSide()) {
			return;
		}
		AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
		if (gravity != null) {
			RESOLVE_NO_GRAVITY.put(living, RESOLVE_BARRAGE_NO_GRAVITY_TICKS);
			if (!gravity.hasModifier(RESOLVE_BARRAGE_NO_GRAVITY_ID)) {
				Vec3 motion = living.getDeltaMovement();
				living.setDeltaMovement(motion.x, Math.max(motion.y, 0), motion.z);
				gravity.addTransientModifier(new AttributeModifier(RESOLVE_BARRAGE_NO_GRAVITY_ID,
						-1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (living instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	@EventBusSubscriber(modid = JojoMod.MOD_ID)
	public static final class ResolveBarrageNoGravity {
		private ResolveBarrageNoGravity() {}

		// 1.16 LivingUtilCap.tickNoGravityModifier, server side, on the target's own tick
		@SubscribeEvent
		public static void onEntityTick(EntityTickEvent.Pre event) {
			if (event.getEntity().level().isClientSide() || RESOLVE_NO_GRAVITY.isEmpty()
					|| !(event.getEntity() instanceof LivingEntity living)) {
				return;
			}
			Integer ticks = RESOLVE_NO_GRAVITY.get(living);
			if (ticks == null) {
				return;
			}
			if (ticks > 1) {
				RESOLVE_NO_GRAVITY.put(living, ticks - 1);
				return;
			}
			RESOLVE_NO_GRAVITY.remove(living);
			AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
			if (gravity != null) {
				gravity.removeModifier(RESOLVE_BARRAGE_NO_GRAVITY_ID);
			}
		}
	}

}
