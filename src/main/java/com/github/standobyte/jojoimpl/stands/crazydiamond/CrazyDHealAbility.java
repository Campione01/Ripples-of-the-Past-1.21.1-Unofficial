package com.github.standobyte.jojoimpl.stands.crazydiamond;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import org.spongepowered.include.com.google.common.base.Objects;

import com.github.standobyte.jojo.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityLingeringSoundInstance;
import com.github.standobyte.jojo.client.sound.sounds.EntityStoppableSoundInstance;
import com.github.standobyte.jojo.entityattachment.syncheddata.SynchedDataBuilder;
import com.github.standobyte.jojo.entityattachment.syncheddata.SyncedDataHolderExtended;
import com.github.standobyte.jojo.init.ModEntityDataSerializers;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mechanics.BleedingEffect;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.network.s2c.TrBarrageHitSoundPacket;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil.StandAndUserEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTargetAim;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.StatusEffectUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

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
	private static final ActionAnimIdentifier REPAIR_ITEM_ANIM = ActionAnimIdentifier.getOrCreate("itemFix", false);
	private static final ActionAnimIdentifier BARRAGE_ANIM = ActionAnimIdentifier.getOrCreate("barrage", false);
	public static final float HEAL_STAMINA_COST_TICK = 1F;
	private static final double ENTITY_TARGET_RANGE = 8.0D;
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
		ActionTarget aimedTarget = getAimedTarget(user, level);
		if (aimedTarget.getType() == TargetType.ENTITY
				&& !HitResultUtil.isTargetWithinRange(aimedTarget, user, level, ENTITY_TARGET_RANGE, ENTITY_TARGET_RANGE)) {
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
		if (!(targetEntity instanceof LivingEntity || targetEntity instanceof ModEntityWithHealth || targetEntity instanceof Boat)) {
			return ConditionCheck.createNegative("heal_target");
		}
		return StandAbilityStamina.check(context, HEAL_STAMINA_COST_TICK);
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
		if (user == null) {
			return ActionTarget.EMPTY;
		}
		ActionTarget target = getAimedTarget(user, level);
		if (isValidHealEntityTarget(user, target, level)) {
			return target;
		}
		target = HitResultUtil.clip(
				user.getEyePosition(),
				user.getLookAngle(),
				ENTITY_TARGET_RANGE,
				ENTITY_TARGET_RANGE,
				level,
				CrazyDHealAbility::canHealEntityTarget,
				user,
				0);
		return target.getType() == TargetType.ENTITY ? target : ActionTarget.EMPTY;
	}

	private static ActionTarget getAimedTarget(LivingEntity user, Level level) {
		ActionTargetAim aim = LivingComponentAction.getAim(user);
		ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
		return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
	}

	private static boolean canHealEntityTarget(Entity target) {
		return canPickEntityForAiming(target)
				&& (target instanceof LivingEntity || target instanceof ModEntityWithHealth || target instanceof Boat);
	}

	private static boolean isValidHealEntityTarget(LivingEntity user, ActionTarget target, Level level) {
		if (target.getType() != TargetType.ENTITY || target.isEmpty(level)) {
			return false;
		}
		Entity targetEntity = target.getEntity();
		return targetEntity != null
				&& canHealEntityTarget(targetEntity)
				&& targetEntity.getBoundingBox().distanceToSqr(user.getEyePosition()) <= ENTITY_TARGET_RANGE * ENTITY_TARGET_RANGE;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return getHealingAnim(action, IDLE_ANIM);
	}

	public static ActionAnimIdentifier getHealingAnim(EntityActionInstance action, ActionAnimIdentifier fallback) {
		if (action instanceof HealingAction healingAction) {
			HealingAction.HealResult.Synched heal = healingAction.synchedData.get(HealingAction.HEAL_RESULT);
			if (heal.isHealing) {
				return heal.barrageVisuals ? BARRAGE_ANIM : REPAIR_ITEM_ANIM;
			}
		}
		return fallback;
	}

	public static class HealingAction extends EntityActionInstance implements SyncedDataHolderExtended {
		public BleedingTimer bleedingTimer;
		private ActionTarget actionTarget = ActionTarget.EMPTY;
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
		
		@Override
		public void onButtonStopHold() {
			startRecovery();
		}
		
		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
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

		protected boolean consumeStaminaTick(StandEntity standEntity) {
			StandPower standPower = standEntity != null ? standEntity.getUserPower() : null;
			return StandAbilityStamina.consume(ability, standPower, staminaCostTick(), true);
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			Level level = level();
			StandEntity standEntity = performer instanceof StandEntity s ? s : null;
			
			HealResult.Synched curHealing;
			if (!level.isClientSide()) {
				if (!consumeStaminaTick(standEntity)) {
					startRecovery();
					return;
				}
				ActionTarget target = getActionTarget(level);
				HealResult healResult = restoreTarget(target, standEntity);
				curHealing = healResult.synched;
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
			userWalkSpeed = getPhase() == ActionPhase.PERFORM ? 0.5F : 1.0F;
		}
		
		public void onHealResultUpdated(HealResult.Synched old, HealResult.Synched cur) {
			if (cur.isHealing && (old == null || !old.isHealing)) {
				healingVisualsStartTick = curPhaseTick;
			}
			else if (!cur.isHealing) {
				healingVisualsStartTick = -1;
			}

			Level level = level();
			cur.target.resolveEntityId(level);
			setBarrageHitSoundsActive(getPhase() == ActionPhase.PERFORM && cur.isHealing && cur.barrageVisuals);
			boolean healingStateChanged = old == null || cur.isHealing != old.isHealing;
			boolean targetChanged = old == null || !cur.target.equals(old.target);
			StandEntity standEntity = performer instanceof StandEntity __ ? __ : null;
			
			if (healingStateChanged) {
				if (cur.isHealing) {
					if (standEntity != null) {
						updateHealingStandOffset(standEntity, cur.target);
					}
				}
				else if (old == null || old.isHealing) {
					if (standEntity != null) {
						standEntity.offsetFromUser.resetToIdle();
					}
				}
			}

			if (targetChanged) {
				if (cur.isHealing && !healingStateChanged && standEntity != null) {
					updateHealingStandOffset(standEntity, cur.target);
				}
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

		private void updateHealingStandOffset(StandEntity standEntity, ActionTarget target) {
			double range = standEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
			if (!setStandFrontOffsetFromTarget(standEntity, target, 0, range)) {
				standEntity.offsetFromUser.resetToIdle();
			}
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

					if (targetEntity instanceof LivingEntity targetLiving) {
						return healLivingEntity(level, targetLiving, crazyDiamond, result);
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
