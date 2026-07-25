package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModStatusEffects;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class HamonHypnosisState implements TickingEntityData, INBTSerializable<CompoundTag> {
	private final LivingEntity entity;
	@Nullable private UUID preHypnosisOwner;
	private int lookAtHypnotizerTicks;
	@Nullable private LivingEntity hypnotizer;

	public HamonHypnosisState(LivingEntity entity) {
		this.entity = entity;
		addTicking(entity);
	}

	public static HamonHypnosisState get(LivingEntity entity) {
		return entity.getData(ModDataAttachmentTypes.HAMON_HYPNOSIS_STATE);
	}

	public static HypnosisTargetCheck canBeHypnotized(LivingEntity entity, LivingEntity hypnotizer) {
		if (!(hypnotizer instanceof Player)) {
			return HypnosisTargetCheck.INVALID;
		}
		UUID hypnotizerId = hypnotizer.getUUID();
		if (entity instanceof TamableAnimal tameable) {
			return !hypnotizerId.equals(tameable.getOwnerUUID())
					? HypnosisTargetCheck.CORRECT
					: HypnosisTargetCheck.ALREADY_TAMED_BY_USER;
		}
		if (entity instanceof AbstractHorse horse) {
			return !hypnotizerId.equals(horse.getOwnerUUID())
					? HypnosisTargetCheck.CORRECT
					: HypnosisTargetCheck.ALREADY_TAMED_BY_USER;
		}
		return HypnosisTargetCheck.INVALID;
	}

	public void hypnotizeEntity(LivingEntity hypnotizer, int duration) {
		if (entity.level().isClientSide() || !(hypnotizer instanceof Player player)) {
			return;
		}
		boolean giveEffect = false;
		if (entity instanceof TamableAnimal tameable) {
			preHypnosisOwner = tameable.getOwnerUUID();
			tameable.tame(player);
			giveEffect = true;
		}
		else if (entity instanceof AbstractHorse horse) {
			preHypnosisOwner = horse.getOwnerUUID();
			horse.tameWithName(player);
			giveEffect = true;
		}
		if (giveEffect) {
			entity.level().broadcastEntityEvent(entity, (byte) 7);
			entity.addEffect(new MobEffectInstance(ModStatusEffects.HYPNOSIS, duration, 0, false, false, true));
		}
	}

	public void relieveHypnosis() {
		if (entity.level().isClientSide()) {
			return;
		}
		if (entity instanceof TamableAnimal tameable) {
			tameable.setOrderedToSit(false);
			tameable.setTame(preHypnosisOwner != null, true);
			tameable.setOwnerUUID(preHypnosisOwner);
			entity.level().broadcastEntityEvent(entity, (byte) 6);
		}
		else if (entity instanceof AbstractHorse horse) {
			horse.setTamed(preHypnosisOwner != null);
			horse.setOwnerUUID(preHypnosisOwner);
			horse.makeMad();
			entity.level().broadcastEntityEvent(entity, (byte) 6);
		}
		preHypnosisOwner = null;
	}

	public void startedHypnosisProcess(LivingEntity hypnotizer) {
		this.hypnotizer = hypnotizer;
		lookAtHypnotizerTicks = 3;
	}

	@Override
	public void tick() {
		if (lookAtHypnotizerTicks > 0) {
			--lookAtHypnotizerTicks;
			if (hypnotizer != null && hypnotizer.isAlive() && entity instanceof Mob mob) {
				mob.getLookControl().setLookAt(hypnotizer, 30.0F, 30.0F);
				mob.getNavigation().stop();
			}
			if (lookAtHypnotizerTicks == 0) {
				hypnotizer = null;
			}
		}
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		if (preHypnosisOwner != null) {
			tag.putUUID("PreHypnosisOwner", preHypnosisOwner);
		}
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		preHypnosisOwner = tag.contains("PreHypnosisOwner", Tag.TAG_INT_ARRAY)
				? tag.getUUID("PreHypnosisOwner")
				: null;
	}

	public enum HypnosisTargetCheck {
		CORRECT,
		INVALID,
		ALREADY_TAMED_BY_USER
	}
}
