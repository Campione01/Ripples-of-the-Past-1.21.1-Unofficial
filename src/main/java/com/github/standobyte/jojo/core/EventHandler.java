package com.github.standobyte.jojo.core;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.entityattachment.DataEventListeners;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.mechanics.HamonSpreadEffect;
import com.github.standobyte.jojo.mechanics.resolve.ResolveCounter;
import com.github.standobyte.jojo.mechanics.VampireSunBurnEffect;
import com.github.standobyte.jojo.mrpresident.CocoJumboTurtleEntity;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.item.OilItem;
import com.github.standobyte.jojo.mechanics.standdisc.StandDiscItem;
import com.github.standobyte.jojo.network.s2c.TrRefreshMovementInTimeStopPacket;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.SyncType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandLinkDamageSource;
import com.github.standobyte.jojo.powersystem.standpower.effect.UserStandEffects;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.ProjectileHamonChargeState;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonPlantItemInfusionAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.SunWeakness;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismUtil;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanBladeBarrageAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanBladeDashAttackAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanUnnaturalAgilityAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonProtectionAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonRebuffOverdriveAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonRopeTrapAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonSendoWaveKickAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonSnakeMufflerAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismFreezeAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.entity.HungryZombieEntity;
import com.github.standobyte.jojoimpl.stands.boyiiman.BoyIIManStandPartTakenEffect;
import com.github.standobyte.jojoimpl.stands.crazydiamond.AngeloRockEntity;
import com.github.standobyte.jojoimpl.stands.goldexperience.GECreatedLifeformEffect;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityBarrageAbility;
import com.github.standobyte.jojoimpl.stands.theworld.TimeStopAbility;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Pre;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class EventHandler {
	private static boolean applyingOiledWeaponHamonDamage;

	@SubscribeEvent
	public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
		CocoJumboTurtleEntity.onRegularTurtleSpawn(event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity dead = event.getEntity();
		if (dead.level().isClientSide()) {
			return;
		}
		if (handleCheatDeath(event, dead)) {
			return;
		}
		if (AngeloRockEntity.preventTargetDeath(dead)) {
			event.setCanceled(true);
			dead.setHealth(0.0001F);
			JojoModUtil.onLivingResurrect(dead);
			return;
		}
		HamonUtil.hamonPerksOnDeath(dead);
		StandPower standPower = StandPower.get(dead);
		if (standPower != null) {
			standPower.userStandEffects.onStandUserDeath(dead);
			dropStandDiscOnDeath(dead, standPower);
			standPower.spawnSoulOnDeath();
		}
		MobEffectInstance freezeEffect = dead.getEffect(ModStatusEffects.FREEZE);
		if (freezeEffect != null && freezeEffect.getAmplifier() >= 3 && !(dead instanceof ServerPlayer)
				&& dead.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
					dead.getX(), dead.getY(0.5), dead.getZ(), 128,
					dead.getBbWidth() / 2, dead.getBbHeight() / 2,
					dead.getBbWidth() / 2, 0.25);
			SoundType soundtype = Blocks.ICE.defaultBlockState().getSoundType(level, dead.blockPosition(), null);
			level.playSound(null, dead.getX(), dead.getY(), dead.getZ(),
					SoundEvents.GLASS_BREAK, dead.getSoundSource(),
					(soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
		}
	}

	private static void dropStandDiscOnDeath(LivingEntity dead, StandPower standPower) {
		JojoModConfig.Common config = JojoModConfig.getCommonConfigInstance(false);
		if (config.dropStandDisc.get() && !config.keepStandOnDeath.get() && standPower.hasPower()) {
			standPower.getStandInstance().ifPresent(stand -> dead.spawnAtLocation(StandDiscItem.withStand(stand)));
		}
	}

	private static boolean handleCheatDeath(LivingDeathEvent event, LivingEntity dead) {
		if (!dead.hasEffect(ModStatusEffects.CHEAT_DEATH)) {
			return false;
		}
		event.setCanceled(true);
		dead.setHealth(dead.getMaxHealth() / 2.0F);
		dead.removeEffect(ModStatusEffects.CHEAT_DEATH);
		dead.clearFire();
		JojoModUtil.onLivingResurrect(dead);
		if (dead.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.POOF, dead.getX(), dead.getY(), dead.getZ(),
					20, dead.getBbWidth() * 2.0D - 1.0D, dead.getBbHeight(), dead.getBbWidth() * 2.0D - 1.0D, 0.02D);
			chorusFruitTeleport(dead);
			level.getEntitiesOfClass(Mob.class, dead.getBoundingBox().inflate(8.0D), mob -> mob.getTarget() == dead)
					.forEach(mob -> mob.setTarget(null));
		}
		dead.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false, true));
		PlayerPower.getPowerData(dead, ModPlayerPowers.HAMON).ifPresent(hamon -> {
			hamon.markCheatDeathConsumed(dead);
			if (hamon.characterIs(ModHamonSkills.CHARACTER_JOSEPH.get())) {
				JojoModUtil.sayVoiceLine(dead, ModSoundEvents.JOSEPH_GIGGLE);
			}
		});
		return true;
	}

	private static void chorusFruitTeleport(LivingEntity entity) {
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		for (int i = 0; i < 16; i++) {
			double newX = x + (entity.getRandom().nextDouble() - 0.5D) * 16.0D;
			double newY = Math.max(entity.level().getMinBuildHeight(),
					Math.min(entity.level().getMaxBuildHeight() - 1,
							y + (double) (entity.getRandom().nextInt(16) - 8)));
			double newZ = z + (entity.getRandom().nextDouble() - 0.5D) * 16.0D;
			if (entity.randomTeleport(newX, newY, newZ, true)) {
				return;
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void cancelLivingHeal(LivingHealEvent event) {
		LivingEntity entity = event.getEntity();
		float amount = event.getAmount();
		if (amount > 0 && entity.hasEffect(ModStatusEffects.VAMPIRE_SUN_BURN)) {
			amount = VampireSunBurnEffect.reduceUndeadHealing();
		}
		if (amount > 0 && entity.hasEffect(ModStatusEffects.HAMON_SPREAD)) {
			amount = HamonSpreadEffect.reduceUndeadHealing(entity.getEffect(ModStatusEffects.HAMON_SPREAD), amount);
		}
		if (amount <= 0) {
			event.setCanceled(true);
		}
		event.setAmount(amount);
		VampirismUtil.consumeEnergyOnHeal(event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onBoyIIManTakenArmsAttack(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		DamageSource dmgSource = event.getSource();
		Entity attacker = dmgSource.getEntity();
		if (target.level().isClientSide() || attacker == null || target.is(attacker) || !target.isAlive()) {
			return;
		}
		if (attacker instanceof LivingEntity attackerLiving && attacker.is(dmgSource.getDirectEntity())) {
			StandPower attackerStand = StandPower.get(attackerLiving);
			StandPower boyIIManStand = StandPower.get(target);
			if (attackerStand != null && attackerStand.hasPower()
					&& attackerStand.getPowerType() != null
					&& boyIIManStand != null && boyIIManStand.hasPower()
					&& boyIIManStand.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_BIIM_STAND_PART_TAKE.get())
							.anyMatch(effect -> isTakenArmsEffectTargetingAttacker(effect, attackerLiving, attackerStand))) {
				attackerLiving.hurt(dmgSource, event.getAmount());
				event.setCanceled(true);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void redirectDamageFromGoldExperienceLifeforms(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		DamageSource dmgSource = event.getSource();
		Entity attacker = dmgSource.getEntity();
		if (target.level().isClientSide() || attacker == null || target.is(attacker) || !target.isAlive()
				|| event.getAmount() <= 0) {
			return;
		}
		if (attacker instanceof LivingEntity attackerLiving) {
			UserStandEffects.getEffectsTargetedBy(target, ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get())
					.findAny()
					.ifPresent(lifeform -> {
						if (!UserStandEffects.isTargetedBy(attackerLiving, ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get())) {
							boolean dealtDamage = attackerLiving.hurt(dmgSource, event.getAmount());
							event.setCanceled(true);
							if (dealtDamage && lifeform instanceof GECreatedLifeformEffect geLifeform
									&& geLifeform.getUserPower() != null) {
								ResolveCounter.addResolve(geLifeform.getUserPower(), target, event.getAmount());
							}
						}
					});
		}
	}

	private static boolean isTakenArmsEffectTargetingAttacker(BoyIIManStandPartTakenEffect effect, LivingEntity attackerLiving, StandPower attackerStand) {
		if (!attackerLiving.getUUID().equals(effect.getTargetUUID()) || attackerStand.getPowerType() == null) {
			return false;
		}
		StandInstance partsTaken = effect.getPartsTaken();
		return partsTaken != null
				&& partsTaken.getStandId().equals(attackerStand.getPowerType().getId())
				&& partsTaken.hasPart(StandPart.ARMS);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onTheWorldTimeStopUserAttacked(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !target.isAlive()
				|| event.getSource().getDirectEntity() == null) {
			return;
		}
		StandPower standPower = StandPower.get(target);
		if (standPower == null || standPower.getPowerType() != ModStands.THE_WORLD.get()) {
			return;
		}
		cancelTheWorldTimeStopCharge(target);
		StandEntity standEntity = standPower.getSummonedStandEntity();
		if (standEntity != null) {
			cancelTheWorldTimeStopCharge(standEntity);
		}
	}

	private static void cancelTheWorldTimeStopCharge(LivingEntity actionHolder) {
		EntityActionInstance curAction = LivingComponentAction.getCurEntityAction(actionHolder);
		if (curAction != null && curAction.ability instanceof TimeStopAbility
				&& curAction.getPhase() == ActionPhase.BUTTON_CHARGE) {
			LivingComponentAction.getComponent(actionHolder).setAction(null, SyncType.TRACKING_AND_SELF);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onStandBarrageUserHealthLinkInterrupted(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		DamageSource source = event.getSource();
		if (target.level().isClientSide() || !target.isAlive()
				|| event.getAmount() < 4F
				|| source.getDirectEntity() == null
				|| !(source instanceof StandLinkDamageSource standLinkDamage)
				|| !(standLinkDamage.standEntity instanceof StandEntity standEntity)
				|| !target.is(standEntity.getUser())) {
			return;
		}
		EntityActionInstance curAction = LivingComponentAction.getCurEntityAction(standEntity);
		if (curAction instanceof StandEntityBarrageAbility.StandEntityBarrage barrage
				&& curAction.getPhase() != ActionPhase.RECOVERY) {
			barrage.startRecovery();
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void reduceDamageToFrozenTimeStopTargets(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(target.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
		if (state.shouldFreeze(target)) {
			float multiplier = JojoModConfig.getCommonConfigInstance(false).timeStopDamageMultiplier.get().floatValue();
			event.setNewDamage(event.getNewDamage() * multiplier);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void stackKnockbackForFrozenTimeStopTargets(LivingKnockBackEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(target.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
		if (!serverLevel.hasData(attachmentType)) {
			return;
		}
		TimeStopState state = serverLevel.getData(attachmentType);
		if (state.shouldFreeze(target)) {
			event.setCanceled(true);
			DamageUtil.applyKnockbackStack(target, event.getStrength(), event.getRatioX(), event.getRatioZ());
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onPillarmanUtilityIncomingDamage(LivingIncomingDamageEvent event) {
		VampirismBloodDrainAbility.onUserIncomingDamage(event);
		if (VampirismFreezeAbility.onUserIncomingDamage(event)
				|| PillarmanBladeDashAttackAbility.onUserIncomingDamage(event)
				|| PillarmanBladeBarrageAbility.onUserIncomingDamage(event)
				|| PillarmanUnnaturalAgilityAbility.onUserIncomingDamage(event)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHamonProtectionIncomingDamage(LivingIncomingDamageEvent event) {
		if (HamonSendoWaveKickAbility.onUserIncomingDamage(event)) {
			event.setCanceled(true);
			return;
		}
		if (HamonRebuffOverdriveAbility.onUserIncomingDamage(event)) {
			return;
		}
		if (HamonSnakeMufflerAbility.onUserIncomingDamage(event)) {
			event.setCanceled(true);
			return;
		}
		LivingEntity target = event.getEntity();
		DamageSource dmgSource = event.getSource();
		if (target.level().isClientSide() || dmgSource.getDirectEntity() == null
				|| dmgSource.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return;
		}
		Power<?> power = PowerClass.PLAYER_POWER.get(target);
		if (power != null) {
			float reduced = HamonProtectionAbility.reduceDamageAmount(power, target, dmgSource, event.getAmount());
			if (reduced != event.getAmount()) {
				event.setAmount(reduced);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onOiledWeaponAttack(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		DamageSource dmgSource = event.getSource();
		Entity attacker = dmgSource.getEntity();
		if (applyingOiledWeaponHamonDamage || target.level().isClientSide()
				|| dmgSource.is(DamageTypeTags.BYPASSES_ARMOR)
				|| !(attacker instanceof LivingEntity hamonUser)
				|| !attacker.is(dmgSource.getDirectEntity())) {
			return;
		}

		ItemStack weapon = hamonUser.getMainHandItem();
		OilItem.remainingOiledUses(weapon).ifPresent(oilUses ->
				PlayerPower.getPowerData(hamonUser, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					float energyCost = 500.0F;
					if (hamon.getEnergy() >= energyCost && hamon.consumeEnergy(energyCost, hamonUser)) {
						applyingOiledWeaponHamonDamage = true;
						try {
							HamonAbilityHelpers.hamonHurt(target, hamonUser, 1.5F);
							hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, energyCost);
							OilItem.setWeaponOilUses(weapon, oilUses - 1);
						}
						finally {
							applyingOiledWeaponHamonDamage = false;
						}
					}
				}));
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onHamonItemToss(ItemTossEvent event) {
		HamonPlantItemInfusionAbility.chargeItemEntity(event.getPlayer(), event.getEntity());
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onPillarmanFoodEaten(LivingEntityUseItemEvent.Finish event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide()) {
			return;
		}
		FoodProperties food = event.getItem().getFoodProperties(entity);
		if (food == null) {
			return;
		}
		PlayerPower.getPowerData(entity, ModPlayerPowers.PILLAR_MAN).ifPresent(pillarman -> {
			pillarman.addEnergy(entity, food.nutrition() * 10.0F);
			pillarman.syncOnUpdate(entity);
		});
	}

	@SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
	public static void onHamonRopeTrapRightClick(PlayerInteractEvent.RightClickBlock event) {
		if (event.getHand() != InteractionHand.MAIN_HAND || event.getUseBlock().isFalse()) {
			return;
		}
		Player player = event.getEntity();
		if (player.isSpectator() || !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
			return;
		}
		BlockPos pos = event.getHitVec().getBlockPos();
		BlockState blockState = player.level().getBlockState(pos);
		if (!blockState.is(Blocks.TRIPWIRE)) {
			return;
		}
		PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
			if (hamon.isSkillLearned(ModHamonSkills.ROPE_TRAP.get())) {
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.SUCCESS);
				if (!player.level().isClientSide()) {
					HamonRopeTrapAbility.ropeTrap(player, pos, blockState, player.level(), hamon);
				}
			}
		});
	}

	@SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
	public static void onHungryZombieLeash(PlayerInteractEvent.EntityInteract event) {
		if (event.getTarget() instanceof HungryZombieEntity zombie
				&& event.getItemStack().is(Items.LEAD)
				&& !zombie.isEntityOwner(event.getEntity())) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.PASS);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
	public static void onPillarmanHeatEatPrimedTnt(PlayerInteractEvent.EntityInteract event) {
		if (event.getHand() != InteractionHand.MAIN_HAND) {
			return;
		}
		Player player = event.getEntity();
		if (player.isSpectator() || player.level().isClientSide() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Entity target = event.getTarget();
		if (!(target instanceof PrimedTnt) && !(target instanceof MinecartTNT)) {
			return;
		}
		PillarmanData pillarman = PlayerPower.getPowerData(player, ModPlayerPowers.PILLAR_MAN).orElse(null);
		if (pillarman == null || pillarman.getMode() != PillarmanMode.HEAT) {
			return;
		}
		int fuse = eatPrimedTntTarget(level, target);
		if (fuse < 0) {
			return;
		}
		pillarman.addEatenTntFuse(fuse);
		level.playSound(null, player, SoundEvents.GENERIC_EAT, player.getSoundSource(), 1.0F, 1.0F);
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}

	private static int eatPrimedTntTarget(ServerLevel level, Entity target) {
		if (target instanceof PrimedTnt tnt) {
			int fuse = tnt.getFuse();
			tnt.discard();
			return fuse;
		}
		if (target instanceof MinecartTNT tntMinecart && tntMinecart.getFuse() > -1) {
			int fuse = tntMinecart.getFuse();
			Minecart regularMinecart = EntityType.MINECART.create(level);
			if (regularMinecart != null) {
				regularMinecart.moveTo(tntMinecart.getX(), tntMinecart.getY(), tntMinecart.getZ(),
						tntMinecart.getYRot(), tntMinecart.getXRot());
				regularMinecart.setDeltaMovement(tntMinecart.getDeltaMovement());
				regularMinecart.setCustomName(tntMinecart.getCustomName());
				regularMinecart.setCustomNameVisible(tntMinecart.isCustomNameVisible());
				level.addFreshEntity(regularMinecart);
			}
			tntMinecart.discard();
			return fuse;
		}
		return -1;
	}

	@SubscribeEvent
	public static void onTimeStopEffectAdded(MobEffectEvent.Added event) {
		if (event.getOldEffectInstance() != null) {
			return;
		}
		if (event.getEffectInstance().getEffect().is(ModStatusEffects.TIME_STOP)) {
			handleTimeStopEffectChange(event.getEntity(), true, false);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onTimeStopEffectExpired(MobEffectEvent.Expired event) {
		if (event.getEffectInstance() != null
				&& event.getEffectInstance().getEffect().is(ModStatusEffects.TIME_STOP)) {
			handleTimeStopEffectChange(event.getEntity(), false, true);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onTimeStopEffectRemoved(MobEffectEvent.Remove event) {
		if (event.getEffect().is(ModStatusEffects.TIME_STOP)) {
			handleTimeStopEffectChange(event.getEntity(), false, false);
		}
	}

	private static void handleTimeStopEffectChange(LivingEntity entity, boolean effectAdded, boolean playCantMoveVoice) {
		if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
		var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
		if (!level.hasData(attachmentType)) {
			return;
		}
		TimeStopState state = level.getData(attachmentType);
		if (!state.isTimeStopped(chunkPos)) {
			return;
		}
		state.refreshTimeStopEffectState(entity);
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
				new TrRefreshMovementInTimeStopPacket(entity.getId(), chunkPos, effectAdded));
		if (!effectAdded && playCantMoveVoice) {
			playDioCantMoveIfNeeded(entity, state, chunkPos);
		}
	}

	private static void playDioCantMoveIfNeeded(LivingEntity entity, TimeStopState state, ChunkPos chunkPos) {
		if (state.getTimeStopTicks(chunkPos) < 40) {
			return;
		}
		StandPower standPower = StandPower.get(entity);
		if (standPower == null || !standPower.hasPower() || standPower.getPowerType() != ModStands.THE_WORLD.get()) {
			return;
		}
		JojoModUtil.sayVoiceLine(entity, ModSoundEvents.DIO_CANT_MOVE);
	}

	@SubscribeEvent
	public static void onEntityCreated(EntityJoinLevelEvent event) {
		/* 
		 * Attach the power data to the player.
		 * The capabilities system is now strictly per-EntityType, can't have an instanceof check anymore 
		 * for smth like a "Mobs with Stands"-type of addon, so we're not using it this time.
		 */ 
		if (event.getEntity() instanceof Player user) {
			for (PowerClass<?> powerClass : PowerClass.values()) {
				powerClass.attachPower(user);
			}
		}
		else if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel level) {
			if (event.getEntity() instanceof Mob mob) {
				VampirismUtil.editMobAiGoals(mob);
			}
			if (!event.loadedFromDisk() && event.getEntity() instanceof Projectile projectile) {
				HamonUtil.tryChargeProjectile(projectile);
			}
			if (event.getEntity() instanceof Chicken chicken) {
				HamonUtil.tryChargeChickenFromChargedEgg(chicken);
			}
			var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
			if (level.hasData(attachmentType)) {
				TimeStopState state = level.getData(attachmentType);
				if (state.isTimeStopped(event.getEntity())) {
					onEntityEnteredTimeStop(event.getEntity(), state);
				}
			}
		}
	}

	@SubscribeEvent
	public static void onProjectileImpact(ProjectileImpactEvent event) {
		ProjectileHamonChargeState.get(event.getProjectile()).onTargetHit(event.getRayTraceResult());
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel level) {
			level.getData(ModDataAttachmentTypes.CHARGED_HAMON_EGGS.get()).tick();
		}
	}

	private static void onEntityEnteredTimeStop(Entity entity, TimeStopState state) {
		state.reconcileFrozenEntity(entity);
	}

	@SubscribeEvent
	public static void onEntityTickPre(Pre event) {
		Entity entity = event.getEntity();
		if (!entity.level().isClientSide() && entity.level() instanceof ServerLevel level) {
			var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
			if (level.hasData(attachmentType)) {
				TimeStopState state = level.getData(attachmentType);
				if (state.interruptTickEarly(entity)) {
					DataEventListeners data = entityEventListeners(entity);
					if (data != null) {
						data.onTick();
					}
					event.setCanceled(true);
				}
			}
		}
	}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Post event) {
		Entity entity = event.getEntity();
		DataEventListeners data = entityEventListeners(entity);
		if (data != null) {
			data.onTick();
		}
		if (!entity.level().isClientSide() && entity.level() instanceof ServerLevel level) {
			var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
			if (level.hasData(attachmentType)) {
				TimeStopState state = level.getData(attachmentType);
				state.reconcileFrozenEntity(entity);
			}
			if (entity instanceof LivingEntity living && entity.tickCount % 20 == 0) {
				SunWeakness.tickSunBurn(living, level);
			}
			if (entity instanceof Player player) {
				removeMagiciansRedFireUnderPlayer(player, level);
			}
		}
	}

	private static void removeMagiciansRedFireUnderPlayer(Player player, ServerLevel level) {
		if (!player.isAlive()) {
			return;
		}
		StandPower standPower = StandPower.get(player);
		if (standPower == null || !standPower.hasPower()
				|| standPower.getPowerType() != ModStands.MAGICIANS_RED.get()
				|| standPower.getSummonedStandEntity() == null) {
			return;
		}
		AABB hitbox = player.getBoundingBox();
		BlockPos pos1 = BlockPos.containing(hitbox.minX + 0.001D, hitbox.minY + 0.001D, hitbox.minZ + 0.001D);
		BlockPos pos2 = BlockPos.containing(hitbox.maxX - 0.001D, hitbox.maxY - 0.001D, hitbox.maxZ - 0.001D);
		BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
		if (!level.hasChunksAt(pos1, pos2)) {
			return;
		}
		for (int x = pos1.getX(); x <= pos2.getX(); ++x) {
			for (int y = pos1.getY(); y <= pos2.getY(); ++y) {
				for (int z = pos1.getZ(); z <= pos2.getZ(); ++z) {
					blockPos.set(x, y, z);
					BlockState blockState = level.getBlockState(blockPos);
					if (!blockState.isAir() && blockState.getBlock() instanceof BaseFireBlock) {
						JojoModUtil.destroyBlock(level, blockPos, false, null);
					}
				}
			}
		}
	}

	@SubscribeEvent
	public static void onStartTracking(PlayerEvent.StartTracking event) {
		ServerPlayer player = (ServerPlayer) event.getEntity();
		DataEventListeners trackedData = entityEventListeners(event.getTarget());
		if (trackedData != null) {
			trackedData.onTracking(player);
		}
	}

	@SubscribeEvent
	public static void onPlayerClone(PlayerEvent.Clone event) {
		Player original = event.getOriginal();
		DataEventListeners data = entityEventListeners(original);
		if (data != null) {
			Player newEntity = event.getEntity();
			boolean wasDeath = event.isWasDeath();
			data.onClone(newEntity, wasDeath);
		}
	}
	
	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		ServerPlayer player = (ServerPlayer) event.getEntity();
		syncAttachedData(player);
		resendTimeStopState(player);
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			JojoModConfig.resetClientSyncedConfig(player);
			handleTimeStopPlayerLogout(player);
		}
	}

	private static void handleTimeStopPlayerLogout(ServerPlayer player) {
		var server = player.getServer();
		if (server == null) {
			return;
		}
		var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
		if (server.getPlayerList().getPlayerCount() <= 1) {
			for (ServerLevel level : server.getAllLevels()) {
				if (level.hasData(attachmentType)) {
					level.getData(attachmentType).reset();
				}
			}
		}
		else {
			ServerLevel level = player.serverLevel();
			if (level.hasData(attachmentType)) {
				level.getData(attachmentType).removeInstance(player.getId());
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		ServerPlayer player = (ServerPlayer) event.getEntity();
		clearTimeStopStateFromPreviousLevel(player, event.getFrom());
		syncAttachedData(player);
		resendTimeStopState(player);
	}

	@SubscribeEvent
	public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
		ServerPlayer player = (ServerPlayer) event.getEntity();
		syncAttachedData(player);
		resendTimeStopState(player);
	}

	public static void syncAttachedData(ServerPlayer player) {
		JojoModConfig.syncWithClient(player);
		DataEventListeners data = entityEventListeners(player);
		if (data != null) {
			data.onSyncToPlayer(player);
		}
	}

	private static void resendTimeStopState(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
		if (level.hasData(attachmentType)) {
			TimeStopState state = level.getData(attachmentType);
			state.resendCurrentStateToPlayer(player);
			state.reconcileFrozenEntity(player);
		}
	}

	private static void clearTimeStopStateFromPreviousLevel(ServerPlayer player, ResourceKey<Level> previousDimension) {
		var server = player.getServer();
		if (server == null) {
			return;
		}
		var previousLevel = server.getLevel(previousDimension);
		if (previousLevel == null) {
			return;
		}
		var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
		if (previousLevel.hasData(attachmentType)) {
			previousLevel.getData(attachmentType).clearActiveInstancesFromPlayer(player);
		}
	}

	@Nullable
	private static DataEventListeners entityEventListeners(Entity entity) {
		AttachmentType<DataEventListeners> key = ModDataAttachmentTypes.DATA_EVENT_HELPER.get();
		return entity.hasData(key) ? entity.getData(key) : null;
	}

}
