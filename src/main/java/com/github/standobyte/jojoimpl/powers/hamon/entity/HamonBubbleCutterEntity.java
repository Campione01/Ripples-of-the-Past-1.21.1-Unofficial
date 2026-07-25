package com.github.standobyte.jojoimpl.powers.hamon.entity;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonPowerType;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class HamonBubbleCutterEntity extends ModdedProjectileEntity {
	private boolean gliding;
	private float hamonStatPoints;

	public HamonBubbleCutterEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.HAMON_BUBBLE_CUTTER.get(), shooter, level);
	}

	public HamonBubbleCutterEntity(EntityType<? extends HamonBubbleCutterEntity> type, Level level) {
		super(type, level);
	}

	public HamonBubbleCutterEntity setGliding(boolean gliding) {
		this.gliding = gliding;
		return this;
	}

	public void setHamonStatPoints(float points) {
		this.hamonStatPoints = points;
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (target instanceof LivingEntity living && owner != null) {
			HamonAbilityHelpers.hamonHurt(living, owner, getBaseDamage());
			return true;
		}
		return false;
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt) {
			addStrengthPoints();
		}
	}

	private void addStrengthPoints() {
		LivingEntity owner = getOwner();
		if (owner != null && hamonStatPoints > 0.0F) {
			PlayerPower.getPowerData(owner, HamonPowerType.HAMON).ifPresent(hamon -> {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, hamonStatPoints);
				hamon.syncOnUpdate(owner);
			});
		}
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (gliding && targetType == TargetType.BLOCK && hitTarget instanceof BlockHitResult blockHit
				&& blockHit.getDirection().getAxis() == Direction.Axis.Y) {
			setDeltaMovement(getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
			return;
		}
		super.breakProjectile(targetType, hitTarget);
	}

	@Override
	public int ticksLifespan() {
		return 100;
	}

	@Override
	protected float getBaseDamage() {
		return 1.0F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0.0F;
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putBoolean("Gliding", gliding);
		nbt.putFloat("HamonStatPoints", hamonStatPoints);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		gliding = nbt.getBoolean("Gliding");
		hamonStatPoints = nbt.getFloat("HamonStatPoints");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeBoolean(gliding);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		gliding = additionalData.readBoolean();
	}
}
