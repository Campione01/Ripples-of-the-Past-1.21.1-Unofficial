package com.github.standobyte.jojoimpl.stands.starplatinum;

import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SPStarFingerEntity extends OwnerBoundProjectileEntity {
	private static final Vec3 OFFSET = new Vec3(-0.3D, -0.2D, 0.75D);
	private int lifeSpan = 20;
	private double distance;
	private boolean movingForward = true;
	private boolean retracting;

	public SPStarFingerEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.SP_STAR_FINGER.get(), shooter, level);
	}

	public SPStarFingerEntity(EntityType<? extends SPStarFingerEntity> type, Level level) {
		super(type, level);
	}

	public void setLifeSpan(int lifeSpan) {
		this.lifeSpan = Math.max(1, lifeSpan);
	}

	@Override
	public int ticksLifespan() {
		return lifeSpan;
	}

	@Override
	protected float getBaseDamage() {
		return 4.5F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 5.0F;
	}

	@Override
	public boolean standDamage() {
		return true;
	}

	@Override
	protected boolean shouldFollowOwner() {
		return false;
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
	protected Vec3 getOwnerRelativeOffset() {
		return OFFSET;
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
		Vec3 originPoint = ownerPosition(1.0F, false);
		Vec3 nextOriginOffset = getNextOriginOffset(livingOwner);
		if (nextOriginOffset == null) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}

		double x = getX();
		double y = getY();
		double z = getZ();
		double nextX = originPoint.x + nextOriginOffset.x;
		double nextY = originPoint.y + nextOriginOffset.y;
		double nextZ = originPoint.z + nextOriginOffset.z;
		setDeltaMovement(nextX - x, nextY - y, nextZ - z);

		xo = x;
		yo = y;
		zo = z;
		xOld = x;
		yOld = y;
		zOld = z;
		setPos(nextX, nextY, nextZ);
	}

	private Vec3 getNextOriginOffset(LivingEntity owner) {
		double nextDistance = updateDistance();
		updateMotionFlags();
		if (retracting && nextDistance <= 0) {
			return null;
		}
		distance = Math.max(0, nextDistance);
		return originOffset(owner.getYRot(), owner.getXRot(), distance);
	}

	private double updateDistance() {
		if (retracting) {
			return distance - retractSpeed() * speedFactor;
		}
		if (movingForward) {
			return distance + movementSpeed() * speedFactor;
		}
		return distance;
	}

	private void updateMotionFlags() {
		int stopForwardMotionMark = Math.max(1, (int) (maxDistance() / movementSpeed()));
		if (movingForward && tickCount >= stopForwardMotionMark) {
			movingForward = false;
		}
		if (!retracting && tickCount >= stopForwardMotionMark + timeAtFullLength()) {
			retracting = true;
		}
	}

	private double maxDistance() {
		return movementSpeed() * retractSpeed() * (ticksLifespan() - timeAtFullLength())
				/ (movementSpeed() + retractSpeed());
	}

	private Vec3 originOffset(float yRot, float xRot, double distance) {
		return Vec3.directionFromRotation(xRot, yRot).scale(distance);
	}

	private float movementSpeed() {
		return 0.3F;
	}

	private int timeAtFullLength() {
		return 4;
	}

	private float retractSpeed() {
		return movementSpeed() * 3F;
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeVarInt(lifeSpan);
		buffer.writeDouble(distance);
		buffer.writeBoolean(movingForward);
		buffer.writeBoolean(retracting);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		lifeSpan = additionalData.readVarInt();
		distance = additionalData.readDouble();
		movingForward = additionalData.readBoolean();
		retracting = additionalData.readBoolean();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putInt("LifeSpan", lifeSpan);
		nbt.putDouble("Distance", distance);
		nbt.putBoolean("MovingForward", movingForward);
		nbt.putBoolean("Retracting", retracting);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		lifeSpan = nbt.contains("LifeSpan") ? nbt.getInt("LifeSpan") : lifeSpan;
		distance = nbt.getDouble("Distance");
		movingForward = !nbt.contains("MovingForward") || nbt.getBoolean("MovingForward");
		retracting = nbt.getBoolean("Retracting");
	}
}
