package com.github.standobyte.jojoimpl.stands.hierophant;

import java.util.Optional;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class HierophantGreenEntity extends StandEntity {
	private static final AttributeModifier SPEED_MODIFIER_RETRACTION = new AttributeModifier(
			JojoMod.resLoc("hierophant_green_retraction_speed_boost"), 2.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final EntityDataAccessor<Integer> PLACED_BARRIERS = SynchedEntityData.defineId(
			HierophantGreenEntity.class, EntityDataSerializers.INT);

	private HGBarrierEntity stringToUser;
	private HGBarrierEntity stringFromStand;
	private final HGBarriersNet placedBarriers = new HGBarriersNet();

	public HierophantGreenEntity(EntityType<? extends HierophantGreenEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			placedBarriers.tick();
			setPlacedBarriersCount(placedBarriers.getSize());
		}
		super.tick();
	}

	public HGBarriersNet getBarriersNet() {
		return placedBarriers;
	}

	@Override
	public void setManuallyControlled(boolean value) {
		if (!level().isClientSide()) {
			AttributeInstance speedAttribute = getAttribute(Attributes.MOVEMENT_SPEED);
			if (speedAttribute != null) {
				speedAttribute.removeModifier(SPEED_MODIFIER_RETRACTION.id());
			}
			boolean summonBarrier = !isManuallyControlled() && value;
			boolean removeBarrier = isManuallyControlled() && !value && followingUserIsEnabled();
			super.setManuallyControlled(value);
			if (summonBarrier) {
				createUserString();
			}
			else if (removeBarrier && stringToUser != null && stringToUser.isAlive() && stringToUser.is(stringFromStand) && speedAttribute != null) {
				speedAttribute.addTransientModifier(SPEED_MODIFIER_RETRACTION);
			}
			return;
		}
		super.setManuallyControlled(value);
	}

	public void createUserString() {
		if (!level().isClientSide() && (stringToUser == null || !stringToUser.isAlive())) {
			stringToUser = new HGBarrierEntity(this, level());
			if (stringFromStand != null && stringFromStand.isAlive()) {
				stringFromStand.discard();
			}
			stringFromStand = stringToUser;
			stringFromStand.withStandSkin(getStandType(), getStandSkin());
			level().addFreshEntity(stringFromStand);
		}
	}

	public boolean canPlaceBarrier() {
		return getPlacedBarriersCount() < getMaxBarriersPlaceable(getUserPower());
	}

	public static int getMaxBarriersPlaceable(StandPower power) {
		if (power != null && power.getUser() != null) {
			int resolveLevel = ResolveModeEffect.getEffectiveResolveLevel(power.getUser(), power);
			return resolveLevel >= 4 ? 100 : 15;
		}
		return 15;
	}

	public boolean hasBarrierAttached() {
		return getPlacedBarriersCount() > 0
				|| stringFromStand != null && stringFromStand.isAlive() && stringFromStand != stringToUser;
	}

	public void attachBarrier(BlockPos blockPos) {
		if (level().isClientSide() || !canPlaceBarrier()) {
			return;
		}
		if (stringFromStand != null && stringFromStand.isAlive()) {
			if (blockPos.equals(stringFromStand.getOriginBlockPos())) {
				return;
			}
			stringFromStand.attachToBlockPos(blockPos);
			placedBarriers.add(stringFromStand);
			setPlacedBarriersCount(placedBarriers.getSize());
		}
		stringFromStand = new HGBarrierEntity(this, level());
		stringFromStand.setOriginBlockPos(blockPos);
		stringFromStand.withStandSkin(getStandType(), getStandSkin());
		level().addFreshEntity(stringFromStand);
		StandUtil.playStandEntitySound(this, ModSoundEvents.HIEROPHANT_GREEN_BARRIER_PLACED, 1.0F, 1.0F);
	}

	public int getPlacedBarriersCount() {
		return entityData.get(PLACED_BARRIERS);
	}

	private void setPlacedBarriersCount(int value) {
		entityData.set(PLACED_BARRIERS, value);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PLACED_BARRIERS, 0);
	}

	@Override
	protected void setStandFlag(StandFlag flag, boolean value) {
		super.setStandFlag(flag, value);
		if (!level().isClientSide() && flag == StandFlag.BEING_RETRACTED && !value && isCloseToUser()) {
			if (stringToUser != null && stringToUser.isAlive() && stringToUser.is(stringFromStand)) {
				AttributeInstance speedAttribute = getAttribute(Attributes.MOVEMENT_SPEED);
				if (speedAttribute != null) {
					speedAttribute.removeModifier(SPEED_MODIFIER_RETRACTION.id());
				}
				stringToUser.discard();
			}
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (!level().isClientSide()) {
			if (stringToUser != null && stringToUser.isAlive()) {
				stringToUser.discard();
			}
			if (stringFromStand != null && stringFromStand.isAlive()) {
				stringFromStand.discard();
			}
			placedBarriers.discardAll();
		}
		super.remove(reason);
	}

	@Override
	public void setSelectedSkin(Optional<ResourceLocation> standSkin) {
		super.setSelectedSkin(standSkin);
		placedBarriers.setStandSkin(standSkin);
		if (stringToUser != null) {
			stringToUser.withStandSkin(standSkin);
		}
		if (stringFromStand != null) {
			stringFromStand.withStandSkin(standSkin);
		}
	}
}
