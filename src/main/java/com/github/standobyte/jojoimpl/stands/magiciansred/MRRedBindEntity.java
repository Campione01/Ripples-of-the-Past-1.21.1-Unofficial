package com.github.standobyte.jojoimpl.stands.magiciansred;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class MRRedBindEntity extends OwnerBoundProjectileEntity {
	protected static final EntityDataAccessor<Integer> ATTACHED_ENTITY = SynchedEntityData.defineId(MRRedBindEntity.class, EntityDataSerializers.INT);
	protected static final EntityDataAccessor<Boolean> KICK_FINISHER = SynchedEntityData.defineId(MRRedBindEntity.class, EntityDataSerializers.BOOLEAN);

	private static final int ORIGINAL_FIRE_SECONDS = 3;
	private static final int ORIGINAL_FIRE_TICKS = ORIGINAL_FIRE_SECONDS * 20;
	private static final Vec3 OFFSET = new Vec3(0.0D, -0.25D, 0.5D);
	private MobEffectInstance immobilizedEffect = null;
	private int ticksTargetClose = 0;
	private double distance;
	private boolean movingForward = true;
	private boolean retracting;
	@Nullable
	private UUID attachedEntityUUID;

	public MRRedBindEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.MR_RED_BIND.get(), shooter, level);
	}

	public MRRedBindEntity(EntityType<? extends MRRedBindEntity> type, Level level) {
		super(type, level);
	}

	public static Optional<MRRedBindEntity> getLandedRedBind(StandEntity stand) {
		return stand.level().getEntitiesOfClass(MRRedBindEntity.class,
				stand.getBoundingBox().inflate(16),
				redBind -> stand.is(redBind.getOwner()) && redBind.isAttachedToAnEntity())
				.stream()
				.findFirst();
	}

	@Override
	public void tick() {
		if (isInWaterOrRain()) {
			clearFire();
			return;
		}
		resolveAttachedEntity();
		super.tick();
		if (!isAlive()) {
			return;
		}
		if (!level().isClientSide()) {
			LivingEntity owner = getOwner();
			if (!isInKickAttack() && !isOwnerStandUsingRedBind(owner)) {
				discard();
				return;
			}
		}
		LivingEntity bound = getEntityAttachedTo();
		if (bound != null) {
			updateAttachedPosition(bound);
			if (!level().isClientSide()) {
				LivingEntity owner = getOwner();
				if (owner == null || !bound.isAlive() || owner.distanceToSqr(bound) > 100) {
					discard();
					return;
				}
				refreshTargetEffects(bound);
				DamageUtil.suffocateTick(bound, isInKickAttack() ? 1 : 0.0025F);
				Vec3 vecToOwner = owner.position().subtract(bound.position());
				if (vecToOwner.lengthSqr() > 4) {
					dragTarget(bound, vecToOwner.normalize().scale(0.2));
					ticksTargetClose = 0;
				}
				else if (!isInKickAttack() && ticksTargetClose++ > 10) {
					discard();
				}
			}
		}
	}

	private boolean isOwnerStandUsingRedBind(@Nullable LivingEntity owner) {
		if (!(owner instanceof StandEntity stand)) {
			return false;
		}
		EntityActionInstance curAction = LivingComponentAction.getCurEntityAction(stand);
		return curAction != null
				&& curAction.ability.getAbilityId() != null
				&& "red_bind".equals(curAction.ability.getAbilityId().nameInMoveset());
	}

	@Override
	public int ticksLifespan() {
		return isAttachedToAnEntity()
				? isInKickAttack() ? Integer.MAX_VALUE : 100
				: 7;
	}

	@Override
	protected float getBaseDamage() {
		return 0.0F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0.0F;
	}

	@Override
	public boolean standDamage() {
		return true;
	}

	@Override
	public boolean isOnFire() {
		return false;
	}

	@Override
	public boolean isFiery() {
		return true;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return OFFSET;
	}

	@Override
	public void clearFire() {
		super.clearFire();
		if (level() instanceof ServerLevel serverLevel) {
			JojoModUtil.extinguishFieryStandEntity(this, serverLevel);
		}
	}

	@Override
	protected boolean shouldFollowOwner() {
		return false;
	}

	@Override
	protected void moveProjectile() {
		if (isAttachedToAnEntity()) {
			LivingEntity bound = getEntityAttachedTo();
			if (bound != null) {
				updateAttachedPosition(bound);
			}
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		moveOwnerBoundRedBind();
	}

	private void moveOwnerBoundRedBind() {
		LivingEntity owner = getOwner();
		if (owner == null) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}

		setRot(owner.getYRot(), owner.getXRot());
		Vec3 nextOriginOffset = getNextOriginOffset(owner);
		if (nextOriginOffset == null) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}

		Vec3 origin = ownerPosition(1.0F, false);
		double x = getX();
		double y = getY();
		double z = getZ();
		double nextX = origin.x + nextOriginOffset.x;
		double nextY = origin.y + nextOriginOffset.y;
		double nextZ = origin.z + nextOriginOffset.z;
		setDeltaMovement(nextX - x, nextY - y, nextZ - z);

		xo = x;
		yo = y;
		zo = z;
		xOld = x;
		yOld = y;
		zOld = z;
		setPos(nextX, nextY, nextZ);
	}

	@Nullable
	private Vec3 getNextOriginOffset(LivingEntity owner) {
		double nextDistance = updateDistance();
		updateMotionFlags();
		if (retracting && nextDistance <= 0.0D) {
			return null;
		}
		distance = Math.max(0.0D, nextDistance);
		return Vec3.directionFromRotation(owner.getXRot(), owner.getYRot()).scale(distance);
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

	private float movementSpeed() {
		return 1.0F;
	}

	private int timeAtFullLength() {
		return 0;
	}

	private float retractSpeed() {
		return movementSpeed();
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (!isAttachedToAnEntity() && target instanceof LivingEntity living
				&& !living.isInvulnerableTo(getDamageSource(owner)) && !JojoModUtil.isTargetBlocking(living)) {
			attachToEntity(living);
			if (!level().isClientSide()) {
				boolean thisEffect = immobilizedEffect == living.getEffect(ModStatusEffects.IMMOBILIZE);
				living.addEffect(new MobEffectInstance(ModStatusEffects.IMMOBILIZE, ticksLifespan() - tickCount));
				if (thisEffect) {
					immobilizedEffect = living.getEffect(ModStatusEffects.IMMOBILIZE);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		removeBoundEffect(getEntityAttachedTo());
		super.remove(reason);
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (!isAttachedToAnEntity()) {
			super.breakProjectile(targetType, hitTarget);
		}
	}

	@Nullable
	public LivingEntity getEntityAttachedTo() {
		int entityId = entityData.get(ATTACHED_ENTITY);
		if (entityId < 0) {
			return null;
		}
		Entity entity = level().getEntity(entityId);
		return entity instanceof LivingEntity living ? living : null;
	}

	public boolean isAttachedToAnEntity() {
		return getEntityAttachedTo() != null;
	}

	public void attachToEntity(LivingEntity target) {
		attachedEntityUUID = null;
		entityData.set(ATTACHED_ENTITY, target.getId());
		updateAttachedPosition(target);
		setDeltaMovement(Vec3.ZERO);
	}

	private void resolveAttachedEntity() {
		if (!level().isClientSide() && attachedEntityUUID != null && entityData.get(ATTACHED_ENTITY) < 0
				&& level() instanceof ServerLevel serverLevel) {
			Entity entity = serverLevel.getEntity(attachedEntityUUID);
			if (entity instanceof LivingEntity living) {
				attachToEntity(living);
				attachedEntityUUID = null;
			}
		}
	}

	public void setKickAttack() {
		entityData.set(KICK_FINISHER, true);
		LivingEntity target = getEntityAttachedTo();
		if (target != null && !level().isClientSide()) {
			removeBoundEffect(target);
			target.addEffect(new MobEffectInstance(ModStatusEffects.STUN, ticksLifespan() - tickCount));
			immobilizedEffect = target.getEffect(ModStatusEffects.STUN);
		}
	}

	public boolean isInKickAttack() {
		return entityData.get(KICK_FINISHER);
	}

	private void refreshTargetEffects(LivingEntity bound) {
		if (bound.getRemainingFireTicks() <= 0 || bound.getRemainingFireTicks() % 20 == 0) {
			DamageUtil.setOnFire(bound, ORIGINAL_FIRE_TICKS, true);
		}
	}

	private void removeBoundEffect(@Nullable LivingEntity bound) {
		if (!level().isClientSide() && bound != null && immobilizedEffect != null) {
			MobEffectInstance currentEffect = bound.getEffect(immobilizedEffect.getEffect());
			if (currentEffect == immobilizedEffect) {
				bound.removeEffect(immobilizedEffect.getEffect());
			}
			immobilizedEffect = null;
		}
	}

	private void dragTarget(LivingEntity bound, Vec3 impulse) {
		bound.setDeltaMovement(bound.getDeltaMovement().add(impulse));
		bound.hurtMarked = true;
	}

	private void updateAttachedPosition(LivingEntity bound) {
		setPos(bound.getX(), bound.getY(0.75), bound.getZ());
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeDouble(distance);
		buffer.writeBoolean(movingForward);
		buffer.writeBoolean(retracting);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		distance = additionalData.readDouble();
		movingForward = additionalData.readBoolean();
		retracting = additionalData.readBoolean();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		LivingEntity attached = getEntityAttachedTo();
		if (attached != null) {
			nbt.putUUID("AttachedEntity", attached.getUUID());
		}
		nbt.putDouble("Distance", distance);
		nbt.putBoolean("MovingForward", movingForward);
		nbt.putBoolean("Retracting", retracting);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		attachedEntityUUID = nbt.hasUUID("AttachedEntity") ? nbt.getUUID("AttachedEntity") : null;
		distance = nbt.getDouble("Distance");
		movingForward = !nbt.contains("MovingForward") || nbt.getBoolean("MovingForward");
		retracting = nbt.getBoolean("Retracting");
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ATTACHED_ENTITY, -1);
		builder.define(KICK_FINISHER, false);
	}
}
