package rotp.core.impl.stands._entitybase;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.barrage.StandCrySoundHandler;
import rotp.core.customobjects.DamageSourceModified;
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
import rotp.core.subsystems.ServerBlockDestroyTracker;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
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
			if (isDirectionalBarrage(stand, action)
					|| powerUser != null && powerUser.hasEffect(ModStatusEffects.RESOLVE)) {
				action.phasesLength.put(ActionPhase.PERFORM, Integer.MAX_VALUE);
			}
			else {
				action.phasesLength.put(ActionPhase.PERFORM, StandStatFormulas.getBarrageMaxDuration(stand.getDurability()));
			}
			action.phasesLength.put(ActionPhase.RECOVERY, stand.isArmsOnlyMode() ? 0 : StandStatFormulas.getBarrageRecovery(stand.getAttackSpeed()));
		}
	}

	// 1.16 StandEntityMeleeBarrage: a hit on the Stand of 4 or more, taken by the user through the health link.
	@Override
	public boolean cancelHeldOnGettingAttacked(EntityActionInstance action, DamageSource dmgSource, float dmgAmount) {
		return dmgAmount >= 4.0F && dmgSource instanceof StandLinkDamageSource;
	}

	// The barrage is held until it goes into recovery.
	@Override
	public boolean isActionHeld(EntityActionInstance action) {
		ActionPhase phase = action.getPhase();
		return phase != null && phase != ActionPhase.RECOVERY;
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
				&& stand.getUser() instanceof Player
				&& !StandEntityPunchAbility.shouldRetainPunchTarget(stand, action.ability);
	}

	public static ActionTarget clipDirectionalBarrageTarget(
			StandEntity stand, EntityActionInstance action, float partialTick) {
		LivingEntity user = stand.getUser();
		if (user == null || !(action.ability instanceof StandEntityAbility ability)) {
			return ActionTarget.EMPTY;
		}
		ActionTarget target = HitResultUtil.clip(user.getEyePosition(partialTick), user.getViewVector(partialTick),
				stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
				stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), stand.level(),
				entity -> ability.canTargetEntityForAiming(stand, entity)
						&& StandEntityPunchAbility.canStandHit(stand, entity), user, 0);
		return StandEntityPunchAbility.validatePunchTarget(stand, target);
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
				StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
				if (isDirectionalBarrage(stand, this)) {
					setActionTargetSnapshot(null);
				}
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
			if (performer instanceof StandEntity stand) {
				StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
			}
			
			if (getPhase() == ActionPhase.PERFORM && performer instanceof StandEntity stand) {
				boolean directional = isDirectionalBarrage(stand, this);
				StandPower standPower = StandPower.get(getPowerUser());
				if (directional && !level.isClientSide()
						&& (standPower == null || !standPower.consumeStamina(4, true))) {
					hitsThisTick = 0;
					stand.setBarrageHitsThisTick(0);
					punchedTarget = null;
					startRecovery();
					return;
				}
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
				if (standPower != null && (!directional || level.isClientSide())) {
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
				if (standEntityAttack(stand, targetEntity, dmgSource, dmgAmount) && resolve) {
					pinResolveBarrageTarget(targetEntity);
				}

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
			BlockPos blockPos = target.getBlockPos();
			BlockState blockState = level.getBlockState(blockPos);
			if (blockState.isAir()) return;
			// 1.16 StandEntity.breakBlock gate (gamerule, LivingDestroyBlockEvent, spawn protection):
			// protected blocks get no break, no cracks and no block hit sound
			if (!StandEntityPunchAbility.StandEntityPunch.canStandBreakBlock(level, blockPos, blockState, stand)) {
				return;
			}
			
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
