package com.github.standobyte.jojoimpl.stands._entitybase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityLingeringSoundInstance;
import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Moveset;
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
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.subsystems.ServerBlockDestroyTracker;
import com.github.standobyte.jojo.subsystems.ServerBlockDestroyTracker.BlockBreakResult;
import com.github.standobyte.jojo.subsystems.entity_grab.LivingComponentGrab;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.OOPMoment;
import com.github.standobyte.v1_21_4_stuff.missingmethods._EntitySelector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class StandEntityPunchAbility extends StandEntityAbility {
	@Nullable private Holder<SoundEvent> punchImpactSound;
	@Nullable private Holder<SoundEvent> standCrySound;

	public StandEntityPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		this(abilityType, abilityId, StandEntityPunch::new);
	}

	protected StandEntityPunchAbility(AbilityType<?> abilityType, AbilityId abilityId,
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		super(abilityType, abilityId, createActionObj);
		usageGroup = AbilityUsageGroup.COMBAT;
		setDefaultPhaseLength(ActionPhase.WINDUP, 4);
		setDefaultPhaseLength(ActionPhase.PERFORM, 2);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 20);
		noFinisherBarDecay = true;
		standAutoSummonMode(AutoSummonMode.MAIN_ARM);
		partsRequired(StandPart.ARMS);
	}

	public StandEntityPunchAbility punchImpactSound(Holder<SoundEvent> punchImpactSound) {
		this.punchImpactSound = punchImpactSound;
		return this;
	}

	public StandEntityPunchAbility standCrySound(Holder<SoundEvent> standCrySound) {
		this.standCrySound = standCrySound;
		return this;
	}

	public StandEntityPunchAbility initIsGrabVariation() {
		usageGroup = AbilityUsageGroup.GRAB;
		return this;
	}

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
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower != null) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null && LivingComponentGrab.getEntityGrabbedBy(standEntity) != null) {
				return abilities.getContextVariationOrDisable(
						name(), "grab_punch");
			}
		}

		Ability punch = getComboPunch(standPower);
		if (punch != null) return punch;
		
		return super.replaceWithSubAbility(context, abilities);
	}
	
	
	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity standUser, LivingEntity standEntity) {
		super.initActionFromConfig(action, level, standUser, standEntity);
		if (!level.isClientSide() && standEntity instanceof StandEntity stand) {
			action.phasesLength.put(ActionPhase.WINDUP, StandStatFormulas.getLightAttackWindup(
					stand.getAttackSpeed(), stand.getFinisherMeter(), stand.guardCounter(), 
					stand.getCurStandAction() == null || stand.getCurStandAction().ability != this));
			float lightRecovery = StandStatFormulas.getLightAttackRecovery(stand.getAttackSpeed(), stand.getFinisherMeter());
			action.phasesLength.put(ActionPhase.PERFORM, lightRecovery);
			action.phasesLength.put(ActionPhase.RECOVERY, lightRecovery * (stand.isArmsOnlyMode() ? 2 : 4));
		}
	}
	
	public static class StandEntityPunch extends EntityActionInstance {
		protected boolean playedSwingSound;
		protected boolean playedStandCrySound;

		public StandEntityPunch(EntityActionType ability) {
			super(ability);
		}
		
		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			playedStandCrySound = prevAction != null;
			if (performer instanceof StandEntity stand) {
				if (stand.isArmsOnlyMode() && stand.getPunchingHand() == InteractionHand.OFF_HAND) {
					stand.setArmsOnlyMode(true, true);
				}
				stand.alternateHands();
				double minOffset = Math.min(0.5, stand.getEffectiveRange());
				double maxOffset = Math.min(2, stand.getMaxRange());
				ActionTarget target = captureActionTargetFromAim(stand);
				setStandFrontOffsetFromTarget(stand, target, minOffset, maxOffset);
				keepStandAimedAtTarget(target);
			}
			aimAs = AimingEntity.STAND;
		}
		
		@Override
		public void actionTick() {
			Level level = performer.level();
			if (level.isClientSide() && !(playedSwingSound && playedStandCrySound)
					&& performer instanceof StandEntity stand && ClientGlobals.canHearStand(stand)) {
				if (!playedSwingSound) {
					// how many ticks are left before the start of the 'perform' phase (when actionPerformStart() is called)
					int ticksDiff = (int) (calcFullTicks(ActionPhase.PERFORM, 0) - getFullTicksPassed());
					if (ticksDiff <= 2) {
						SoundEvent swingSound = getPunchSwingSound(stand);
						if (swingSound != null) {
							level.playLocalSound(stand.getX(), stand.getEyeY(), stand.getZ(), ClientsideSoundsHelper.withStandSkin(
									swingSound, stand), 
									stand.getSoundSource(), getPunchSwingVolume(stand), getPunchSwingPitch(stand), false);
						}
						playedSwingSound = true;
					}
				}
				
				if (!playedStandCrySound) {
					if (shouldPlayStandCrySound(level, stand)) {
						SoundEvent standCrySound = getStandCrySound(stand);
						if (standCrySound != null) {
							ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
									standCrySound, stand), 
									stand.getSoundSource(), 1, 1, stand, stand.level()));
						}
					}
					playedStandCrySound = true;
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
					
					Holder<SoundEvent> impactSound = getPunchImpactSound(target);
					if (impactSound != null && playHitSound(target, level)) {
						StandUtil.broadcastSound((ServerLevel) level, target.getCenterPos(), 
								impactSound, true, standPower, 
								stand.getSoundSource(), 1, 1);
					}
					
					stand.addFinisherMeter(0.1f);
					boolean deflectedTarget = deflectSilverChariotProjectiles(stand, target);
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
					standPower.consumeStamina(10);
				}
				/*
				 *  During the punch, the Stand entity keeps rotating towards the target (keepStandAimedAtTarget()).
				 *  Additionally, when we set aimAs == AimingEntity.STAND, 
				 *  effectively this makes the Stand locked on the target entity after the punch,
				 *  because the Stand keeps aiming at *its* direction rather than the player's.
				 *  Here, if the Stand does not hit an entity, we reset this field to AimingEntity.CAMERA_ENTITY, 
				 *  resetting the aim back to the default.
				 */
				if (target.getType() == TargetType.ENTITY) {
					standRotationTarget = target;
				}
				else {
					aimAs = AimingEntity.CAMERA_ENTITY;
				}
			}
		}
		
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand) {
			Entity targetEntity = target.getMainEntity();
			if (targetEntity instanceof LivingEntity targetLiving) {
                DamageSource dmgSource = makePunchDamageSource();
				((DamageSourceModified) dmgSource).jojo_ripples$modifyKnockback(stand.guardCounter(), 1);
				float dmgAmount = StandStatFormulas.getLightAttackDamage(stand.getAttackDamage());
				if (standEntityAttack(stand, targetLiving, dmgSource, dmgAmount)) {
					stand.addFinisherMeter(0.1f);
				}
			}
		}

		@Nullable
		protected Holder<SoundEvent> getPunchImpactSound(ActionTarget target) {
			if (ability instanceof StandEntityPunchAbility punchAbility && punchAbility.punchImpactSound != null) {
				return punchAbility.punchImpactSound;
			}
			return ModSoundEvents.STAND_PUNCH_LIGHT;
		}

		protected boolean shouldPlayStandCrySound(Level level, StandEntity stand) {
			return !stand.isArmsOnlyMode()
					&& stand.getFinisherMeter() <= 0
					&& getPunchTarget(stand).getType() == TargetType.ENTITY;
		}

		@Nullable
		protected SoundEvent getStandCrySound(StandEntity stand) {
			if (ability instanceof StandEntityPunchAbility punchAbility && punchAbility.standCrySound != null) {
				return punchAbility.standCrySound.value();
			}
			return null;
		}

		@Nullable
		protected SoundEvent getPunchSwingSound(StandEntity stand) {
			return ModSoundEvents.STAND_PUNCH_SWING.get();
		}

		protected float getPunchSwingVolume(StandEntity stand) {
			return 1.0F;
		}

		protected float getPunchSwingPitch(StandEntity stand) {
			return 0.9F + stand.getRandom().nextFloat() * 0.2F;
		}
		
		protected void hitBlock(ActionTarget target, Level level, StandEntity stand) {
			hitBlockTarget(target, level, stand, !isUserCreative());
		}
		
		public static void hitBlockTarget(ActionTarget target, Level level, StandEntity stand, boolean dropBlock) {
			BlockPos blockPos = target.getBlockPos();
			BlockState blockState = level.getBlockState(blockPos);
			
			double standStrength = stand.getAttackDamage();
			float blockDamage = (float) standStrength * StandStatFormulas.getBlockMiningEfficiency(standStrength) * 0.05f;
			float blockHardness = stand.getBlockHardnessForStandBreak(blockState, level, blockPos);
			if (blockHardness < 0) {
				return;
			}
			
			BlockBreakResult blockPunch = ServerBlockDestroyTracker.addBlockDestroyProgress((ServerLevel) level, stand, 
					blockPos, blockState, blockDamage / blockHardness);
			if (blockPunch.progressNew < 1) return;
			
			level.destroyBlock(blockPos, dropBlock, stand);
			blockDamage -= blockPunch.progressAdded * blockHardness;

			// add cracks to the blocks around
			if (blockDamage > 0) {
				float aroundDamageTotal = blockDamage;
				List<BlockPosState> blocksAround = new ArrayList<>(25);
				BlockPos.MutableBlockPos nearbyPos = new BlockPos.MutableBlockPos();
				int centerX = blockPos.getX();
				int centerY = blockPos.getY();
				int centerZ = blockPos.getZ();
				for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
					int manhattanDist = Math.abs(x) + Math.abs(y) + Math.abs(z);
					if (manhattanDist > 0 && manhattanDist < 3) {
						nearbyPos.set(centerX + x, centerY + y, centerZ + z);
						BlockState nearbyState = level.getBlockState(nearbyPos);
						if (!nearbyState.isEmpty() && nearbyState.getDestroySpeed(level, nearbyPos) > 0) {
							blocksAround.add(new BlockPosState(nearbyPos.immutable(), nearbyState, manhattanDist));
						}
					}
				}
				if (!blocksAround.isEmpty()) {
					Collections.shuffle(blocksAround);
					for (BlockPosState block : blocksAround) {
						blockHardness = stand.getBlockHardnessForStandBreak(block.blockState, level, block.blockPos);
						if (blockHardness < 0) {
							continue;
						}
						float multiplier = (0.5f + 0.5f * OOPMoment.RANDOM.nextFloat()) / block.manhattanDist;
						float damageToDeal = Math.min(blockDamage, aroundDamageTotal / blocksAround.size() * multiplier);
						blockPunch = ServerBlockDestroyTracker.addBlockDestroyProgress((ServerLevel) level, stand, 
								block.blockPos, block.blockState, damageToDeal / blockHardness, false);
						blockDamage -= blockPunch.progressAdded * blockHardness;
						if (blockDamage <= 0) break;
					}
				}
			}
		}
		protected static record BlockPosState(BlockPos blockPos, BlockState blockState, int manhattanDist) {}
		
		protected ActionTarget getPunchTarget(StandEntity stand) {
			if (isGrabVariation()) {
				return new ActionTarget(LivingComponentGrab.getEntityGrabbedBy(stand));
			}
			ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(stand, getActionTargetSnapshot(stand.level()));
			setActionTargetSnapshot(target);
			return target;
		}
		
		
		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return getPhase() == ActionPhase.RECOVERY;
		}
		
		@Override
		public boolean savePrevPoseForAnimTransition(EntityActionInstance prevAction) {
			return prevAction instanceof StandEntityPunch;
		}
		
	}
	
	
	public static ActionTarget aimAtPunchTarget(StandEntity stand) {
		return HitResultUtil.clipEntityLook(stand, entity -> StandEntityPunchAbility.canStandHit(stand, entity), 0);
	}

	public static ActionTarget validatePunchTarget(StandEntity stand, ActionTarget target) {
		Level level = stand.level();
		target = target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
		if (target.isEmpty(level)) {
			return ActionTarget.EMPTY;
		}
		if (!HitResultUtil.isTargetWithinRange(target, stand, level,
				stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
				stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))) {
			return ActionTarget.EMPTY;
		}
		if (target.getType() == TargetType.ENTITY) {
			Entity targetEntity = target.getMainEntity();
			return targetEntity != null && targetEntity.isAlive()
					&& StandEntityPunchAbility.canStandHit(stand, targetEntity) ? target : ActionTarget.EMPTY;
		}
		return target;
	}

	public static ActionTarget getFreshPunchTarget(StandEntity stand, ActionTarget preferredTarget) {
		Level level = stand.level();
		ActionTarget target = validatePunchTarget(stand, preferredTarget);
		if (!target.isEmpty(level)) {
			return target;
		}

		var standAim = LivingComponentAction.getAim(stand);
		if (standAim != null) {
			target = validatePunchTarget(stand, standAim.getTarget());
			if (!target.isEmpty(level)) {
				return target;
			}
		}

		LivingEntity user = stand.getUser();
		var userAim = user != null && !stand.isManuallyControlled() ? LivingComponentAction.getAim(user) : null;
		if (userAim != null) {
			target = validatePunchTarget(stand, userAim.getTarget());
			if (!target.isEmpty(level)) {
				return target;
			}
		}

		return ActionTarget.EMPTY;
	}
	
	public static boolean canStandHit(StandEntity stand, Entity target) {
		return target.isAlive() && canStandPick(stand, target) && stand.canAttackEntity(target);
	}
	
	// TODO make stand entities not pickable, add a separate OR predicate to CAN_BE_PICKED
	public static boolean canStandPick(StandEntity stand, Entity target) {
		return _EntitySelector.CAN_BE_PICKED.test(target);
	}
	
	public static boolean playHitSound(ActionTarget target, Level level) {
		if (target.isEmpty(level)) {
			return false;
		}
		return switch (target.getType()) {
			case ENTITY -> true;
			case BLOCK -> {
				BlockPos blockPos = target.getBlockPos();
				BlockState blockState = level.getBlockState(blockPos);
				yield blockState.getDestroySpeed(level, blockPos) != 0;
			}
			default -> false;
		};
	}
	
	public static boolean deflectSilverChariotProjectiles(StandEntity stand, ActionTarget target) {
		boolean deflectedTarget = target.getType() == TargetType.ENTITY
				&& stand.deflectSilverChariotTargetProjectile(target.getMainEntity());
		stand.deflectSilverChariotNearbyProjectiles();
		return deflectedTarget;
	}
	
	
	// 
	
	protected static List<String> punchNamesBuffer = new ArrayList<>();
	@Nullable
	protected Ability getComboPunch(StandPower standPower) {
		if (standPower == null) return null;
		
		Moveset moveset = standPower.getMoveset();
		StandEntity standEntity = standPower.getSummonedStandEntity();
		
		if (this.isSubAbility) return null;
		
		punchNamesBuffer.clear();
		String baseName = this.name();
		if (baseName == null) {
			return null;
		}
		punchNamesBuffer.add(baseName);
		for (int i = 2; ; i++) {
			String comboPunchName = baseName + i;
			if (moveset.getAbility(comboPunchName) != null) {
				punchNamesBuffer.add(comboPunchName);
			}
			else break;
		}
		
		int startFromPunch = 0;
		if (standEntity != null) {
			AbilityId curAbility = LivingComponentAction.getComponent(standEntity).comboString.getLast();
			if (curAbility != null) {
				String actionName = curAbility.nameInMoveset();
				for (int i = 0; i < punchNamesBuffer.size(); i++) {
					if (punchNamesBuffer.get(i).equals(actionName)) {
						startFromPunch = i + 1;
						break;
					}
				}
			}
		}
		
		int size = punchNamesBuffer.size();
		for (int i = 0; i < size; i++) {
			int index = (startFromPunch + i) % size;
			String nextPunchName = punchNamesBuffer.get(index);
			Ability nextPunch = moveset.getAbility(nextPunchName);
			if (nextPunch != null && nextPunch.isAbilityAvailable(standPower)) {
				return nextPunch;
			}
		}
		
		return null;
	}

}
