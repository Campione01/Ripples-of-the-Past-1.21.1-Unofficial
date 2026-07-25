package com.github.standobyte.jojoimpl.stands.hierophant;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModEntityDataSerializers;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

public class HGBarrierEntity extends OwnerBoundProjectileEntity {
	private static final EntityDataAccessor<Boolean> WAS_RIPPED = SynchedEntityData.defineId(HGBarrierEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Optional<Vec3>> RIPPED_POINT = SynchedEntityData.defineId(HGBarrierEntity.class, ModEntityDataSerializers.VEC3_OPTIONAL.get());
	private static final EntityDataAccessor<Optional<ResourceLocation>> STAND_SKIN = SynchedEntityData.defineId(HGBarrierEntity.class, ModEntityDataSerializers.RESOURCE_LOCATION_OPTIONAL.get());
	private static final EntityDataAccessor<Boolean> ATTACHED_TO_BLOCK = SynchedEntityData.defineId(HGBarrierEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<BlockPos> BLOCK_ATTACHED_TO = SynchedEntityData.defineId(HGBarrierEntity.class, EntityDataSerializers.BLOCK_POS);

	private static final Vec3 OWNER_RELATIVE_OFFSET = new Vec3(0.15D, -1.4D, 0.0D);

	private boolean rippedHurtOwner;
	private LivingEntity standUser;
	private BlockPos originBlockPos;
	private int rippedTicks = -1;

	public HGBarrierEntity(StandEntity stand, Level level) {
		super(ModEntityTypes.HG_BARRIER.get(), stand, level);
		this.standUser = stand.getUser();
	}

	public HGBarrierEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.HG_BARRIER.get(), shooter, level);
		this.standUser = shooter instanceof StandEntity stand ? stand.getUser() : shooter;
	}

	public HGBarrierEntity(EntityType<? extends HGBarrierEntity> type, Level level) {
		super(type, level);
	}

	public void setOriginBlockPos(BlockPos blockPos) {
		this.originBlockPos = blockPos;
	}

	@Nullable
	public BlockPos getOriginBlockPos() {
		return originBlockPos;
	}

	public void attachToBlockPos(BlockPos blockPos) {
		entityData.set(BLOCK_ATTACHED_TO, blockPos);
		entityData.set(ATTACHED_TO_BLOCK, true);
		moveToBlock(blockPos);
		setDeltaMovement(Vec3.ZERO);
	}

	public boolean isAttachedToBlock() {
		return entityData.get(ATTACHED_TO_BLOCK);
	}

	@Nullable
	public BlockPos getBlockPosAttachedTo() {
		return isAttachedToBlock() ? entityData.get(BLOCK_ATTACHED_TO) : null;
	}

	@Override
	public void tick() {
		if (isTimeStoppedAroundThis()) {
			tickStoppedInTime();
			return;
		}

		if (rippedTicks > 0) {
			if (--rippedTicks == 0) {
				if (!level().isClientSide()) {
					discard();
				}
				return;
			}
			if (!level().isClientSide() && !rippedHurtOwner && standUser != null) {
				DamageUtil.hurtThroughInvulTicks(standUser, standUser.damageSources().generic(), 0.2F);
				rippedHurtOwner = true;
			}
			return;
		}

		if (!level().isClientSide() && getStandUser() == null) {
			discard();
			return;
		}
		BlockPos attachedBlock = getBlockPosAttachedTo();
		if (attachedBlock != null) {
			moveToBlock(attachedBlock);
		}
		super.tick();
	}

	private void tickStoppedInTime() {
		if (!level().isClientSide() && !wasRipped()) {
			for (HitResult result : rayTrace()) {
				if (result.getType() == HitResult.Type.ENTITY && !EventHooks.onProjectileImpact(this, result)) {
					ripAt(result.getLocation());
					break;
				}
			}
		}
	}

