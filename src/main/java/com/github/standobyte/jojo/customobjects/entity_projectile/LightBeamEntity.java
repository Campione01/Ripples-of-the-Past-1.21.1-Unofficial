package com.github.standobyte.jojo.customobjects.entity_projectile;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LightBeamEntity extends DamagingEntity {
	protected HitResult target;
	protected float length;
	protected float damage;

	public LightBeamEntity(EntityType<? extends LightBeamEntity> entityType, LivingEntity shooter, Level level) {
		super(entityType, shooter, level);
	}

	public LightBeamEntity(EntityType<? extends LightBeamEntity> entityType, Level level) {
		super(entityType, level);
	}

	public void shoot(float damage, float length) {
		this.damage = damage;
		this.length = length;
		LivingEntity shooter = getOwner();
		if (shooter != null) {
			target = rayTrace()[0];
			if (target.getType() != HitResult.Type.MISS) {
				this.length = (float) Math.sqrt(position().distanceToSqr(target.getLocation()));
			}
		}
	}

	@Override
	protected HitResult[] rayTrace() {
		Vec3 start = position();
		Vec3 end = getEndPoint();
		BlockHitResult blockHit = level().clip(new ClipContext(start, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 clipEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
		AABB searchBox = getBoundingBox().expandTowards(clipEnd.subtract(start)).inflate(1.0D);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, start, clipEnd, searchBox, this::canHitEntity);
		return new HitResult[] { entityHit != null ? entityHit : blockHit };
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			discard();
		}
	}

	@Override
	protected boolean hurtTarget(Entity target, DamageSource dmgSource, float dmgAmount) {
		if (!level().isClientSide()) {
			target.igniteForSeconds((int) damage / 2);
			if (target instanceof LivingEntity livingTarget && JojoDefinitions.isUndeadOrVampiric(livingTarget)) {
				return target.hurt(dmgSource, dmgAmount);
			}
		}
		return false;
	}

	@Override
	protected void onHitBlock(BlockHitResult blockRayTraceResult) {
		if (!level().isClientSide()) {
			Level level = level();
			BlockPos blockPos = blockRayTraceResult.getBlockPos().relative(blockRayTraceResult.getDirection());
			if (level instanceof ServerLevel && level.isEmptyBlock(blockPos)) {
				level.setBlockAndUpdate(blockPos, BaseFireBlock.getState(level, blockPos));
			}
		}
	}

	@Override
	public float getBaseDamage() {
		return damage;
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 2.5F;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().expandTowards(getEndPoint().subtract(position()));
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return super.shouldRenderAtSqrDistance(distance - length * length);
	}

	public Vec3 getEndPoint() {
		return position().add(Vec3.directionFromRotation(getXRot(), getYRot()).scale(length));
	}

	public float getLength() {
		return length;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putFloat("Length", length);
		nbt.putFloat("Damage", damage);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		damage = nbt.getFloat("Damage");
		length = nbt.getFloat("Length");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeFloat(length);
		buffer.writeFloat(damage);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		length = additionalData.readFloat();
		damage = additionalData.readFloat();
	}

	@Override
	public int ticksLifespan() {
		return 1;
	}
}
