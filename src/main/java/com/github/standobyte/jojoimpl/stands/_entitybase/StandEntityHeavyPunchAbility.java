package com.github.standobyte.jojoimpl.stands._entitybase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityLingeringSoundInstance;
import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.customobjects.entity_projectile.BlockShardEntity;
import com.github.standobyte.jojo.customobjects.explosion.CustomExplosion;
import com.github.standobyte.jojo.init.ModCustomExplosions;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.mechanics.KnockbackCollisionImpact;
import com.github.standobyte.jojo.network.s2c.BrokenBlocksParticlesAndSoundsPacket;
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
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.subsystems.entity_grab.LivingComponentGrab;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions_network.StreamCodecs;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDBlockBulletAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.BrokenBlocksChunkData;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.PrevBlockInfo;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class StandEntityHeavyPunchAbility extends StandEntityAbility {
	@Nullable private Holder<SoundEvent> heavyPunchImpactSound;
	@Nullable private Holder<SoundEvent> heavyPunchCrySound;
	@Nullable private Holder<SoundEvent> heavyPunchPerformSound;

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
		public float finisherValue;
		public boolean playedSwingSound;
		public boolean playedStandCrySound;

		public StandEntityHeavyPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			aimAs = AimingEntity.STAND;
			if (performer instanceof StandEntity stand) {
				stand.alternateHands();
				double minOffset = Math.min(0.5, stand.getEffectiveRange());
				double maxOffset = Math.min(2, stand.getMaxRange());
				ActionTarget target = captureActionTargetFromAim(stand);
				setStandFrontOffsetFromTarget(stand, target, minOffset, maxOffset);
				keepStandAimedAtTarget(target);
				Level level = performer.level();
				if (isGrabVariation() && stand.offsetFromUser.grabIdleOffset != null) {
					stand.offsetFromUser.setOffset(stand.offsetFromUser.grabIdleOffset, StandOffsetFromUser.Rotations.HEAD);
				}
				if (!level.isClientSide()) {
					stand.setHeavyPunchFinisher();
				}
				finisherValue = stand.getLastHeavyFinisherValue();
				phasesLength.put(ActionPhase.RECOVERY, StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), finisherValue));
				if (!level.isClientSide()) {
					stand.addFinisherMeter(-0.51F, 0);
				}
			}
		}

		@Override
		public void actionTick() {
			Level level = performer.level();
			if (level.isClientSide() && !(playedSwingSound && playedStandCrySound)
					&& performer instanceof StandEntity stand && ClientGlobals.canHearStand(stand)) {
				if (!playedSwingSound) {
					// how many ticks are left before the start of the 'perform' phase (when actionPerformStart() is called)
					int ticksDiff = (int) (calcFullTicks(ActionPhase.PERFORM, 0) - getFullTicksPassed());
					if (ticksDiff <= 4) {
						level.playLocalSound(stand.getX(), stand.getEyeY(), stand.getZ(), ClientsideSoundsHelper.withStandSkin(
								ModSoundEvents.STAND_PUNCH_HEAVY_SWING.get(), stand), 
								stand.getSoundSource(), 1, 1, false);
						playedSwingSound = true;
					}
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
				if (!level.isClientSide()) {
					StandPower standPower = StandPower.get(getPowerUser());
					playHeavyPunchPerformSound(stand);

					if (StandEntityPunchAbility.playHitSound(target, level)) {
						Holder<SoundEvent> impactSound = getHeavyPunchImpactSound(target);
						StandUtil.broadcastSound((ServerLevel) level, target.getCenterPos(), 
								impactSound, true, standPower,
								stand.getSoundSource(), 1, 1);
					}
					DamageSource dmgSource = makePunchDamageSource();
					((DamageSourceModified) dmgSource).jojo_ripples$setStandInvulTicks(10);
					float dmgAmount = StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage());
					float explRadius = calcExplosionRadius(stand);

					lastDamageDealtToLiving = 0;
					boolean deflectedTarget = StandEntityPunchAbility.deflectSilverChariotProjectiles(stand, target);
					switch (target.getType()) {
						case ENTITY -> {
							if (!deflectedTarget) {
								hitEntity(target, level, stand, dmgSource, dmgAmount, explRadius);
							}
						}
						case BLOCK -> hitBlock(target, level, stand, dmgSource, dmgAmount, explRadius);
						default -> {}
					}

					punchedTarget = target;
					standPower.consumeStamina(50);
				}
				if (target.getType() == TargetType.ENTITY) {
					standRotationTarget = target;
				}
				else {
					aimAs = AimingEntity.CAMERA_ENTITY;
				}
			}
		}
		
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand, 
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			Entity targetEntity = target.getMainEntity();
			if (targetEntity instanceof LivingEntity targetLiving) {
				addKnockback(dmgSource);
				if (getPunchModifiers().stream().anyMatch(modifier -> modifier.makesAttackNonLethal(targetLiving))) {
					dmgAmount = Math.min(dmgAmount, Math.max(targetLiving.getHealth() - 0.0001F, 0.0F));
				}
				float healthBeforeHit = targetLiving.getHealth();
				boolean hurt = standEntityAttack(stand, targetLiving, dmgSource, dmgAmount);
				lastDamageDealtToLiving = hurt ? Math.max(healthBeforeHit - targetLiving.getHealth(), 0) : 0;
				afterHeavyPunchHit(stand, targetLiving, dmgSource, dmgAmount, hurt);

				if (hurt) {
					Entity knockedBack = targetEntity;
					
					EntityActionInstance targetAction = LivingComponentAction.getCurEntityAction(targetLiving);
					if (targetAction != null) {
						if (targetAction instanceof StandEntityBarrageAbility.StandEntityBarrage) {
							targetAction.setPhaseStart(ActionPhase.RECOVERY);
							targetAction.syncPhaseChanges();
						}
					}
					
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
			float strength = (float) stand.getAttackDamage();
			return 0.5F + strength / (8 - finisherValue * 4);
		}
		
		protected void hitBlock(ActionTarget target, Level level, StandEntity stand, 
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			BlockPos blockPos = target.getBlockPos();
			Direction face = target.getFace();
			Vec3 pos = Vec3.atCenterOf(blockPos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.6));
			DamageSource aoeDmgSource = dmgSource;
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

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return getPhase() == ActionPhase.RECOVERY && finisherValue >= 0.5F;
		}

		protected ActionTarget getPunchTarget(StandEntity stand) {
			if (isGrabVariation()) {
				return new ActionTarget(punchTarget);
			}
			ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(stand, getActionTargetSnapshot(stand.level()));
			setActionTargetSnapshot(target);
			return target;
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
				EntityActionInstance.standEntityAttack(attackerAsStand, entity, damageSource, damage);

				entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
				if (entity instanceof Player player) {
					if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
						getHitPlayers().put(player, knockback);
					}
				}
			}
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