	private boolean isTimeStoppedAroundThis() {
		if (level().isClientSide()) {
			return TimeStopState.getClientDisplayInstance(new ChunkPos(blockPosition())).isPresent();
		}
		if (level() instanceof ServerLevel serverLevel && serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
			return serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get()).isTimeStopped(this);
		}
		return false;
	}

	@Nullable
	private LivingEntity getStandUser() {
		if (standUser == null && getOwner() instanceof StandEntity stand) {
			standUser = stand.getUser();
		}
		return standUser;
	}

	@Override
	protected void moveProjectile() {
		BlockPos attachedBlock = getBlockPosAttachedTo();
		if (attachedBlock != null) {
			moveToBlock(attachedBlock);
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		super.moveProjectile();
	}

	private void moveToBlock(BlockPos blockPos) {
		setPos(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D);
	}

	@Override
	public int ticksLifespan() {
		return Integer.MAX_VALUE;
	}

	@Override
	protected float getBaseDamage() {
		return 2.0F;
	}

	@Override
	protected float knockbackMultiplier() {
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
	public boolean isPickable() {
		return false;
	}

	@Override
	protected boolean shouldFollowOwner() {
		return !isAttachedToBlock() && getOwner() != null && getOwner().isAlive();
	}

	@Override
	public boolean canTickInStoppedTime() {
		return true;
	}

	@Override
	protected Vec3 ownerOffset() {
		LivingEntity owner = getOwner();
		double eyeYOffset = owner != null ? owner.getEyeHeight() : 0.0D;
		return OWNER_RELATIVE_OFFSET.add(0.0D, eyeYOffset, 0.0D);
	}

	@Override
	public Vec3 getOriginPoint(float partialTick) {
		if (originBlockPos != null) {
			return Vec3.atCenterOf(originBlockPos);
		}
		LivingEntity user = getStandUser();
		return user != null ? user.getPosition(partialTick) : getPosition(partialTick);
	}

	@Override
	protected HitResult[] rayTrace() {
		if (!isAttachedToBlock() || wasRipped()) {
			return new HitResult[0];
		}
		Vec3 start = getOriginPoint(1.0F);
		Vec3 end = position().add(getDeltaMovement());
		AABB aabb = new AABB(start, end).inflate(1.0D);
		List<EntityHitResult> hits = level().getEntities(this, aabb, this::canHitEntity).stream()
				.map(entity -> clipEntity(entity, start, end))
				.flatMap(Optional::stream)
				.sorted(Comparator.comparingDouble(hit -> hit.getLocation().distanceToSqr(start)))
				.toList();
		return hits.isEmpty() ? new HitResult[0] : new HitResult[] { hits.getFirst() };
	}

	private Optional<EntityHitResult> clipEntity(Entity entity, Vec3 start, Vec3 end) {
		AABB targetBox = entity.getBoundingBox().inflate(getBbWidth() / 2.0D);
		if (targetBox.contains(start)) {
			return Optional.of(new EntityHitResult(entity, start));
		}
		return targetBox.clip(start, end).map(pos -> new EntityHitResult(entity, pos));
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return isAttachedToBlock() && !wasRipped() && super.canHitEntity(entity);
	}

	@Override
	protected void onHitEntity(EntityHitResult entityRayTraceResult) {
		if (getBlockPosAttachedTo() != null && !wasRipped()) {
			Entity target = entityRayTraceResult.getEntity();
			target.setDeltaMovement(Vec3.ZERO);
			if (!level().isClientSide()) {
				super.onHitEntity(entityRayTraceResult);
				ripAt(entityRayTraceResult.getLocation());
				StandPower standPower = null;
				SoundSource soundSource = getSoundSource();
				if (getOwner() instanceof HierophantGreenEntity stand) {
					standPower = stand.getUserPower();
					soundSource = stand.getSoundSource();
					stand.getBarriersNet().shootEmeraldsFromBarriers(stand.getUserPower(), stand,
							target.getBoundingBox().getCenter(), 0, 20 * stand.staminaCondition,
							HierophantEmeraldSplashAbility.barrierRippedEmeraldStaminaCostTick(), 2, false);
				}
				StandUtil.broadcastSound((ServerLevel) level(), target.position(),
						ModSoundEvents.HIEROPHANT_GREEN_BARRIER_RIPPED, true, standPower,
						soundSource, 1.0F, 1.0F);
			}
		}
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> dataParameter) {
		super.onSyncedDataUpdated(dataParameter);
		if (WAS_RIPPED.equals(dataParameter) && wasRipped()) {
			rippedTicks = 40;
		}
	}

	public boolean wasRipped() {
		return entityData.get(WAS_RIPPED);
	}

	public Optional<Vec3> wasRippedAt() {
		return entityData.get(RIPPED_POINT);
	}

	public void ripAt(Vec3 pos) {
		if (!level().isClientSide()) {
			entityData.set(RIPPED_POINT, Optional.of(pos));
			rippedTicks = 40;
			entityData.set(WAS_RIPPED, true);
		}
	}

	@Override
	public boolean isCurrentlyGlowing() {
		boolean shouldGlow = level().isClientSide()
				&& !isTimeStoppedAroundThis()
				&& wasRipped()
				&& getOwner() instanceof StandEntity stand
				&& stand.getUser() == ClientProxy.getClientPlayer();
		return shouldGlow || super.isCurrentlyGlowing();
	}

	@Override
	public int getTeamColor() {
		return wasRipped() ? 0xFF0000 : super.getTeamColor();
	}

	public void withStandSkin(Optional<ResourceLocation> skinLocation) {
		super.withStandSkin(skinLocation);
		entityData.set(STAND_SKIN, skinLocation != null ? skinLocation : Optional.empty());
	}

	@Override
	public Optional<ResourceLocation> getStandSkin() {
		Optional<ResourceLocation> syncedSkin = entityData.get(STAND_SKIN);
		return syncedSkin.isPresent() ? syncedSkin : super.getStandSkin();
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buf) {
		super.writeSpawnData(buf);
		buf.writeInt(standUser != null ? standUser.getId() : -1);
		buf.writeBoolean(originBlockPos != null);
		if (originBlockPos != null) {
			buf.writeBlockPos(originBlockPos);
		}
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf buf) {
		super.readSpawnData(buf);
		Entity entity = level().getEntity(buf.readInt());
		if (entity instanceof LivingEntity living) {
			standUser = living;
		}
		if (buf.readBoolean()) {
			originBlockPos = buf.readBlockPos();
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		if (originBlockPos != null) {
			nbt.putInt("OriginX", originBlockPos.getX());
			nbt.putInt("OriginY", originBlockPos.getY());
			nbt.putInt("OriginZ", originBlockPos.getZ());
			nbt.putBoolean("HasOriginBlock", true);
		}
		wasRippedAt().ifPresent(point -> {
			nbt.putBoolean("HasRippedPoint", true);
			nbt.putDouble("RippedX", point.x);
			nbt.putDouble("RippedY", point.y);
			nbt.putDouble("RippedZ", point.z);
		});
		nbt.putBoolean("WasRipped", wasRipped());
		BlockPos attachedBlock = getBlockPosAttachedTo();
		if (attachedBlock != null) {
			nbt.putBoolean("HasAttachedBlock", true);
			nbt.putInt("AttachedBlockX", attachedBlock.getX());
			nbt.putInt("AttachedBlockY", attachedBlock.getY());
			nbt.putInt("AttachedBlockZ", attachedBlock.getZ());
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.getBoolean("HasOriginBlock")) {
			originBlockPos = new BlockPos(nbt.getInt("OriginX"), nbt.getInt("OriginY"), nbt.getInt("OriginZ"));
		}
		if (nbt.getBoolean("HasAttachedBlock")) {
			attachToBlockPos(new BlockPos(nbt.getInt("AttachedBlockX"), nbt.getInt("AttachedBlockY"), nbt.getInt("AttachedBlockZ")));
		}
		if (nbt.getBoolean("HasRippedPoint")) {
			entityData.set(RIPPED_POINT, Optional.of(new Vec3(nbt.getDouble("RippedX"), nbt.getDouble("RippedY"), nbt.getDouble("RippedZ"))));
		}
		boolean wasRipped = nbt.getBoolean("WasRipped");
		entityData.set(WAS_RIPPED, wasRipped);
		if (wasRipped) {
			rippedTicks = 40;
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(WAS_RIPPED, false);
		builder.define(RIPPED_POINT, Optional.empty());
		builder.define(STAND_SKIN, Optional.empty());
		builder.define(ATTACHED_TO_BLOCK, false);
		builder.define(BLOCK_ATTACHED_TO, BlockPos.ZERO);
	}
}
