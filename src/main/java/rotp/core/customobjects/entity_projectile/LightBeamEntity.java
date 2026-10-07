package rotp.core.customobjects.entity_projectile;

import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.block.WoodenCoffinBlock;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
		}
	}

	// 1.16 JojoModUtil.rayTrace(this, length, e -> e != getOwner()): from the beam's own eye, the nearest pickable
	// entity anywhere on the ray by its box and pick radius, else the first block outline
	@Override
	protected HitResult[] rayTrace() {
		Vec3 start = getEyePosition();
		Vec3 ray = getLookAngle().scale(length);
		Vec3 end = start.add(ray);
		LivingEntity owner = getOwner();
		double maxDistanceSqr = ray.lengthSqr();
		EntityHitResult entityHit = null;
		double entityDistanceSqr = Double.MAX_VALUE;
		for (Entity candidate : level().getEntities(this, getBoundingBox().expandTowards(ray).inflate(1.0D),
				entity -> !entity.isSpectator() && entity.isPickable() && entity != owner)) {
			AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
			Optional<Vec3> clip = box.clip(start, end);
			double distanceSqr;
			if (box.contains(start)) {
				distanceSqr = 0.0D;
			}
			else if (clip.isPresent() && start.distanceToSqr(clip.get()) < maxDistanceSqr) {
				distanceSqr = start.distanceToSqr(clip.get());
			}
			else {
				continue;
			}
			if (distanceSqr < entityDistanceSqr) {
				entityDistanceSqr = distanceSqr;
				entityHit = new EntityHitResult(candidate, clip.orElse(start));
			}
		}
		if (entityHit != null) {
			return new HitResult[] { entityHit };
		}
		return new HitResult[] { level().clip(new ClipContext(start, end,
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this)) };
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
			if (target instanceof LivingEntity livingTarget
					&& JojoDefinitions.isUndeadOrVampiric(livingTarget)
					&& !WoodenCoffinBlock.isSleepingInCoffin(livingTarget)
					&& PlayerPower.getPowerData(livingTarget, ModPlayerPowers.PILLAR_MAN)
							.map(data -> !data.isStoneFormEnabled()).orElse(true)) {
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
	protected ResourceKey<DamageType> getDamageTypeKey() {
		return ModDamageTypes.ULTRAVIOLET_ENTITY;
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
