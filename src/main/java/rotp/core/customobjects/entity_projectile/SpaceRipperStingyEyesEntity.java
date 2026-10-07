package rotp.core.customobjects.entity_projectile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.init.ModEntityTypes;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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

public class SpaceRipperStingyEyesEntity extends ModdedProjectileEntity {
	private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(
			SpaceRipperStingyEyesEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> BOUND_TO_OWNER = SynchedEntityData.defineId(
			SpaceRipperStingyEyesEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> RETRACTING = SynchedEntityData.defineId(
			SpaceRipperStingyEyesEntity.class, EntityDataSerializers.BOOLEAN);

	private boolean rightEye;
	@Nullable
	private Vec3 detachedOriginPos;
	private boolean detachAfterMove;
	private long lastTickGameTime = Long.MIN_VALUE;

	public SpaceRipperStingyEyesEntity(Level level, LivingEntity owner, boolean rightEye) {
		super(ModEntityTypes.SPACE_RIPPER_STINGY_EYES.get(), owner, level);
		this.rightEye = rightEye;
		setBoundToOwner(true);
		setShootingPosOf(owner);
	}

	public SpaceRipperStingyEyesEntity(EntityType<? extends SpaceRipperStingyEyesEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		lastTickGameTime = level().getGameTime();
		super.tick();
		if (!isAlive()) {
			return;
		}
		if (!isBoundToOwner() && detachedOriginPos != null) {
			detachedOriginPos = detachedOriginPos.add(position().subtract(xOld, yOld, zOld));
		}
		if (tickCount > 20 || detachAfterMove) {
			detach();
		}
	}

	/**
	 * 1.16 ticked the beams in the level before the player whose action detaches them, so a beam was detached after
	 * the bound move of that tick. Here the owner's action ticks first: the detach waits for this tick's move.
	 */
	public void detachAfterThisTicksMove() {
		if (!isBoundToOwner() || level().isClientSide()) {
			return;
		}
		// Stopped in time, the beam makes no move this tick to wait for.
		if (lastTickGameTime == level().getGameTime() || TimeStopState.shouldFreezeOnServer(this)) {
			detach();
		}
		else {
			detachAfterMove = true;
		}
	}

	/**
	 * 1.16 OwnerBoundProjectileEntity.canUpdate: a beam bound to its owner is stopped in time exactly when that owner is.
	 */
	@Nullable
	public LivingEntity getOwnerBoundTo() {
		return isBoundToOwner() ? getOwner() : null;
	}

	@Override
	protected void moveProjectile() {
		if (isBoundToOwner()) {
			LivingEntity owner = getOwner();
			if (owner == null) {
				if (!level().isClientSide()) {
					discard();
				}
				return;
			}

			setRot(owner.getYRot(), owner.getXRot());
			Vec3 origin = ownerPosition(1.0F);
			boolean retracting = entityData.get(RETRACTING);
			float length = getLength() + (retracting ? -movementSpeed() : movementSpeed()) * (float) getSpeedFactor();
			if (retracting && length <= 0.0F) {
				if (!level().isClientSide()) {
					discard();
				}
				return;
			}
			setLength(length);
			Vec3 next = origin.add(Vec3.directionFromRotation(owner.getXRot(), owner.getYRot()).scale(length));
			setDeltaMovement(next.subtract(position()));

			double x = getX();
			double y = getY();
			double z = getZ();
			xo = x;
			yo = y;
			zo = z;
			xOld = x;
			yOld = y;
			zOld = z;
			setPos(next.x, next.y, next.z);
			return;
		}
		super.moveProjectile();
	}

	public void detach() {
		if (isBoundToOwner() && !level().isClientSide()) {
			Vec3 origin = getOriginPoint(1.0F);
			detachedOriginPos = origin;
			setLength((float) position().subtract(origin).length());
			setBoundToOwner(false);
			Vec3 direction = position().subtract(origin);
			if (direction.lengthSqr() > 1.0E-6D) {
				setDeltaMovement(direction.normalize().scale(movementSpeed()));
			}
		}
	}

	private void setBoundToOwner(boolean value) {
		entityData.set(BOUND_TO_OWNER, value);
	}

	public boolean isBoundToOwner() {
		return entityData.get(BOUND_TO_OWNER);
	}

	private void setLength(float length) {
		entityData.set(LENGTH, length);
	}

	public float getLength() {
		return entityData.get(LENGTH);
	}

	public Vec3 getOriginPoint(float partialTick) {
		if (!isBoundToOwner()) {
			if (detachedOriginPos == null) {
				detachedOriginPos = ownerPosition(partialTick);
			}
			return detachedOriginPos;
		}
		return ownerPosition(partialTick);
	}

	private Vec3 ownerPosition(float partialTick) {
		LivingEntity owner = getOwner();
		if (owner != null) {
			float yRot = Mth.lerp(partialTick, owner.yRotO, owner.getYRot());
			float xRot = Mth.lerp(partialTick, owner.xRotO, owner.getXRot());
			return getPos(owner, partialTick, yRot, xRot);
		}
		return getPosition(partialTick);
	}

	private static final Vec3 OFFSET_LEFT_EYE = new Vec3(0.09375D, -0.2D, 0.0D);
	private static final Vec3 OFFSET_RIGHT_EYE = new Vec3(-OFFSET_LEFT_EYE.x, OFFSET_LEFT_EYE.y, OFFSET_LEFT_EYE.z);

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return rightEye ? OFFSET_RIGHT_EYE : OFFSET_LEFT_EYE;
	}

	private static final Vec3 OFFSET_XROT = new Vec3(0.0D, 0.2D, 0.0D);

	@Override
	protected Vec3 getXRotOffset() {
		return OFFSET_XROT;
	}

	@Override
	public int ticksLifespan() {
		if (isBoundToOwner()) {
			return 50;
		}
		return Mth.floor(getLength() / movementSpeed() * 20.0F) + 20;
	}

	protected float movementSpeed() {
		return 0.5F + level().getDifficulty().getId() * 0.25F;
	}

	@Override
	protected HitResult[] rayTrace() {
		Vec3 start = getOriginPoint(1.0F);
		Vec3 end = position().add(getDeltaMovement());
		Vec3 ray = start.subtract(end);
		if (ray.lengthSqr() < 1.0E-7D) {
			return new HitResult[0];
		}

		List<HitResult> hits = new ArrayList<>();
		BlockHitResult blockHit = level().clip(new ClipContext(start, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (blockHit.getType() != HitResult.Type.MISS) {
			hits.add(blockHit);
		}

		AABB rayBox = getBoundingBox().expandTowards(ray).inflate(1.0D);
		double inflation = getBbWidth() / 2.0D;
		for (Entity target : level().getEntities(this, rayBox, this::canHitEntity)) {
			AABB targetBox = target.getBoundingBox().inflate(target.getPickRadius() + inflation);
			Optional<Vec3> clip = targetBox.clip(start, end);
			if (targetBox.contains(start)) {
				hits.add(new EntityHitResult(target, start));
			}
			else if (clip.isPresent()) {
				hits.add(new EntityHitResult(target, clip.get()));
			}
		}

		// Entity selection suppresses block effects before damage acceptance is known.
		if (hits.stream().anyMatch(hit -> hit.getType() == HitResult.Type.ENTITY)) {
			hits.removeIf(hit -> hit.getType() != HitResult.Type.ENTITY);
		}
		hits.sort(Comparator.comparingDouble(hit -> hit.getLocation().distanceToSqr(start)));
		return hits.toArray(HitResult[]::new);
	}

	@Override
	protected void afterBlockHit(BlockHitResult blockHit, boolean blockDestroyed) {
		if (!blockDestroyed) {
			entityData.set(RETRACTING, true);
		}
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		return DamageUtil.hurtThroughInvulTicks(target, getDamageSource(owner), getDamageAmount());
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	public float getBaseDamage() {
		return 1.0F + level().getDifficulty().getId();
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 3.0F;
	}

	@Override
	protected float knockbackMultiplier() {
		return 0.0F;
	}

	@Override
	public boolean canBeDeflected(@Nullable Entity context) {
		return context != null && context.getType() == ModEntityTypes.HAMON_PROJECTILE_SHIELD.get();
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		Entity sourceEntity = source.getEntity();
		return !(sourceEntity instanceof Player player && player.getAbilities().instabuild);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().expandTowards(getOriginPoint(1.0F).subtract(position()));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LENGTH, 0.0F);
		builder.define(BOUND_TO_OWNER, true);
		builder.define(RETRACTING, false);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putFloat("Length", getLength());
		nbt.putBoolean("IsRightEye", rightEye);
		nbt.putBoolean("BoundToOwner", isBoundToOwner());
		nbt.putBoolean("IsRetracting", entityData.get(RETRACTING));
		if (detachedOriginPos != null) {
			nbt.putDouble("DetachedOriginX", detachedOriginPos.x);
			nbt.putDouble("DetachedOriginY", detachedOriginPos.y);
			nbt.putDouble("DetachedOriginZ", detachedOriginPos.z);
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		setLength(nbt.getFloat("Length"));
		rightEye = nbt.getBoolean("IsRightEye");
		setBoundToOwner(!nbt.contains("BoundToOwner") || nbt.getBoolean("BoundToOwner"));
		entityData.set(RETRACTING, nbt.getBoolean("IsRetracting"));
		if (nbt.contains("DetachedOriginX")) {
			detachedOriginPos = new Vec3(
					nbt.getDouble("DetachedOriginX"),
					nbt.getDouble("DetachedOriginY"),
					nbt.getDouble("DetachedOriginZ"));
		}
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeBoolean(rightEye);
		buffer.writeFloat(getLength());
		buffer.writeBoolean(isBoundToOwner());
		buffer.writeBoolean(entityData.get(RETRACTING));
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		rightEye = additionalData.readBoolean();
		setLength(additionalData.readFloat());
		setBoundToOwner(additionalData.readBoolean());
		entityData.set(RETRACTING, additionalData.readBoolean());
	}
}
