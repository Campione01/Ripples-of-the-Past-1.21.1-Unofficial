package rotp.core.impl.stands._entitybase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.JojoModConfig;
import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityLingeringSoundInstance;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.entity_projectile.BlockShardEntity;
import rotp.core.customobjects.explosion.CustomExplosion;
import rotp.core.init.ModCustomExplosions;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.network.s2c.BrokenBlocksParticlesAndSoundsPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.functions.MathUtil;
import rotp.core.util.functions_network.StreamCodecs;
import rotp.core.impl.stands.crazydiamond.CrazyDBlockBulletAbility;
import rotp.core.impl.stands.crazydiamond.brokenblocks.BrokenBlocksChunkData;
import rotp.core.impl.stands.crazydiamond.brokenblocks.PrevBlockInfo;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

public class StandEntityHeavyPunchAbility extends StandEntityAbility {
	@Nullable private Holder<SoundEvent> heavyPunchImpactSound;
	@Nullable private Holder<SoundEvent> heavyPunchCrySound;
	@Nullable private Holder<SoundEvent> heavyPunchPerformSound;
	private boolean detachedPunchKnockback;
	private boolean recoveryPhaseEntryTiming;

	public StandEntityHeavyPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, StandEntityHeavyPunch::new);
		usageGroup = AbilityUsageGroup.COMBAT;
		setDefaultPhaseLength(ActionPhase.WINDUP, StandStatFormulas.getHeavyAttackWindup(12, 0) /* 17 */);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
		noFinisherBarDecay = true;
		partsRequired(StandPart.ARMS);
	}

	public StandEntityHeavyPunchAbility heavyPunchImpactSound(Holder<SoundEvent> heavyPunchImpactSound) {
		this.heavyPunchImpactSound = heavyPunchImpactSound;
		return this;
	}

	public StandEntityHeavyPunchAbility heavyPunchCrySound(Holder<SoundEvent> heavyPunchCrySound) {
		this.heavyPunchCrySound = heavyPunchCrySound;
		return this;
	}

	public StandEntityHeavyPunchAbility heavyPunchPerformSound(Holder<SoundEvent> heavyPunchPerformSound) {
		this.heavyPunchPerformSound = heavyPunchPerformSound;
		return this;
	}

	public StandEntityHeavyPunchAbility initIsGrabVariation() {
		usageGroup = AbilityUsageGroup.GRAB;
		return this;
	}

	public StandEntityHeavyPunchAbility detachedPunchKnockback() {
		detachedPunchKnockback = true;
		return this;
	}

	public StandEntityHeavyPunchAbility recoveryPhaseEntryTiming() {
		recoveryPhaseEntryTiming = true;
		return this;
	}

	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower != null) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null && LivingComponentGrab.getEntityGrabbedBy(standEntity) != null) {
				return abilities.getContextVariationOrDisable(
						name(), "grab_heavy_punch");
			}
		}
		return super.replaceWithSubAbility(context, abilities);
	}

	// 1.16 StandEntityHeavyAttack.lockOnTargetPosition: the heavy punch turns the Stand to its target, never its offset
	@Override
	public boolean lockOnTargetPosition(@Nullable StandPower standPower, StandEntity standEntity, EntityActionInstance action) {
		return false;
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

	public static float calcExplosionRadius(StandEntity stand) {
		return Math.min((float) stand.getAttackDamage() * 0.175f, 10);
	}

	public static float calcExplosionDamage(StandEntity stand) {
		return (float) stand.getAttackDamage() * 0.4f;
	}

	/**
	 * 1.16 StandEntityPunch.doAttack + DamageUtil.disableShield: a disablesBlocking heavy hit that hurt a player
	 * with a raised shield puts it on a 100-tick cooldown.
	 */
	public static void disableShieldOnHit(LivingEntity target, boolean hurt, float chance) {
		if (hurt && target instanceof Player player && !player.level().isClientSide()
				&& player.getUseItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)
				&& player.getRandom().nextFloat() < chance) {
			player.getCooldowns().addCooldown(player.getUseItem().getItem(), 100);
			player.stopUsingItem();
			player.level().broadcastEntityEvent(player, (byte) 30);
		}
	}

	/**
	 * Whether blocks hide an entity from a Stand's body: both the line from the Stand's eyes to the entity's eyes (the
	 * vanilla line of sight) and the one to the centre of its box meet a block's collision shape. The bodily sweeps of
	 * ordinary Stands skip such an entity, as the original story stops their attacks at walls (owner, 2026-10-15).
	 */
	public static boolean wallHidesFromStand(StandEntity stand, Entity target) {
		Vec3 eyes = stand.getEyePosition();
		return wallBetween(stand, eyes, target.getEyePosition())
				&& wallBetween(stand, eyes, target.getBoundingBox().getCenter());
	}

	private static boolean wallBetween(StandEntity stand, Vec3 from, Vec3 to) {
		return HitResultUtil.clipBlocks(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, stand),
				stand.level()).getType() != HitResult.Type.MISS;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, 
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && performer instanceof StandEntity stand) {
			action.phasesLength.put(ActionPhase.WINDUP, StandStatFormulas.getHeavyAttackWindup(
					stand.getAttackSpeed(), stand.getFinisherMeter()));
			action.phasesLength.put(ActionPhase.RECOVERY, StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), stand.getFinisherMeter()));
		}
	}

	public static class StandEntityHeavyPunch extends EntityActionInstance {
		protected LivingEntity punchTarget;
		@Nullable private Entity keptEntity;
		public float finisherValue;
		private boolean finisherValueSynced;
		public boolean playedSwingSound;
		public boolean playedStandCrySound;
		/**
		 * Server: whether the entity hit of this heavy punch makes its impact sound (1.16 IPunch.playImpactSound: the
		 * hit landed, or the target is not living). Null when a hitEntity override did not say: the sound plays.
		 */
		@Nullable protected Boolean impactSoundDue;

		public StandEntityHeavyPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			aimAs = AimingEntity.STAND;
			if (performer instanceof StandEntity stand) {
				// a failed input that puts this punch back keeps the hand it took
				if (!isRestoringInputLifecycle()) {
					stand.alternateHands();
				}
				double minOffset = Math.min(0.5, stand.getEffectiveRange());
				double maxOffset = Math.min(2, stand.getMaxRange());
				ActionTarget target = setActionTargetSnapshot(StandEntityPunchAbility.pressTarget(
						stand, getActionTargetSnapshot(stand.level()), StandEntityPunchAbility.CrosshairRule.ENTITY));
				keptEntity = target.getType() == ActionTarget.TargetType.ENTITY
						? target.copy().resolveEntityId(stand.level()).getMainEntity() : null;
				setStandFrontOffsetFromTarget(stand, windupPlacementTarget(), minOffset, maxOffset);
				keepStandAimedAtTarget(target);
				if (standRotationTarget == null) {
					aimAs = AimingEntity.CAMERA_ENTITY;
				}
				StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
				Level level = performer.level();
				if (isGrabVariation() && stand.offsetFromUser.grabIdleOffset != null) {
					stand.offsetFromUser.setOffset(stand.offsetFromUser.grabIdleOffset, StandOffsetFromUser.Rotations.HEAD);
				}
				// a failed input that puts this punch back keeps the finisher it took and spent when it was first set
				if (usesHeavyFinisherSetup() && !isRestoringInputLifecycle()) {
					if (!level.isClientSide()) {
						stand.setHeavyPunchFinisher();
						finisherValue = stand.getLastHeavyFinisherValue();
						phasesLength.put(ActionPhase.RECOVERY, StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), finisherValue));
						stand.addFinisherMeter(-0.51F, 0);
					}
					// the client gets the action before the Stand's new snapshot: the server's value and recovery come
					// with the action (toBuf, phase lengths), as 1.16's onClick took it on the client too
					else if (!finisherValueSynced) {
						finisherValue = stand.getLastHeavyFinisherValue();
					}
				}
			}
		}

		protected boolean usesHeavyFinisherSetup() {
			return true;
		}

		// 1.16 StandEntityHeavyAttack.getOffsetFromUser: front3dOffset of the task target, which through the windup was the
		// entity the press kept (standKeepsTarget), a dying one too until the windup dropped it, or nothing, and then the
		// user's look placed the Stand
		private ActionTarget windupPlacementTarget() {
			return keptEntity != null && !keptEntity.isRemoved() && keptEntity.level() == performer.level()
					? new ActionTarget(keptEntity) : ActionTarget.EMPTY;
		}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			super.toBuf(buf);
			buf.writeFloat(finisherValue);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			super.fromBuf(buf);
			finisherValue = buf.readFloat();
			finisherValueSynced = true;
		}

		// 1.16 StandEntityHeavyAttack.standTickWindup: the swing sound with 3 windup ticks left
		protected boolean heavySwingSoundDue() {
			// how many ticks are left before the start of the 'perform' phase (when actionPerformStart() is called)
			int ticksDiff = (int) (calcFullTicks(ActionPhase.PERFORM, 0) - getFullTicksPassed());
			return !playedSwingSound && ticksDiff <= 3;
		}

		// 1.16 StandEntityHeavyAttack.clPlayPunchSwingSound
		protected float getHeavySwingPitch(StandEntity stand) {
			return 0.65F + stand.getRandom().nextFloat() * 0.2F;
		}

		private boolean samplesRecoveryPhaseEntry(ActionPhase newPhase) {
			return newPhase == ActionPhase.RECOVERY && phase != null && phase != newPhase
					&& performer instanceof StandEntity && !performer.level().isClientSide()
					&& !isGrabVariation() && usesHeavyFinisherSetup()
					&& ability instanceof StandEntityHeavyPunchAbility heavy && heavy.recoveryPhaseEntryTiming;
		}

		@Override
		public void setPhase(ActionPhase newPhase, int tick) {
			boolean samplesEntry = samplesRecoveryPhaseEntry(newPhase);
			float previousLength = samplesEntry ? phasesLength.getFloat(newPhase) : 0;
			super.setPhase(newPhase, tick);
			if (samplesEntry && Float.compare(previousLength, phasesLength.getFloat(newPhase)) != 0) {
				syncPhaseChanges();
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (samplesRecoveryPhaseEntry(newPhase) && performer instanceof StandEntity stand) {
				phasesLength.put(newPhase, StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), finisherValue));
			}
			// from the recovery on the user's aim is what the next press takes (1.16 EntityStandType.clientHitResult)
			if (newPhase == ActionPhase.RECOVERY) {
				aimAs = AimingEntity.CAMERA_ENTITY;
			}
		}

		@Override
		public void actionTick() {
			Level level = performer.level();
			if (performer instanceof StandEntity stand) {
				StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
				// 1.16 StandEntityTask.tick dropped the task target that left the action's range on every tick of the task, on
				// the server only, and StandEntityHeavyAttack.getOffsetFromUser kept the Stand's distance from it through the
				// windup, so it steps after one that backs away
				if (!takesTargetsFromServer() && !isGrabVariation()) {
					releaseTargetOutOfRange(stand);
				}
				if (getPhase() == ActionPhase.WINDUP && usesHeavyFinisherSetup() && !isGrabVariation()
						&& !level.isClientSide()) {
					StandEntityBarrageAbility.refreshFrontOffset(this, stand, windupPlacementTarget(),
							Math.min(0.5, stand.getEffectiveRange()), Math.min(2, stand.getMaxRange()));
				}
			}
			if (level.isClientSide() && !(playedSwingSound && playedStandCrySound)
					&& performer instanceof StandEntity stand && ClientGlobals.canHearStand(stand)) {
				if (heavySwingSoundDue()) {
					level.playLocalSound(stand.getX(), stand.getEyeY(), stand.getZ(), ClientsideSoundsHelper.withStandSkin(
							ModSoundEvents.STAND_PUNCH_HEAVY_SWING.get(), stand), 
							stand.getSoundSource(), 1, getHeavySwingPitch(stand), false);
					playedSwingSound = true;
				}

				if (!playedStandCrySound) {
					if (!stand.isArmsOnlyMode()) {
						ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
								getHeavyPunchCrySound(), stand), 
								stand.getSoundSource(), 1, 1, stand, stand.level()));
					}
					playedStandCrySound = true;
				}
			}

			if (isGrabVariation() && punchTarget == null) {
				int ticksDiff = (int) (calcFullTicks(ActionPhase.PERFORM, 0) - getFullTicksPassed());
				if (ticksDiff <= 4) {
					LivingComponentGrab standGrab = performer.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
					if (standGrab != null) {
						LivingEntity grabbed = standGrab.getGrabbedEntity();
						if (grabbed != null) {
							punchTarget = grabbed;
							if (!level.isClientSide()) {
								standGrab.setGrabTarget(null);
								grabbed.setDeltaMovement(0, 0.4, 0);
								grabbed.hurtMarked = true;
							}
						}
					}
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
					playHeavyPunchPerformSound(stand);

					boolean entityTarget = target.getType() == TargetType.ENTITY;
					Vec3 impactSoundPos = target.isEmpty(level) ? null : target.getCenterPos();
					// 1.16 HeavyPunchBlockInstance.playImpactSound: a block always sounds, whatever its hardness
					if (target.getType() == TargetType.BLOCK && impactSoundPos != null) {
						StandUtil.broadcastSound((ServerLevel) level, impactSoundPos, 
								getHeavyPunchImpactSound(target), true, standPower,
								stand.getSoundSource(), 1, 1);
					}
					DamageSource dmgSource = makePunchDamageSource();
					((DamageSourceModified) dmgSource).jojo_ripples$setStandInvulTicks(10);
					float dmgAmount = StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage());
					float explRadius = calcExplosionRadius(stand);

					lastDamageDealtToLiving = 0;
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
								getHeavyPunchImpactSound(target), true, standPower,
								stand.getSoundSource(), 1, 1);
					}

					punchedTarget = target;
					standPower.consumeStamina(50);
				}
				// the server's strike set the targets by 1.16's task rule (getPunchTarget), again here for an override of
				// its own; a client sets them from the target it holds
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
				StandEntityPunchAbility.releaseUnlockedPunchTarget(stand, this);
			}
		}
		
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand, 
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			Entity targetEntity = target.getMainEntity();
			boolean hurt = false;
			if (targetEntity instanceof LivingEntity targetLiving) {
				if (!usesDetachedPunchKnockback()) {
					addKnockback(dmgSource);
				}
				boolean nonLethal = getPunchModifiers().stream().anyMatch(modifier -> modifier.makesAttackNonLethal(targetLiving));
				if (nonLethal) {
					((DamageSourceModified) dmgSource).jojo_ripples$setNonLethal(true);
				}
				float healthBeforeHit = targetLiving.getHealth();
				hurt = usesDetachedPunchKnockback()
						? attemptHeavyPunch(stand, targetLiving, targetEntity, dmgSource, dmgAmount, true)
						: standEntityAttack(stand, targetLiving, dmgSource, dmgAmount);
				lastDamageDealtToLiving = hurt ? Math.max(healthBeforeHit - targetLiving.getHealth(), 0) : 0;
				afterHeavyPunchHit(stand, targetLiving, dmgSource, dmgAmount, hurt);

				if (hurt) {
					// 1.16 HeavyPunchInstance.afterAttack: a Stand action that stops on a heavy attack (a barrage) goes into recovery.
					EntityActionAbility.onHitByHeavyAttack(targetLiving);
				}
			}
			else if (targetEntity != null) {
				if (usesDetachedPunchKnockback()) {
					hurt = attemptHeavyPunch(stand, targetEntity, targetEntity, dmgSource, dmgAmount, true);
				}
				else {
					// 1.16 StandEntityPunch.knockbackTarget: every heavy punch sets an admitted non-living target's
					// motion, with the subclass's additional knockback and angles, before the wall impact is armed
					addKnockback(dmgSource);
					DamageSourceModified knockback = (DamageSourceModified) dmgSource;
					float halfAdditional = Math.max(knockback.jojo_ripples$addKnockback(), 0) * 0.5F;
					float xRot = knockback.jojo_ripples$knockbackXRotDeg();
					float yRot = knockback.jojo_ripples$knockbackYRotDeg();
					boolean admitted = dmgAmount > 0 && stand.canAttackEntity(targetEntity);
					hurt = standEntityAttack(stand, targetEntity, dmgSource, dmgAmount);
					if (admitted) {
						applyDetachedPunchKnockback(stand, targetEntity, targetEntity, halfAdditional, xRot, yRot, hurt);
					}
				}
			}
			impactSoundDue = hurt || !(targetEntity instanceof LivingEntity);
			if (hurt) {
				Entity knockedBack = targetEntity;
				if (targetEntity instanceof StandEntity targetStand) {
					LivingEntity standUser = targetStand.getUser();
					if (standUser != null) {
						knockedBack = standUser;
					}
				}

				Entity _knockedBack = knockedBack;
				KnockbackCollisionImpact kbImpact = KnockbackCollisionImpact.getHandler(_knockedBack);
				if (kbImpact != null) {
					kbImpact
					.onPunchSetKnockbackImpact(_knockedBack.getDeltaMovement(), stand)
					.withImpactExplosion(Math.max(explRadius - 0.5f, 0), null, 0);
				}
			}
		}

		protected boolean usesDetachedPunchKnockback() {
			return ability instanceof StandEntityHeavyPunchAbility heavy && heavy.detachedPunchKnockback;
		}

		protected boolean attemptHeavyPunch(StandEntity stand, Entity target, Entity primaryTarget,
				DamageSource source, float rawDamage, boolean mainTarget) {
			addKnockback(source);
			if (!usesDetachedPunchKnockback()) {
				return standEntityAttack(stand, target, source, rawDamage, mainTarget);
			}
			DamageSourceModified modified = (DamageSourceModified) source;
			float halfAdditional = Math.max(modified.jojo_ripples$addKnockback(), 0) * 0.5F;
			float xRot = modified.jojo_ripples$knockbackXRotDeg();
			float yRot = modified.jojo_ripples$knockbackYRotDeg();
			modified.jojo_ripples$modifyKnockback(0, modified.jojo_ripples$knockbackMultiplier());
			modified.jojo_ripples$knockbackXRot(0);
			modified.jojo_ripples$knockbackYRot(0);
			modified.jojo_ripples$setKnockbackXRotAppliedStrength(0);

			// Read admission before the one virtual hurt call; its Stand raw-history gate is mutating.
			boolean admitted = rawDamage > 0 && stand.canAttackEntity(target)
					&& (!(target instanceof LivingEntity living) || !StandEntity.isStandAttackInvulnerable(living));
			boolean hurt = standEntityAttack(stand, target, source, rawDamage, mainTarget);
			if (admitted) {
				if (target instanceof LivingEntity living) {
					if (hurt) {
						applyDetachedPunchKnockback(stand, target, living, halfAdditional, xRot, yRot, true);
					}
					afterDetachedPunchHurt(stand, living, source, rawDamage, hurt);
				}
				// 1.16 uses the primary position here, including a Kick's secondary attempts.
				applyDetachedPunchKnockback(stand, primaryTarget, target, halfAdditional, xRot, yRot, hurt);
			}
			return hurt;
		}

		protected void afterDetachedPunchHurt(StandEntity stand, LivingEntity target,
				DamageSource source, float rawDamage, boolean hurt) {
		}

		private static void applyDetachedPunchKnockback(StandEntity stand, Entity directionTarget, Entity target,
				float strength, float xRot, float yRot, boolean hurt) {
			if (strength <= 0) {
				return;
			}
			Vec3 toTarget = directionTarget.position().subtract(stand.position());
			float yaw = (float) -Mth.atan2(toTarget.x, toTarget.z) * MathUtil.RAD_TO_DEG + yRot;
			float radians = MathUtil.DEG_TO_RAD;
			if (target instanceof LivingEntity living) {
				if (Math.abs(xRot) < 90) {
					living.knockback(strength * Mth.cos(xRot * radians), Mth.sin(yaw * radians), -Mth.cos(yaw * radians));
				}
				if (xRot != 0) {
					applyDetachedUpwardKnockback(living, -strength * Mth.sin(xRot * radians), hurt);
				}
			}
			else {
				Vec3 velocity = new Vec3(0, 0, strength);
				if (xRot != 0) {
					velocity = velocity.xRot(-xRot * radians);
				}
				target.setDeltaMovement(velocity.yRot(-yaw * radians));
			}
		}

		// 1.16 DamageUtil.upwardsKnockback never marked the target: only a hit that landed (and so marked it in its
		// hurt) sends the push to a player's own client, not one blocked by a shield
		private static void applyDetachedUpwardKnockback(LivingEntity target, float strength, boolean hurt) {
			strength *= 1.0F - (float) target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			if (strength != 0) {
				target.setDeltaMovement(target.getDeltaMovement().add(0, strength, 0));
				if (hurt) {
					target.hurtMarked = true;
				}
			}
			if (target instanceof StandEntity stand) {
				LivingEntity user = stand.getUser();
				if (user != null && !user.is(target)) {
					applyDetachedUpwardKnockback(user, strength, hurt);
				}
			}
		}

		protected Holder<SoundEvent> getHeavyPunchImpactSound(ActionTarget target) {
			if (ability instanceof StandEntityHeavyPunchAbility heavyPunchAbility && heavyPunchAbility.heavyPunchImpactSound != null) {
				return heavyPunchAbility.heavyPunchImpactSound;
			}
			return ModSoundEvents.STAND_PUNCH_HEAVY;
		}

		protected SoundEvent getHeavyPunchCrySound() {
			if (ability instanceof StandEntityHeavyPunchAbility heavyPunchAbility && heavyPunchAbility.heavyPunchCrySound != null) {
				return heavyPunchAbility.heavyPunchCrySound.value();
			}
			return ModSoundEvents.STAND_PUNCH_HEAVY_CRY.get();
		}

		protected void playHeavyPunchPerformSound(StandEntity stand) {
			if (ability instanceof StandEntityHeavyPunchAbility heavyPunchAbility && heavyPunchAbility.heavyPunchPerformSound != null) {
				StandUtil.playStandEntitySound(stand, heavyPunchAbility.heavyPunchPerformSound, 1.0F, 1.0F);
			}
		}

		protected void afterHeavyPunchHit(StandEntity stand, LivingEntity targetLiving, DamageSource dmgSource, float dmgAmount, boolean hurt) {
		}
		
		protected void addKnockback(DamageSource dmgSource) {
			DamageSourceModified knockback = (DamageSourceModified) dmgSource;
			StandEntity stand = (StandEntity) performer;
			knockback.jojo_ripples$modifyKnockback(getAdditionalHeavyPunchKnockback(stand), 1);
		}

		protected float getAdditionalHeavyPunchKnockback(StandEntity stand) {
			return StandStatFormulas.getHeavyAttackKnockback(
					stand.getAttackDamage(), finisherValue);
		}
		
		protected void hitBlock(ActionTarget target, Level level, StandEntity stand, 
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			BlockPos blockPos = target.getBlockPos();
			Direction face = target.getFace();
			// 1.16 HeavyPunchBlockInstance.doHit: the plain block punch (StandEntity.breakBlock) comes before the blast
			StandEntityPunchAbility.StandEntityPunch.breakStandBlock(level, blockPos, stand, !isUserCreative());
			Vec3 pos = Vec3.atCenterOf(blockPos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.6));
			DamageSource aoeDmgSource = makeBlastDamageSource();
			float aoeDmg = calcExplosionDamage(stand);
			HeavyPunchExplosion explosion = new HeavyPunchExplosion(level, stand, 
					new ActionTarget(blockPos, face), stand.getLookAngle(), 
					aoeDmgSource, 
					pos.x, pos.y, pos.z, 
					explRadius, false, 
					JojoModUtil.breakingBlocksEnabled(level) ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.KEEP)
					.aoeDamage(aoeDmg)
					.createBlockShards(stand.getAttackDamage(), stand.getPrecision());
			CustomExplosion.explode(explosion);
		}

		/**
		 * 1.16 StandEntityHeavyAttack.explosionDmgSource: a fresh source of the Stand's power flagged as an explosion,
		 * without the punch's Stand invulnerability ticks.
		 */
		protected DamageSource makeBlastDamageSource() {
			DamageSource dmgSource = new DamageSource(DamageUtil.type(performer.level(), ModDamageTypes.STAND_EXPLOSION), performer);
			if (dmgSource instanceof DamageSourceModified modified) {
				modified.jojo_ripples$setStandPower(StandPower.get(getPowerUser()));
			}
			return dmgSource;
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return getPhase() == ActionPhase.RECOVERY && finisherValue >= 0.5F;
		}

		/**
		 * The target the heavy punch would strike now. Through the windup the target it was pressed at stays its 1.16 task
		 * target while within the action's range ({@link StandEntityPunchAbility#keepsTargetInRange}), even out of the
		 * Stand's reach for a while, and no new aim takes its place; the strike takes it if in reach, otherwise aims again
		 * (StandEntity.punch, aimWithThisOrUser), and the strike makes it the target the heavy punch keeps and turns to by
		 * the task rule ({@link #takeStrikeTarget}): a struck block (StandEntityHeavyAttack.standKeepsTarget) or entity that is
		 * not living is refused and leaves the targets as they were. Only the server judges it: a client strikes at the
		 * target the server last sent it, the kept one or the strike's own once the server sent it.
		 */
		protected ActionTarget getPunchTarget(StandEntity stand) {
			if (isGrabVariation()) {
				return new ActionTarget(punchTarget);
			}
			if (takesTargetsFromServer()) {
				return getActionTargetSnapshot(stand.level());
			}
			boolean windup = getPhase() == ActionPhase.WINDUP;
			ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(stand, getActionTargetSnapshot(stand.level()),
					StandEntityPunchAbility.shouldRetainPunchTarget(stand, ability),
					StandEntityPunchAbility.CrosshairRule.ENTITY);
			if (isStartingPerform()) {
				takeStrikeTarget(target, keepsStrikeTarget(stand, target));
			}
			else if (!windup) {
				setActionTargetSnapshot(target);
			}
			return target;
		}

		/**
		 * What this attack keeps as its target through the task and of its strike (1.16 standKeepsTarget):
		 * StandEntityHeavyAttack only an entity.
		 */
		protected StandEntityPunchAbility.CrosshairRule keptTargetRule() {
			return StandEntityPunchAbility.CrosshairRule.ENTITY;
		}

		// 1.16 StandEntityTask.keepTarget of the struck target: a block by the attack's standKeepsTarget, never an entity
		// that is not living (checkStandTarget)
		protected final boolean keepsStrikeTarget(StandEntity stand, ActionTarget struck) {
			return StandEntityPunchAbility.keepsTargetInRange(stand, struck, keptTargetRule());
		}

		@Override
		protected boolean sendsPerformStartTargets() {
			return true;
		}

		// the target the heavy punch keeps is its task's (1.16 StandEntity.punch), which a next action may take over
		@Override
		public ActionTarget getTaskTarget(Level level) {
			return heldTarget(actionTargetSnapshot, level);
		}

		// 1.16 StandEntityTask.tick, on every server tick of the task: setTaskTarget(EMPTY) once keepTarget failed, sent to
		// tracking clients; the Stand stops turning to the target. Read as held, so that a block broken since (air) or an
		// entity removed is dropped too.
		private void releaseTargetOutOfRange(StandEntity stand) {
			if (actionTargetSnapshot != null
					&& !StandEntityPunchAbility.keepsTargetInRange(stand, actionTargetSnapshot, keptTargetRule())) {
				setActionTargetSnapshot(null);
				keptEntity = null;
				standRotationTarget = null;
				aimAs = AimingEntity.CAMERA_ENTITY;
				syncTargetsToTracking();
			}
		}

	}



	public static class HeavyPunchExplosion extends CustomExplosion {
		protected LivingEntity attacker;
		@Nullable protected StandEntity attackerAsStand;
		protected ActionTarget hitBlock;
		protected Vec3 explosionDirection;
		protected float aoeDamage;
		public boolean dropBlocks;

		protected boolean createBlockShards = false;
		protected double strength;
		protected double precision;
		protected List<Entity> noDamage = new ArrayList<>();


		public HeavyPunchExplosion(Level pLevel, double pToBlowX, double pToBlowY, double pToBlowZ, float pRadius) {
			super(pLevel, pToBlowX, pToBlowY, pToBlowZ, pRadius);
		}

		public HeavyPunchExplosion(Level pLevel, LivingEntity attacker, 
				ActionTarget hitBlock, Vec3 direction, 
				@Nullable DamageSource pDamageSource, 
				double pToBlowX, double pToBlowY, double pToBlowZ, 
				float pRadius, boolean pFire, Explosion.BlockInteraction pBlockInteraction) {
			super(pLevel, attacker, 
					pDamageSource, 
					pToBlowX, pToBlowY, pToBlowZ, 
					pRadius, pFire, pBlockInteraction, 
					null, null, null);
			this.attacker = attacker;
			this.attackerAsStand = attacker instanceof StandEntity ? (StandEntity) attacker : null;
			this.hitBlock = hitBlock;
			this.explosionDirection = direction.normalize();
		}

		public HeavyPunchExplosion createBlockShards(double strength, double precision) {
			this.createBlockShards = true;
			this.strength = strength;
			this.precision = precision;
			return this;
		}

		public HeavyPunchExplosion aoeDamage(float damage) {
			this.aoeDamage = damage;
			return this;
		}

		public HeavyPunchExplosion entityNoDamage(Entity entityNoDamage) {
			this.noDamage.add(entityNoDamage);
			return this;
		}


		@Override
		public float getEntityDamageAmount(Entity entity, double impact) {
			return aoeDamage;
		}

		@Override
		public Optional<Float> getBlockExplosionResistance(BlockGetter pLevel, BlockPos pPos, BlockState pBlockState, FluidState pFluidState) {
			return super.getBlockExplosionResistance(pLevel, pPos, pBlockState, pFluidState);
		}

		@Override
		public boolean shouldBlockExplode(BlockGetter pLevel, BlockPos pPos, BlockState pBlockState, float pExplosionPower) {
			return pBlockState.getBlock() != Blocks.SPAWNER;
		}


		@Override
		public void finalizeExplosion(boolean pSpawnParticles) {
			super.finalizeExplosion(pSpawnParticles);
			remainingBlocksShockWave();
		}

		@Override
		protected void explodeBlocks() {
			if (level instanceof ServerLevel world) {
				List<BlockPos> toBlow = getToBlow();
				LivingEntity standUser = StandUtil.getStandUser(attacker);

				Map<BlockPos, BlockShardEntity[]> blockShardEntities = new HashMap<>();
				if (createBlockShards) {
					RandomSource random = attacker.getRandom();
					float shardsVelocity = 0.5f + (float) strength * 0.05f;
					double shardsInaccuracy = Math.max(100 - precision * 4.5, 0);

					shardsInaccuracy = Math.min(shardsInaccuracy * 0.0075, 1);
					Vec3 vecMaxAccuracy = explosionDirection.normalize();

					for (BlockPos blockPos : toBlow) {
						BlockState blockState = level.getBlockState(blockPos);
						if (CrazyDBlockBulletAbility.hardMaterial(blockState)) {
							BlockShardEntity[] shards = new BlockShardEntity[3];
							for (int i = 0; i < shards.length; i++) {
								BlockShardEntity blockShard = new BlockShardEntity(attacker, level, blockState, blockPos);
								blockShard.setPos(
										blockPos.getX() + random.nextDouble(),
										blockPos.getY() + random.nextDouble(),
										blockPos.getZ() + random.nextDouble());

								Vec3 vecMinAccuracy = blockShard.position().subtract(this.center()).normalize();
								Vec3 shootVec = new Vec3(
										Mth.lerp(shardsInaccuracy, vecMaxAccuracy.x, vecMinAccuracy.x),
										Mth.lerp(shardsInaccuracy, vecMaxAccuracy.y, vecMinAccuracy.y),
										Mth.lerp(shardsInaccuracy, vecMaxAccuracy.z, vecMinAccuracy.z));

								blockShard.shoot(shootVec.x, shootVec.y, shootVec.z, shardsVelocity, 4);
								shards[i] = blockShard;
							}
							blockShardEntities.put(blockPos, shards);
						}
					}
				}

				dropBlocks = JojoModUtil.dropBrokenBlock(standUser);
				JojoModUtil.destroyBlocksInBulk(toBlow, world, attacker, dropBlocks);

				if (!blockShardEntities.isEmpty()) {
					for (Map.Entry<BlockPos, BlockShardEntity[]> blockShards : blockShardEntities.entrySet()) {
						BlockPos pos = blockShards.getKey();
						BlockShardEntity[] shards = blockShards.getValue();

						for (Entity blockShard : shards) {
							level.addFreshEntity(blockShard);
						}

						BrokenBlocksChunkData brokenBlocks = BrokenBlocksChunkData.getChunkData(level, pos);
						if (brokenBlocks != null) {
							PrevBlockInfo brokenBlock = brokenBlocks.getBrokenBlockAt(pos);
							if (brokenBlock != null) {
								brokenBlock.withEntities(shards);
							}
						}
					}
				}
			}
		}

		@Override
		protected void filterEntities(List<Entity> entities) {
			Iterator<Entity> iter = entities.iterator();
			while (iter.hasNext()) {
				Entity entity = iter.next();
				if (!(entity instanceof LivingEntity && JojoModUtil.canHarm(attacker, entity)) || noDamage.contains(entity)) {
					iter.remove();
				}
			}
		}

		@Override
		protected void hurtEntity(Entity entity, float damage, Vec3 knockback) {
			if (attackerAsStand != null) {
				// 1.16 StandEntityHeavyAttack: the explosion used hurtTarget, no shield wear
				hurtTarget(attackerAsStand, entity, damageSource, damage);

				entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
				if (entity instanceof Player player) {
					if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
						getHitPlayers().put(player, knockback);
					}
				}
			}
		}

		/**
		 * 1.16 StandEntity.hurtTarget: the Stand damage multiplier through the target's hurt cooldown, without the
		 * Stand-attack immunity the punches check and set, then the usual aggro and enchantment hooks.
		 */
		private static boolean hurtTarget(StandEntity stand, Entity target, DamageSource source, float damage) {
			float multiplier = JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get().floatValue();
			boolean hurt = DamageUtil.hurtThroughInvulTicks(target, source, damage * multiplier);
			if (hurt && target.level() instanceof ServerLevel serverLevel) {
				LivingEntity user = stand.getUser();
				if (user != null && target instanceof LivingEntity targetLiving) {
					LivingEntity aggroTo = stand.isFollowingUser() || targetLiving.hasLineOfSight(user) ? user
							: StandUtil.isEntityStandUser(targetLiving) ? stand : null;
					if (aggroTo != null && aggroTo != source.getEntity()) {
						Brain<?> brain = targetLiving.getBrain();
						Optional<LivingEntity> attackTarget = brain.getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
						if (attackTarget != null && attackTarget.filter(t -> t == source.getEntity()).isPresent()) {
							brain.setMemory(MemoryModuleType.ATTACK_TARGET, aggroTo);
						}
					}
				}
				EnchantmentHelper.doPostAttackEffects(serverLevel, target, source);
			}
			return hurt;
		}

		@Override
		protected void lithiumPerformRayCast(RandomSource random, double vecX, double vecY, double vecZ, LongOpenHashSet touched) {
			// only break blocks in the direction of the punch, not behind the stand
			if (vecX * explosionDirection.x + vecY * explosionDirection.y + vecZ * explosionDirection.z >= 0) {
				super.lithiumPerformRayCast(random, vecX, vecY, vecZ, touched);
			}
		}

		protected void remainingBlocksShockWave() {
			if (!level.isClientSide()) {
				BrokenBlocksParticlesAndSoundsPacket blocksShockwaveVisual = new BrokenBlocksParticlesAndSoundsPacket();
				Vec3 pos = center();
				double radius = this.radius;
				int minX = Mth.floor(pos.x - radius);
				int minY = Mth.floor(pos.y - radius);
				int minZ = Mth.floor(pos.z - radius);
				int maxX = Mth.ceil(pos.x + radius);
				int maxY = Mth.ceil(pos.y + radius);
				int maxZ = Mth.ceil(pos.z + radius);
				boolean test = true;
				JojoModUtil.iterateOverBlocks(minX, minY, minZ, maxX, maxY, maxZ, blockPos -> {
					if (test || pos.distanceToSqr(blockPos.getX() + 0.5, blockPos.getX() + 0.5, blockPos.getX() + 0.5) > radius + 0.5) {
						BlockState blockState = level.getBlockState(blockPos);
						if (!blockState.isAir()) {
							blocksShockwaveVisual.addBlock(blockPos.mutable(), blockState);
						}
					}
				});
				blocksShockwaveVisual.sendToPlayers((ServerLevel) level, minX, minY, minZ, maxX, maxY, maxZ);
			}
		}

		@Override
		protected void playSound() {}

		@Override
		protected void spawnParticles() {}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			StreamCodecs.VEC_3D_APPROX.encode(buf, explosionDirection);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			explosionDirection = StreamCodecs.VEC_3D_APPROX.decode(buf);
		}

		@Override
		public ResourceLocation getExplosionType() {
			return ModCustomExplosions.STAND_HEAVY_PUNCH;
		}
	}

}
