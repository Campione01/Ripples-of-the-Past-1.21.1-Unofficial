package com.github.standobyte.jojoimpl.powers.pillarman;

import com.github.standobyte.jojo.init.ModBlocks;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRFlameEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

public class PillarmanVeinEntity extends PillarmanExtendingBodyPartEntity {
	private float yRotOffset;
	private float xRotOffset;
	protected float knockback = 0.0F;
	private double xOriginOffset;
	private double yOriginOffset;
	private double zOriginOffset;

	public PillarmanVeinEntity(Level level, LivingEntity entity, float angleXZ, float angleYZ,
			double offsetX, double offsetY, double offsetZ) {
		super(ModEntityTypes.PILLAR_MAN_VEINS.get(), entity, level);
		this.xRotOffset = angleXZ;
		this.yRotOffset = angleYZ;
		this.xOriginOffset = offsetX;
		this.yOriginOffset = offsetY;
		this.zOriginOffset = offsetZ;
	}

	public PillarmanVeinEntity(EntityType<? extends PillarmanVeinEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			Vec3 center = getBoundingBox().getCenter();
			Vec3 sparkVec = center.add(new Vec3(
					(random.nextDouble() - 0.5D),
					(random.nextDouble() - 0.5D),
					(random.nextDouble() - 0.5D))
					.normalize().scale(random.nextDouble() * 2.0D));
			level().addParticle(ModParticles.BLOOD.get(), false,
					sparkVec.x, sparkVec.y, sparkVec.z, 0.0D, -1.0D, 0.0D);
		}
	}

	@Override
	protected float getBaseDamage() {
		return 0.35F;
	}

	public void addKnockback(float knockback) {
		this.knockback = knockback;
	}

	@Override
	protected boolean hurtTarget(Entity target, LivingEntity owner) {
		if (!isRetracting()) {
			return DamageUtil.dealDamageAndSetOnFire(target,
					entity -> super.hurtTarget(entity, owner), 10 * 20, true);
		}
		return false;
	}

	@Override
	protected boolean shouldHurtThroughInvulTicks() {
		return true;
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt) {
			Entity target = entityRayTraceResult.getEntity();
			if (knockback > 0.0F && target instanceof LivingEntity livingTarget) {
				double yRotRad = Math.toRadians(getYRot());
				livingTarget.knockback(knockback, Math.sin(yRotRad), -Math.cos(yRotRad));
			}
			setIsRetracting(true);
		}
	}

	@Override
	protected void afterBlockHit(BlockHitResult blockRayTraceResult, boolean blockDestroyed) {
		if (!level().isClientSide()) {
			LivingEntity owner = getOwner();
			if (owner == null || EventHooks.canEntityGrief(level(), owner)) {
				BlockPos blockPos = blockRayTraceResult.getBlockPos();
				BlockState blockState = level().getBlockState(blockPos);
				if (!MRFlameEntity.meltIceAndSnow(level(), blockState, blockPos)
						&& !blockState.getCollisionShape(level(), blockPos).isEmpty()) {
					blockPos = blockPos.relative(blockRayTraceResult.getDirection());
					if (level().isEmptyBlock(blockPos) && !isRetracting()) {
						level().setBlockAndUpdate(blockPos, ModBlocks.BOILING_BLOOD.get().defaultBlockState()
								.setValue(LiquidBlock.LEVEL, 4));
						Vec3 center = getBoundingBox().getCenter();
						level().playSound(null, center.x, center.y, center.z, SoundEvents.LAVA_EXTINGUISH,
								SoundSource.AMBIENT, 0.2F, 1.0F);
					}
				}
			}
		}
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
	protected float movementSpeed() {
		return 16.0F / (float) ticksLifespan();
	}

	@Override
	public boolean isBodyPart() {
		return true;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return new Vec3(xOriginOffset, yOriginOffset, zOriginOffset);
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
		nbt.putDouble("XOriginOffset", xOriginOffset);
		nbt.putDouble("YOriginOffset", yOriginOffset);
		nbt.putDouble("ZOriginOffset", zOriginOffset);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		yRotOffset = nbt.getFloat("YRotOffset");
		xRotOffset = nbt.getFloat("XRotOffset");
		knockback = nbt.getFloat("Knockback");
		xOriginOffset = nbt.getDouble("XOriginOffset");
		yOriginOffset = nbt.getDouble("YOriginOffset");
		zOriginOffset = nbt.getDouble("ZOriginOffset");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeFloat(yRotOffset);
		buffer.writeFloat(xRotOffset);
		buffer.writeDouble(xOriginOffset);
		buffer.writeDouble(yOriginOffset);
		buffer.writeDouble(zOriginOffset);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		yRotOffset = additionalData.readFloat();
		xRotOffset = additionalData.readFloat();
		xOriginOffset = additionalData.readDouble();
		yOriginOffset = additionalData.readDouble();
		zOriginOffset = additionalData.readDouble();
	}
}
