package rotp.core.impl.stands._entitybase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityLingeringSoundInstance;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier.ActionAnimIdHandsided;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.entity.StandStatFormulas.BlockMiningTier;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.compat.v1_21_4.missingmethods._EntitySelector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class StandEntityPunchAbility extends StandEntityAbility {
	// ServerPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER
	private static final ResourceLocation CREATIVE_BLOCK_RANGE = ResourceLocation.withDefaultNamespace("creative_mode_block_range");
	// 1.16 harvest levels by (int) strength / 4. Level 0 covered every pickaxe, axe and shovel block, so below
	// strength 4 only blocks no tool type is made for pass.
	private static final BlockMiningTier[] LEGACY_HARVEST_TIERS = {
			blockState -> !blockState.requiresCorrectToolForDrops() && !blockState.is(BlockTags.MINEABLE_WITH_PICKAXE)
					&& !blockState.is(BlockTags.MINEABLE_WITH_AXE) && !blockState.is(BlockTags.MINEABLE_WITH_SHOVEL),
			new BlockMiningTier.VanillaTierWrapper(Tiers.WOOD),
			new BlockMiningTier.VanillaTierWrapper(Tiers.STONE),
			new BlockMiningTier.VanillaTierWrapper(Tiers.IRON),
			new BlockMiningTier.VanillaTierWrapper(Tiers.DIAMOND),
			new BlockMiningTier.VanillaTierWrapper(Tiers.NETHERITE),
			BlockMiningTier.ANY_BLOCK
	};
	@Nullable private Holder<SoundEvent> punchImpactSound;
	@Nullable private Holder<SoundEvent> standCrySound;
	private boolean detachedLightCounterKnockback;
	private boolean laterPhaseEntryTiming;

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
		// 1.16 StandEntityLightAttack.Builder
		standUserWalkSpeed(1.0F);
	}

	public StandEntityPunchAbility punchImpactSound(Holder<SoundEvent> punchImpactSound) {
		this.punchImpactSound = punchImpactSound;
		return this;
	}

	public StandEntityPunchAbility standCrySound(Holder<SoundEvent> standCrySound) {
		this.standCrySound = standCrySound;
		return this;
	}

	public StandEntityPunchAbility detachedLightCounterKnockback() {
		detachedLightCounterKnockback = true;
		return this;
	}

	public StandEntityPunchAbility laterPhaseEntryTiming() {
		laterPhaseEntryTiming = true;
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
			if (action instanceof StandEntityPunch punch) {
				punch.handedAnimation = usesSinglePunchHandedAnimation(stand.getUserPower(), stand);
			}
			action.phasesLength.put(ActionPhase.WINDUP, StandStatFormulas.getLightAttackWindup(
					stand.getAttackSpeed(), stand.getFinisherMeter(), stand.guardCounter(), 
					isFirstPunch(stand)));
			float lightRecovery = StandStatFormulas.getLightAttackRecovery(stand.getAttackSpeed(), stand.getFinisherMeter());
			action.phasesLength.put(ActionPhase.PERFORM, lightRecovery);
			action.phasesLength.put(ActionPhase.RECOVERY, lightRecovery * (stand.isArmsOnlyMode() ? 2 : 4));
		}
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		ActionAnimIdentifier baseAnim = super.getEntityAnim(action);
		if (action instanceof StandEntityPunch punch
				&& punch.usesHandedAnimation()
				&& action.getPerformer() instanceof StandEntity stand) {
			HumanoidArm side = punch.getPunchingHand() == InteractionHand.MAIN_HAND
					? stand.getMainArm() : stand.getMainArm().getOpposite();
			return selectHandedAnimation(baseAnim, true, side);
		}
		return baseAnim;
	}

	private boolean usesSinglePunchHandedAnimation(StandPower standPower, StandEntity stand) {
		if (usageGroup != AbilityUsageGroup.COMBAT
				|| anim.index() > 0
				|| !stand.canAlternatePunchHands()) {
			return false;
		}
		String baseName = name();
		return standPower != null && baseName != null
				&& standPower.getMoveset().getAbility(baseName + "2") == null;
	}

	static ActionAnimIdentifier selectHandedAnimation(
			ActionAnimIdentifier baseAnim,
			boolean handedAnimation,
			HumanoidArm side) {
		return handedAnimation
				? new ActionAnimIdHandsided(baseAnim).get(side)
				: baseAnim;
	}
	
	public static class StandEntityPunch extends EntityActionInstance {
		protected boolean playedSwingSound;
		protected boolean playedStandCrySound;
		protected InteractionHand punchingHand = InteractionHand.MAIN_HAND;
		protected boolean handedAnimation;
		/**
		 * Server: whether the entity hit of this punch makes its impact sound (1.16 IPunch.playImpactSound: the hit
		 * landed, or the target is not living). Null when a hitEntity override did not say: the sound plays.
		 */
		@Nullable protected Boolean impactSoundDue;

		public StandEntityPunch(EntityActionType ability) {
			super(ability);
		}

		private boolean samplesLaterPhaseEntry(ActionPhase newPhase) {
			return phase != null && phase != newPhase
					&& (newPhase == ActionPhase.PERFORM || newPhase == ActionPhase.RECOVERY)
					&& performer instanceof StandEntity && !performer.level().isClientSide()
					&& getClass() == StandEntityPunch.class && !isGrabVariation()
					&& ability instanceof StandEntityPunchAbility punch && punch.laterPhaseEntryTiming;
		}

		@Override
		public void setPhase(ActionPhase newPhase, int tick) {
			boolean samplesEntry = samplesLaterPhaseEntry(newPhase);
			float previousLength = samplesEntry ? phasesLength.getFloat(newPhase) : 0;
			super.setPhase(newPhase, tick);
			// Publish the assigned phase/time, including a phase skipped by the base transition.
			if (samplesEntry && Float.compare(previousLength, phasesLength.getFloat(newPhase)) != 0) {
				syncPhaseChanges();
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (samplesLaterPhaseEntry(newPhase) && performer instanceof StandEntity stand) {
				int length = StandStatFormulas.getLightAttackRecovery(stand.getAttackSpeed(), stand.getFinisherMeter());
				phasesLength.put(newPhase, newPhase == ActionPhase.RECOVERY
						? length * (stand.isArmsOnlyMode() ? 2 : 4) : length);
			}
		}
		
		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			// 1.16 replayed the punch cry with every new punch task, chained ones too (StandEntityLightAttack.getSounds)
			playedStandCrySound = false;
			if (performer instanceof StandEntity stand) {
				if (stand.level().isClientSide()) {
					stand.applySyncedPunchingHand(punchingHand);
				}
				else {
					punchingHand = stand.alternateHands();
				}
				if (stand.isArmsOnlyMode() && punchingHand == InteractionHand.OFF_HAND) {
					stand.setArmsOnlyMode(true, true);
				}
				double minOffset = Math.min(0.5, stand.getEffectiveRange());
				double maxOffset = Math.min(2, stand.getMaxRange());
				ActionTarget target = setActionTargetSnapshot(pressTarget(stand, getActionTargetSnapshot(stand.level())));
				setStandFrontOffsetFromTarget(stand, target, minOffset, maxOffset);
				keepStandAimedAtTarget(target);
			}
			aimAs = AimingEntity.STAND;
			if (performer instanceof StandEntity stand) {
				releaseUnlockedPunchTarget(stand, this);
			}
		}

		public InteractionHand getPunchingHand() {
			return punchingHand;
		}

		public boolean usesHandedAnimation() {
			return handedAnimation;
		}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			super.toBuf(buf);
			buf.writeEnum(punchingHand);
			buf.writeBoolean(handedAnimation);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			super.fromBuf(buf);
			punchingHand = buf.readEnum(InteractionHand.class);
			handedAnimation = buf.readBoolean();
		}
		
		@Override
		public void actionTick() {
			Level level = performer.level();
			if (performer instanceof StandEntity stand) {
				releaseUnlockedPunchTarget(stand, this);
			}
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
					boolean entityTarget = target.getType() == TargetType.ENTITY;
					boolean playImpactSound = impactSound != null && playHitSound(target, level);
					Vec3 impactSoundPos = playImpactSound ? target.getCenterPos() : null;
					if (playImpactSound && !entityTarget) {
						StandUtil.broadcastSound((ServerLevel) level, impactSoundPos, 
								impactSound, true, standPower, 
								stand.getSoundSource(), 1, 1);
					}
					
					stand.addFinisherMeter(0.1f);
					boolean deflectedTarget = deflectSilverChariotProjectiles(stand, target);
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
					// 1.16 StandEntity.attackTarget: an entity's impact sound comes after the hit, if it landed
					if (playImpactSound && entityTarget && (impactSoundDue == null || impactSoundDue)) {
						StandUtil.broadcastSound((ServerLevel) level, impactSoundPos, 
								impactSound, true, standPower, 
								stand.getSoundSource(), 1, 1);
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
				releaseUnlockedPunchTarget(stand, this);
			}
		}
		
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand) {
			Entity targetEntity = target.getMainEntity();
			if (targetEntity != null && ability instanceof StandEntityPunchAbility punch
					&& punch.detachedLightCounterKnockback) {
				DamageSource source = makePunchDamageSource();
				float damage = StandStatFormulas.getLightAttackDamage(stand.getAttackDamage());
				float halfCounter = Math.max(stand.guardCounter(), 0) * 0.5F;
				((DamageSourceModified) source).jojo_ripples$modifyKnockback(0, 1);
				// Capture raw admission before the one virtual hurt mutates Stand immunity history.
				boolean admitted = damage > 0 && stand.canAttackEntity(targetEntity)
						&& (!(targetEntity instanceof LivingEntity living) || !StandEntity.isStandAttackInvulnerable(living));
				boolean hurt = standEntityAttack(stand, targetEntity, source, damage);
				impactSoundDue = hurt || !(targetEntity instanceof LivingEntity);
				if (admitted) {
					if (hurt && targetEntity instanceof LivingEntity) {
						applyCounterKnockback(stand, targetEntity, halfCounter);
					}
					applyCounterKnockback(stand, targetEntity, halfCounter);
				}
				stand.addFinisherMeter(0.1F);
				return;
			}
			if (targetEntity instanceof LivingEntity targetLiving) {
                DamageSource dmgSource = makePunchDamageSource();
				((DamageSourceModified) dmgSource).jojo_ripples$modifyKnockback(stand.guardCounter(), 1);
				float dmgAmount = StandStatFormulas.getLightAttackDamage(stand.getAttackDamage());
				impactSoundDue = standEntityAttack(stand, targetLiving, dmgSource, dmgAmount);
				// 1.16 StandEntityPunch.doHit: added even when blocked or in i-frames
				stand.addFinisherMeter(0.1f);
			}
			// 1.16 punched non-living entities too (boats, minecarts, item frames, end crystals)
			else if (targetEntity != null) {
				float dmgAmount = StandStatFormulas.getLightAttackDamage(stand.getAttackDamage());
				standEntityAttack(stand, targetEntity, makePunchDamageSource(), dmgAmount);
				impactSoundDue = true;
				stand.addFinisherMeter(0.1f);
			}
		}

		private static void applyCounterKnockback(StandEntity stand, Entity target, float strength) {
			if (strength <= 0) return;
			Vec3 toTarget = target.position().subtract(stand.position());
			float yaw = (float) -Mth.atan2(toTarget.x, toTarget.z) * Mth.RAD_TO_DEG;
			float radians = yaw * Mth.DEG_TO_RAD;
			if (target instanceof LivingEntity living) {
				living.knockback(strength, Mth.sin(radians), -Mth.cos(radians));
			}
			else {
				target.setDeltaMovement(new Vec3(0, 0, strength).yRot(-radians));
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

		/** 1.16 StandBlockPunch.doHit, shared by the light punch and Silver Chariot's dash. */
		public static void hitBlockTarget(ActionTarget target, Level level, StandEntity stand, boolean dropBlock) {
			breakStandBlock(level, target.getBlockPos(), stand, dropBlock);
		}

		/**
		 * 1.16 StandEntity.breakBlock: a protected block gets nothing; a block within the Stand's strength rule breaks
		 * at once, after the user's playerWillDestroy (piglin anger, Creative shulker box contents); any other block
		 * only plays its hit sound. Returns whether the block broke.
		 */
		public static boolean breakStandBlock(Level level, BlockPos blockPos, StandEntity stand, boolean dropBlock) {
			BlockState blockState = level.getBlockState(blockPos);
			if (!canStandBreakBlock(level, blockPos, blockState, stand)) {
				return false;
			}
			if (!canStandBreakBlockByStrength(stand, blockState, level, blockPos)) {
				playBlockHitSound(level, blockPos, blockState, stand);
				return false;
			}
			if (stand.getUser() instanceof Player player) {
				blockState.getBlock().playerWillDestroy(level, blockPos, blockState, player);
				dropBlock &= !player.getAbilities().instabuild;
			}
			if (level.destroyBlock(blockPos, dropBlock, stand)) {
				blockState.getBlock().destroy(level, blockPos, blockState);
				return true;
			}
			return false;
		}

		/** 1.16 StandEntity.canBreakBlock; a Stand with a rule of its own (Silver Chariot's visible rapier) uses that instead. */
		public static boolean canStandBreakBlockByStrength(StandEntity stand, BlockState blockState, Level level, BlockPos blockPos) {
			if (stand.getBlockHardnessForStandBreak(blockState, level, blockPos) < 0) {
				return false;
			}
			return stand.replacesStandBreakStrengthRule(blockState, level, blockPos)
					|| isBlockBreakableByStrength(stand.getAttackDamage(), blockState, level, blockPos);
		}

		/**
		 * 1.16 StandStatFormulas.isBlockBreakable: hardness (x0.6 when no tool is needed) under e^(strength/4) and a
		 * harvest level under (int) strength / 4.
		 */
		public static boolean isBlockBreakableByStrength(double strength, BlockState blockState, Level level, BlockPos blockPos) {
			float hardness = blockState.getDestroySpeed(level, blockPos);
			if (!blockState.requiresCorrectToolForDrops()) {
				hardness *= 0.6F;
			}
			if (hardness < 0 || !(hardness < Math.exp(strength / 4))) {
				return false;
			}
			int harvestTier = Mth.clamp((int) strength / 4, 0, LEGACY_HARVEST_TIERS.length - 1);
			return LEGACY_HARVEST_TIERS[harvestTier].canMine(blockState);
		}

		/** Gamerule, block, LivingDestroyBlockEvent and spawn protection gate for Stand punch block breaking. */
		public static boolean canStandBreakBlock(Level level, BlockPos blockPos, BlockState blockState, StandEntity stand) {
			return level instanceof ServerLevel serverLevel && !blockState.isAir()
					&& JojoModUtil.canEntityDestroy(serverLevel, blockPos, blockState, stand);
		}
		
		public static void playBlockHitSound(Level level, BlockPos blockPos, BlockState blockState, StandEntity stand) {
			SoundType blockSounds = blockState.getSoundType(level, blockPos, stand);
			level.playSound(null, blockPos, blockSounds.getHitSound(), SoundSource.BLOCKS,
					(blockSounds.getVolume() + 1.0F) / 8.0F, blockSounds.getPitch() * 0.5F);
		}
		
		protected ActionTarget getPunchTarget(StandEntity stand) {
			if (isGrabVariation()) {
				return new ActionTarget(LivingComponentGrab.getEntityGrabbedBy(stand));
			}
			ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(
					stand, getActionTargetSnapshot(stand.level()), shouldRetainPunchTarget(stand, ability));
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

	/**
	 * The target of the Stand's current action. Every action aims through the user as its 1.16 task did
	 * (StandEntity.aimWithThisOrUser): an action that is not one of the ordinary punches or barrages (an add-on's bomb,
	 * erase, absorption or Open Space) with the precision ray, the punches and barrages through {@link #userAimTarget}.
	 */
	public static ActionTarget getFreshPunchTarget(StandEntity stand, ActionTarget preferredTarget) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
		return getFreshPunchTarget(stand, preferredTarget,
				action == null || shouldRetainPunchTarget(stand, action.ability),
				action != null && !isPunchOrBarrage(action.ability));
	}

	public static boolean shouldRetainPunchTarget(StandEntity stand, EntityActionType ability) {
		if (stand.isManuallyControlled() || ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB
				|| !isPunchOrBarrage(ability)) {
			return true;
		}
		return PlayerClientBroadcastedSettings.isStandAttackTargetLockEnabled(stand.getUserPower());
	}

	private static boolean isPunchOrBarrage(EntityActionType ability) {
		return ability instanceof StandEntityPunchAbility
				|| ability instanceof StandEntityHeavyPunchAbility
				|| ability instanceof StandEntityBarrageAbility;
	}

	public static void releaseUnlockedPunchTarget(StandEntity stand, EntityActionInstance action) {
		if (!shouldRetainPunchTarget(stand, action.ability)) {
			action.standRotationTarget = null;
			action.aimAs = AimingEntity.CAMERA_ENTITY;
		}
	}

	public static ActionTarget getFreshPunchTarget(
			StandEntity stand, ActionTarget preferredTarget, boolean retainPreferredTarget) {
		return getFreshPunchTarget(stand, preferredTarget, retainPreferredTarget, false);
	}

	/**
	 * @param precisionAim the user's own aim is 1.16's precision ray (StandEntity.aimWithThisOrUser), walls still
	 *        blocking it, whatever {@link #userAimAssist} says; otherwise {@link #userAimTarget}
	 */
	public static ActionTarget getFreshPunchTarget(
			StandEntity stand, ActionTarget preferredTarget, boolean retainPreferredTarget, boolean precisionAim) {
		Level level = stand.level();
		ActionTarget target = retainPreferredTarget
				? validatePunchTarget(stand, preferredTarget) : ActionTarget.EMPTY;
		if (!target.isEmpty(level)) {
			return target;
		}

		LivingEntity user = stand.getUser();
		boolean userAims = user != null && !stand.isManuallyControlled();
		// 1.16 EntityStandType.clientHitResult sent what the user's crosshair met. The Stand's synced aim, a precision
		// clip that takes the nearest mob, speaks first only under manual control and for an action that is not a punch.
		var standAim = !userAims || precisionAim ? LivingComponentAction.getAim(stand) : null;
		if (standAim != null) {
			target = validatePunchTarget(stand, standAim.getTarget());
			if (!target.isEmpty(level)) {
				return target;
			}
		}

		var userAim = userAims ? LivingComponentAction.getAim(user) : null;
		if (userAim != null) {
			target = validatePunchTarget(stand, userAim.getTarget());
			if (!target.isEmpty(level)) {
				return target;
			}
		}

		// the synced aims are short (the idle Stand aim reaches 2.5 blocks from the eyes, the vanilla pick 3)
		return aimWithThisOrUser(stand, userAims ? user : null, 1.0F,
				entity -> entity != stand && canStandHit(stand, entity), precisionAim);
	}

	/**
	 * 1.16 StandEntity.aimWithThisOrUser with no target the Stand can reach: the user's own aim (the precision ray, or
	 * {@link #userAimTarget}); when that ray met nothing at all, or no user aims (manual control), 1.16's precision ray
	 * from the Stand's own eyes. Walls block both rays. Checked against the Stand's reach.
	 */
	public static ActionTarget aimWithThisOrUser(StandEntity stand, @Nullable LivingEntity user, float partialTick,
			Predicate<Entity> canTarget, boolean precisionAim) {
		ActionTarget aim = ActionTarget.EMPTY;
		if (user != null) {
			aim = precisionAim
					? userPrecisionRayTarget(stand, user, canTarget)
					: userAimTarget(stand, user, partialTick, canTarget);
		}
		if (aim.getType() == TargetType.EMPTY) {
			aim = standPrecisionRayTarget(stand, canTarget);
		}
		return validatePunchTarget(stand, aim);
	}

	/**
	 * The target a punch, heavy punch or barrage is pressed at: what the user's crosshair met (1.16
	 * EntityStandType.clientHitResult kept the vanilla pick); when it met nothing, the Stand's synced aim, which stands
	 * in for 1.16's precision ray there; under manual control the Stand's aim. A client keeps what its server sent.
	 */
	public static ActionTarget pressTarget(StandEntity stand, ActionTarget sentByServer) {
		Level level = stand.level();
		if (level.isClientSide()) {
			return sentByServer;
		}
		LivingEntity user = stand.getUser();
		var userAim = user != null && !stand.isManuallyControlled() ? LivingComponentAction.getAim(user) : null;
		if (userAim != null) {
			ActionTarget crosshair = userAim.getTarget().resolveEntityId(level);
			if (!crosshair.isEmpty(level)) {
				return crosshair;
			}
		}
		var standAim = LivingComponentAction.getAim(stand);
		return standAim != null ? standAim.getTarget() : ActionTarget.EMPTY;
	}

	/**
	 * Whether the user's own Stand aim (the default barrage, a punch's fallback) has 1.16's precision aim assist. On:
	 * the owner restored it on 2026-10-14 in place of the strict crosshair of 2026-09-13; walls still block it.
	 */
	private static boolean userAimAssist() {
		return true;
	}

	/**
	 * The user's own aim for a Stand attack. Without the aim assist, what the crosshair ray meets first from the user's
	 * eyes at the user's reach; blocks stop the ray. With it, as 1.16's client and server did together: the vanilla pick
	 * ({@link #crosshairPick}) while the Stand can reach it (EntityStandType.clientHitResult kept it, aimWithThisOrUser
	 * kept a target in reach), otherwise 1.16's precision ray.
	 */
	public static ActionTarget userAimTarget(StandEntity stand, LivingEntity user, float partialTick, Predicate<Entity> canTarget) {
		if (!userAimAssist()) {
			double reach = userAimReach(user, stand);
			return HitResultUtil.clip(user.getEyePosition(partialTick), user.getViewVector(partialTick),
					reach, reach, stand.level(), canTarget, user, 0);
		}
		ActionTarget crosshair = crosshairPick(stand, user, partialTick, canTarget);
		if (!validatePunchTarget(stand, crosshair).isEmpty(stand.level())) {
			return crosshair;
		}
		return userPrecisionRayTarget(stand, user, canTarget);
	}

	/**
	 * 1.16's vanilla pick (GameRenderer.pick), what a client's crosshair target is: the first entity or block on the
	 * crosshair ray, a player's entity within its entity interaction range (3 blocks in Survival, 5 in Creative) and a
	 * block within its block interaction range, else a miss even with a block behind; a mob looks as far as its reach.
	 */
	public static ActionTarget crosshairPick(StandEntity stand, LivingEntity user, float partialTick, Predicate<Entity> canTarget) {
		double blockRange = user instanceof Player player ? player.blockInteractionRange() : userAimReach(user, stand);
		double entityRange = user instanceof Player player ? player.entityInteractionRange() : blockRange;
		double range = Math.max(blockRange, entityRange);
		Level level = stand.level();
		Vec3 eyes = user.getEyePosition(partialTick);
		ActionTarget hit = HitResultUtil.clip(eyes, user.getViewVector(partialTick), range, range, level, canTarget, user, 0);
		if (hit.isEmpty(level)) {
			return ActionTarget.EMPTY;
		}
		double hitRange = hit.getType() == TargetType.ENTITY ? entityRange : blockRange;
		double distanceSqr = hit.getClipPos().map(eyes::distanceToSqr)
				.orElseGet(() -> hit.getBoundingBox(level).map(box -> box.distanceToSqr(eyes)).orElse(0.0D));
		return distanceSqr < hitRange * hitRange ? hit : ActionTarget.EMPTY;
	}

	/**
	 * The Stand's aim while its user aims, the one a client keeps (the marker, the Stand aim a press carries): 1.16
	 * EntityStandType.clientHitResult, the vanilla pick when it met a block or an entity the Stand aims at, otherwise the
	 * precision ray from the user's eyes, and when that met nothing at all the Stand's own, as aimWithThisOrUser went on.
	 * Not limited to the Stand's reach, as the 1.16 pressed target was not; its readers check the reach.
	 */
	public static ActionTarget userStandAim(StandEntity stand, LivingEntity user, ActionTarget vanillaPick,
			Predicate<Entity> canTarget) {
		if (vanillaPick.getType() == TargetType.BLOCK) {
			return vanillaPick;
		}
		Entity picked = vanillaPick.getType() == TargetType.ENTITY ? vanillaPick.getEntity() : null;
		if (picked != null && picked != stand && canTarget.test(picked)) {
			return vanillaPick;
		}
		ActionTarget aim = userPrecisionRayTarget(stand, user, canTarget);
		return aim.getType() == TargetType.EMPTY ? standPrecisionRayTarget(stand, canTarget) : aim;
	}

	public static ActionTarget userPrecisionRayTarget(StandEntity stand, LivingEntity user) {
		return userPrecisionRayTarget(stand, user, entity -> entity != stand && canStandHit(stand, entity));
	}

	/**
	 * 1.16 StandEntity.precisionRayTrace(user, getAimDistance(user)): the user's reach, the Stand's precision hitboxes,
	 * and of several entities a player or Stand first, then a hostile mob, then the rest; else the block. An entity
	 * behind a block on the ray is not picked, measured to the nearest point of its own box (the owner keeps walls
	 * blocking the aim); sneaking switches the precision hitboxes off, as it does for the port's client aim.
	 */
	public static ActionTarget userPrecisionRayTarget(StandEntity stand, LivingEntity user, Predicate<Entity> canTarget) {
		return StandPrecisionRay.closestWithPriority(StandPrecisionRay.clipTargetsBeforeWalls(user,
				userAimReach(user, stand), canTarget, 0, user.isShiftKeyDown() ? 0 : stand.getPrecision()));
	}

	// 1.16 precisionRayTrace(this, getAimDistance(getUser())), with the user's sneak rule and walls as above
	private static ActionTarget standPrecisionRayTarget(StandEntity stand, Predicate<Entity> canTarget) {
		LivingEntity user = stand.getUser();
		double reach = user != null && !stand.isManuallyControlled()
				? userAimReach(user, stand) : stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		return StandPrecisionRay.closestWithPriority(StandPrecisionRay.clipTargetsBeforeWalls(stand, reach,
				canTarget.and(entity -> entity != user), 0,
				user != null && user.isShiftKeyDown() ? 0 : stand.getPrecision()));
	}

	/**
	 * 1.16 StandEntity.getAimDistance: the user's REACH_DISTANCE, 5 in either game mode: the 1.21 Survival attribute
	 * and half a block, without the Creative half block a server player gets on top.
	 */
	public static double userAimReach(LivingEntity user, StandEntity stand) {
		AttributeInstance reach = user.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		if (reach == null) {
			return stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		}
		AttributeModifier creative = reach.getModifier(CREATIVE_BLOCK_RANGE);
		double creativeBonus = creative != null && creative.operation() == AttributeModifier.Operation.ADD_VALUE
				? creative.amount() : 0;
		return reach.getValue() - creativeBonus + 0.5D;
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
	
	private boolean isFirstPunch(StandEntity stand) {
		EntityActionInstance previous = stand.getCurStandAction();
		if (previous == null) return true;
		if (previous.ability == this) return false;
		StandPower power = stand.getUserPower();
		return previous.isOver() || power == null || !isSameLightComboFamily(power.getMoveset(), this, previous.ability);
	}

	private static boolean isSameLightComboFamily(Moveset moveset, EntityActionType first, EntityActionType second) {
		if (!(first instanceof Ability firstAbility) || !(second instanceof Ability secondAbility)
				|| first.getAbilityUsageCategory() != AbilityUsageGroup.COMBAT || second.getAbilityUsageCategory() != AbilityUsageGroup.COMBAT
				|| firstAbility.abilityType != ModStandAbilities.PUNCH.get() || secondAbility.abilityType != ModStandAbilities.PUNCH.get()) {
			return false;
		}
		for (Ability root : moveset.abilities.values()) {
			String name = root.name();
			if (root.isSubAbility || root.usageGroup != AbilityUsageGroup.COMBAT || root.abilityType != ModStandAbilities.PUNCH.get()
					|| name == null || moveset.getAbility(name) != root) continue;
			boolean firstMember = false;
			boolean secondMember = false;
			for (String memberName : getComboPunchNames(moveset, name)) {
				Ability member = moveset.getAbility(memberName);
				if (member != root && !member.isSubAbility) continue;
				firstMember |= member == firstAbility;
				secondMember |= member == secondAbility;
			}
			if (firstMember && secondMember) return true;
		}
		return false;
	}

	@Nullable
	protected Ability getComboPunch(StandPower standPower) {
		if (standPower == null) return null;

		Moveset moveset = standPower.getMoveset();
		StandEntity standEntity = standPower.getSummonedStandEntity();

		if (this.isSubAbility) return null;

		// This list used to be a static field reused across calls, and that crashed the client with
		// "Index 0 out of bounds for length 0" here. Two threads run this method - the render thread through
		// ClientPowerCache.onFrame -> Power.updateAvailableMoves, and the server thread through the ability input
		// packets - so one could clear the shared list between the other's size() and its get(i). It has to be a
		// cross-thread clear: the loop below re-reads the bound each iteration, so a same-thread clear would just
		// end it early. The second loop does capture size once, which would make a re-entrant clear fatal too; no
		// isAbilityAvailable override reaches back into this method today, but a local list means neither hazard
		// can return.
		String baseName = this.name();
		if (baseName == null) {
			return null;
		}
		List<String> punchNamesBuffer = getComboPunchNames(moveset, baseName);
		
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

	private static List<String> getComboPunchNames(Moveset moveset, String baseName) {
		List<String> names = new ArrayList<>();
		names.add(baseName);
		for (int i = 2; ; i++) {
			String name = baseName + i;
			if (moveset.getAbility(name) == null) break;
			names.add(name);
		}
		return names;
	}

}
