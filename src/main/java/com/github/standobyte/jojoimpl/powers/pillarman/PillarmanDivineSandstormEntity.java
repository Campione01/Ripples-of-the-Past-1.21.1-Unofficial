package com.github.standobyte.jojoimpl.powers.pillarman;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.DamageUtil;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class PillarmanDivineSandstormEntity extends ModdedProjectileEntity {
	private float radius;
	private float damage;
	private float particlesCount;
	private int duration;
	private float xOriginOffset;
	private boolean atmosphericRift;

	public PillarmanDivineSandstormEntity(Level level, LivingEntity entity, float offsetX) {
		super(ModEntityTypes.PILLAR_MAN_DIVINE_SANDSTORM.get(), entity, level);
		this.xOriginOffset = offsetX;
	}

	public PillarmanDivineSandstormEntity(EntityType<? extends PillarmanDivineSandstormEntity> entityType, Level level) {
		super(entityType, level);
	}

	public PillarmanDivineSandstormEntity setRadius(float radius) {
		this.radius = Math.max(radius, 0.0F);
		this.particlesCount = isAtmospheric() ? radius * 10 : radius * 2;
		refreshDimensions();
		return this;
	}

	public PillarmanDivineSandstormEntity setDamage(float damage) {
		this.damage = damage;
		return this;
	}

	public PillarmanDivineSandstormEntity setAtmospheric(boolean atmosphericRift) {
		this.atmosphericRift = atmosphericRift;
		this.particlesCount = isAtmospheric() ? radius * 10 : radius * 2;
		return this;
	}

	public boolean isAtmospheric() {
		return atmosphericRift;
	}

	public ParticleOptions setParticle() {
		return isAtmospheric() ? ModParticles.RIFT.get() : ModParticles.SANDSTORM.get();
	}

	public PillarmanDivineSandstormEntity setDuration(int ticks) {
		this.duration = Math.max(ticks, 0);
		return this;
	}

	@Override
	public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
		setPos(getX(), getY() - radius, getZ());
		super.shoot(x, y, z, velocity, inaccuracy);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			Vec3 center = getBoundingBox().getCenter();
			int count = Math.max((int) (this.particlesCount * damageWearOffMultiplier()), 1);
			for (int i = 0; i < count; i++) {
				Vec3 particleVec = center.add(new Vec3(
						random.nextDouble() - 1.0,
						random.nextDouble() - 1.0,
						random.nextDouble() - 1.0)
						.normalize().scale(random.nextDouble() * radius));
				level().addParticle(setParticle(), false, particleVec.x, particleVec.y, particleVec.z, 0, 0, 0);
			}
		}
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt && entityRayTraceResult.getEntity() instanceof LivingEntity target) {
			DamageUtil.knockback3d(target, radius * 0.035F, getXRot(), getYRot());
		}
	}

	@Override
	protected void afterBlockHit(BlockHitResult blockRayTraceResult, boolean blockDestroyed) {
		super.afterBlockHit(blockRayTraceResult, blockDestroyed);
		Vec3 center = getBoundingBox().getCenter();
		if (isAtmospheric()) {
			level().playSound(null, center.x, center.y, center.z,
					SoundEvents.WITHER_BREAK_BLOCK, SoundSource.AMBIENT, 0.3F, 1.0F);
		}
		else {
			level().playSound(null, center.x, center.y, center.z,
					SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 0.7F, 1.0F);
		}
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (targetType != TargetType.ENTITY) {
			super.breakProjectile(targetType, hitTarget);
		}
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		float size = Math.max(radius * 1.2F, 0.1F);
		return EntityDimensions.scalable(size, size);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public int ticksLifespan() {
		return duration;
	}

	@Override
	protected float getBaseDamage() {
		return damage;
	}

	@Override
	protected float knockbackMultiplier() {
		return 0.1F;
	}

	@Override
	protected float getDamageAmount() {
		return damage * Math.max(damageWearOffMultiplier(), 0.5F);
	}

	private float damageWearOffMultiplier() {
		if (duration <= 0) {
			return 1.0F;
		}
		float ageRatio = (float) tickCount / (float) duration;
		return Math.min(2 - ageRatio * 2, 1);
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 5.0F;
	}

	@Override
	public boolean canBeDeflected(@Nullable Entity context) {
		return false;
	}

	@Override
	public boolean canBeEvaded(@Nullable Entity context) {
		return false;
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return new Vec3(xOriginOffset, 0.8F, 0);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putFloat("Radius", radius);
		nbt.putFloat("Damage", damage);
		nbt.putInt("Duration", duration);
		nbt.putFloat("Particles", particlesCount);
		nbt.putFloat("XOriginOffset", xOriginOffset);
		nbt.putBoolean("AtmosphericRift", atmosphericRift);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		setRadius(nbt.getFloat("Radius"));
		damage = nbt.getFloat("Damage");
		duration = nbt.getInt("Duration");
		particlesCount = nbt.getFloat("Particles");
		xOriginOffset = nbt.getFloat("XOriginOffset");
		atmosphericRift = nbt.getBoolean("AtmosphericRift");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeFloat(radius);
		buffer.writeVarInt(duration);
		buffer.writeFloat(particlesCount);
		buffer.writeFloat(xOriginOffset);
		buffer.writeBoolean(atmosphericRift);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		setRadius(additionalData.readFloat());
		setDuration(additionalData.readVarInt());
		particlesCount = additionalData.readFloat();
		xOriginOffset = additionalData.readFloat();
		atmosphericRift = additionalData.readBoolean();
	}
}
