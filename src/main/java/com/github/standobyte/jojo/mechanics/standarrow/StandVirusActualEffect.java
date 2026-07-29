package com.github.standobyte.jojo.mechanics.standarrow;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.api.stand.StandVirusMobGiver;
import com.github.standobyte.jojo.api.stand.StandVirusMobGiverContext;
import com.github.standobyte.jojo.api.stand.StandVirusMobGivers;
import com.github.standobyte.jojo.api.stand.StandVirusMobGivers.Match;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.mechanics.standarrow.StandVirusMobGiverLifecyclePolicy.Resolution;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffect;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectType;
import com.github.standobyte.jojo.entityattachment.syncheddata.SynchedDataBuilder;
import com.github.standobyte.jojo.entityattachment.syncheddata.SyncedDataHolderExtended;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModEntityCustomEffects;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.util.functions.DamageUtil;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class StandVirusActualEffect extends EntityCustomEffect implements SyncedDataHolderExtended {
	public static final EntityDataAccessor<Integer> CONSUMED_LEVELS = SynchedEntityData.defineId(StandVirusActualEffect.class, EntityDataSerializers.INT);
	public static final int STAND_XP_LEVELS_REQUIREMENT = 30;
	@Nullable protected StandType standToGive;
	protected ItemStack standArrowItem = ItemStack.EMPTY;
	@Nullable protected UUID standArrowShooterUUID;
	@Nullable protected ResourceLocation mobGiverOwner;
	protected int mobGiverAmplifier;
	protected boolean mobGiverResolutionHandled;

	public StandVirusActualEffect() {
		this(ModEntityCustomEffects.STAND_VIRUS.get());
	}

	public StandVirusActualEffect(EntityCustomEffectType<?> effectType) {
		super(effectType);
	}


	@Override
	public void defineSynchedData(SynchedDataBuilder builder) {
		builder.define(CONSUMED_LEVELS, 0);
	}

	@Override
	public <T> void onSyncedDataUpdated(T oldValue, T newValue, EntityDataAccessor<T> dataAccessor) {
	}


	public int getXpLevelsTakenByArrow() {
		return synchedData.get(CONSUMED_LEVELS);
	}

	public int incXpLevelsTakenByArrow() {
		int levels = getXpLevelsTakenByArrow() + 1;
		synchedData.set(CONSUMED_LEVELS, levels);
		return levels;
	}

	public int getStandXpLevelsRequirement() {
		return STAND_XP_LEVELS_REQUIREMENT;
	}

	public static int getEffectDurationToApply() {
		return (STAND_XP_LEVELS_REQUIREMENT + 1) * 20;
	}

	public StandVirusActualEffect withArrowContext(@Nullable StandType standToGive, ItemStack arrowItem, @Nullable Entity arrowShooter) {
		return withArrowContext(
				standToGive,
				arrowItem,
				arrowShooter,
				null,
				0);
	}

	public StandVirusActualEffect withArrowContext(
			@Nullable StandType standToGive,
			ItemStack arrowItem,
			@Nullable Entity arrowShooter,
			@Nullable ResourceLocation mobGiverOwner) {
		int amplifier = 0;
		if (entity instanceof LivingEntity livingEntity) {
			var visibleEffect =
					livingEntity.getEffect(ModStatusEffects.STAND_VIRUS);
			if (visibleEffect != null) {
				amplifier = visibleEffect.getAmplifier();
			}
		}
		return withArrowContext(
				standToGive,
				arrowItem,
				arrowShooter,
				mobGiverOwner,
				amplifier);
	}

	public StandVirusActualEffect withArrowContext(
			@Nullable StandType standToGive,
			ItemStack arrowItem,
			@Nullable Entity arrowShooter,
			@Nullable ResourceLocation mobGiverOwner,
			int amplifier) {
		this.standToGive = standToGive;
		this.standArrowItem = arrowItem != null ? arrowItem.copy() : ItemStack.EMPTY;
		this.standArrowShooterUUID = arrowShooter != null ? arrowShooter.getUUID() : null;
		this.mobGiverOwner = mobGiverOwner;
		this.mobGiverAmplifier = Math.max(0, amplifier);
		this.mobGiverResolutionHandled = false;
		return this;
	}

	@Override
	protected void start() {}

	@Override
	protected void tick() {
		if (!level.isClientSide() && tickCount % 10 == 0) {
			LivingEntity entity = (LivingEntity) this.entity;
			Holder<MobEffect> vanillaEffect = ModStatusEffects.STAND_VIRUS;

			var visibleEffect = entity.getEffect(vanillaEffect);
			if (visibleEffect == null) {
				remove();
				return;
			}
			StandPower power = StandPower.get(entity);
			if (power != null && power.hasPower()) {
				mobGiverResolutionHandled = true;
				stopEffectOnGaveStand = true;
				entity.removeEffect(vanillaEffect);
				remove();
				return;
			}

			int amplifier = Math.max(0, visibleEffect.getAmplifier());
			mobGiverAmplifier = amplifier;
			MobGiverLookup mobGiver = resolveMobGiver(entity);
			Resolution resolution = mobGiver.resolution();
			if (resolution.clearsWithoutGrant()) {
				if (resolution == Resolution.FAIL_CLOSED) {
					failClosedMobGiver(entity, vanillaEffect);
				}
				else {
					clearMobGiverVirus(entity, vanillaEffect);
				}
				return;
			}
			if (resolution == Resolution.MOB_GIVER) {
				tickMobGiver(
						entity,
						vanillaEffect,
						mobGiver.match(),
						amplifier);
				return;
			}

			float damage = damageAmount();
			boolean stopEffect = false;

			if (entity instanceof Player player) {
				boolean hasXpLevel = player.getAbilities().instabuild || player.experienceLevel > 0;
				if (hasXpLevel) {
					int standXpRequirements = this.getStandXpLevelsRequirement();
					if (this.incXpLevelsTakenByArrow() >= standXpRequirements) {
						stopEffectOnGaveStand = giveStandFromVirus(entity);
						stopEffect = true;
					}
				}

				player.giveExperienceLevels(-1);
				if (hasXpLevel) {
					damage /= 10;
					if (damage > entity.getHealth()) {
						damage = 0.001F;
					}
				}
			}
			else if (entity.getHealth() <= damage) {
				stopEffectOnGaveStand = giveStandFromVirus(entity);
				if (stopEffectOnGaveStand) {
					damage = 0;
				}
				stopEffect = true;
			}

			if (damage > 0) {
				entity.hurt(DamageUtil.make(level, ModDamageTypes.STAND_ARROW_VIRUS), damage);
			}
			if (stopEffect || stopEffectOnGaveStand) {
				entity.removeEffect(vanillaEffect);
				this.remove();
			}
		}
	}

	protected float damageAmount() {
		return 1.5F;
	}

	private void tickMobGiver(
			LivingEntity entity,
			Holder<MobEffect> vanillaEffect,
			Match match,
			int amplifier) {
		float baseDamage = 1.5F + amplifier * 2.0F;
		boolean stopEffect = false;

		if (entity.getHealth() > baseDamage) {
			DamageUtil.hurtThroughInvulTicks(
					entity,
					DamageUtil.make(
							level,
							ModDamageTypes.STAND_ARROW_VIRUS),
					baseDamage);
		}
		else {
			resolveMobGiver(entity, match, amplifier, baseDamage);
			stopEffect = true;
		}

		if (entity.isAlive()) {
			StandVirusMobGiverContext context =
					mobGiverContext(entity, match, amplifier, baseDamage);
			float stopHealth = safeStopHealth(match, context);
			if (stopHealth >= 0.0F
					&& entity.getHealth() <= stopHealth) {
				if (!mobGiverResolutionHandled) {
					resolveMobGiver(
							entity,
							match,
							amplifier,
							baseDamage);
				}
				stopEffect = true;
			}
			else {
				float extraDamage = safeExtraDamage(match, context);
				if (extraDamage > 0.0F) {
					DamageUtil.hurtThroughInvulTicks(
							entity,
							DamageUtil.make(
									level,
									ModDamageTypes
											.STAND_ARROW_VIRUS),
							extraDamage);
				}
			}
		}

		if (stopEffect || mobGiverResolutionHandled) {
			entity.removeEffect(vanillaEffect);
			remove();
		}
	}

	protected boolean stopEffectOnGaveStand;
	@Override
	protected void stop() {
		if (stopEffectOnGaveStand || mobGiverResolutionHandled
				|| level.isClientSide()
				|| !entity.isAlive()) {
			return;
		}
		LivingEntity livingEntity = (LivingEntity) entity;
		MobGiverLookup mobGiver = resolveMobGiver(livingEntity);
		Resolution resolution = mobGiver.resolution();
		if (resolution.clearsWithoutGrant()) {
			if (resolution == Resolution.FAIL_CLOSED) {
				failClosedMobGiver(
						livingEntity,
						ModStatusEffects.STAND_VIRUS);
			}
			return;
		}
		if (resolution == Resolution.MOB_GIVER) {
			int amplifier = Math.max(0, mobGiverAmplifier);
			resolveMobGiver(
					livingEntity,
					mobGiver.match(),
					amplifier,
					1.5F + amplifier * 2.0F);
		}
		else if (resolution.allowsCoreGrant()) {
			giveStandFromVirus(livingEntity);
		}
	}

	private MobGiverLookup resolveMobGiver(LivingEntity target) {
		boolean hasPersistedOwner = mobGiverOwner != null;
		Match match = null;
		if (hasPersistedOwner) {
			StandVirusMobGiver giver =
					StandVirusMobGivers.get(mobGiverOwner).orElse(null);
			if (giver != null) {
				match = new Match(mobGiverOwner, giver);
			}
		}
		else if (!(target instanceof Player)) {
			match = StandVirusMobGivers.find(target).orElse(null);
			if (match != null) {
				mobGiverOwner = match.owner();
			}
		}
		Resolution resolution =
				StandVirusMobGiverLifecyclePolicy.decide(
						hasPersistedOwner,
						match != null,
						mobGiverResolutionHandled);
		return new MobGiverLookup(resolution, match);
	}

	private void failClosedMobGiver(
			LivingEntity target,
			Holder<MobEffect> vanillaEffect) {
		JojoMod.getLogger().warn(
				"Stand-virus mob giver {} is unavailable; "
						+ "ending its owned virus without a core grant.",
				mobGiverOwner);
		clearMobGiverVirus(target, vanillaEffect);
	}

	private void clearMobGiverVirus(
			LivingEntity target,
			Holder<MobEffect> vanillaEffect) {
		mobGiverResolutionHandled = true;
		target.removeEffect(vanillaEffect);
		remove();
	}

	private boolean resolveMobGiver(
			LivingEntity target,
			Match match,
			int amplifier,
			float baseDamage) {
		if (mobGiverResolutionHandled) {
			return stopEffectOnGaveStand;
		}
		mobGiverResolutionHandled = true;
		StandVirusMobGiverContext context =
				mobGiverContext(target, match, amplifier, baseDamage);
		boolean gaveStand = false;
		try {
			float chance = match.giver().survivalChance(context);
			gaveStand = StandVirusMobGiverLifecyclePolicy
					.passesSurvivalRoll(
					target.getRandom().nextFloat(), chance)
					&& match.giver().giveStand(context);
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"Stand-virus mob giver {} resolution failed.",
					match.owner(),
					error);
		}

		stopEffectOnGaveStand = gaveStand;
		if (gaveStand) {
			triggerArrowCriterion(target);
		}
		else if (target.isAlive() && baseDamage > 0.0F) {
			DamageUtil.hurtThroughInvulTicks(
					target,
					DamageUtil.make(
							level,
							ModDamageTypes.STAND_ARROW_VIRUS),
					baseDamage);
		}
		return gaveStand;
	}

	private StandVirusMobGiverContext mobGiverContext(
			LivingEntity target,
			Match match,
			int amplifier,
			float baseDamage) {
		return new StandVirusMobGiverContext(
				match.owner(),
				target,
				standArrowItem,
				resolveArrowShooter(),
				amplifier,
				baseDamage);
	}

	private float safeStopHealth(
			Match match,
			StandVirusMobGiverContext context) {
		try {
			return match.giver().stopHealth(context);
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"Stand-virus mob giver {} stop-health check failed.",
					match.owner(),
					error);
			return -1.0F;
		}
	}

	private float safeExtraDamage(
			Match match,
			StandVirusMobGiverContext context) {
		try {
			return Math.max(0.0F, match.giver().extraDamage(context));
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"Stand-virus mob giver {} extra damage failed.",
					match.owner(),
					error);
			return 0.0F;
		}
	}

	@Nullable
	private Entity resolveArrowShooter() {
		return standArrowShooterUUID != null
				&& level instanceof ServerLevel serverLevel
				? serverLevel.getEntity(standArrowShooterUUID)
				: null;
	}

	private void triggerArrowCriterion(LivingEntity target) {
		if (resolveArrowShooter() instanceof ServerPlayer shooter) {
			ModCriteriaTriggers.triggerStandArrowHit(
					shooter,
					target,
					true,
					shooter == target);
		}
	}

	protected boolean giveStandFromVirus(LivingEntity entity) {
		boolean gaveStand = standToGive != null
				? StandArrowItem.giveStand(level, entity, standToGive)
				: StandArrowItem.giveStand(level, entity);
		if (gaveStand && standArrowShooterUUID != null && level instanceof ServerLevel serverLevel) {
			Player shooterPlayer = serverLevel.getPlayerByUUID(standArrowShooterUUID);
			if (shooterPlayer instanceof ServerPlayer shooter) {
				ModCriteriaTriggers.triggerStandArrowHit(shooter, entity, true, shooter == entity);
			}
		}
		return gaveStand;
	}

	private record MobGiverLookup(
			Resolution resolution,
			@Nullable Match match) {}


	@Override
	protected void writeAdditionalSaveData(CompoundTag nbt) {
		nbt.putInt("LevelsConsumed", synchedData.get(CONSUMED_LEVELS));
		if (standToGive != null) {
			nbt.putString("StandToGive", standToGive.getId().toString());
		}
		if (!standArrowItem.isEmpty() && level != null) {
			nbt.put("StandArrowItem", standArrowItem.save(level.registryAccess()));
		}
		if (standArrowShooterUUID != null) {
			nbt.putUUID("StandArrowShooter", standArrowShooterUUID);
		}
		if (mobGiverOwner != null) {
			nbt.putString("MobGiverOwner", mobGiverOwner.toString());
		}
		nbt.putInt("MobGiverAmplifier", mobGiverAmplifier);
		nbt.putBoolean(
				"MobGiverResolutionHandled",
				mobGiverResolutionHandled);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		synchedData.set(CONSUMED_LEVELS, nbt.getInt("LevelsConsumed"));
		if (nbt.contains("StandToGive", Tag.TAG_STRING)) {
			standToGive = StandType.fromId(ResourceLocation.parse(nbt.getString("StandToGive")));
		}
		if (level != null && nbt.contains("StandArrowItem", Tag.TAG_COMPOUND)) {
			standArrowItem = ItemStack.parseOptional(level.registryAccess(), nbt.getCompound("StandArrowItem"));
		}
		if (nbt.hasUUID("StandArrowShooter")) {
			standArrowShooterUUID = nbt.getUUID("StandArrowShooter");
		}
		if (nbt.contains("MobGiverOwner", Tag.TAG_STRING)) {
			mobGiverOwner =
					ResourceLocation.tryParse(
							nbt.getString("MobGiverOwner"));
		}
		mobGiverAmplifier = Math.max(
				0,
				nbt.getInt("MobGiverAmplifier"));
		mobGiverResolutionHandled =
				nbt.getBoolean("MobGiverResolutionHandled");
	}

}
