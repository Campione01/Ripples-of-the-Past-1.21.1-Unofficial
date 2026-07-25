package com.github.standobyte.jojo.mechanics.standarrow;

import java.util.UUID;

import javax.annotation.Nullable;

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
		this.standToGive = standToGive;
		this.standArrowItem = arrowItem != null ? arrowItem.copy() : ItemStack.EMPTY;
		this.standArrowShooterUUID = arrowShooter != null ? arrowShooter.getUUID() : null;
		return this;
	}

	@Override
	protected void start() {}

	@Override
	protected void tick() {
		if (!level.isClientSide() && tickCount % 10 == 0) {
			LivingEntity entity = (LivingEntity) this.entity;
			Holder<MobEffect> vanillaEffect = ModStatusEffects.STAND_VIRUS;

			if (!entity.hasEffect(vanillaEffect)) {
				remove();
				return;
			}
			StandPower power = StandPower.get(entity);
			if (power != null && power.hasPower()) {
				remove();
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

	protected boolean stopEffectOnGaveStand;
	@Override
	protected void stop() {
		if (!stopEffectOnGaveStand) {
			Level level = entity.level();
			if (!level.isClientSide() && entity.isAlive()) {
				giveStandFromVirus((LivingEntity) entity);
			}
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
	}

}
