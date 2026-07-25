package com.github.standobyte.jojo.customobjects;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.util.functions.DamageUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RoadRollerEntity extends Entity {
	private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(RoadRollerEntity.class, EntityDataSerializers.FLOAT);
	private static final float MAX_HEALTH = 50;
	private static final Vec3 UPWARDS_VECTOR = new Vec3(0.0D, 1.0D, 0.0D);

	private int ticksBeforeExplosion = -1;
	private int ticksInAir = 0;
	@Nullable private Entity owner;
	@Nullable private UUID ownerId;
	private double tickDamageMotion = 0;
	private boolean punchedFromBelow = false;

	public RoadRollerEntity(Level level) {
		this(ModEntityTypes.ROAD_ROLLER.get(), level);
	}

	public RoadRollerEntity(EntityType<? extends RoadRollerEntity> entityType, Level level) {
		super(entityType, level);
	}

	public void setOwner(@Nullable Entity entity) {
		this.owner = entity;
		this.ownerId = entity != null ? entity.getUUID() : null;
	}

	@Override
	public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean shouldRiderSit() {
		return false;
	}

	@Override
	public Vec3 getPassengerRidingPosition(Entity passenger) {
		return position().add(0.0D, getBbHeight() * 0.95D + Math.abs(getXRot()) / 90.0D, 0.0D);
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void tick() {
		boolean wasOnGround = onGround();
		super.tick();

		Vec3 movement = getDeltaMovement();
		if (!isNoGravity() && !punchedFromBelow) {
			setDeltaMovement(movement.add(-movement.x, -0.0467D, -movement.z));
		}

		tickDamageMotion = 0;
		punchedFromBelow = false;

		DamageSource damageSource = DamageUtil.make(level(), ModDamageTypes.ROAD_ROLLER, this, this);
		float damage = (float) -getDeltaMovement().y * 10.0F;
		if (damage > 0.0F) {
			AABB crushAabb = getBoundingBox().contract(0.0D, getBbHeight() * 0.75D, 0.0D)
					.expandTowards(0.0D, -getBbHeight() * 0.25D, 0.0D);
			level().getEntitiesOfClass(LivingEntity.class, crushAabb,
					entity -> entity.isAlive() && !this.is(entity.getVehicle())).forEach(entity -> {
						if (!entity.isInvulnerableTo(damageSource)) {
							if (!level().isClientSide()) {
								entity.hurt(damageSource, damage);
							}
							entity.setDeltaMovement(Vec3.ZERO);
						}
					});
		}

		move(MoverType.SELF, getDeltaMovement());
		if (onGround()) {
			setDeltaMovement(movement.x, 0.0D, movement.z);
			if (getXRot() > 0.0F) {
				setXRot(Math.max(getXRot() - 6.0F, 0.0F));
			}
			else {
				setXRot(Math.min(getXRot() + 6.0F, 0.0F));
			}
		}
		if (level().isClientSide() && !wasOnGround) {
			ticksInAir++;
			if (onGround()) {
				level().playLocalSound(getX(), getY(), getZ(), ModSoundEvents.ROAD_ROLLER_LAND.get(),
						getSoundSource(), (float) ticksInAir * 0.05F, 1.0F, false);
				ticksInAir = 0;
			}
		}

		if (ticksBeforeExplosion > 0) {
			ticksBeforeExplosion--;
		}
		else if (ticksBeforeExplosion == 0) {
			discard();
		}
		Entity owner = getOwner();
		if (!level().isClientSide() && (ticksBeforeExplosion == 0
				|| ticksBeforeExplosion > 0 && ticksBeforeExplosion < 40 && owner != null && distanceToSqr(owner) > 100.0D)) {
			explode();
			discard();
		}
	}

	@Nullable
	private Entity getOwner() {
		if (!level().isClientSide() && owner == null && ownerId != null && level() instanceof ServerLevel serverLevel) {
			owner = serverLevel.getEntity(ownerId);
		}
		return owner;
	}

	private void explode() {
		level().explode(this, getX(), getY(0.0625D), getZ(), 4.0F, Level.ExplosionInteraction.NONE);
	}

	@Override
	public boolean canCollideWith(Entity entity) {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return true;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurt(DamageSource damageSource, float amount) {
		if (isInvulnerableTo(damageSource)) {
			return false;
		}
		if (!level().isClientSide()) {
			double cos = -1.0D;
			Entity directEntity = damageSource.getDirectEntity();
			if (directEntity != null) {
				Vec3 damagePos = directEntity.getEyePosition(1.0F);
				Vec3 damageVector = position().subtract(damagePos).normalize();
				cos = damageVector.dot(UPWARDS_VECTOR);
				double damageMotion = cos * amount * 0.08D;
				if (damageMotion > 0.0D) {
					damageMotion = Math.min(-tickDamageMotion, damageMotion);
					punchedFromBelow = true;
				}
				setDeltaMovement(getDeltaMovement().add(0.0D, damageMotion, 0.0D));
				tickDamageMotion += damageMotion;
			}
			if (getHealth() > 0.0F) {
				setHealth(cos < 0.0D ? getHealth() - amount : getHealth() + amount);
			}
			markHurt();
			level().playSound(null, getX(), getY(), getZ(), ModSoundEvents.ROAD_ROLLER_HIT.get(),
					getSoundSource(), amount * 0.25F, 1.0F + (random.nextFloat() - 0.5F) * 0.3F);
		}
		return true;
	}

	public float getHealth() {
		return entityData.get(HEALTH);
	}

	public void setHealth(float health) {
		entityData.set(HEALTH, Mth.clamp(health, 0.0F, MAX_HEALTH));
	}

	public float getMaxHealth() {
		return MAX_HEALTH;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> dataParameter) {
		super.onSyncedDataUpdated(dataParameter);
		if (HEALTH.equals(dataParameter)) {
			if (getHealth() <= 0.0F) {
				ticksBeforeExplosion = 60;
				if (!level().isClientSide()) {
					ejectPassengers();
				}
			}
			else {
				ticksBeforeExplosion = -1;
			}
		}
	}

	public int getTicksBeforeExplosion() {
		return ticksBeforeExplosion;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(HEALTH, MAX_HEALTH);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		if (nbt.contains("Health")) {
			setHealth(nbt.getFloat("Health"));
		}
		tickCount = nbt.getInt("Age");
		if (nbt.contains("ExplosionTime")) {
			ticksBeforeExplosion = nbt.getInt("ExplosionTime");
		}
		if (nbt.hasUUID("Owner")) {
			ownerId = nbt.getUUID("Owner");
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		nbt.putFloat("Health", getHealth());
		nbt.putInt("Age", tickCount);
		nbt.putInt("ExplosionTime", ticksBeforeExplosion);
		UUID ownerUuid = owner != null ? owner.getUUID() : ownerId;
		if (ownerUuid != null) {
			nbt.putUUID("Owner", ownerUuid);
		}
	}
}
