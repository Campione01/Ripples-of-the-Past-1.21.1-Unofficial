package com.github.standobyte.jojoimpl.stands._entitybase;

import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.barrage.StandCrySoundHandler;
import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.network.s2c.TrBarrageHitSoundPacket;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.AbilityUsageGroup;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil.StandStat;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.subsystems.ServerBlockDestroyTracker;
import com.github.standobyte.jojo.subsystems.entity_grab.LivingComponentGrab;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

public class StandEntityBarrageAbility extends StandEntityAbility {
	@Nullable private Holder<SoundEvent> barrageHitSound;
	@Nullable private Holder<SoundEvent> barrageCrySound;

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

	public StandEntityBarrageAbility initIsGrabVariation() {
		usageGroup = AbilityUsageGroup.GRAB;
		return this;
	}

	@Override
	protected ConditionCheck checkStandEntityConditions(StandPower standPower, StandEntity standEntity) {
		ConditionCheck check = super.checkStandEntityConditions(standPower, standEntity);
		if (!check.isPositive()) {
			return check;
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
				return abilities.getContextVariation("grab_barrage");
			}
		}
		return super.replaceWithSubAbility(context, abilities);
	}
	
	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, 
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && performer instanceof StandEntity stand) {
			if (powerUser != null && powerUser.hasEffect(ModStatusEffects.RESOLVE)) {
				action.phasesLength.put(ActionPhase.PERFORM, Integer.MAX_VALUE);
			}
			else {
				action.phasesLength.put(ActionPhase.PERFORM, StandStatFormulas.getBarrageMaxDuration(stand.getDurability()));
			}
			action.phasesLength.put(ActionPhase.RECOVERY, stand.isArmsOnlyMode() ? 0 : StandStatFormulas.getBarrageRecovery(stand.getAttackSpeed()));
		}
	}
	
	public static class StandEntityBarrage extends EntityActionInstance {
		public int hitsThisTick;
		private boolean delayedHit;
		private int delayedHits;

		public StandEntityBarrage(EntityActionType ability) {
			super(ability);
		}
		
		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			Level level = performer.level();
			if (performer instanceof StandEntity stand) {
				double minOffset = Math.min(0.5, stand.getEffectiveRange());
				double maxOffset = Math.min(1.5, stand.getEffectiveRange());
				ActionTarget target = captureActionTargetFromAim(stand);
				setStandFrontOffsetFromTarget(stand, target, minOffset, maxOffset);
				keepStandAimedAtTarget(target);
				aimAs = AimingEntity.STAND;
				if (level.isClientSide()) {
					if (ClientGlobals.canHearStand(stand) && !stand.isArmsOnlyMode() && shouldPlayBarrageCry(level, stand)) {
						StandCrySoundHandler.create(stand, getBarrageCrySound(), 1, 1,
								() -> this.isOver() || this.phase != ActionPhase.PERFORM);
					}
				}
				onBarrageSet(level, stand);
			}
		}
		
		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = newPhase == ActionPhase.PERFORM ? 0.15F : 1.0F;
			if (level().isClientSide() && performer instanceof StandEntity stand && stand.clientStuff != null) {
				stand.clientStuff.barrageHitSounds.setIsBarraging(newPhase == ActionPhase.PERFORM);
			}
		}

		@Override
		public void actionTick() {
			Level level = performer.level();
			
			if (getPhase() == ActionPhase.PERFORM && performer instanceof StandEntity stand) {
				hitsThisTick = getHitsThisTick(level, stand);
				stand.setBarrageHitsThisTick(hitsThisTick);
				
				StandPower standPower = StandPower.get(getPowerUser());
				if (hitsThisTick > 0 && level.isClientSide()) {
					if (ClientGlobals.canHearStand(stand)) {
						level.playLocalSound(stand.getX(), stand.getEyeY(), stand.getZ(), ClientsideSoundsHelper.withStandSkin(
								getBarrageSwingSound(), stand),
								stand.getSoundSource(), getBarrageSwingVolume(stand), getBarrageSwingPitch(stand), false);
					}
				}
				else if (hitsThisTick > 0) {
					ActionTarget target = getPunchTarget(stand);

					tickBarrageSound(stand, target);
					
					boolean deflectedTarget = StandEntityPunchAbility.deflectSilverChariotProjectiles(stand, target);
					switch (target.getType()) {
						case ENTITY -> {
							if (!deflectedTarget) {
								hitEntity(target, level, stand);
							}
						}
						case BLOCK -> hitBlock(target, level, stand);
						default -> {}
					}
					punchedTarget = target;
				}
				if (standPower != null) {
					standPower.consumeStamina(4, true);
				}
			}
		}
		
		@Override
		public void onButtonStopHold() {
			startRecovery();
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			if (performer instanceof StandEntity stand && newAction != this) {
				stand.resetBarrageParry();
				if (!level().isClientSide()) {
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
			return phasesLength.getFloat(ActionPhase.RECOVERY) <= 0 && cancellingAbility != this.ability;
		}

		private static boolean canFollowUpBarrage(EntityActionType cancellingAbility) {
			return cancellingAbility instanceof StandEntityHeavyPunchAbility;
		}

		protected void tickBarrageSound(StandEntity stand, ActionTarget target) {
			boolean playSound = StandEntityPunchAbility.playHitSound(target, stand.level());
			TrBarrageHitSoundPacket.send(stand, playSound, getBarrageHitSound(),
					playSound ? target.getCenterPos() : null);
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

		protected float getBarrageSwingVolume(StandEntity stand) {
			return 1.0F;
		}

		protected float getBarrageSwingPitch(StandEntity stand) {
			return 1.0F;
		}

		@Nullable
		protected Holder<SoundEvent> getBarrageHitSound() {
			if (ability instanceof StandEntityBarrageAbility barrageAbility && barrageAbility.barrageHitSound != null) {
				return barrageAbility.barrageHitSound;
			}
			return ModSoundEvents.STAND_PUNCH_BARRAGE;
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
		
		protected ActionTarget getPunchTarget(StandEntity stand) {
			if (isGrabVariation()) {
				return new ActionTarget(LivingComponentGrab.getEntityGrabbedBy(stand));
			}
			ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(stand, getActionTargetSnapshot(stand.level()));
			setActionTargetSnapshot(target);
			return target;
		}
		
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand) {
			Entity targetEntity = target.getMainEntity();
			if (targetEntity != null) {
				DamageSource dmgSource = makeBarrageDamageSource();
				float dmgAmount = StandStatFormulas.getBarrageHitDamage(stand.getAttackDamage(), stand.getPrecision()) * hitsThisTick;
				standEntityAttack(stand, targetEntity, dmgSource, dmgAmount);
				
				stand.addFinisherMeter(0.005f * hitsThisTick);
			}
		}

		protected DamageSource makeBarrageDamageSource() {
			DamageSource dmgSource = makePunchDamageSource();
			DamageSourceModified modified = (DamageSourceModified) dmgSource;
			modified.jojo_ripples$modifyKnockback(0, 0.1f);
			modified.jojo_ripples$setBarrageHitsCount(hitsThisTick);
			return dmgSource;
		}
		
		protected void hitBlock(ActionTarget target, Level level, StandEntity stand) {
			BlockPos blockPos = target.getBlockPos();
			BlockState blockState = level.getBlockState(blockPos);
			
			double standStrength = stand.getAttackDamage();
			double standSpeed = stand.getAttackSpeed();
			
			float blockHardnessForStand = stand.getBlockHardnessForStandBreak(blockState, level, blockPos);
			if (blockHardnessForStand >= 0) {
				float standEfficiency = StandStatFormulas.getBarrageBlockMiningEfficiency(standStrength, standSpeed);
				float destroyProgress = standEfficiency / (blockHardnessForStand * 100);
				
				boolean breakBlock = blockHardnessForStand == 0 || ServerBlockDestroyTracker.addBlockDestroyProgress((ServerLevel) level, stand, 
						blockPos, blockState, destroyProgress).progressNew >= 1;
				if (breakBlock) {
					boolean dropBlock = !isUserCreative();
					level.destroyBlock(blockPos, dropBlock, stand);
				}
			}
			
			if (blockHardnessForStand != 0) {
				if (curPhaseTick % 2 == 0) {
					SoundType blockSounds = blockState.getSoundType(level, blockPos, stand);
					level.playSound(null, blockPos, blockSounds.getHitSound(), SoundSource.BLOCKS, 
							(blockSounds.getVolume() + 1.0F) / 8.0F, blockSounds.getPitch() * 0.5F);
				}
			}
		}
		
	}

}
