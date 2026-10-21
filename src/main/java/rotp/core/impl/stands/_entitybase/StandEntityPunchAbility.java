package rotp.core.impl.stands._entitybase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandSkillWalls;
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
import rotp.core.subsystems.target.ActionTargetRange;
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
import net.minecraft.world.entity.projectile.Projectile;
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

	/**
	 * Whether this punch stands for a 1.16 heavy attack (a StandEntityHeavyAttack such as King Crimson's Ground Punch):
	 * it keeps and takes targets by the heavy punch's {@link CrosshairRule#ENTITY}. An add-on punch overrides it.
	 */
	protected boolean heavyAttack() {
		return false;
	}

	// 1.16 StandEntityLightAttack.lockOnTargetPosition: the punch turns the Stand to its target, never its offset
	@Override
	public boolean lockOnTargetPosition(@Nullable StandPower standPower, StandEntity standEntity, EntityActionInstance action) {
		return false;
	}

	@Override
	public void rotateStandTowardsTarget(StandEntity standEntity, EntityActionInstance action, ActionTarget target) {
		super.rotateStandTowardsTarget(standEntity, action, grabTurnTarget(action, target));
	}

	// a grab's punch (a port move) faces the entity the Stand holds once its strike turned to it, not what it was pressed at
	static ActionTarget grabTurnTarget(EntityActionInstance action, ActionTarget taskTarget) {
		ActionTarget turnedTo = action.standRotationTarget;
		return action.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB && turnedTo != null
				&& !turnedTo.isEmpty(action.level()) ? turnedTo : taskTarget;
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
			if (newPhase == ActionPhase.RECOVERY) {
				aimAs = AimingEntity.CAMERA_ENTITY;
			}
		}
		
		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			// 1.16 replayed the punch cry with every new punch task, chained ones too (StandEntityLightAttack.getSounds)
			playedStandCrySound = false;
			if (performer instanceof StandEntity stand) {
				// a failed input that puts this punch back keeps its hand
				if (stand.level().isClientSide() || isRestoringInputLifecycle()) {
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
				ActionTarget target = setActionTargetSnapshot(takeOverIfPressedAtNothing(stand,
						pressTarget(stand, getActionTargetSnapshot(stand.level()), punchRule(ability)),
						isRestoringInputLifecycle() ? null : prevAction, punchRule(ability)));
				setStandFrontOffsetFromTarget(stand, target, minOffset, maxOffset);
				keepStandAimedAtTarget(target);
			}
			aimAs = standRotationTarget != null ? AimingEntity.STAND : AimingEntity.CAMERA_ENTITY;
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
				if (!takesTargetsFromServer() && !isGrabVariation()) {
					releaseTargetOutOfRange(stand);
				}
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
				// judged before the hit, which may break the block or remove the entity, as 1.16 set the task target first
				boolean keepsStruck = !takesTargetsFromServer() && keepsStrikeTarget(stand, target);
				if (!level.isClientSide()) {
					StandPower standPower = StandPower.get(getPowerUser());
					stand.turnTowardsStrike(isGrabVariation() ? target : strikeAim(stand, target, punchRule(ability)));
					
					Holder<SoundEvent> impactSound = getPunchImpactSound(target);
					boolean entityTarget = target.getType() == TargetType.ENTITY;
					// judged before the hit, as 1.16's StandBlockPunch.playImpactSound read the hardness of the block struck
					boolean playImpactSound = impactSound != null && playHitSound(target, level);
					Vec3 impactSoundPos = playImpactSound ? target.getCenterPos() : null;
					
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
					// 1.16 StandEntity.attackTarget: the impact sound comes after the hit, which may have broken the block, and an
					// entity's only if the hit landed
					if (playImpactSound && (!entityTarget || impactSoundDue == null || impactSoundDue)) {
						StandUtil.broadcastSound((ServerLevel) level, impactSoundPos, 
								impactSound, true, standPower, 
								stand.getSoundSource(), 1, 1);
					}

					punchedTarget = target;
					standPower.consumeStamina(10);
				}
				/*
				 *  During the punch, the Stand entity keeps rotating towards the target (keepStandAimedAtTarget()),
				 *  and while it keeps one it aims as itself (aimAs == AimingEntity.STAND). From the recovery on
				 *  (onSetPhase) it aims as its user's camera again, as 1.16 took the next press from the user's view
				 *  (EntityStandType.clientHitResult); a strike that met no entity hands the aim back at once. The
				 *  server's strike sets the targets by 1.16's task rule (takeStrikeTarget), again here for a
				 *  getPunchTarget override of its own, and sends what it changed (sendsPerformStartTargets); a client
				 *  sets them from the target it holds.
				 */
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

		// 1.16 StandEntityLightAttack.getSounds: the cry when the task target the press kept is an entity, read as it is
		protected boolean shouldPlayStandCrySound(Level level, StandEntity stand) {
			ActionTarget kept = isGrabVariation()
					? new ActionTarget(LivingComponentGrab.getEntityGrabbedBy(stand)) : getActionTargetSnapshot(level);
			return !stand.isArmsOnlyMode()
					&& stand.getFinisherMeter() <= 0
					&& kept.getType() == TargetType.ENTITY;
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
		
		/**
		 * The target the punch strikes now. The server aims as 1.16's StandEntity.punch did and, at the strike, makes it the
		 * target the punch keeps and turns to by the task rule ({@link #takeStrikeTarget}), so an action that strikes in an
		 * actionPerformStart of its own gets it too; a client strikes at the target the server last sent it, the kept one
		 * or the strike's own once the server sent it.
		 */
		protected ActionTarget getPunchTarget(StandEntity stand) {
			if (isGrabVariation()) {
				return new ActionTarget(LivingComponentGrab.getEntityGrabbedBy(stand));
			}
			if (takesTargetsFromServer()) {
				return getActionTargetSnapshot(stand.level());
			}
			ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(
					stand, getActionTargetSnapshot(stand.level()), shouldRetainPunchTarget(stand, ability), punchRule(ability));
			if (isStartingPerform()) {
				takeStrikeTarget(target, keepsStrikeTarget(stand, target));
			}
			else {
				setActionTargetSnapshot(target);
			}
			return target;
		}

		// 1.16 StandEntityTask.keepTarget of the struck target: StandEntityLightAttack.standKeepsTarget kept a block,
		// StandEntityHeavyAttack's refused it, and neither kept an entity that is not living (checkStandTarget)
		private boolean keepsStrikeTarget(StandEntity stand, ActionTarget struck) {
			return keepsTargetInRange(stand, struck, punchRule(ability));
		}

		@Override
		protected boolean sendsPerformStartTargets() {
			return true;
		}

		// the target the punch keeps is its task's (1.16 StandEntity.punch set it with setTaskTarget), which a next action
		// may take over
		@Override
		public ActionTarget getTaskTarget(Level level) {
			return heldTarget(actionTargetSnapshot, level);
		}

		@Override
		@Nullable
		protected ActionTarget storedTaskTarget() {
			return actionTargetSnapshot;
		}

		// dropped as releaseTargetOutOfRange drops it
		@Override
		protected void storeTaskTarget(@Nullable ActionTarget target) {
			setActionTargetSnapshot(target);
			if (target == null) {
				standRotationTarget = null;
				aimAs = AimingEntity.CAMERA_ENTITY;
			}
		}

		@Override
		protected boolean keepsAsTaskTarget(StandEntity stand, ActionTarget target) {
			return keepsTargetInRange(stand, target, punchRule(ability));
		}

		// 1.16 StandEntityTask.tick, on every server tick of the task: setTaskTarget(EMPTY) once keepTarget failed, sent to
		// tracking clients; the Stand stops turning to the target. Read as held, so that a block broken since (air) or an
		// entity removed is dropped too.
		private void releaseTargetOutOfRange(StandEntity stand) {
			if (actionTargetSnapshot != null && !keepsTargetInRange(stand, actionTargetSnapshot, punchRule(ability))) {
				setActionTargetSnapshot(null);
				standRotationTarget = null;
				aimAs = AimingEntity.CAMERA_ENTITY;
				syncTargetsToTracking();
			}
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
	 * 1.16 StandEntityTask.keepTarget, checked by the server on every tick of the task: an attack keeps the target it was
	 * pressed at while its class keeps that kind of target ({@link CrosshairRule#keeps}), it is kept as an entity
	 * ({@link #keepsEntity}: the Stand can attack it, a dying one too) and it stays within the action's range of the Stand
	 * (Action.checkRangeAndTarget: 8 blocks to an entity, 4 when the Stand cannot see it, 10 to a block). The Stand's
	 * reach is checked only at the strike (StandEntity.aimWithThisOrUser).
	 */
	public static boolean keepsTargetInRange(StandEntity stand, ActionTarget target, CrosshairRule rule) {
		Level level = stand.level();
		ActionTarget kept = target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
		if (kept.isEmpty(level) || rule.keeps(kept).isEmpty(level)) {
			return false;
		}
		if (kept.getType() == TargetType.ENTITY && !keepsEntity(stand, kept.getEntity())) {
			return false;
		}
		return ActionTargetRange.isTargetWithinRange(stand, kept, level);
	}

	/**
	 * Whether an attack keeps an entity as its target, as 1.16's StandEntityTask.keepTarget kept its task target: until it
	 * is removed or has lain dead for 20 ticks (LivingEntity.deathTime), while the Stand may attack it
	 * (StandEntityAction.checkStandTarget: canStandTargetEntity, StandEntity.canAttack). A dying entity stays kept, though
	 * a strike hurts it no more.
	 */
	@SuppressWarnings("deprecation")
	static boolean keepsEntity(StandEntity stand, Entity entity) {
		if (entity.isRemoved() || entity instanceof LivingEntity dying && dying.deathTime >= 20) {
			return false;
		}
		return canStandPick(stand, entity)
				&& (entity instanceof LivingEntity living ? stand.canAttack(living) : stand.canAttackEntity(entity));
	}

	/**
	 * The target an attack kept, while the Stand can reach it: 1.16's StandEntity.aimWithThisOrUser struck the task target
	 * in reach whatever its health, so one dying is struck too as long as it is kept ({@link #keepsEntity}).
	 */
	private static ActionTarget keptInReach(StandEntity stand, @Nullable ActionTarget kept) {
		Level level = stand.level();
		ActionTarget target = kept != null ? kept.resolveEntityId(level) : ActionTarget.EMPTY;
		if (target.isEmpty(level) || !HitResultUtil.isTargetWithinRange(target, stand, level,
				stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
				stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))) {
			return ActionTarget.EMPTY;
		}
		if (target.getType() == TargetType.ENTITY) {
			Entity targetEntity = target.getMainEntity();
			return targetEntity != null && keepsEntity(stand, targetEntity) ? target : ActionTarget.EMPTY;
		}
		return target;
	}

	/**
	 * What the aim marker shows while a punch or heavy punch winds up and strikes: the target it keeps, which the Stand is
	 * turned to. Null when it keeps none, where the marker shows the aim its strike will take, and from its recovery on,
	 * where the marker shows what the next press takes.
	 */
	@Nullable
	public static ActionTarget keptStrikeTarget(StandEntity stand, @Nullable EntityActionInstance action) {
		if (!(action instanceof StandEntityPunch || action instanceof StandEntityHeavyPunchAbility.StandEntityHeavyPunch)
				|| action.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB
				|| action.getPhase() != ActionPhase.WINDUP && action.getPhase() != ActionPhase.PERFORM
				|| action.standRotationTarget == null) {
			return null;
		}
		ActionTarget kept = action.standRotationTarget.resolveEntityId(stand.level());
		return kept.isEmpty(stand.level()) ? null : kept;
	}

	/**
	 * The target of the Stand's current action. Every action aims through the user as its 1.16 task did
	 * (StandEntity.aimWithThisOrUser): an action that is not one of the ordinary punches or barrages (an add-on's bomb,
	 * erase, absorption or Open Space) with the precision ray, the punches and barrages by their {@link CrosshairRule}.
	 */
	public static ActionTarget getFreshPunchTarget(StandEntity stand, ActionTarget preferredTarget) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
		boolean precisionAim = action != null && !isPunchOrBarrage(action.ability);
		return getFreshPunchTarget(stand, preferredTarget,
				action == null || shouldRetainPunchTarget(stand, action.ability),
				action == null ? CrosshairRule.BLOCK_OR_ENTITY : crosshairRule(action.ability), precisionAim, false);
	}

	/**
	 * What an attack takes from its user's crosshair while the aim assist is on, as its 1.16 action kept the target it
	 * was pressed at: the light punch a block or an entity the Stand can attack (StandEntityLightAttack.standKeepsTarget),
	 * the heavy punch only such an entity (StandEntityHeavyAttack.standKeepsTarget), the barrage nothing
	 * (StandEntityMeleeBarrage.checkStandTarget). An entity the attack cannot take is refused as 1.16's press refused it
	 * (PowerBaseImpl.checkTarget, StandEntity.canAttack). What the attack does not take, 1.16's precision ray decides.
	 * Without the assist every attack takes what the crosshair meets. What it keeps of its press ({@link #keeps}) does
	 * not depend on the assist: a heavy punch pressed at a block keeps nothing, nor does any attack pressed at an entity
	 * that is not living, and its strike aims again.
	 */
	public enum CrosshairRule {
		BLOCK_OR_ENTITY,
		ENTITY,
		NONE;

		boolean takes(StandEntity stand, ActionTarget crosshair, Predicate<Entity> canTarget) {
			Level level = stand.level();
			return switch (crosshair.getType()) {
				case ENTITY -> crosshair.getEntity() != null && canTarget.test(crosshair.getEntity())
						&& !validatePunchTarget(stand, crosshair).isEmpty(level);
				case BLOCK -> this == BLOCK_OR_ENTITY && !validatePunchTarget(stand, crosshair).isEmpty(level);
				default -> false;
			};
		}

		/**
		 * What the attack keeps of the target it was pressed at, as 1.16's task kept it by the attack's class whatever
		 * the aim: the heavy punch only an entity (StandEntityHeavyAttack.standKeepsTarget), the others what it is. Of an
		 * entity, only a living one (StandEntityAction.checkStandTarget); the callers check that the Stand may attack it
		 * (canStandTargetEntity, StandEntity.canAttack). A boat, minecart or item frame is not kept, and the strike aims
		 * again (StandEntity.punch).
		 */
		ActionTarget keeps(ActionTarget target) {
			return switch (target.getType()) {
				case BLOCK -> this == ENTITY ? ActionTarget.EMPTY : target;
				case ENTITY -> this == NONE || target.getEntity() instanceof LivingEntity ? target : ActionTarget.EMPTY;
				default -> target;
			};
		}

		// what a strike takes from a synced aim: with the assist, a block the heavy punch leaves to the precision ray
		ActionTarget takesFromAim(ActionTarget target, boolean aimAssist) {
			return aimAssist && this == ENTITY && target.getType() == TargetType.BLOCK ? ActionTarget.EMPTY : target;
		}
	}

	private static CrosshairRule crosshairRule(EntityActionType ability) {
		if (ability instanceof StandEntityHeavyPunchAbility || ability instanceof StandEntityHeavyPunchChargedAbility
				|| ability instanceof StandEntityPunchAbility punch && punch.heavyAttack()) {
			return CrosshairRule.ENTITY;
		}
		return isPunchOrBarrage(ability) ? CrosshairRule.BLOCK_OR_ENTITY : CrosshairRule.NONE;
	}

	// the rule of a punch action (StandEntityPunch), whatever ability started it
	private static CrosshairRule punchRule(EntityActionType ability) {
		CrosshairRule rule = crosshairRule(ability);
		return rule == CrosshairRule.NONE ? CrosshairRule.BLOCK_OR_ENTITY : rule;
	}

	/**
	 * Whether an attack keeps the target it was pressed at instead of aiming again. Every action but the barrage keeps it,
	 * the light and heavy punch among them: 1.16 kept their pressed target for the whole task while it stayed in range
	 * (StandEntityTask.keepTarget, StandEntityLightAttack/StandEntityHeavyAttack.standKeepsTarget) and struck it while in
	 * reach (StandEntity.aimWithThisOrUser), whatever the user's crosshair met by then. The barrage keeps it only with its
	 * user's "keep attack target" setting, also under manual control and for a Stand that aims without its user, since
	 * 1.16's barrage refused every pressed target (StandEntityMeleeBarrage.checkStandTarget) and each hit aimed again
	 * (StandEntity.punch, aimWithThisOrUser).
	 */
	public static boolean shouldRetainPunchTarget(StandEntity stand, EntityActionType ability) {
		if (ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB || !(ability instanceof StandEntityBarrageAbility)) {
			return true;
		}
		// a mob user has no such setting; 1.16 aimed its barrage hits again from its own eyes too
		return stand.getUser() instanceof Player
				&& PlayerClientBroadcastedSettings.isStandAttackTargetLockEnabled(stand.getUserPower());
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
	 *        blocking it, while the user has the aim assist ({@link #aimAssist}); otherwise {@link #userAimTarget}
	 */
	public static ActionTarget getFreshPunchTarget(
			StandEntity stand, ActionTarget preferredTarget, boolean retainPreferredTarget, boolean precisionAim) {
		return getFreshPunchTarget(stand, preferredTarget, retainPreferredTarget,
				precisionAim ? CrosshairRule.NONE : CrosshairRule.BLOCK_OR_ENTITY, precisionAim, false);
	}

	/**
	 * A punch's or heavy punch's target, what it takes from the crosshair given by its {@link CrosshairRule}. The target
	 * it kept is struck while in reach, a dying one too as 1.16's task kept it ({@link #keptInReach}).
	 */
	public static ActionTarget getFreshPunchTarget(
			StandEntity stand, ActionTarget preferredTarget, boolean retainPreferredTarget, CrosshairRule rule) {
		return getFreshPunchTarget(stand, preferredTarget, retainPreferredTarget, rule, false, true);
	}

	private static ActionTarget getFreshPunchTarget(StandEntity stand, ActionTarget preferredTarget,
			boolean retainPreferredTarget, CrosshairRule rule, boolean precisionAim, boolean keptByTask) {
		Level level = stand.level();
		LivingEntity user = stand.getUser();
		boolean assist = aimAssist(user);
		ActionTarget target = !retainPreferredTarget ? ActionTarget.EMPTY
				: rule.keeps(keptByTask ? keptInReach(stand, preferredTarget) : validatePunchTarget(stand, preferredTarget));
		if (!target.isEmpty(level)) {
			return throughWallsTarget(stand, target, rule, true);
		}

		boolean userAims = user != null && !stand.aimsWithoutUser();
		// 1.16 EntityStandType.clientHitResult sent what the user's crosshair met. The Stand's synced aim speaks first only
		// under manual control; without the assist the server takes what the crosshair meets, whatever a client sent. With
		// the assist an action that is not a punch strikes as 1.16's StandEntity.punch aimed it, aimWithThisOrUser with the
		// precision ray and its priority, never a vanilla pick.
		boolean precisionRayOnly = userAims && precisionAim && assist;
		var standAim = !userAims ? LivingComponentAction.getAim(stand) : null;
		if (standAim != null) {
			target = rule.takesFromAim(validatePunchTarget(stand, standAim.getTarget()), assist);
			if (!target.isEmpty(level)) {
				return throughWallsTarget(stand, target, rule, true);
			}
		}

		var userAim = userAims && !precisionRayOnly ? LivingComponentAction.getAim(user) : null;
		if (userAim != null) {
			target = rule.takesFromAim(validatePunchTarget(stand, userAim.getTarget()), assist);
			if (!target.isEmpty(level)) {
				return throughWallsTarget(stand, target, rule, true);
			}
		}

		// the synced aims are short (the idle Stand aim reaches 2.5 blocks from the eyes, the vanilla pick 3)
		return aimWithThisOrUser(stand, userAims ? user : null, 1.0F,
				entity -> entity != stand && canStandHit(stand, entity), rule, true);
	}

	public static ActionTarget aimWithThisOrUser(StandEntity stand, @Nullable LivingEntity user, float partialTick,
			Predicate<Entity> canTarget, boolean precisionAim) {
		return aimWithThisOrUser(stand, user, partialTick, canTarget,
				precisionAim ? CrosshairRule.NONE : CrosshairRule.BLOCK_OR_ENTITY, true);
	}

	/**
	 * 1.16 StandEntity.aimWithThisOrUser with no target the Stand can reach: the user's own aim ({@link #userAimTarget}
	 * by the attack's {@link CrosshairRule}, the precision ray for {@link CrosshairRule#NONE}); when that ray met nothing
	 * at all, or no user aims (manual control), 1.16's precision ray from the Stand's own eyes. Without the aim assist
	 * only the crosshair: no precision ray, and the Stand's eyes only under manual control. Walls block every ray, except
	 * for an action marked through walls ({@link StandSkillWalls}). Every ray, the user's too, follows the Stand's
	 * current action. Checked against the Stand's reach.
	 * @param wallBeforeEntityOutOfReach for a marked action, when all its ray meets is an entity out of the Stand's
	 *        reach, the block the same aim meets is taken instead, so a wall the user aims at is struck; false leaves no
	 *        target then, for a move that still closes in on the entity
	 */
	public static ActionTarget aimWithThisOrUser(StandEntity stand, @Nullable LivingEntity user, float partialTick,
			Predicate<Entity> canTarget, CrosshairRule rule, boolean wallBeforeEntityOutOfReach) {
		if (user != null && stand.aimsWithoutUser()) {
			user = null;
		}
		boolean wallsHideEntities = StandSkillWalls.wallsHideEntities(stand);
		ActionTarget aim = pickWithThisOrUser(stand, user, partialTick, canTarget, rule, wallsHideEntities);
		ActionTarget target = validatePunchTarget(stand, aim);
		if (wallBeforeEntityOutOfReach && !wallsHideEntities
				&& target.getType() == TargetType.EMPTY && aim.getType() == TargetType.ENTITY) {
			target = validatePunchTarget(stand, pickWithThisOrUser(stand, user, partialTick, canTarget, rule, true));
		}
		return target;
	}

	/**
	 * Server: what 1.16's StandEntity.aimWithThisOrUser aimed a strike at, which the Stand turned toward before the hit
	 * ({@link StandEntity#turnTowardsStrike}): the target the strike takes or, with none in the Stand's reach, what the
	 * aim met beyond it (the user's aim, else the Stand's own, as {@link #aimWithThisOrUser} picks it), which the strike
	 * then misses.
	 */
	public static ActionTarget strikeAim(StandEntity stand, ActionTarget struck, CrosshairRule rule, Predicate<Entity> canTarget) {
		if (!struck.isEmpty(stand.level())) {
			return struck;
		}
		LivingEntity user = stand.getUser();
		return pickWithThisOrUser(stand, user != null && !stand.aimsWithoutUser() ? user : null, 1.0F, canTarget, rule,
				StandSkillWalls.wallsHideEntities(stand));
	}

	public static ActionTarget strikeAim(StandEntity stand, ActionTarget struck, CrosshairRule rule) {
		return strikeAim(stand, struck, rule, entity -> entity != stand && canStandHit(stand, entity));
	}

	private static ActionTarget pickWithThisOrUser(StandEntity stand, @Nullable LivingEntity user, float partialTick,
			Predicate<Entity> canTarget, CrosshairRule rule, boolean wallsHideEntities) {
		ActionTarget aim = ActionTarget.EMPTY;
		if (user != null) {
			aim = userAimTarget(stand, user, partialTick, canTarget, wallsHideEntities, rule);
		}
		if (aim.getType() == TargetType.EMPTY && (user == null || aimAssist(stand.getUser()))) {
			aim = standPrecisionRayTarget(stand, canTarget, wallsHideEntities);
		}
		return aim;
	}

	public static ActionTarget pressTarget(StandEntity stand, ActionTarget sentByServer) {
		return pressTarget(stand, sentByServer, CrosshairRule.BLOCK_OR_ENTITY);
	}

	/**
	 * 1.16 StandEntity.setTask: a punch or heavy punch pressed at nothing takes the target of the action it replaces (that
	 * task's target, {@link EntityActionInstance#getTaskTarget}) when it keeps that target ({@link #keepsTargetInRange}).
	 * Server side; a client keeps what its server sent.
	 */
	public static ActionTarget takeOverIfPressedAtNothing(StandEntity stand, ActionTarget pressed,
			@Nullable EntityActionInstance prevAction, CrosshairRule rule) {
		Level level = stand.level();
		if (level.isClientSide() || prevAction == null || !pressed.isEmpty(level)) {
			return pressed;
		}
		ActionTarget previous = prevAction.getTaskTarget(level);
		return keepsTargetInRange(stand, previous, rule) ? previous.copy().resolveEntityId(level) : pressed;
	}

	/**
	 * The target a punch, heavy punch or barrage is pressed at: what the user's crosshair met (1.16
	 * EntityStandType.clientHitResult kept the vanilla pick); when it met nothing, the Stand's synced aim, which stands
	 * in for 1.16's precision ray there, or without the aim assist what the crosshair meets; under manual control the
	 * Stand's aim. 1.16 refused a pressed entity that is not living or that the Stand cannot attack
	 * (PowerBaseImpl.checkTarget, StandEntityAction.checkStandTarget, StandEntity.canAttack), kept a dying one
	 * (StandEntityTask.keepTarget, {@link #keepsEntity}) and, with the
	 * {@link CrosshairRule#ENTITY} rule, a pressed block, with or without the aim assist: the task had no target, so the
	 * Stand did not turn to it, and the attack aimed again when it struck. A client keeps what its server sent.
	 */
	public static ActionTarget pressTarget(StandEntity stand, ActionTarget sentByServer, CrosshairRule rule) {
		Level level = stand.level();
		if (level.isClientSide()) {
			return sentByServer;
		}
		LivingEntity user = stand.getUser();
		boolean userAims = user != null && !stand.aimsWithoutUser();
		boolean assist = aimAssist(user);
		var userAim = userAims ? LivingComponentAction.getAim(user) : null;
		if (userAim != null) {
			ActionTarget crosshair = userAim.getTarget().resolveEntityId(level);
			// clientHitResult took a vanilla pick of the Stand itself for a miss
			if (!crosshair.isEmpty(level) && crosshair.getEntity() != stand) {
				return pressedTarget(stand, crosshair, rule);
			}
		}
		if (userAims && !assist) {
			return rule.keeps(userAimTarget(stand, user, 1.0F, entity -> entity != stand && canStandHit(stand, entity)));
		}
		var standAim = LivingComponentAction.getAim(stand);
		return standAim != null
				? pressedTarget(stand, standAim.getTarget().resolveEntityId(level), rule) : ActionTarget.EMPTY;
	}

	private static ActionTarget pressedTarget(StandEntity stand, ActionTarget target, CrosshairRule rule) {
		return switch (target.getType()) {
			case ENTITY -> target.getEntity() != null && keepsEntity(stand, target.getEntity())
					? rule.keeps(target) : ActionTarget.EMPTY;
			case BLOCK -> throughWallsTarget(stand, rule.keeps(target), rule, true);
			default -> ActionTarget.EMPTY;
		};
	}

	public static ActionTarget throughWallsTarget(StandEntity stand, ActionTarget target, boolean precisionAim) {
		return throughWallsTarget(stand, target, precisionAim ? CrosshairRule.NONE : CrosshairRule.BLOCK_OR_ENTITY, true);
	}

	/**
	 * A target from a synced aim or a kept press, for an action marked through walls ({@link StandSkillWalls}): those
	 * aims were picked while no action ran, so a block among them may hide an entity behind it. The aim is picked again
	 * through walls, and the block stays only when that finds no entity the Stand can reach. The new pick looks from
	 * where the user, or the Stand that aims without it, looks now, not along the old aim: a wall kept from the key press
	 * (keep attack target, manual control) gives way to the entity the current aim meets, which need not be behind that
	 * wall once the aim has turned.
	 * @param wallBeforeEntityOutOfReach false: when the new pick meets an entity out of the Stand's reach, no target
	 *        instead of the block, for a move that still closes in on the entity
	 */
	public static ActionTarget throughWallsTarget(StandEntity stand, ActionTarget target, CrosshairRule rule,
			boolean wallBeforeEntityOutOfReach) {
		if (target.getType() != TargetType.BLOCK || StandSkillWalls.wallsHideEntities(stand)) {
			return target;
		}
		LivingEntity user = stand.getUser();
		ActionTarget entityBehind = aimWithThisOrUser(stand, user != null && !stand.aimsWithoutUser() ? user : null,
				1.0F, entity -> entity != stand && canStandHit(stand, entity), rule, wallBeforeEntityOutOfReach);
		if (entityBehind.getType() == TargetType.ENTITY) {
			return entityBehind;
		}
		return wallBeforeEntityOutOfReach || entityBehind.getType() != TargetType.EMPTY ? target : ActionTarget.EMPTY;
	}

	/**
	 * Whether the Stand's aims have 1.16's precision aim assist: the user's own "aim assist" Stand setting (the owner,
	 * 2026-10-15), on by default; a mob always has it. Off is the strict crosshair of 2026-09-13. Sneaking changes nothing.
	 */
	public static boolean aimAssist(@Nullable LivingEntity user) {
		return PlayerClientBroadcastedSettings.isStandAimAssistEnabled(user);
	}

	/**
	 * The user's own aim for a Stand attack. Without the aim assist, what the crosshair ray meets first from the user's
	 * eyes at the user's reach; blocks stop the ray. With it, as 1.16's client and server did together: the vanilla pick
	 * ({@link #crosshairPick} of any entity but the Stand) while the Stand can reach it and the attack takes it
	 * ({@link CrosshairRule}; EntityStandType.clientHitResult sent it, the action kept or refused it, aimWithThisOrUser
	 * kept a target in reach), otherwise 1.16's precision ray, also past an entity the attack refused. Walls follow the
	 * Stand's current action ({@link StandSkillWalls}), not an action of the user's own.
	 */
	public static ActionTarget userAimTarget(StandEntity stand, LivingEntity user, float partialTick, Predicate<Entity> canTarget) {
		return userAimTarget(stand, user, partialTick, canTarget, StandSkillWalls.wallsHideEntities(stand),
				CrosshairRule.BLOCK_OR_ENTITY);
	}

	private static ActionTarget userAimTarget(StandEntity stand, LivingEntity user, float partialTick,
			Predicate<Entity> canTarget, boolean wallsHideEntities, CrosshairRule rule) {
		if (!aimAssist(user)) {
			return strictCrosshairTarget(stand, user, partialTick, canTarget, wallsHideEntities);
		}
		if (rule != CrosshairRule.NONE) {
			ActionTarget crosshair = crosshairPick(stand, user, partialTick,
					entity -> entity != stand && canStandPick(stand, entity), wallsHideEntities);
			if (rule.takes(stand, crosshair, canTarget)) {
				return crosshair;
			}
		}
		return userPrecisionRayTarget(stand, user, canTarget, wallsHideEntities);
	}

	/**
	 * 1.16's vanilla pick (GameRenderer.pick), what a client's crosshair target is: the first entity or block on the
	 * crosshair ray, a player's entity within its entity interaction range (3 blocks in Survival, 5 in Creative) and a
	 * block within its block interaction range, else a miss even with a block behind; a mob looks as far as its reach.
	 */
	public static ActionTarget crosshairPick(StandEntity stand, LivingEntity user, float partialTick, Predicate<Entity> canTarget) {
		return crosshairPick(stand, user, partialTick, canTarget, StandSkillWalls.wallsHideEntities(stand));
	}

	private static ActionTarget crosshairPick(StandEntity stand, LivingEntity user, float partialTick,
			Predicate<Entity> canTarget, boolean wallsHideEntities) {
		double blockRange = user instanceof Player player ? player.blockInteractionRange() : userAimReach(user, stand);
		double entityRange = user instanceof Player player ? player.entityInteractionRange() : blockRange;
		double range = Math.max(blockRange, entityRange);
		Level level = stand.level();
		Vec3 eyes = user.getEyePosition(partialTick);
		ActionTarget hit = HitResultUtil.clip(eyes, user.getViewVector(partialTick), range, range, level, canTarget, user, 0,
				wallsHideEntities);
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
	 * EntityStandType.clientHitResult, the vanilla pick when it met a block or any entity but the Stand, a miss when it
	 * met the Stand itself, otherwise the precision ray from the user's eyes, and when that met nothing at all the Stand's
	 * own, as aimWithThisOrUser went on. Not limited to the Stand's reach, as the 1.16 pressed target was not; its readers
	 * check the reach. Without the aim assist, what the crosshair meets. (A client never picks its user's own Stand:
	 * StandEntity.isPickable, as 1.16's.)
	 */
	public static ActionTarget userStandAim(StandEntity stand, LivingEntity user, ActionTarget vanillaPick,
			Predicate<Entity> aimable) {
		Predicate<Entity> canTarget = aimable.and(entity -> entity != stand);
		if (!aimAssist(user)) {
			return strictCrosshairTarget(stand, user, 1.0F, canTarget, StandSkillWalls.wallsHideEntities(stand));
		}
		if (vanillaPick.getType() == TargetType.BLOCK) {
			return vanillaPick;
		}
		// JojoModUtil.isAnotherEntityTargeted: any entity but the Stand; the readers' own filters judge it
		Entity picked = vanillaPick.getType() == TargetType.ENTITY ? vanillaPick.getEntity() : null;
		if (picked != null) {
			return picked != stand ? vanillaPick : ActionTarget.EMPTY;
		}
		ActionTarget aim = userPrecisionRayTarget(stand, user, canTarget);
		return aim.getType() == TargetType.EMPTY
				? standPrecisionRayTarget(stand, canTarget, StandSkillWalls.wallsHideEntities(stand)) : aim;
	}

	/**
	 * The Stand's aim a client keeps while its user aims (ClientsideAim), whatever action the Stand runs: 1.16
	 * EntityStandType.clientHitResult's precision ray took any entity StandEntity.canTarget allowed
	 * ({@link #canUserAimTarget}), and the action's checkTarget then refused one it could not take.
	 */
	public static ActionTarget userStandAim(StandEntity stand, LivingEntity user, ActionTarget vanillaPick) {
		return userStandAim(stand, user, vanillaPick, entity -> canUserAimTarget(stand, entity));
	}

	/**
	 * 1.16 StandEntity.canTarget, over the pickable entities: any living entity but the Stand, its user and the Stand's
	 * own projectiles, friendly fire allowed.
	 */
	public static boolean canUserAimTarget(StandEntity stand, Entity entity) {
		return StandEntityAbility.canPickEntityForAiming(entity) && entity != stand && entity != stand.getUser()
				&& entity.isAlive() && !(entity instanceof Projectile projectile && projectile.getOwner() == stand);
	}

	/**
	 * The Stand's aim a client keeps while its user controls it manually, whatever action the Stand runs: 1.16's
	 * crosshair target with the Stand as the camera (InputHandler.pickMouseTarget, EntityStandType.clientHitResult), the
	 * vanilla pick from the Stand's eyes (a block within the player's block reach, any entity within its entity reach)
	 * when it met a block or an entity other than the Stand, otherwise the precision ray from the Stand as far as its own
	 * reach (getAimDistance under manual control) over any entity but the Stand, its user and their projectiles. Without
	 * the aim assist, what the Stand's crosshair line meets first within that reach. Its readers judge the target.
	 */
	public static ActionTarget manualStandAim(StandEntity stand, ActionTarget vanillaPick) {
		double reach = stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		boolean wallsHideEntities = StandSkillWalls.wallsHideEntities(stand);
		if (!aimAssist(stand.getUser())) {
			return StandPrecisionRay.crosshair(stand, stand, reach, true, wallsHideEntities);
		}
		if (vanillaPick.getType() == TargetType.BLOCK || vanillaPick.getType() == TargetType.ENTITY
				&& vanillaPick.getEntity() != null && vanillaPick.getEntity() != stand) {
			return vanillaPick;
		}
		return StandPrecisionRay.trace(stand, stand, reach, 0.0D, true, wallsHideEntities);
	}

	/**
	 * The Stand's aim a client keeps while its camera is an entity that is neither its user nor the Stand (the soul out of
	 * its body, Gold Experience's split consciousness, a puppet): 1.16's EntityStandType.clientHitResult with that camera
	 * entity, its vanilla pick when it met a block or an entity other than the Stand, a miss when it met the Stand,
	 * otherwise the precision ray from the camera over any entity but the Stand and its user, as far as getAimDistance of
	 * the camera: its own reach when it is a living entity that has one (a player), otherwise and under manual control the
	 * Stand's. Without the aim assist, what the camera's crosshair line meets first within that reach.
	 */
	public static ActionTarget cameraStandAim(StandEntity stand, Entity camera, ActionTarget vanillaPick) {
		double reach = !stand.isManuallyControlled() && camera instanceof LivingEntity living
				? userAimReach(living, stand) : stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		boolean wallsHideEntities = StandSkillWalls.wallsHideEntities(stand);
		if (!aimAssist(stand.getUser())) {
			return StandPrecisionRay.crosshair(camera, stand, reach, true, wallsHideEntities);
		}
		if (vanillaPick.getType() == TargetType.BLOCK) {
			return vanillaPick;
		}
		Entity picked = vanillaPick.getType() == TargetType.ENTITY ? vanillaPick.getEntity() : null;
		if (picked != null) {
			return picked != stand ? vanillaPick : ActionTarget.EMPTY;
		}
		return StandPrecisionRay.trace(camera, stand, reach, 0.0D, true, wallsHideEntities);
	}

	/**
	 * The Stand's aim a client keeps (ClientsideAim): 1.16's one mouse target, what the camera entity's crosshair met
	 * (InputHandler.pickMouseTarget, EntityStandType.clientHitResult), whatever action the Stand ran. Under manual control,
	 * while the Stand aims as itself (EntityActionInstance#aimAs), the Stand's crosshair ({@link #manualStandAim});
	 * otherwise its user's ({@link #userStandAim}), also while an action aims as the Stand, which turns to its own target
	 * on the server, and during a directional barrage, whose every hit aims again on the server (StandEntity.punch,
	 * aimWithThisOrUser without friendly fire). The vanilla pick counts only while the aiming entity is the camera. With
	 * a camera that is neither the user nor the Stand, that camera's crosshair ({@link #cameraStandAim}).
	 */
	public static ActionTarget clientStandAim(StandEntity stand, LivingEntity user, @Nullable Entity camera,
			ActionTarget vanillaPick) {
		boolean userIsCamera = camera == null || camera == user;
		if (!userIsCamera && camera != stand) {
			return cameraStandAim(stand, camera, vanillaPick);
		}
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
		AimingEntity aimAs = action != null ? action.aimAs : AimingEntity.CAMERA_ENTITY;
		boolean standAims = aimAs == AimingEntity.STAND || aimAs == AimingEntity.CAMERA_ENTITY && !userIsCamera;
		if (standAims && stand.isManuallyControlled()) {
			return manualStandAim(stand, camera == stand ? vanillaPick : ActionTarget.EMPTY);
		}
		return userStandAim(stand, user, userIsCamera ? vanillaPick : ActionTarget.EMPTY);
	}

	// the strict crosshair of 2026-09-13: from the user's eyes at the user's reach, blocks stopping the ray
	private static ActionTarget strictCrosshairTarget(StandEntity stand, LivingEntity user, float partialTick,
			Predicate<Entity> canTarget, boolean wallsHideEntities) {
		double reach = userAimReach(user, stand);
		return HitResultUtil.clip(user.getEyePosition(partialTick), user.getViewVector(partialTick),
				reach, reach, stand.level(), canTarget, user, 0, wallsHideEntities);
	}

	public static ActionTarget userPrecisionRayTarget(StandEntity stand, LivingEntity user) {
		return userPrecisionRayTarget(stand, user, entity -> entity != stand && canStandHit(stand, entity));
	}

	/**
	 * 1.16 StandEntity.precisionRayTrace(user, getAimDistance(user)): the user's reach, the Stand's precision hitboxes,
	 * and of several entities a player or Stand first, then a hostile mob, then the rest; else the block. An entity
	 * behind a block on the ray is not picked, measured to the nearest point of its own box (the owner keeps walls
	 * blocking the aim), unless the Stand's current action is marked through walls. As 1.16, sneaking does not change it.
	 */
	public static ActionTarget userPrecisionRayTarget(StandEntity stand, LivingEntity user, Predicate<Entity> canTarget) {
		return userPrecisionRayTarget(stand, user, canTarget, StandSkillWalls.wallsHideEntities(stand));
	}

	private static ActionTarget userPrecisionRayTarget(StandEntity stand, LivingEntity user, Predicate<Entity> canTarget,
			boolean wallsHideEntities) {
		return StandPrecisionRay.closestWithPriority(StandPrecisionRay.clipTargets(user,
				userAimReach(user, stand), canTarget, 0, stand.getPrecision(), wallsHideEntities));
	}

	// 1.16 precisionRayTrace(this, getAimDistance(getUser())) with walls as above; without the aim assist the crosshair.
	// getAimDistance took the Stand's own reach only under manual control: the 1.16 Purple Haze, aiming on its own while
	// mad, still looked as far as its user's reach
	private static ActionTarget standPrecisionRayTarget(StandEntity stand, Predicate<Entity> canTarget,
			boolean wallsHideEntities) {
		LivingEntity user = stand.getUser();
		double reach = user != null && !stand.isManuallyControlled()
				? userAimReach(user, stand) : stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		Predicate<Entity> filter = canTarget.and(entity -> entity != user);
		if (!aimAssist(user)) {
			return HitResultUtil.clip(stand.getEyePosition(), stand.getLookAngle(), reach, reach, stand.level(), filter, stand, 0,
					wallsHideEntities);
		}
		return StandPrecisionRay.closestWithPriority(StandPrecisionRay.clipTargets(stand, reach,
				filter, 0, stand.getPrecision(), wallsHideEntities));
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
