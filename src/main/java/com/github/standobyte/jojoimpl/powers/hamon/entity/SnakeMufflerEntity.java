package com.github.standobyte.jojoimpl.powers.hamon.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanExtendingBodyPartEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SnakeMufflerEntity extends PillarmanExtendingBodyPartEntity {
	@Nullable private Entity entityToJumpOver;
	@Nullable private UUID targetId;

	public SnakeMufflerEntity(Level level, LivingEntity user) {
		super(ModEntityTypes.SNAKE_MUFFLER.get(), user, level);
	}

	public SnakeMufflerEntity(EntityType<? extends SnakeMufflerEntity> entityType, Level level) {
		super(entityType, level);
	}

	public void setEntityToJumpOver(@Nullable Entity entity) {
		this.entityToJumpOver = entity;
		this.targetId = entity != null ? entity.getUUID() : null;
	}

	@Override
	public void tick() {
		super.tick();
		LivingEntity owner = getOwner();
		if (owner == null || !owner.isAlive()) {
			return;
		}
		resolveJumpTarget();
		if (tickCount < 5) {
			Vec3 jumpVec = new Vec3(0.0D, 0.45D, 0.0D);
			if (entityToJumpOver != null && entityToJumpOver.isAlive()) {
				Vec3 posDiff = entityToJumpOver.position().subtract(owner.position());
				posDiff = posDiff.subtract(0.0D, posDiff.y, 0.0D);
				double lengthSqr = posDiff.lengthSqr();
				if (lengthSqr < 25.0D) {
					if (lengthSqr > 0.04D) {
						posDiff = posDiff.scale(0.4D / Math.sqrt(lengthSqr));
					}
					jumpVec = posDiff.add(0.0D, (entityToJumpOver.getBbHeight() + 0.5D) / ticksLifespan(), 0.0D);
				}
			}
			owner.setDeltaMovement(jumpVec);
			owner.hurtMarked = true;
		}
		owner.fallDistance = 0.0F;
	}

	private void resolveJumpTarget() {
		if (entityToJumpOver == null && targetId != null && level() instanceof ServerLevel serverLevel) {
			entityToJumpOver = serverLevel.getEntity(targetId);
		}
	}

	@Override
	protected Vec3 getNextOriginOffset() {
		return Vec3.ZERO;
	}

	@Override
	public int ticksLifespan() {
		return 10;
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
	protected float movementSpeed() {
		return 0.001F;
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		if (targetId != null) {
			nbt.putUUID("TargetEntity", targetId);
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.hasUUID("TargetEntity")) {
			targetId = nbt.getUUID("TargetEntity");
		}
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		resolveJumpTarget();
		buffer.writeBoolean(entityToJumpOver != null);
		if (entityToJumpOver != null) {
			buffer.writeInt(entityToJumpOver.getId());
		}
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		entityToJumpOver = additionalData.readBoolean() ? level().getEntity(additionalData.readInt()) : null;
		targetId = entityToJumpOver != null ? entityToJumpOver.getUUID() : null;
	}
}
