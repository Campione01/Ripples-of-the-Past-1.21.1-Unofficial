package com.github.standobyte.jojoimpl.stands.magiciansred;

import java.util.Collections;
import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModBlocks;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRestoreTerrainAbility;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

public class MRFlameEntity extends ModdedProjectileEntity {

	private static final int ORIGINAL_FIRE_SECONDS = 10;
	private static final int ORIGINAL_FIRE_TICKS = ORIGINAL_FIRE_SECONDS * 20;

	private Vec3 startingPos;

	public MRFlameEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.MR_FLAME.get(), shooter, level);
	}

	protected MRFlameEntity(EntityType<? extends MRFlameEntity> type, LivingEntity shooter, Level level) {
		super(type, shooter, level);
	}

	public MRFlameEntity(EntityType<? extends MRFlameEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
		super.shoot(x, y, z, velocity, inaccuracy);
		startingPos = position();
	}

	@Override
	public Vec3 getStartingPos() {
		return startingPos != null ? startingPos : super.getStartingPos();
	}

	@Override
	public int ticksLifespan() {
		return 8;
	}

	@Override
	protected float getBaseDamage() {
		return 1.0F;
	}

	@Override
	protected float knockbackMultiplier() {
		return 0.1F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0;
	}

	@Override
	public boolean standDamage() {
		return true;
	}

	@Override
	protected boolean usesFireDamageType() {
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
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		return DamageUtil.dealDamageAndSetOnFire(target,
				entity -> super.hurtTarget(entity, owner), ORIGINAL_FIRE_TICKS, true);
	}

	@Override
	protected HitResult[] rayTrace() {
		return new HitResult[] { ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, ClipContext.Block.OUTLINE) };
	}

	@Override
	protected void afterBlockHit(BlockHitResult blockRayTraceResult, boolean blockDestroyed) {
		if (!level().isClientSide() && EventHooks.canEntityGrief(level(), this)) {
			Level level = level();
			BlockPos blockPos = blockRayTraceResult.getBlockPos();
			BlockState blockState = level.getBlockState(blockPos);
			if (!meltIceAndSnow(level, blockState, blockPos) && !blockState.getCollisionShape(level, blockPos).isEmpty()) {
				BlockPos firePos = blockPos.relative(blockRayTraceResult.getDirection());
				if (level.isEmptyBlock(firePos) && BaseFireBlock.canBePlacedAt(level, firePos, blockRayTraceResult.getDirection())) {
					level.setBlockAndUpdate(firePos, ModBlocks.MAGICIANS_RED_FIRE.get().getStateForPlacement(level, firePos));
				}
			}
		}
	}

	public static boolean meltIceAndSnow(Level level, BlockState blockState, BlockPos blockPos) {
		if (level.isClientSide()) {
			return false;
		}
		if (blockState.is(Blocks.SNOW) || blockState.is(Blocks.SNOW_BLOCK)
				|| blockState.is(Blocks.ICE) || blockState.is(Blocks.PACKED_ICE)
				|| blockState.is(Blocks.BLUE_ICE) || blockState.is(Blocks.FROSTED_ICE)
				|| blockState.is(Blocks.POWDER_SNOW)) {
			if (level.dimensionType().ultraWarm() || !blockState.isCollisionShapeFullBlock(level, blockPos)) {
				CrazyDRestoreTerrainAbility.rememberBrokenBlock(level, blockPos, blockState,
						Optional.ofNullable(level.getBlockEntity(blockPos)), Collections.emptyList());
				level.removeBlock(blockPos, false);
			}
			else {
				level.setBlockAndUpdate(blockPos, Blocks.WATER.defaultBlockState());
				level.neighborChanged(blockPos, Blocks.WATER, blockPos);
			}
			return true;
		}
		return false;
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (targetType == TargetType.BLOCK) {
			BlockHitResult blockHit = (BlockHitResult) hitTarget;
			BlockPos blockPos = blockHit.getBlockPos();
			BlockState blockState = level().getBlockState(blockPos);
			if (!blockState.isCollisionShapeFullBlock(level(), blockPos)) {
				return;
			}
		}
		super.breakProjectile(targetType, hitTarget);
	}

	@Override
	protected boolean canBreakBlock(BlockPos blockPos, BlockState blockState) {
		return super.canBreakBlock(blockPos, blockState) && !(blockState.getBlock() instanceof BaseFireBlock);
	}

	@Override
	public boolean canBeEvaded(@Nullable Entity context) {
		return false;
	}

	private static final Vec3 OFFSET_XROT = new Vec3(0, 0.2, 0.0);

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return Vec3.ZERO;
	}

	@Override
	protected Vec3 getXRotOffset() {
		return OFFSET_XROT;
	}

	@Override
	public void tick() {
		if (isInWaterOrRain()) {
			clearFire();
		}
		else {
			super.tick();
		}
	}

	@Override
	public void clearFire() {
		super.clearFire();
		if (level() instanceof ServerLevel serverLevel) {
			JojoModUtil.extinguishFieryStandEntity(this, serverLevel);
		}
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		boolean hasStartingPos = startingPos != null;
		buffer.writeBoolean(hasStartingPos);
		if (hasStartingPos) {
			buffer.writeDouble(startingPos.x);
			buffer.writeDouble(startingPos.y);
			buffer.writeDouble(startingPos.z);
		}
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		if (additionalData.readBoolean()) {
			startingPos = new Vec3(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble());
		}
		else {
			startingPos = position();
		}
	}
}
