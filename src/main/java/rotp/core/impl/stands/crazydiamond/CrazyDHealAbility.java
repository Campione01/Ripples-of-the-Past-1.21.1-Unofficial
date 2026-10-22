package rotp.core.impl.stands.crazydiamond;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import org.spongepowered.include.com.google.common.base.Objects;

import rotp.core.api.healing.CrazyDiamondRestoreExtensions;
import rotp.core.api.healing.ExternalRestoreContext;
import rotp.core.api.healing.ExternalRestoreResult;
import rotp.core.api.healing.LivingRestoreContext;
import rotp.core.api.healing.RestoreAugmentation;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.client.ClientGlobals;
import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityLingeringSoundInstance;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import rotp.core.entityattachment.syncheddata.SynchedDataBuilder;
import rotp.core.entityattachment.syncheddata.SyncedDataHolderExtended;
import rotp.core.init.ModEntityDataSerializers;
import rotp.core.init.ModParticles;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.BleedingEffect;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.network.s2c.TrBarrageHitSoundPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandUtil.StandAndUserEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.functions.StatusEffectUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;

public class CrazyDHealAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier IDLE_ANIM = ActionAnimIdentifier.getOrCreate("idle", true);
	private static final ActionAnimIdentifier BARRAGE_ANIM = ActionAnimIdentifier.getOrCreate("barrage", false);
	public static final float HEAL_STAMINA_COST_TICK = 1F;
	private static final double HEAL_SPEED_MULTIPLIER = 1;

	public CrazyDHealAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, HealingAction::new);
		partsRequired(StandPart.ARMS);
		setButtonHoldPhase(ActionPhase.PERFORM);
		standAutoSummonMode(AutoSummonMode.MAIN_ARM);
	}
	
	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}

		LivingEntity user = context.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}

		Level level = user.level();
		ActionTarget aimedTarget = getAimedTarget(user, crosshairAimingEntity(user, summonedStand(user)), level);
		if (aimedTarget.getType() == TargetType.ENTITY
				&& !ActionTargetRange.isTargetWithinRange(ActionTargetRange.standPerformer(user), aimedTarget, level)) {
			return ConditionCheck.createNegative("target_too_far");
		}
		ActionTarget target = getCurrentEntityTarget(user, level);
		if (target.getType() != TargetType.ENTITY) {
			return ConditionCheck.createNegative("heal_target");
		}

		Entity targetEntity = target.getEntity();
		if (targetEntity == null) {
			return ConditionCheck.createNegative("heal_target");
		}
		if (targetEntity.is(user)) {
			return ConditionCheck.createNegative("cd_heal_self");
		}
		if (!canHealEntityTarget(targetEntity)) {
			return ConditionCheck.createNegative("heal_target");
		}
		return check;
	}

	@Override
	public boolean canTargetEntityForAiming(StandEntity standEntity, Entity target) {
		return canHealEntityTarget(target);
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (action instanceof HealingAction healingAction) {
			ActionTarget target = getCurrentEntityTarget(powerUser, level);
			healingAction.setActionTarget(target);
			if (!target.isEmpty(level)) {
				action.standRotationTarget = target.copy();
				action.aimAs = AimingEntity.STAND;
			}
		}
	}

	protected static ActionTarget getCurrentEntityTarget(LivingEntity user, Level level) {
		return getCrosshairEntityTarget(user, level, CrazyDHealAbility::canHealEntityTarget, true);
	}

	/*
	 * 1.16 took Heal's and Revert's target under the crosshair (InputHandler.pickMouseTarget): the vanilla pick, which the
	 * client's aim carries, and only where that met nothing and the Stand was out, EntityStandType.clientHitResult's
	 * precision ray from the camera entity (the Stand under manual control) as far as StandEntity.getAimDistance: the
	 * user's REACH_DISTANCE, or the Stand's own under manual control. That ray (StandEntity.precisionRayTrace) took any
	 * entity, by the Stand's precision boxes and its player / hostile / other priority, and the action refused it only
	 * then, so an entity it does not work on hid one behind. That ray is cast here, on either side, from the same eyes;
	 * without the user's aim assist setting, the crosshair line instead, as for the punches.
	 */
	static ActionTarget getCrosshairEntityTarget(LivingEntity user, Level level, Predicate<Entity> worksOn) {
		return getCrosshairEntityTarget(user, level, worksOn, false);
	}

	/**
	 * @param pressedBlockGivesWay the press of a 1.16 StandEntityAction (Heal, Revert): a block without a collision shape
	 *        under the crosshair gave way to the collision ray of overrideVanillaMouseTarget
	 *        (StandEntityAbility#pastBlockWithoutCollision), so a mob behind a torch is the target, on the client and
	 *        its HUD too; false for a StandEntityActionModifier's (Misshaping), which had no such rule
	 */
	static ActionTarget getCrosshairEntityTarget(LivingEntity user, Level level, Predicate<Entity> worksOn,
			boolean pressedBlockGivesWay) {
		if (user == null) {
			return ActionTarget.EMPTY;
		}
		StandEntity stand = summonedStand(user);
		LivingEntity aiming = crosshairAimingEntity(user, stand);
		if (isClientEntityMissing(aiming, level)) {
			return ActionTarget.EMPTY;
		}
		ActionTarget target = getAimedTarget(user, aiming, level);
		boolean wallsHideEntities = StandSkillWalls.wallsHideEntities(aiming);
		if (target.getType() == TargetType.EMPTY && stand != null) {
			double reach = aiming == stand
					? stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) : StandEntityPunchAbility.userAimReach(user, stand);
			target = StandEntityPunchAbility.aimAssist(user)
					? StandPrecisionRay.trace(aiming, stand, reach, 0.0D, true, wallsHideEntities)
					: StandPrecisionRay.crosshair(aiming, stand, reach, true, wallsHideEntities);
		}
		if (pressedBlockGivesWay) {
			target = pastBlockWithoutCollision(user, target, wallsHideEntities);
		}
		return isValidEntityTarget(user, target, level, worksOn) ? target : ActionTarget.EMPTY;
	}

	// the camera entity of 1.16's crosshair: the Stand under manual control, otherwise its user
	private static LivingEntity crosshairAimingEntity(LivingEntity user, @Nullable StandEntity stand) {
		return stand != null && stand.isManuallyControlled() ? stand : user;
	}

	@Nullable
	private static StandEntity summonedStand(LivingEntity user) {
		StandPower power = StandPower.get(user);
		return power != null ? power.getSummonedStandEntity() : null;
	}

	/**
	 * Server: the aim the client sent is an entity the server no longer has. 1.16 PowerBaseImpl.checkTarget emptied the
	 * whole target of such a press, so no ray of the server's own takes another entity in its place.
	 */
	static boolean isClientEntityMissing(LivingEntity aiming, Level level) {
		ActionTargetAim aim = !level.isClientSide() ? LivingComponentAction.getAim(aiming) : null;
		return aim != null && aim.isClientEntityMissing();
	}

	// the aims go only from the user's client to the server, so that client reads the ones it sends
	private static ActionTarget getAimedTarget(LivingEntity user, LivingEntity aiming, Level level) {
		ActionTargetAim aim = level.isClientSide() && user == ClientProxy.getClientPlayer()
				? (aiming == user ? ClientsideAim.playerAim : ClientsideAim.standAim)
				: LivingComponentAction.getAim(aiming);
		ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
		return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
	}

	private static boolean canHealEntityTarget(Entity target) {
		return canPickEntityForAiming(target)
				&& (target instanceof LivingEntity
						|| target instanceof ModEntityWithHealth
						|| target instanceof Boat
						|| CrazyDiamondRestoreExtensions
								.canTarget(target));
	}

	private static boolean isValidEntityTarget(LivingEntity user, ActionTarget target, Level level,
			Predicate<Entity> worksOn) {
		if (target.getType() != TargetType.ENTITY || target.isEmpty(level)) {
			return false;
		}
		Entity targetEntity = target.getEntity();
		return targetEntity != null
				&& canPickEntityForAiming(targetEntity)
				&& worksOn.test(targetEntity)
				// 1.16 Action.checkRangeAndTarget: from the Stand while it is out, less without a line of sight
				&& ActionTargetRange.isEntityWithinRange(ActionTargetRange.standPerformer(user), targetEntity,
						ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return getHealingAnim(action, IDLE_ANIM);
	}

	public static ActionAnimIdentifier getHealingAnim(EntityActionInstance action, ActionAnimIdentifier fallback) {
		// 1.16 StandEntityAction.getStandPose: the barrage's pose whenever barrageVisuals held, healing or not; else the
		// action's own (the heal had none, StandAction.Builder's IDLE; the revert ITEM_FIX_POSE): the fallback
		if (action instanceof HealingAction healingAction
				&& healingAction.synchedData.get(HealingAction.HEAL_RESULT).barrageVisuals) {
			return BARRAGE_ANIM;
		}
		return fallback;
	}

	public static class HealingAction extends EntityActionInstance implements SyncedDataHolderExtended {
		public BleedingTimer bleedingTimer;
		private ActionTarget actionTarget = ActionTarget.EMPTY;
		private boolean placedInFront;
		private int healingVisualsStartTick = -1;

		public HealingAction(EntityActionType ability) {
			super(ability);
		}

		protected void setActionTarget(ActionTarget target) {
			this.actionTarget = target != null ? target.copy() : ActionTarget.EMPTY;
		}

		protected ActionTarget getActionTarget(Level level) {
			return actionTarget.resolveEntityId(level);
		}

		// the entity the server picked and keeps (1.16 targetRequirement ENTITY), which the healing Stand turns its offset to
		@Override
		public ActionTarget getTaskTarget(Level level) {
			return heldTarget(actionTarget, level);
		}

		@Override
		@Nullable
		protected ActionTarget storedTaskTarget() {
			return actionTarget.getType() != TargetType.EMPTY ? actionTarget : null;
		}

		// it aims as itself while it holds an entity, as initActionFromConfig and onHealResultUpdated set it
		@Override
		protected void storeTaskTarget(@Nullable ActionTarget target) {
			setActionTarget(target);
			aimAs = target != null ? AimingEntity.STAND : AimingEntity.CAMERA_ENTITY;
		}

		@Override
		protected boolean keepsAsTaskTarget(StandEntity stand, ActionTarget target) {
			return target.getType() == TargetType.ENTITY && keepsEntityTarget(target, level());
		}

		// the previous action's target this one took over (1.16 StandEntity.setTask) is the entity it works on
		@Override
		protected void onTaskTargetInherited(ActionTarget target) {
			setActionTarget(target);
			standRotationTarget = target.copy().resolveEntityId(level());
			aimAs = AimingEntity.STAND;
		}
		
		// 1.16 CrazyDiamondHeal and CrazyDiamondPreviousState was a holdType Stand action
		@Override
		public boolean isHoldTypeStandAction() {
			return true;
		}

		@Override
		public void onButtonStopHold() {
			startRecovery();
		}
		
		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}

		// 1.16 CrazyDiamondHeal and CrazyDiamondPreviousState were not chainable
		@Override
		public boolean refusesOwnAbilityPressedAgain() {
			return true;
		}

		@Override
		public void extractAnim(LivingAnimState animVariables, LivingEntity performer, float partialTick) {
			super.extractAnim(animVariables, performer, partialTick);
			HealResult.Synched heal = synchedData.get(HEAL_RESULT);
			if (heal.isHealing && healingVisualsStartTick >= 0) {
				float localPhaseTime = Math.max(0, animVariables.phaseTime - healingVisualsStartTick);
				float localPhaseLength = Math.max(1, getAnimPhaseLength() - healingVisualsStartTick);
				animVariables.phaseTime = localPhaseTime;
				animVariables.phaseCompletion = Mth.clamp(localPhaseTime / localPhaseLength, 0, 1);
				animVariables.time = Math.max(0, animVariables.time - healingVisualsStartTick);
			}
		}
		
		protected float staminaCostTick() {
			return HEAL_STAMINA_COST_TICK;
		}

		protected void consumeStaminaTick(StandEntity standEntity) {
			StandPower standPower = standEntity != null ? standEntity.getUserPower() : null;
			StandAbilityStamina.consume(ability, standPower, staminaCostTick(), true);
		}

		/**
		 * 1.16 CrazyDiamondHeal.standCanTickPerform: without an entity target the Stand task neither ticked the heal nor
		 * paid its tick cost. Revert, whose 1.16 action had no such condition, pays on its item path too.
		 */
		protected boolean paysTickWithoutEntityTarget() {
			return false;
		}

		/**
		 * 1.16 StandEntityTask.keepTarget, every server tick: an entity target that is the Stand itself, dead with
		 * deathTime >= 20 (a player who has not respawned) or out of the action's range is dropped.
		 */
		protected boolean keepsEntityTarget(ActionTarget target, Level level) {
			Entity targetEntity = target.getEntity();
			LivingEntity user = getPowerUser();
			return targetEntity != null && targetEntity != performer
					&& !(targetEntity instanceof LivingEntity living && living.deathTime >= 20)
					&& user != null && isValidEntityTarget(user, target, level, CrazyDHealAbility::canHealEntityTarget);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			if (performer instanceof StandEntity standEntity) {
				placeStandByTarget(standEntity);
			}
		}

		@Override
		public void actionTick() {
			if (!level().isClientSide()) {
				releaseEntityTargetNoLongerKept();
			}
			StandEntity standEntity = performer instanceof StandEntity s ? s : null;
			if (standEntity != null) {
				placeStandByTarget(standEntity);
			}
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			Level level = level();

			HealResult.Synched curHealing;
			if (!level.isClientSide()) {
				// 1.16 StandEntityTask paid each perform tick and ignored the result: an empty bar stays at 0
				ActionTarget target = getActionTarget(level);
				if (paysTickWithoutEntityTarget() || target.getType() == TargetType.ENTITY && !target.isEmpty(level)) {
					consumeStaminaTick(standEntity);
				}
				HealResult healResult = restoreTarget(target, standEntity);
				curHealing = healResult.synched;
				curHealing.barrageVisuals |= showsBarrageVisuals(target);
				if (healResult.hpForExp > 0) {
					StandPower userPower = standEntity.getUserPower();
					if (userPower != null) {
						userPower.addExp(healResult.hpForExp * 0.1f);
					}
				}
				synchedData.set(HEAL_RESULT, curHealing);
				if (curHealing.target.getType() == TargetType.ENTITY && curHealing.target.getEntity() != null) {
					playBarrageVisualsSound(level, standEntity, curHealing.target);
				}
			}
			
			else {
				curHealing = synchedData.get(HEAL_RESULT);
				if (curHealing.isHealing && curHealing.target.getType() == TargetType.ENTITY) {
					Entity targetEntity = curHealing.target.getEntity();
					if (targetEntity != null) {
						if (targetEntity instanceof LivingEntity targetLiving) {
							StandAndUserEntity standAndUser = StandUtil.getStandAndUser(targetLiving);
							
							if (standAndUser.standUser != null) 
								addParticlesAround(standAndUser.standUser);
							if (standAndUser.standEntity != null) 
								addParticlesAround(standAndUser.standEntity);
							
							if (curHealing.deathTime != HealResult.Synched.NO_DEATH_TIME_CHANGE) {
								if (standAndUser.standUser != null) standAndUser.standUser.deathTime = curHealing.deathTime;
								if (standAndUser.standEntity != null) standAndUser.standEntity.deathTime = curHealing.deathTime;
							}
						}
						else {
							addParticlesAround(targetEntity);
						}
					}
				}
			}
		}
		
		/*
		 * 1.16 StandEntityTask.tick, on every server tick of the task in every phase and before the action ticked: an entity
		 * keepTarget no longer passed was dropped (setTaskTarget(EMPTY)), sent to tracking clients, and the Stand neither
		 * stood in front toward it nor turned to it any more
		 */
		private void releaseEntityTargetNoLongerKept() {
			Level level = level();
			ActionTarget target = getActionTarget(level);
			if (target.getType() == TargetType.ENTITY && !keepsEntityTarget(target, level)) {
				setTaskTarget(null);
			}
		}

		public void onHealResultUpdated(HealResult.Synched old, HealResult.Synched cur) {
			if (cur.isHealing && (old == null || !old.isHealing)) {
				healingVisualsStartTick = curPhaseTick;
			}
			else if (!cur.isHealing) {
				healingVisualsStartTick = -1;
			}

			Level level = level();
			// the target the server dropped (1.16 setTaskTarget(EMPTY) reached the clients as well)
			if (level.isClientSide() && old != null && old.target.getType() == TargetType.ENTITY
					&& cur.target.getType() != TargetType.ENTITY) {
				setActionTarget(ActionTarget.EMPTY);
			}
			cur.target.resolveEntityId(level);
			setBarrageHitSoundsActive(getPhase() == ActionPhase.PERFORM && cur.isHealing && cur.barrageVisuals);
			boolean targetChanged = old == null || !cur.target.equals(old.target);

			if (targetChanged) {
				if (cur.target.getType() == TargetType.ENTITY) {
					standRotationTarget = cur.target;
					aimAs = AimingEntity.STAND;
					
					Entity targetEntity = cur.target.getEntity();
					LivingEntity user = getPowerUser();
					if (user == targetEntity && user != null && user.level().isClientSide() && user == ClientProxy.getClientPlayer()) {
						ClientProxy.setOverlayMessage(ConditionCheck.message("cd_heal_self"), false);
					}
				}
				else {
					standRotationTarget = ActionTarget.EMPTY;
					aimAs = AimingEntity.CAMERA_ENTITY;
				}
			}
		}

		/*
		 * 1.16 CrazyDiamondHeal and CrazyDiamondPreviousState.getOffsetFromUser: offsetToTarget(task target, 0,
		 * getMaxEffectiveRange) in front while the task holds an entity, healing it or not; the action's own offset
		 * otherwise. Both sides, from the target each holds.
		 */
		private void placeStandByTarget(StandEntity standEntity) {
			Entity targetEntity = getTaskTarget(level()).getEntity();
			// the entity's own position as 1.16 getTargetPos took it, not where the aim met its box
			boolean inFront = targetEntity != null && setStandFrontOffsetFromTarget(standEntity, new ActionTarget(targetEntity),
					0, standEntity.getEffectiveRange());
			if (!inFront && placedInFront) {
				standEntity.offsetFromUser.resetToIdle();
			}
			placedInFront = inFront;
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			ActionPhase previousPhase = this.phase;
			if (newPhase == ActionPhase.PERFORM) {
				playFixLoopSounds();
				setBarrageHitSoundsActive(synchedData.get(HEAL_RESULT).isHealing && synchedData.get(HEAL_RESULT).barrageVisuals);
			}
			else if (previousPhase == ActionPhase.PERFORM) {
				playFixEndedSound();
				setBarrageHitSoundsActive(false);
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			if (phase == ActionPhase.PERFORM) {
				playFixEndedSound();
			}
			setBarrageHitSoundsActive(false);
		}

		private void setBarrageHitSoundsActive(boolean active) {
			Level level = level();
			if (level.isClientSide() && performer instanceof StandEntity standEntity && standEntity.clientStuff != null) {
				standEntity.clientStuff.barrageHitSounds.setIsBarraging(active);
			}
		}

		private void playFixLoopSounds() {
			Level level = level();
			if (!level.isClientSide() || !(performer instanceof StandEntity standEntity) || !ClientGlobals.canHearStand(standEntity)) {
				return;
			}
			ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
					ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), standEntity),
					standEntity.getSoundSource(), 1, 1, standEntity, level));

			ClientsideSoundsHelper.playNonVanillaClassSound(new EntityStoppableSoundInstance(ClientsideSoundsHelper.withStandSkin(
					ModSoundEvents.CRAZY_DIAMOND_FIX_LOOP.get(), standEntity),
					standEntity.getSoundSource(), 1, 1, true, standEntity, level.random.nextLong(),
					() -> this.isOver() || this.phase != ActionPhase.PERFORM));
		}

		private void playFixEndedSound() {
			Level level = level();
			if (!level.isClientSide() || !(performer instanceof StandEntity standEntity) || !ClientGlobals.canHearStand(standEntity)) {
				return;
			}
			ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
					ModSoundEvents.CRAZY_DIAMOND_FIX_ENDED.get(), standEntity),
					standEntity.getSoundSource(), 1, 1, standEntity, level));
		}

		public HealResult restoreTarget(ActionTarget target, StandEntity crazyDiamond) {
			HealResult result = new HealResult();
			result.synched.target = target;
			
			if (target.getType() == TargetType.ENTITY) {
				Entity targetEntity = target.getEntity();
				if (targetEntity != null) {
					Level level = targetEntity.level();
					LivingEntity user = getPowerUser();
					
					if (targetEntity == performer || targetEntity == user) {
						return result;
					}

					if (!level.isClientSide() && user != null) {
						ExternalRestoreResult external =
								CrazyDiamondRestoreExtensions
										.restoreExternal(
												new ExternalRestoreContext(
														targetEntity,
														user,
														crazyDiamond));
						if (external.handled()) {
							result.synched.isHealing =
									external.healingActive();
							result.synched.barrageVisuals =
									external.barrageVisuals();
							result.hpForExp =
									external.hpForExperience();
							return result;
						}
					}

					if (targetEntity instanceof LivingEntity targetLiving) {
						HealResult livingResult = healLivingEntity(
								level,
								targetLiving,
								crazyDiamond,
								result);
						if (!level.isClientSide() && user != null) {
							RestoreAugmentation augmentation =
									CrazyDiamondRestoreExtensions
											.afterLivingRestoreAttempt(
													new LivingRestoreContext(
															targetLiving,
															user,
															crazyDiamond,
															livingResult
																	.synched
																	.isHealing,
															livingResult
																	.synched
																	.barrageVisuals,
															livingResult
																	.hpForExp));
							livingResult.synched.isHealing |=
									augmentation.healingActive();
							livingResult.synched.barrageVisuals |=
									augmentation.barrageVisuals();
							livingResult.hpForExp +=
									augmentation.hpForExperience();
						}
						return livingResult;
					}

					else if (targetEntity instanceof ModEntityWithHealth toHeal) {
						if (toHeal.getHealth() < toHeal.getMaxHealth()) {
							if (!level.isClientSide()) {
								float hpToHeal = toHeal.getMaxHealth() / 40 * (float) healSpeedWithConfig(crazyDiamond);
								toHeal.setHealth(toHeal.getHealth() + hpToHeal);
								result.hpForExp = hpToHeal;
							}
							result.synched.isHealing = true;
							return result;
						}
					}

					else if (targetEntity instanceof Boat toHeal) {
						if (toHeal.getDamage() > 0) {
							if (!level.isClientSide()) {
								float hpToHeal = (float) healSpeedWithConfig(crazyDiamond);
								toHeal.setDamage(Math.max(toHeal.getDamage() - hpToHeal, 0));
								result.hpForExp = hpToHeal;
							}
							result.synched.isHealing = true;
							return result;
						}
					}
				}
			}
			return result;
		}

		private void playBarrageVisualsSound(Level level, StandEntity standEntity, ActionTarget target) {
			if (level.isClientSide() || standEntity == null) {
				return;
			}
			if (target.getType() == TargetType.ENTITY && target.getEntity() != null) {
				TrBarrageHitSoundPacket.send(standEntity, true, ModSoundEvents.STAND_PUNCH_BARRAGE, target.getCenterPos());
			}
			else {
				TrBarrageHitSoundPacket.send(standEntity, false, null, null);
			}
		}

		public HealResult healLivingEntity(Level level, LivingEntity entity, StandEntity crazyDiamond, HealResult result) {
			LivingEntity toHeal = StandUtil.getStandUser(entity);
			if (entity.deathTime > 0) {
				// boolean resolveEffect = standEntity.getUser() != null && ResolveModeEffect.isInResolveEffect(standEntity.getUser());
				// if (!resolveEffect && entity.deathTime > 1 || entity.deathTime > 15) {
				// 	return false;
				// }
				if (entity.deathTime > 15) {
					return result;
				}

				toHeal.deathTime = Math.max(toHeal.deathTime - 2, 0);
				entity.deathTime = toHeal.deathTime;
				result.synched.deathTime = toHeal.deathTime;
				
				if (!level.isClientSide() && toHeal.deathTime <= 0 && toHeal.getHealth() <= 0) {
					toHeal.setHealth(0.001F);
					JojoModUtil.onLivingResurrect(toHeal);
				}
				result.synched.isHealing = true;
				result.synched.barrageVisuals = useOriginalBarrageVisuals(entity);
				result.hpForExp = 1;
			}
			else {
				float healingSpeed = (float) healSpeedWithConfig(crazyDiamond);
				float health = toHeal.getHealth();
				
				if (toHeal.getHealth() < toHeal.getMaxHealth()) {
					result.synched.isHealing = true;
					result.synched.barrageVisuals |= useOriginalBarrageVisuals(entity);
					if (!level.isClientSide()) {
						float hpToHeal = 0.5F * healingSpeed;
						toHeal.setHealth(health + hpToHeal);
						result.hpForExp = hpToHeal;
					}
				}
				
				MobEffectInstance bleeding = toHeal.getEffect(ModStatusEffects.BLEEDING);
				if (bleeding != null) {
					result.synched.isHealing = true;
					result.synched.barrageVisuals |= useOriginalBarrageVisuals(entity);
					if (!level.isClientSide()) {
						int reduceBleedingTime = (int) (20 / healingSpeed);
						if (bleedingTimer == null) bleedingTimer = new BleedingTimer(reduceBleedingTime);
						if (bleedingTimer.tick(reduceBleedingTime)) {
							StatusEffectUtil.reduceEffect(toHeal, ModStatusEffects.BLEEDING, 0, 1);
						}
						result.hpForExp += healingSpeed;
					}
				}
			}

			return result;
		}

		/**
		 * 1.16 CrazyDiamondHeal.barrageVisuals (:196-208), asked every frame whether the heal healed or not: its user under
		 * Resolve, or its task's target a living entity at half its health or less.
		 */
		public boolean showsBarrageVisuals(ActionTarget target) {
			LivingEntity user = getPowerUser();
			if (user != null && ResolveModeEffect.getResolveEffectLvl(user) >= 0) {
				return true;
			}
			return target.getType() == TargetType.ENTITY && target.getEntity() instanceof LivingEntity targetLiving
					&& useOriginalBarrageVisuals(targetLiving);
		}

		private boolean useOriginalBarrageVisuals(LivingEntity target) {
			LivingEntity user = getPowerUser();
			if (user != null && ResolveModeEffect.getResolveEffectLvl(user) >= 0) {
				return true;
			}
			float maxHealthWithoutBleeding = BleedingEffect.getMaxHealthWithoutBleeding(target);
			return maxHealthWithoutBleeding > 0 && target.getHealth() / maxHealthWithoutBleeding <= 0.5F;
		}


		public static final EntityDataAccessor<HealResult.Synched> HEAL_RESULT = SynchedEntityData.defineId(HealingAction.class, ModEntityDataSerializers.CD_HEAL_RESULT.get());
		public static class HealResult {
			public final Synched synched = new Synched();
			public float hpForExp;
			
			public static class Synched {
				public ActionTarget target;
				public boolean isHealing;
				public boolean barrageVisuals;
				public int deathTime;
				
				public static final int NO_DEATH_TIME_CHANGE = 67;
				
				public Synched() {
					this(ActionTarget.EMPTY, false, false, NO_DEATH_TIME_CHANGE);
				}
				
				public Synched(ActionTarget target, boolean isHealing, boolean barrageVisuals, int deathTime) {
					this.target = target;
					this.isHealing = isHealing;
					this.barrageVisuals = barrageVisuals;
					this.deathTime = deathTime;
				}
				
				@Override
				public boolean equals(Object obj) {
					if (obj.getClass() == HealResult.Synched.class) {
						HealResult.Synched other = (HealResult.Synched) obj;
						return this.target.equals(other.target) 
								&& this.isHealing == other.isHealing
								&& this.barrageVisuals == other.barrageVisuals
								&& this.deathTime == other.deathTime;
					}
					return false;
				}
				
				@Override
				public int hashCode() {
					return Objects.hashCode(target, isHealing, barrageVisuals, deathTime);
				}
				
				public static final StreamCodec<? super RegistryFriendlyByteBuf, HealResult.Synched> STREAM_CODEC = StreamCodec.composite(
						ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID, heal -> heal.target, 
						ByteBufCodecs.BOOL, heal -> heal.isHealing, 
						ByteBufCodecs.BOOL, heal -> heal.barrageVisuals, 
						ByteBufCodecs.VAR_INT, heal -> heal.deathTime, 
						HealResult.Synched::new);
				
				public HealResult.Synched copy() {
					return new HealResult.Synched(this.target.copy(), this.isHealing, this.barrageVisuals, this.deathTime);
				}
			}
		}
		
		@Override
		public void defineSynchedData(SynchedDataBuilder builder) {
			builder.define(HEAL_RESULT, new HealResult.Synched());
		}
		
		@Override
		public <T> void onSyncedDataUpdated(T oldValue, T newValue, EntityDataAccessor<T> dataKey) {
			if (dataKey == HEAL_RESULT) {
				onHealResultUpdated((HealResult.Synched) oldValue, (HealResult.Synched) newValue);
			}
		}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(buf, actionTarget);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			actionTarget = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(buf);
		}
		
		
		protected double healSpeedWithConfig(StandEntity standEntity) {
			return crazyDRestorationSpeed(standEntity) * HEAL_SPEED_MULTIPLIER;
		}

	}

	public static double crazyDRestorationSpeed(StandEntity standEntity) {
		return standEntity.getAttackSpeed() * 0.05F + 0.55;
	}

	public static void addParticlesAround(Entity entity) {
		Level level = entity.level();
		if (level.isClientSide() && ClientGlobals.canSeeStands) {
			int particlesCount = Math.max(Mth.ceil(entity.getBbWidth() * (entity.getBbHeight() * 2 * entity.getBbHeight())), 1);
			for (int i = 0; i < particlesCount; i++) {
				level.addParticle(ModParticles.CD_RESTORATION.get(), entity.getRandomX(1), entity.getRandomY(), entity.getRandomZ(1), 0, 0, 0);
			}
		}
	}

	public static class BleedingTimer {
		public int timer;

		public BleedingTimer(int reduceEffectTime) {
			this.timer = reduceEffectTime / 2;
		}

		public boolean tick(int reduceEffectTime) {
			if (timer++ >= reduceEffectTime) {
				timer = 0;
				return true;
			}
			return false;
		}
	}

}
