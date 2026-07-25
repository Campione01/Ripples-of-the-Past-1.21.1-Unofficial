package com.github.standobyte.jojoimpl.powers.pillarman;

import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.util.functions.JojoModUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class PillarmanRibEntity extends PillarmanExtendingBodyPartEntity {
	private float yRotOffset;
	private float xRotOffset;
	protected float knockback = 0.0F;
	private double yOriginOffset;
	private double xOriginOffset;
	private boolean dealtDamage;

	public PillarmanRibEntity(LivingEntity entity, Level level) {
		super(ModEntityTypes.PILLAR_MAN_RIBS.get(), entity, level);
	}

	public PillarmanRibEntity(EntityType<? extends PillarmanRibEntity> entityType, Level level) {
		super(entityType, level);
	}

	public void setRibProperties(float xRotOffset, float yRotOffset, double xOriginOffset, double yOriginOffset) {
		this.xRotOffset = xRotOffset;
		this.yRotOffset = yRotOffset;
		this.xOriginOffset = xOriginOffset;
		this.yOriginOffset = yOriginOffset;
	}

	@Override
	protected float getBaseDamage() {
		return 0.5F;
	}

	@Override
	protected boolean hurtTarget(Entity target, LivingEntity owner) {
		return !dealtDamage && super.hurtTarget(target, owner);
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt) {
			dealtDamage = true;
			Entity target = entityRayTraceResult.getEntity();
			if (target instanceof LivingEntity livingTarget && !JojoModUtil.isTargetBlocking(livingTarget)) {
				attachToEntity(livingTarget);
				int duration = Math.max(1, ticksLifespan() - tickCount);
				livingTarget.addEffect(new MobEffectInstance(ModStatusEffects.STUN, duration));
			}
		}
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	public void addKnockback(float knockback) {
		this.knockback = knockback;
	}

	@Override
	protected boolean shouldHurtThroughInvulTicks() {
		return true;
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
	public int ticksLifespan() {
		int ticks = super.ticksLifespan();
		if (isAttachedToAnEntity()) {
			ticks += 20;
		}
		return ticks;
	}

	@Override
	protected float movementSpeed() {
		return 8.0F / (float) ticksLifespan();
	}

	@Override
	public boolean isBodyPart() {
		return true;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return new Vec3(xOriginOffset, yOriginOffset, 0.0D);
	}

	@Override
	protected Vec3 originOffset(float yRot, float xRot, double distance) {
		return super.originOffset(yRot + yRotOffset, xRot + xRotOffset, distance);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putFloat("YRotOffset", yRotOffset);
		nbt.putFloat("XRotOffset", xRotOffset);
		nbt.putFloat("Knockback", knockback);
		nbt.putDouble("YOriginOffset", yOriginOffset);
		nbt.putDouble("XOriginOffset", xOriginOffset);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		yRotOffset = nbt.getFloat("YRotOffset");
		xRotOffset = nbt.getFloat("XRotOffset");
		knockback = nbt.getFloat("Knockback");
		yOriginOffset = nbt.getDouble("YOriginOffset");
		xOriginOffset = nbt.getDouble("XOriginOffset");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeFloat(yRotOffset);
		buffer.writeFloat(xRotOffset);
		buffer.writeDouble(xOriginOffset);
		buffer.writeDouble(yOriginOffset);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		yRotOffset = additionalData.readFloat();
		xRotOffset = additionalData.readFloat();
		xOriginOffset = additionalData.readDouble();
		yOriginOffset = additionalData.readDouble();
	}
}
