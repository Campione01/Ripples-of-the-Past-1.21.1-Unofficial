package com.github.standobyte.jojoimpl.powers.hamon.entity;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class HamonTurquoiseBlueOverdriveEntity extends ModdedProjectileEntity {
	private float radius;
	private float damage;
	private float points;
	private float sparksCount;
	private boolean gaveHamonPoints;
	private int duration;

	public HamonTurquoiseBlueOverdriveEntity(Level level, LivingEntity user) {
		super(ModEntityTypes.TURQUOISE_BLUE_OVERDRIVE.get(), user, level);
	}

	public HamonTurquoiseBlueOverdriveEntity(EntityType<? extends HamonTurquoiseBlueOverdriveEntity> entityType, Level level) {
		super(entityType, level);
	}

	public HamonTurquoiseBlueOverdriveEntity setRadius(float radius) {
		this.radius = Math.max(radius, 0.0F);
		this.sparksCount = this.radius * this.radius * 3.0F;
		Vec3 center = getBoundingBox().getCenter();
		refreshDimensions();
		setBoundingBox(new AABB(center, center).inflate(this.radius));
		return this;
	}

	public HamonTurquoiseBlueOverdriveEntity setDamage(float damage) {
		this.damage = Math.max(damage, 0.0F);
		return this;
	}

	public HamonTurquoiseBlueOverdriveEntity setPoints(float points) {
		this.points = Math.max(points, 0.0F);
		return this;
	}

	public HamonTurquoiseBlueOverdriveEntity setDuration(int ticks) {
		this.duration = Math.max(ticks, 1);
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
			int sparks = Math.max((int) (sparksCount * damageWearOffMultiplier()), 1);
			for (int i = 0; i < sparks; i++) {
				Vec3 sparkVec = center.add(new Vec3(
						random.nextDouble() - 0.5D,
						random.nextDouble() - 0.5D,
						random.nextDouble() - 0.5D)
						.normalize().scale(random.nextDouble() * radius));
				if (level().getFluidState(BlockPos.containing(sparkVec)).is(FluidTags.WATER)) {
					level().addParticle(ModParticles.HAMON_SPARK_BLUE.get(), sparkVec.x, sparkVec.y, sparkVec.z, 0.0D, 0.0D, 0.0D);
				}
			}
			level().playLocalSound(center.x, center.y, center.z, ModSoundEvents.HAMON_SPARK.get(),
					SoundSource.AMBIENT, Math.min(0.1F + radius * 0.15F, 1.0F),
					1.0F + (random.nextFloat() - 0.5F) * 0.15F, false);
		}
	}

	@Override
	protected void checkHit() {
		if (!level().isClientSide()) {
			if (!isInWaterOrBubble()) {
				discard();
				return;
			}
			level().getEntitiesOfClass(LivingEntity.class, getBoundingBox(),
					entity -> entity.isInWaterOrBubble() && canHitEntity(entity))
					.forEach(target -> onHitEntity(new EntityHitResult(target)));
		}
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		return owner != null && target instanceof LivingEntity livingTarget
				&& HamonAbilityHelpers.hamonHurtWithParticles(livingTarget, owner,
						getDamageAmount(), ModParticles.HAMON_SPARK_BLUE.get(), 8);
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (!entityHurt) {
			return;
		}
		Entity target = entityRayTraceResult.getEntity();
		if (target instanceof LivingEntity livingTarget && target.isInWaterOrBubble()) {
			DamageUtil.knockback3d(livingTarget, radius * 0.1F, getXRot(), getYRot());
		}
		if (!gaveHamonPoints) {
			PlayerPower.getPowerData(getOwner(), ModPlayerPowers.HAMON).ifPresent(hamon -> {
				gaveHamonPoints = true;
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, points);
			});
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
		EntityDimensions defaultSize = super.getDimensions(pose);
		return EntityDimensions.scalable(radius * 2.0F, radius * 2.0F).withEyeHeight(defaultSize.eyeHeight());
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
	protected float getDamageAmount() {
		return damage * damageWearOffMultiplier();
	}

	private float damageWearOffMultiplier() {
		float ageRatio = duration > 0 ? (float) tickCount / (float) duration : 1.0F;
		return Math.min(2.0F - ageRatio * 2.0F, 1.0F);
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
	public boolean canBeDeflected(@Nullable Entity context) {
		return false;
	}

	@Override
	public boolean canBeEvaded(@Nullable Entity context) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putFloat("Radius", radius);
		nbt.putBoolean("PointsGiven", gaveHamonPoints);
		nbt.putFloat("Damage", damage);
		nbt.putFloat("Points", points);
		nbt.putInt("Duration", duration);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		setRadius(nbt.getFloat("Radius"));
		gaveHamonPoints = nbt.getBoolean("PointsGiven");
		damage = nbt.getFloat("Damage");
		points = nbt.getFloat("Points");
		duration = nbt.getInt("Duration");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeFloat(radius);
		buffer.writeVarInt(duration);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		setRadius(additionalData.readFloat());
		setDuration(additionalData.readVarInt());
	}
}
