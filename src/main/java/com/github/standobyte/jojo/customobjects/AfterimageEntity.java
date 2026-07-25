package com.github.standobyte.jojo.customobjects;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModEntityTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class AfterimageEntity extends Entity implements IEntityWithComplexSpawn {
	private static final int DEFAULT_LIFESPAN = 1200;
	private static final Map<LivingEntity, List<AfterimageEntity>> AFTERIMAGES = new WeakHashMap<>();

	@Nullable
	private LivingEntity originEntity;
	@Nullable
	private UUID originUuid;
	private int ticksDelayed;
	private int delay;
	private int lifeSpan;
	private double speedLowerLimit;
	private final Queue<PosData> originPosQueue = new LinkedList<>();

	public AfterimageEntity(Level level, LivingEntity originEntity, int delay) {
		this(ModEntityTypes.AFTERIMAGE.get(), level);
		setOriginEntity(originEntity);
		this.delay = delay;
		this.lifeSpan = DEFAULT_LIFESPAN;
	}

	public AfterimageEntity(EntityType<?> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	private void setOriginEntity(@Nullable LivingEntity entity) {
		originEntity = entity;
		originUuid = entity != null ? entity.getUUID() : originUuid;
		if (entity != null) {
			copyPosition(entity);
		}
	}

	@Nullable
	public LivingEntity getOriginEntity() {
		return originEntity;
	}

	public void setLifeSpan(int lifeSpan) {
		this.lifeSpan = lifeSpan;
	}

	public void setMinSpeed(double speed) {
		speedLowerLimit = speed;
	}

	public boolean shouldRenderAfterimage() {
		return originEntity != null && originEntity.getAttributeValue(Attributes.MOVEMENT_SPEED) >= speedLowerLimit;
	}

	public static void addAfterimages(LivingEntity entity, int count, int lifespan) {
		if (entity.level().isClientSide()) {
			return;
		}
		List<AfterimageEntity> afterimages = AFTERIMAGES.computeIfAbsent(entity, ignored -> new ArrayList<>());
		int active = 0;
		Iterator<AfterimageEntity> iterator = afterimages.iterator();
		while (iterator.hasNext()) {
			AfterimageEntity afterimage = iterator.next();
			if (afterimage.isAlive()) {
				afterimage.setLifeSpan(lifespan < 0 ? Integer.MAX_VALUE : afterimage.tickCount + lifespan);
				active++;
			}
			else {
				iterator.remove();
			}
		}

		double minSpeed = entity.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		double speed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
		for (int i = active; i < count; i++) {
			AfterimageEntity afterimage = new AfterimageEntity(entity.level(), entity, i + 1);
			afterimage.setLifeSpan(lifespan < 0 ? Integer.MAX_VALUE : lifespan);
			afterimage.setMinSpeed(minSpeed + (speed - minSpeed) * (double) (i + 1) / (double) count);
			afterimages.add(afterimage);
			entity.level().addFreshEntity(afterimage);
		}
	}

	@Override
	public void tick() {
		super.tick();
		ticksDelayed++;
		if (originEntity == null || !originEntity.isAlive() || !level().isClientSide() && tickCount > lifeSpan) {
			discard();
			return;
		}

		originPosQueue.add(new PosData(originEntity.position(), originEntity.getXRot(), originEntity.getYRot()));
		if (ticksDelayed > delay) {
			PosData posData = originPosQueue.remove();
			moveTo(posData.pos.x, posData.pos.y, posData.pos.z, posData.yRot, posData.xRot);
		}

		if (!level().isClientSide() && originEntity.isSprinting() && shouldRenderAfterimage()) {
			level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(8),
					mob -> mob.getTarget() == originEntity && mob.hasLineOfSight(this)).forEach(mob -> {
				if (mob.getRandom().nextDouble() < 0.01) {
					loseTarget(mob, originEntity);
				}
			});
		}
	}

	private static void loseTarget(Mob mob, LivingEntity target) {
		if (mob.getTarget() == target) {
			mob.setTarget(null);
			for (WrappedGoal goal : mob.targetSelector.getAvailableGoals()) {
				if (goal.isRunning()) {
					goal.stop();
				}
			}
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean isInvisible() {
		return super.isInvisible() || originEntity != null && originEntity.isInvisible();
	}

	@Override
	public boolean isInvisibleTo(Player player) {
		return super.isInvisibleTo(player) || originEntity != null && originEntity.isInvisibleTo(player);
	}

	@Override
	public boolean displayFireAnimation() {
		return originEntity != null && originEntity.displayFireAnimation();
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		delay = nbt.getInt("Delay");
		tickCount = nbt.getInt("Age");
		lifeSpan = nbt.getInt("LifeSpan");
		speedLowerLimit = nbt.getDouble("Speed");
		if (nbt.hasUUID("Origin")) {
			originUuid = nbt.getUUID("Origin");
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		nbt.putInt("Delay", delay);
		nbt.putInt("Age", tickCount);
		nbt.putInt("LifeSpan", lifeSpan);
		nbt.putDouble("Speed", speedLowerLimit);
		UUID uuid = originEntity != null ? originEntity.getUUID() : originUuid;
		if (uuid != null) {
			nbt.putUUID("Origin", uuid);
		}
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		resolveOriginFromUuid();
		buffer.writeInt(originEntity == null ? -1 : originEntity.getId());
		buffer.writeVarInt(delay);
		buffer.writeInt(lifeSpan);
		buffer.writeDouble(speedLowerLimit);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		Entity entity = level().getEntity(additionalData.readInt());
		if (entity instanceof LivingEntity living) {
			setOriginEntity(living);
		}
		delay = additionalData.readVarInt();
		lifeSpan = additionalData.readInt();
		speedLowerLimit = additionalData.readDouble();
	}

	private void resolveOriginFromUuid() {
		if (originEntity == null && originUuid != null && level() instanceof ServerLevel serverLevel) {
			Entity entity = serverLevel.getEntity(originUuid);
			if (entity instanceof LivingEntity living) {
				setOriginEntity(living);
			}
		}
	}

	private static class PosData {
		private final Vec3 pos;
		private final float xRot;
		private final float yRot;

		private PosData(Vec3 pos, float xRot, float yRot) {
			this.pos = pos;
			this.xRot = xRot;
			this.yRot = yRot;
		}
	}
}
