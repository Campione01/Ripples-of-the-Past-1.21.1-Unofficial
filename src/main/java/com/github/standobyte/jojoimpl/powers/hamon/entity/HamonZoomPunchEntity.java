package com.github.standobyte.jojoimpl.powers.hamon.entity;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonZoomPunchState;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class HamonZoomPunchEntity extends OwnerBoundProjectileEntity {
	private static final EntityDataAccessor<Boolean> IS_MOVING_FORWARD = SynchedEntityData.defineId(
			HamonZoomPunchEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> IS_RETRACTING = SynchedEntityData.defineId(
			HamonZoomPunchEntity.class, EntityDataSerializers.BOOLEAN);
	private static final Vec3 RIGHT_HAND_OFFSET = new Vec3(-0.35D, -0.47D, 0.0D);
	private static final Vec3 LEFT_HAND_OFFSET = new Vec3(-RIGHT_HAND_OFFSET.x, RIGHT_HAND_OFFSET.y, RIGHT_HAND_OFFSET.z);
	private static final double ORIGIN_FORWARD_OFFSET = 0.75D;

	private HumanoidArm side = HumanoidArm.RIGHT;
	private float speed;
	private int lifeSpan = 14;
	private double distance;
	private float hamonDamage;
	private float hamonDamageCost;
	private boolean spendHamonStability;
	private float baseHitPoints;
	private boolean gaveHamonPointsForBaseHit;
	@Nullable
	private LivingEntity zoomPunchStateOwner;

	public HamonZoomPunchEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.HAMON_ZOOM_PUNCH.get(), shooter, level);
		this.side = shooter.getMainArm();
	}

	public HamonZoomPunchEntity(EntityType<? extends HamonZoomPunchEntity> type, Level level) {
		super(type, level);
	}

	public HamonZoomPunchEntity setSpeed(float speed) {
		this.speed = Math.max(speed, 0.0F);
		return this;
	}

	public HamonZoomPunchEntity setDuration(int lifeSpan) {
		this.lifeSpan = Math.max(1, lifeSpan);
		return this;
	}

	public HamonZoomPunchEntity setHamonDamageOnHit(float damage, float hitCost, boolean useBreathStability) {
		this.hamonDamage = Math.max(damage, 0.0F);
		this.hamonDamageCost = Math.max(hitCost, 0.0F);
		this.spendHamonStability = useBreathStability;
		return this;
	}

	public HamonZoomPunchEntity setBaseUsageStatPoints(float points) {
		this.baseHitPoints = Math.max(points, 0.0F);
		return this;
	}

	public HumanoidArm getSide() {
		return side;
	}

	@Override
	public int ticksLifespan() {
		return lifeSpan;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return side == HumanoidArm.LEFT ? LEFT_HAND_OFFSET : RIGHT_HAND_OFFSET;
	}

	@Override
	public boolean isBodyPart() {
		return true;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected float getBaseDamage() {
		LivingEntity owner = getOwner();
		return owner != null ? (float) owner.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)
				: (float) Attributes.ATTACK_DAMAGE.value().getDefaultValue();
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
	protected boolean shouldFollowOwner() {
		return false;
	}

	@Override
	protected DamageSource getDamageSource(LivingEntity owner) {
		if (owner != null) {
			return new DamageSource(DamageUtil.type(level(),
					owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK), this, owner);
		}
		return super.getDamageSource(owner);
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (isRetracting() || owner == null) {
			return false;
		}
		boolean regularAttack = super.hurtTarget(target, owner);
		boolean hamonAttack = tryHamonAttack(target, owner);
		if (regularAttack) {
			applyOriginalKnockback(target, owner);
		}
		return regularAttack || hamonAttack;
	}

	private boolean tryHamonAttack(Entity target, LivingEntity owner) {
		if (!(target instanceof LivingEntity livingTarget)) {
			return false;
		}
		return PlayerPower.getPowerData(owner, ModPlayerPowers.HAMON).map(hamon -> {
			float energyBefore = hamon.getEnergy();
			boolean hasEnergy = energyBefore > 0.0F;
			if (!hasEnergy && !spendHamonStability) {
				return false;
			}
			float efficiency = hamon.getHamonEnergyUsageEfficiency(hamonDamageCost, true, owner);
			if (efficiency <= 0.0F) {
				return false;
			}

			boolean dealtHamonDamage = HamonAbilityHelpers.hamonHurt(livingTarget, owner, hamonDamage * efficiency);
			if (hasEnergy && dealtHamonDamage) {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, Math.min(hamonDamageCost, energyBefore) * efficiency);
			}
			if (!gaveHamonPointsForBaseHit) {
				gaveHamonPointsForBaseHit = true;
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, baseHitPoints);
			}
			hamon.syncOnUpdate(owner);
			return dealtHamonDamage;
		}).orElse(false);
	}

	private void applyOriginalKnockback(Entity target, LivingEntity owner) {
		float knockback = (float) owner.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
		if (knockback <= 0.0F) {
			return;
		}
		if (target instanceof LivingEntity livingTarget) {
			livingTarget.knockback(knockback * 0.5F,
					Mth.sin(owner.getYRot() * MathUtil.DEG_TO_RAD),
					-Mth.cos(owner.getYRot() * MathUtil.DEG_TO_RAD));
		}
		else {
			target.push(
					-Mth.sin(owner.getYRot() * MathUtil.DEG_TO_RAD) * knockback * 0.5F,
					0.1D,
					Mth.cos(owner.getYRot() * MathUtil.DEG_TO_RAD) * knockback * 0.5F);
		}
		setDeltaMovement(getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt) {
			setRetracting(true);
		}
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		setRetracting(true);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isAlive()) {
			setOwnerZoomPunch(true);
			Vec3 pos = position();
			HamonSparksLoopSound.playSparkSound(this, pos, 0.25F);
			level().addParticle(ModParticles.HAMON_SPARK.get(), pos.x, pos.y, pos.z,
					(random.nextDouble() - 0.5D) * 0.05D,
					(random.nextDouble() - 0.5D) * 0.05D,
					(random.nextDouble() - 0.5D) * 0.05D);
		}
	}

	@Override
	protected void moveProjectile() {
		Entity owner = getOwner();
		if (!(owner instanceof LivingEntity livingOwner)) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		setRot(livingOwner.getYRot(), livingOwner.getXRot());
		double nextDistance = updateDistance();
		if (isRetracting() && nextDistance <= 0.0D) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		distance = Math.max(0.0D, nextDistance);
		Vec3 origin = ownerPosition(1.0F, false);
		Vec3 next = origin.add(originOffset(livingOwner.getYRot(), livingOwner.getXRot(), distance + ORIGIN_FORWARD_OFFSET));
		Vec3 pos = position();
		setDeltaMovement(next.subtract(pos));
		xo = pos.x;
		yo = pos.y;
		zo = pos.z;
		xOld = pos.x;
		yOld = pos.y;
		zOld = pos.z;
		setPos(next);
	}

	private double updateDistance() {
		double nextDistance = distance;
		if (isRetracting()) {
			nextDistance -= movementSpeed() * speedFactor;
		}
		else if (isMovingForward()) {
			nextDistance += movementSpeed() * speedFactor;
		}
		updateMotionFlags();
		return nextDistance;
	}

	private void updateMotionFlags() {
		int forwardTicks = Math.max(1, lifeSpan / 2);
		if (isMovingForward() && tickCount >= forwardTicks) {
			setMovingForward(false);
		}
		if (!isRetracting() && tickCount >= forwardTicks) {
			setRetracting(true);
		}
	}

	private Vec3 originOffset(float yRot, float xRot, double distance) {
		return Vec3.directionFromRotation(xRot, yRot).scale(distance);
	}

	private float movementSpeed() {
		return Math.max(speed, 0.01F);
	}

	private boolean isMovingForward() {
		return entityData.get(IS_MOVING_FORWARD);
	}

	private void setMovingForward(boolean movingForward) {
		entityData.set(IS_MOVING_FORWARD, movingForward);
	}

	public boolean isRetracting() {
		return entityData.get(IS_RETRACTING);
	}

	public void setRetracting(boolean retracting) {
		entityData.set(IS_RETRACTING, retracting);
		if (retracting) {
			setMovingForward(false);
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(IS_MOVING_FORWARD, true);
		builder.define(IS_RETRACTING, false);
	}

	@Override
	public boolean isOnFire() {
		LivingEntity owner = getOwner();
		return owner == null ? super.isOnFire() : owner.isOnFire();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putBoolean("LeftArm", side == HumanoidArm.LEFT);
		nbt.putFloat("Speed", speed);
		nbt.putInt("LifeSpan", lifeSpan);
		nbt.putDouble("Distance", distance);
		nbt.putBoolean("MovingForward", isMovingForward());
		nbt.putBoolean("Retracting", isRetracting());
		nbt.putFloat("HamonDamage", hamonDamage);
		nbt.putFloat("HamonDamageCost", hamonDamageCost);
		nbt.putBoolean("SpendStab", spendHamonStability);
		nbt.putBoolean("PointsGiven", gaveHamonPointsForBaseHit);
		nbt.putFloat("Points", baseHitPoints);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		side = nbt.getBoolean("LeftArm") ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		speed = nbt.getFloat("Speed");
		lifeSpan = nbt.contains("LifeSpan") ? Math.max(1, nbt.getInt("LifeSpan")) : lifeSpan;
		distance = nbt.getDouble("Distance");
		setMovingForward(!nbt.contains("MovingForward") || nbt.getBoolean("MovingForward"));
		setRetracting(nbt.getBoolean("Retracting"));
		hamonDamage = nbt.getFloat("HamonDamage");
		hamonDamageCost = nbt.getFloat("HamonDamageCost");
		spendHamonStability = nbt.getBoolean("SpendStab");
		gaveHamonPointsForBaseHit = nbt.getBoolean("PointsGiven");
		baseHitPoints = nbt.getFloat("Points");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeBoolean(side == HumanoidArm.LEFT);
		buffer.writeFloat(speed);
		buffer.writeVarInt(lifeSpan);
		buffer.writeDouble(distance);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		side = additionalData.readBoolean() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		speed = additionalData.readFloat();
		lifeSpan = additionalData.readVarInt();
		distance = additionalData.readDouble();
		setOwnerZoomPunch(true);
	}

	@Override
	public void remove(RemovalReason reason) {
		setOwnerZoomPunch(false);
		super.remove(reason);
	}

	private void setOwnerZoomPunch(boolean value) {
		if (!level().isClientSide()) {
			return;
		}
		if (value) {
			LivingEntity owner = getOwner();
			if (owner == null) {
				return;
			}
			if (zoomPunchStateOwner == owner) {
				HamonZoomPunchState.touch(owner);
			}
			else {
				if (zoomPunchStateOwner != null) {
					HamonZoomPunchState.remove(zoomPunchStateOwner);
				}
				zoomPunchStateOwner = owner;
				HamonZoomPunchState.add(owner);
			}
		}
		else if (zoomPunchStateOwner != null) {
			HamonZoomPunchState.remove(zoomPunchStateOwner);
			zoomPunchStateOwner = null;
		}
	}
}
