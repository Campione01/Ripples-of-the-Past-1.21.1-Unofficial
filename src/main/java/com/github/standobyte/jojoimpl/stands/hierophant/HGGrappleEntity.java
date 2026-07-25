package com.github.standobyte.jojoimpl.stands.hierophant;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mechanics.ImmobilizeEffect;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.JojoModUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class HGGrappleEntity extends OwnerBoundProjectileEntity {
	private static final EntityDataAccessor<Integer> ATTACHED_ENTITY = SynchedEntityData.defineId(HGGrappleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BIND_ENTITIES = SynchedEntityData.defineId(HGGrappleEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ATTACHED_TO_BLOCK = SynchedEntityData.defineId(HGGrappleEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<BlockPos> BLOCK_ATTACHED_TO = SynchedEntityData.defineId(HGGrappleEntity.class, EntityDataSerializers.BLOCK_POS);
	private boolean placedBarrier;
	private double distance;
	@Nullable
	private UUID attachedEntityUUID;
	private final Set<Entity> dragged = new HashSet<>();

	public HGGrappleEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.HG_GRAPPLE.get(), shooter, level);
	}

	public HGGrappleEntity(EntityType<? extends HGGrappleEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public int ticksLifespan() {
		return !isAttachedToAnEntity() && !isAttachedToBlock() ? 40 : Integer.MAX_VALUE;
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
	public boolean isBodyPart() {
		return true;
	}

	@Override
	protected boolean shouldFollowOwner() {
		return false;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return new Vec3(-0.3D, -0.2D, 0.55D);
	}

	public void setBindEntities(boolean bindEntities) {
		entityData.set(BIND_ENTITIES, bindEntities);
	}

	public boolean isBindEntities() {
		return entityData.get(BIND_ENTITIES);
	}

	@Override
	public void tick() {
		clearDraggedMotion();
		resolveAttachedEntity();
		super.tick();
		if (!isAlive()) {
			return;
		}
		if (!level().isClientSide() && !isMatchingHeldGrappleAction()) {
			discard();
			return;
		}
		LivingEntity bound = getEntityAttachedTo();
		if (bound != null) {
			LivingEntity owner = getOwner();
			if (!bound.isAlive()) {
				if (!level().isClientSide()) {
					discard();
				}
			}
			else if (owner != null) {
				Vec3 vecToOwner = owner.position().subtract(bound.position());
				if (vecToOwner.length() < 2) {
					if (!level().isClientSide()) {
						discard();
					}
				}
				else {
					dragTarget(bound, vecToOwner.normalize().scale(1));
					bound.fallDistance = 0;
				}
			}
		}
	}

	@Override
	protected void moveProjectile() {
		LivingEntity bound = getEntityAttachedTo();
		if (bound != null) {
			updateAttachedPosition(bound);
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		if (isAttachedToBlock()) {
			BlockPos blockPos = getBlockPosAttachedTo();
			setPos(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D);
			setDeltaMovement(Vec3.ZERO);
			pullOwnerToBlock();
			return;
		}
		moveOwnerBoundGrapple();
	}

	private void moveOwnerBoundGrapple() {
		LivingEntity owner = getOwner();
		if (owner == null) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		setRot(owner.getYRot(), owner.getXRot());
		distance += movementSpeed() * speedFactor;
		Vec3 origin = ownerPosition(1.0F, false);
		Vec3 next = origin.add(Vec3.directionFromRotation(owner.getXRot(), owner.getYRot()).scale(distance));
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

	private void pullOwnerToBlock() {
		LivingEntity owner = getOwner();
		if (owner == null) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		Vec3 vecFromOwner = position().subtract(owner.position());
		if (vecFromOwner.lengthSqr() > 4) {
			Vec3 grappleVec = vecFromOwner.normalize().scale(2);
			Entity entity = owner;
			if (owner instanceof StandEntity stand && stand.isFollowingUser()) {
				LivingEntity user = stand.getUser();
				if (user != null) {
					entity = user;
				}
			}
			entity = entity.getRootVehicle();
			entity.setDeltaMovement(grappleVec);
			entity.fallDistance = 0;
			entity.hurtMarked = true;
		}
		else if (!level().isClientSide()) {
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		if (!(entity instanceof LivingEntity)) {
			return false;
		}
		LivingEntity owner = getOwner();
		if (owner != null) {
			if (entity.is(owner)) {
				return false;
			}
			if (owner instanceof StandEntity stand) {
				LivingEntity user = stand.getUser();
				if (user != null && entity.is(user) && stand.isFollowingUser()) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (getEntityAttachedTo() == null && isBindEntities() && target instanceof LivingEntity livingTarget) {
			if (!JojoModUtil.isTargetBlocking(livingTarget)) {
				attachToEntity(livingTarget);
				playSound(ModSoundEvents.HIEROPHANT_GREEN_GRAPPLE_CATCH.get(), 1.0F, 1.0F);
				return true;
			}
		}
		return false;
	}

	@Override
	protected void afterBlockHit(BlockHitResult blockRayTraceResult, boolean brokenBlock) {
		BlockPos blockHitPos = blockRayTraceResult.getBlockPos();
		if (level().getBlockState(blockHitPos).is(Blocks.BARRIER)) {
			discard();
			return;
		}
		if (!brokenBlock && !isBindEntities()) {
			if (!isAttachedToBlock()) {
				playSound(ModSoundEvents.HIEROPHANT_GREEN_GRAPPLE_CATCH.get(), 1.0F, 1.0F);
				attachToBlockPos(blockHitPos);
			}
			placeBarrier(blockHitPos);
		}
	}

	private boolean isMatchingHeldGrappleAction() {
		LivingEntity owner = getOwner();
		if (!(owner instanceof StandEntity stand)) {
			return false;
		}
		EntityActionInstance curAction = stand.getCurStandAction();
		if (curAction == null || curAction.getPhase() != ActionPhase.PERFORM || curAction.ability.getAbilityId() == null) {
			return false;
		}
		String expectedAction = isBindEntities() ? "grapple_entity" : "grapple";
		return expectedAction.equals(curAction.ability.getAbilityId().nameInMoveset());
	}

	private void placeBarrier(BlockPos blockPos) {
		if (!level().isClientSide() && !placedBarrier && getOwner() instanceof HierophantGreenEntity hierophant) {
			if (hierophant.hasBarrierAttached()) {
				hierophant.attachBarrier(blockPos);
			}
			placedBarrier = true;
		}
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (!isAttachedToAnEntity() && !isAttachedToBlock()) {
			if (targetType == TargetType.BLOCK && isBindEntities() || targetType == TargetType.EMPTY) {
				super.breakProjectile(targetType, hitTarget);
			}
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
		markGrappleActionCaughtEntity();
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

	private void markGrappleActionCaughtEntity() {
		if (getOwner() instanceof StandEntity stand) {
			EntityActionInstance curAction = stand.getCurStandAction();
			if (curAction instanceof HierophantGrappleAbility.GrappleShot grapple) {
				grapple.setCaughtEntity(true);
			}
		}
	}

	public boolean isAttachedToBlock() {
		return entityData.get(ATTACHED_TO_BLOCK);
	}

	public void attachToBlockPos(BlockPos blockPos) {
		entityData.set(BLOCK_ATTACHED_TO, blockPos);
		entityData.set(ATTACHED_TO_BLOCK, true);
	}

	public BlockPos getBlockPosAttachedTo() {
		return entityData.get(BLOCK_ATTACHED_TO);
	}

	private void updateAttachedPosition(LivingEntity target) {
		setPos(target.getX(), target.getY(0.5D), target.getZ());
	}

	private void dragTarget(Entity target, Vec3 impulse) {
		Entity entity = target.getRootVehicle();
		doDragEntity(entity, impulse);
		if (entity instanceof StandEntity stand) {
			LivingEntity standUser = stand.getUser();
			if (standUser != null) {
				doDragEntity(entity, impulse);
			}
		}
	}

	private void doDragEntity(Entity entity, Vec3 impulse) {
		if (entity instanceof LivingEntity living) {
			for (Holder<MobEffect> effect : living.getActiveEffectsMap().keySet()) {
				if (effect.is(ModStatusEffects.IMMOBILIZE) && effect.value() instanceof ImmobilizeEffect immobilize
						&& immobilize.resetsDeltaMovement()) {
					entity.move(MoverType.PLAYER, impulse);
					return;
				}
			}
		}
		entity.setDeltaMovement(impulse);
		dragged.add(entity);
		entity.fallDistance = 0;
		entity.hurtMarked = true;
	}

	private void clearDraggedMotion() {
		dragged.forEach(entity -> entity.setDeltaMovement(Vec3.ZERO));
		dragged.clear();
	}

	private double movementSpeed() {
		return 4.0D;
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		clearDraggedMotion();
		super.remove(reason);
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeDouble(distance);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		distance = additionalData.readDouble();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putDouble("Distance", distance);
		nbt.putBoolean("BindEntities", isBindEntities());
		nbt.putBoolean("PlacedBarrier", placedBarrier);
		LivingEntity attached = getEntityAttachedTo();
		if (attached != null) {
			nbt.putUUID("AttachedEntity", attached.getUUID());
		}
		if (isAttachedToBlock()) {
			BlockPos blockPos = getBlockPosAttachedTo();
			nbt.putBoolean("AttachedToBlock", true);
			nbt.putInt("AttachedBlockX", blockPos.getX());
			nbt.putInt("AttachedBlockY", blockPos.getY());
			nbt.putInt("AttachedBlockZ", blockPos.getZ());
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		distance = nbt.getDouble("Distance");
		setBindEntities(nbt.getBoolean("BindEntities"));
		placedBarrier = nbt.getBoolean("PlacedBarrier");
		attachedEntityUUID = nbt.hasUUID("AttachedEntity") ? nbt.getUUID("AttachedEntity") : null;
		if (nbt.getBoolean("AttachedToBlock")) {
			attachToBlockPos(new BlockPos(nbt.getInt("AttachedBlockX"), nbt.getInt("AttachedBlockY"), nbt.getInt("AttachedBlockZ")));
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ATTACHED_ENTITY, -1);
		builder.define(BIND_ENTITIES, false);
		builder.define(ATTACHED_TO_BLOCK, false);
		builder.define(BLOCK_ATTACHED_TO, BlockPos.ZERO);
	}
}
