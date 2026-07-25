package com.github.standobyte.jojoimpl.stands.hierophant;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class HGStringEntity extends OwnerBoundProjectileEntity {
	private static final EntityDataAccessor<Integer> ATTACHED_ENTITY = SynchedEntityData.defineId(HGStringEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RETRACTING = SynchedEntityData.defineId(HGStringEntity.class, EntityDataSerializers.BOOLEAN);

	private float yRotOffset;
	private float xRotOffset;
	private boolean binding;
	private boolean dealtDamage;
	private float knockback = 0;
	private int lifespan = 16;
	private double distance;
	@Nullable
	private UUID attachedEntityUUID;

	public HGStringEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.HG_STRING.get(), shooter, level);
	}

	public HGStringEntity(EntityType<? extends HGStringEntity> type, Level level) {
		super(type, level);
	}

	public void setStringProperties(float yRotOffset, float xRotOffset, boolean binding, int lifespan) {
		this.yRotOffset = yRotOffset;
		this.xRotOffset = xRotOffset;
		this.binding = binding;
		this.lifespan = lifespan;
	}

	public boolean isBinding() {
		return binding;
	}

	public void addKnockback(float knockback) {
		this.knockback = knockback;
	}

	@Override
	public int ticksLifespan() {
		int ticks = lifespan;
		if (binding && isAttachedToAnEntity()) {
			ticks += 10;
		}
		return ticks;
	}

	@Override
	protected float getBaseDamage() {
		return binding ? 0.5F : 1.0F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0;
	}

	@Override
	protected boolean shouldFollowOwner() {
		return false;
	}

	@Override
	protected void moveProjectile() {
		if (isAttachedToAnEntity()) {
			LivingEntity attached = getEntityAttachedTo();
			if (attached != null) {
				updateAttachedPosition(attached);
			}
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		if (isRetracting()) {
			moveOwnerBoundString(true);
			return;
		}
		moveOwnerBoundString(false);
	}

	private void moveOwnerBoundString(boolean retracting) {
		LivingEntity owner = getOwner();
		if (owner == null) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		setRot(owner.getYRot(), owner.getXRot());
		double nextDistance = retracting ? distance - retractSpeed() * speedFactor : distance + movementSpeed() * speedFactor;
		if (retracting && nextDistance <= 0.0D) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		distance = Math.max(0.0D, nextDistance);
		Vec3 origin = ownerPosition(1.0F, false);
		Vec3 next = origin.add(originOffset(owner.getYRot() + yRotOffset, owner.getXRot() + xRotOffset, distance));
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

	private Vec3 originOffset(float yRot, float xRot, double distance) {
		return Vec3.directionFromRotation(xRot, yRot).scale(distance);
	}

	@Override
	protected Vec3 getXRotOffset() {
		return new Vec3(0.0D, 0.0D, 0.25D);
	}

	@Override
	public boolean standDamage() {
		return true;
	}

	@Override
	public boolean isBodyPart() {
		return true;
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (!shouldHurtThroughInvulTicks()) {
			return !dealtDamage && super.hurtTarget(target, owner);
		}
		return !dealtDamage && DamageUtil.hurtThroughInvulTicks(target, getDamageSource(owner), getDamageAmount());
	}

	protected boolean shouldHurtThroughInvulTicks() {
		return true;
	}

	@Override
	protected void afterEntityHit(net.minecraft.world.phys.EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt) {
			dealtDamage = true;
			Entity target = entityRayTraceResult.getEntity();
			if (binding && target instanceof LivingEntity livingTarget && !JojoModUtil.isTargetBlocking(livingTarget)) {
				attachToEntity(livingTarget);
				applyBindingEffect(livingTarget);
			}
			if (!binding && knockback > 0 && target instanceof LivingEntity livingTarget) {
				livingTarget.knockback(knockback,
						Mth.sin(getYRot() * MathUtil.DEG_TO_RAD),
						-Mth.cos(getYRot() * MathUtil.DEG_TO_RAD));
			}
			if (!binding) {
				setRetracting(true);
			}
		}
	}

	@Override
	protected float knockbackMultiplier() {
		return 0F;
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (binding && isAttachedToAnEntity()) {
			return;
		}
		if (targetType != TargetType.ENTITY || dealtDamage) {
			setRetracting(true);
		}
	}

	@Override
	public void tick() {
		resolveAttachedEntity();
		if (!isAttachedToAnEntity() && !isRetracting() && tickCount >= forwardTicks()) {
			setRetracting(true);
		}
		super.tick();
		if (!isAlive()) {
			return;
		}
		LivingEntity attached = getEntityAttachedTo();
		if (attached != null) {
			updateAttachedPosition(attached);
			if (!level().isClientSide()) {
				if (!attached.isAlive()) {
					discard();
				}
			}
		}
	}

	private int forwardTicks() {
		return Math.max(1, lifespan / 2);
	}

	private double movementSpeed() {
		return 16.0D / Math.max(lifespan, 1);
	}

	private double retractSpeed() {
		return movementSpeed();
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

	private boolean isRetracting() {
		return entityData.get(RETRACTING);
	}

	private void setRetracting(boolean retracting) {
		entityData.set(RETRACTING, retracting);
	}

	private void applyBindingEffect(LivingEntity target) {
		int duration = Math.max(1, ticksLifespan() - tickCount);
		target.addEffect(new MobEffectInstance(ModStatusEffects.IMMOBILIZE, duration));
	}

	private void updateAttachedPosition(LivingEntity target) {
		setPos(target.getX(), target.getY(0.75D), target.getZ());
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeFloat(yRotOffset);
		buffer.writeFloat(xRotOffset);
		buffer.writeBoolean(binding);
		buffer.writeInt(lifespan);
		buffer.writeBoolean(isRetracting());
		buffer.writeDouble(distance);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		yRotOffset = additionalData.readFloat();
		xRotOffset = additionalData.readFloat();
		binding = additionalData.readBoolean();
		lifespan = additionalData.readInt();
		setRetracting(additionalData.readBoolean());
		distance = additionalData.readDouble();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putFloat("YRotOffset", yRotOffset);
		nbt.putFloat("XRotOffset", xRotOffset);
		nbt.putBoolean("Binding", binding);
		nbt.putBoolean("DealtDamage", dealtDamage);
		nbt.putFloat("Knockback", knockback);
		nbt.putInt("Lifespan", lifespan);
		nbt.putBoolean("Retracting", isRetracting());
		nbt.putDouble("Distance", distance);
		LivingEntity attached = getEntityAttachedTo();
		if (attached != null) {
			nbt.putUUID("AttachedEntity", attached.getUUID());
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		yRotOffset = nbt.getFloat("YRotOffset");
		xRotOffset = nbt.getFloat("XRotOffset");
		binding = nbt.getBoolean("Binding");
		dealtDamage = nbt.getBoolean("DealtDamage");
		knockback = nbt.getFloat("Knockback");
		lifespan = nbt.contains("Lifespan") ? nbt.getInt("Lifespan") : lifespan;
		setRetracting(nbt.getBoolean("Retracting"));
		distance = nbt.getDouble("Distance");
		attachedEntityUUID = nbt.hasUUID("AttachedEntity") ? nbt.getUUID("AttachedEntity") : null;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ATTACHED_ENTITY, -1);
		builder.define(RETRACTING, false);
	}
}
