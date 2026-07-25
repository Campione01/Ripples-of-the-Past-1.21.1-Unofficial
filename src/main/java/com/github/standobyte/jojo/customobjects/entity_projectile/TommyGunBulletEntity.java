package com.github.standobyte.jojo.customobjects.entity_projectile;

import java.util.LinkedList;
import java.util.List;

import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TommyGunBulletEntity extends ModdedProjectileEntity {
	public final List<Vec3> tracePos = new LinkedList<>();
	public Vec3 initialPos;
	private boolean blockDestroyed;

	public TommyGunBulletEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.TOMMY_GUN_BULLET.get(), shooter, level);
	}

	public TommyGunBulletEntity(EntityType<? extends TommyGunBulletEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		super.tick();
		if (tickCount == 5) {
			setNoGravity(false);
		}
		if (level().isClientSide()) {
			Vec3 pos = position();
			boolean addPos = true;
			if (tracePos.size() > 1) {
				Vec3 lastPos = tracePos.get(tracePos.size() - 1);
				addPos = pos.distanceToSqr(lastPos) >= 0.0625D;
			}
			if (addPos) {
				tracePos.add(pos);
			}
		}
	}

	@Override
	protected boolean constVelocity() {
		return false;
	}

	@Override
	public int ticksLifespan() {
		return 40;
	}

	@Override
	protected float getBaseDamage() {
		return 2.0F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0.3F;
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	protected double getGravityAcceleration() {
		return 1.5D;
	}

	@Override
	public boolean hasDeflectedVisuals() {
		return true;
	}

	@Override
	public void setIsDeflected(Vec3 deflectVec, Vec3 deflectPos) {
		super.setIsDeflected(deflectVec, deflectPos);
		if (level().isClientSide() && deflectVec != null) {
			setDeltaMovement(deflectVec);
			tracePos.add(deflectPos);
		}
	}

	@Override
	protected void afterBlockHit(BlockHitResult blockHitResult, boolean blockDestroyed) {
		this.blockDestroyed = blockDestroyed;
		if (blockDestroyed && !level().isClientSide()) {
			setDeltaMovement(getDeltaMovement().scale(0.9D));
			checkHit();
		}
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (!(targetType == TargetType.BLOCK && blockDestroyed)) {
			super.breakProjectile(targetType, hitTarget);
		}
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		initialPos = position();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return true;
	}
}
