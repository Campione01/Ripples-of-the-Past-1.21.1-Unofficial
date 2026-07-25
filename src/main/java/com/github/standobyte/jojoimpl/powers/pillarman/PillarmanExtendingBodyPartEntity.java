package com.github.standobyte.jojoimpl.powers.pillarman;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.DamageUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class PillarmanExtendingBodyPartEntity extends OwnerBoundProjectileEntity {
	private static final EntityDataAccessor<Boolean> IS_MOVING_FORWARD = SynchedEntityData.defineId(
			PillarmanExtendingBodyPartEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> IS_RETRACTING = SynchedEntityData.defineId(
			PillarmanExtendingBodyPartEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> EXTENSION_DISTANCE = SynchedEntityData.defineId(
			PillarmanExtendingBodyPartEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> ENTITY_ATTACHED_TO = SynchedEntityData.defineId(
			PillarmanExtendingBodyPartEntity.class, EntityDataSerializers.INT);

	private int lifeSpan = 1;

	protected PillarmanExtendingBodyPartEntity(EntityType<? extends PillarmanExtendingBodyPartEntity> type,
			LivingEntity shooter, Level level) {
		super(type, shooter, level);
	}

	protected PillarmanExtendingBodyPartEntity(EntityType<? extends PillarmanExtendingBodyPartEntity> type,
			Level level) {
		super(type, level);
	}

	@Override
	protected boolean shouldFollowOwner() {
		return false;
	}

	@Override
	protected void moveProjectile() {
		if (moveToEntityAttached()) {
			return;
		}
		if (moveBoundToOwner()) {
			return;
		}
		super.moveProjectile();
	}

	protected boolean moveBoundToOwner() {
		LivingEntity owner = getOwner();
		if (owner == null) {
			if (!level().isClientSide()) {
				discard();
			}
			return true;
		}

		setRot(owner.getYRot(), owner.getXRot());
		Vec3 originPoint = ownerPosition(1.0F, false);
		Vec3 nextOriginOffset = getNextOriginOffset();
		if (nextOriginOffset == null) {
			if (!level().isClientSide()) {
				discard();
			}
			return true;
		}

		double x = getX();
		double y = getY();
		double z = getZ();
		double nextX = originPoint.x + nextOriginOffset.x;
		double nextY = originPoint.y + nextOriginOffset.y;
		double nextZ = originPoint.z + nextOriginOffset.z;
		setDeltaMovement(new Vec3(nextX - getX(), nextY - getY(), nextZ - getZ()));

		xo = x;
		yo = y;
		zo = z;
		xOld = x;
		yOld = y;
		zOld = z;
		setPos(nextX, nextY, nextZ);
		return true;
	}

	protected boolean moveToEntityAttached() {
		LivingEntity bound = getEntityAttachedTo();
		if (bound != null) {
			moveTo(bound.getX(), bound.getY(attachedTargetHeight()), bound.getZ(), bound.getYRot(), bound.getXRot());
			setDeltaMovement(Vec3.ZERO);
			return true;
		}
		return false;
	}

	protected double attachedTargetHeight() {
		return 0.5D;
	}

	@Override
	public Vec3 getOriginPoint(float partialTick) {
		return ownerPosition(partialTick, isBodyPart());
	}

	protected Vec3 ownerPosition(float partialTick, boolean useBodyRotation) {
		LivingEntity owner = getOwner();
		if (owner != null) {
			float yRot = useBodyRotation
					? Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot)
					: Mth.lerp(partialTick, owner.yRotO, owner.getYRot());
			float xRot = Mth.lerp(partialTick, owner.xRotO, owner.getXRot());
			return getPos(owner, partialTick, yRot, xRot);
		}
		return getPosition(partialTick);
	}

	public boolean isBodyPart() {
		return false;
	}

	@Nullable
	protected Vec3 getNextOriginOffset() {
		LivingEntity owner = getOwner();
		if (owner == null) {
			return null;
		}

		double distance = updateDistance();
		updateMotionFlags();
		if (isRetracting() && distance <= 0.0D) {
			return null;
		}
		setDistance(distance);
		return originOffset(owner.getYRot(), owner.getXRot(), distance);
	}

	protected float updateDistance() {
		if (isRetracting()) {
			return (float) (getDistance() - retractSpeed() * speedFactor);
		}
		if (isMovingForward()) {
			return (float) (getDistance() + movementSpeed() * speedFactor);
		}
		return (float) getDistance();
	}

	protected abstract float movementSpeed();

	protected int timeAtFullLength() {
		return 0;
	}

	protected float retractSpeed() {
		return movementSpeed();
	}

	protected void updateMotionFlags() {
		int stopForwardMotionMark = (int) (maxDistance() / movementSpeed());
		if (isMovingForward() && tickCount >= stopForwardMotionMark) {
			setIsMovingForward(false);
		}
		if (!isRetracting() && tickCount >= stopForwardMotionMark + timeAtFullLength()) {
			setIsRetracting(true);
		}
	}

	private double maxDistance() {
		return movementSpeed() * retractSpeed() * (ticksLifespan() - timeAtFullLength())
				/ (movementSpeed() + retractSpeed());
	}

	protected Vec3 originOffset(float yRot, float xRot, double distance) {
		return Vec3.directionFromRotation(xRot, yRot).scale(distance);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().expandTowards(getOriginPoint(1.0F).subtract(position()));
	}

	@Override
	protected HitResult[] rayTrace() {
		Vec3 startPos = getOriginPoint(1.0F);
		Vec3 endPos = position().add(getDeltaMovement());
		Vec3 rayVec = startPos.subtract(endPos);
		if (rayVec.lengthSqr() < 1.0E-7D) {
			return new HitResult[0];
		}

		List<HitResult> hits = new ArrayList<>();
		BlockHitResult blockHit = level().clip(new ClipContext(startPos, endPos,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (blockHit.getType() != HitResult.Type.MISS) {
			hits.add(blockHit);
		}

		AABB rayBox = getBoundingBox().expandTowards(rayVec).inflate(1.0D);
		double entityInflation = getBbWidth() / 2.0D;
		for (Entity potentialTarget : level().getEntities(this, rayBox, entity -> canHitEntity(entity))) {
			AABB targetBox = potentialTarget.getBoundingBox().inflate(potentialTarget.getPickRadius() + entityInflation);
			Optional<Vec3> clip = targetBox.clip(startPos, endPos);
			if (targetBox.contains(startPos)) {
				hits.add(new EntityHitResult(potentialTarget, startPos));
			}
			else if (clip.isPresent()) {
				hits.add(new EntityHitResult(potentialTarget, clip.get()));
			}
		}

		hits.sort(Comparator.comparingDouble(hit -> hit.getLocation().distanceToSqr(startPos)));
		return hits.toArray(HitResult[]::new);
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (shouldHurtThroughInvulTicks()) {
			return DamageUtil.hurtThroughInvulTicks(target, getDamageSource(owner), getDamageAmount());
		}
		return super.hurtTarget(target, owner);
	}

	protected boolean shouldHurtThroughInvulTicks() {
		return false;
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {}

	@Override
	protected void afterBlockHit(BlockHitResult blockRayTraceResult, boolean blockDestroyed) {
		if (!blockDestroyed) {
			setIsRetracting(true);
		}
	}

	public void attachToEntity(LivingEntity boundTarget) {
		entityData.set(ENTITY_ATTACHED_TO, boundTarget.getId());
	}

	@Nullable
	public LivingEntity getEntityAttachedTo() {
		int id = entityData.get(ENTITY_ATTACHED_TO);
		if (id < 0) {
			return null;
		}
		Entity entity = level().getEntity(id);
		return entity instanceof LivingEntity living ? living : null;
	}

	public boolean isAttachedToAnEntity() {
		return entityData.get(ENTITY_ATTACHED_TO) > -1;
	}

	protected void setDistance(double distance) {
		entityData.set(EXTENSION_DISTANCE, (float) distance);
	}

	protected double getDistance() {
		return entityData.get(EXTENSION_DISTANCE);
	}

	protected void setIsMovingForward(boolean isMovingForward) {
		entityData.set(IS_MOVING_FORWARD, isMovingForward);
	}

	protected boolean isMovingForward() {
		return entityData.get(IS_MOVING_FORWARD);
	}

	protected void setIsRetracting(boolean isRetracting) {
		entityData.set(IS_RETRACTING, isRetracting);
	}

	protected boolean isRetracting() {
		return entityData.get(IS_RETRACTING);
	}

	public void setLifeSpan(int lifeSpan) {
		this.lifeSpan = lifeSpan;
	}

	@Override
	public int ticksLifespan() {
		return lifeSpan;
	}

	@Override
	public boolean isInvisible() {
		LivingEntity owner = getOwner();
		return ownerInvisibility() && owner != null && owner.isInvisible() || super.isInvisible();
	}

	@Override
	public boolean isInvisibleTo(Player player) {
		LivingEntity owner = getOwner();
		return ownerInvisibility() && owner != null && owner.isInvisibleTo(player) || super.isInvisibleTo(player);
	}

	public boolean ownerInvisibility() {
		return isBodyPart();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(IS_MOVING_FORWARD, true);
		builder.define(IS_RETRACTING, false);
		builder.define(EXTENSION_DISTANCE, 0.0F);
		builder.define(ENTITY_ATTACHED_TO, -1);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putInt("AttachedEntity", entityData.get(ENTITY_ATTACHED_TO));
		nbt.putDouble("Distance", getDistance());
		nbt.putBoolean("IsMovingForward", isMovingForward());
		nbt.putBoolean("IsRetracting", isRetracting());
		nbt.putInt("LifeSpan", lifeSpan);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		entityData.set(ENTITY_ATTACHED_TO, nbt.getInt("AttachedEntity"));
		setDistance(nbt.getDouble("Distance"));
		setIsMovingForward(nbt.getBoolean("IsMovingForward"));
		setIsRetracting(nbt.getBoolean("IsRetracting"));
		lifeSpan = nbt.getInt("LifeSpan");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeVarInt(lifeSpan);
		buffer.writeDouble(getDistance());
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		lifeSpan = additionalData.readVarInt();
		setDistance(additionalData.readDouble());
	}
}
