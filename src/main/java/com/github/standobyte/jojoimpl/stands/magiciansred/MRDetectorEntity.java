package com.github.standobyte.jojoimpl.stands.magiciansred;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.stands.magiciansred.client.MRDetectorSound;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class MRDetectorEntity extends Entity implements IEntityWithComplexSpawn {
	private static final EntityDataAccessor<Boolean> ENTITY_DETECTED = SynchedEntityData.defineId(MRDetectorEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DETECTED_X = SynchedEntityData.defineId(MRDetectorEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DETECTED_Y = SynchedEntityData.defineId(MRDetectorEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DETECTED_Z = SynchedEntityData.defineId(MRDetectorEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(MRDetectorEntity.class, EntityDataSerializers.INT);

	public static final double DETECTION_RADIUS = 15.0D;
	private static final int LIFESPAN_TICKS = 600;
	private static final int TOUCH_FIRE_TICKS = 4 * 20;
	private static final Vec3 OWNER_OFFSET = new Vec3(-0.5D, -0.5D, 1.5D);

	@Nullable
	private LivingEntity owner;

	public MRDetectorEntity(LivingEntity owner, Level level) {
		this(ModEntityTypes.MR_DETECTOR.get(), level);
		setOwner(owner);
	}

	public MRDetectorEntity(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		if (isInWaterOrRain()) {
			clearFire();
			return;
		}

		super.tick();

		LivingEntity owner = getOwnerEntity();
		if (tickCount < LIFESPAN_TICKS && owner != null && owner.isAlive()) {
			Vec3 newPos = owner.getEyePosition(1.0F).add(OWNER_OFFSET.yRot(-owner.getYRot() * MathUtil.DEG_TO_RAD));
			setPos(newPos.x, newPos.y, newPos.z);
		}
		else {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}

		if (!level().isClientSide()) {
			setDetectedOffset(detectEntities(owner));
		}
	}

	@Override
	public void clearFire() {
		super.clearFire();
		if (level() instanceof ServerLevel serverLevel) {
			JojoModUtil.extinguishFieryStandEntity(this, serverLevel);
		}
	}

	@Nullable
	private Vec3 detectEntities(LivingEntity owner) {
		AABB area = new AABB(position(), position()).inflate(DETECTION_RADIUS);
		List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, area,
				entity -> entity.isAlive() && !entity.isSpectator() && isValidDetectedTarget(entity, owner));
		LivingEntity closest = targets.stream()
				.min(Comparator.comparingDouble(entity -> entity.distanceToSqr(this)))
				.orElse(null);
		if (closest != null && getBoundingBox().intersects(closest.getBoundingBox())) {
			DamageUtil.setOnFire(closest, TOUCH_FIRE_TICKS, true);
		}
		return closest != null ? closest.position().subtract(position()) : null;
	}

	private boolean isValidDetectedTarget(LivingEntity entity, LivingEntity owner) {
		if (entity == owner || entity.getType() == EntityType.ARMOR_STAND || entity.isAlliedTo(owner)) {
			return false;
		}
		StandPower ownerPower = StandPower.get(owner);
		return ownerPower == null || entity != ownerPower.getSummonedStandEntity();
	}

	private void setDetectedOffset(@Nullable Vec3 detected) {
		if (detected == null) {
			entityData.set(ENTITY_DETECTED, false);
		}
		else {
			entityData.set(ENTITY_DETECTED, true);
			entityData.set(DETECTED_X, (float) detected.x);
			entityData.set(DETECTED_Y, (float) detected.y);
			entityData.set(DETECTED_Z, (float) detected.z);
		}
	}

	private void setOwner(@Nullable LivingEntity owner) {
		this.owner = owner;
		entityData.set(OWNER_ID, owner != null ? owner.getId() : -1);
	}

	@Nullable
	public LivingEntity getOwnerEntity() {
		if (owner == null) {
			Entity entity = level().getEntity(entityData.get(OWNER_ID));
			if (entity instanceof LivingEntity living) {
				owner = living;
			}
		}
		return owner;
	}

	public boolean isOwner(LivingEntity entity) {
		return entity == getOwnerEntity();
	}

	public boolean isEntityDetected() {
		return entityData.get(ENTITY_DETECTED);
	}

	public Vec3 getDetectedDirection() {
		return new Vec3(entityData.get(DETECTED_X), entityData.get(DETECTED_Y), entityData.get(DETECTED_Z));
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> dataParameter) {
		if (level().isClientSide() && ENTITY_DETECTED.equals(dataParameter) && isEntityDetected()
				&& ClientGlobals.canHearStands && !isSilent()) {
			ClientsideSoundsHelper.playNonVanillaClassSound(new MRDetectorSound(this));
		}
		super.onSyncedDataUpdated(dataParameter);
	}

	@Override
	public boolean isInvisible() {
		if (level().isClientSide() && ClientGlobals.canSeeStands) {
			return super.isInvisible();
		}
		return true;
	}

	@Override
	public boolean isInvisibleTo(Player player) {
		return !StandUtil.entityCanSeeStands(player)
				|| !JojoModUtil.seesInvisibleAsSpectator(player) && super.isInvisible();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(ENTITY_DETECTED, false);
		builder.define(DETECTED_X, 0.0F);
		builder.define(DETECTED_Y, 0.0F);
		builder.define(DETECTED_Z, 0.0F);
		builder.define(OWNER_ID, -1);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		// Original detector is no-save transient state.
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		// Original detector is no-save transient state.
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(entityData.get(OWNER_ID));
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		entityData.set(OWNER_ID, additionalData.readVarInt());
		owner = null;
	}
}
