package com.github.standobyte.jojo.customobjects.entity_projectile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.client.ClientProxy;

import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Slice 5a framework extension — owner-bound projectile base.
 *
 * <p>Abstract subclass of {@link ModdedProjectileEntity} that re-anchors itself
 * relative to its owner each tick (when {@link #shouldFollowOwner()} returns true).
 * Used by HG barrier (4 stationary nodes around user), HG grapple (tether anchor),
 * legacy SC rapier-on-stand-arm anchor, and any future tether-style projectile.</p>
 *
 * <p>Slice 5a delivers the abstract anchor behavior. Subclass migration of
 * existing HG / SC projectiles is consumed by Slice 5b family follow-ups.</p>
 */
public abstract class OwnerBoundProjectileEntity extends ModdedProjectileEntity {

	protected OwnerBoundProjectileEntity(EntityType<? extends OwnerBoundProjectileEntity> type, LivingEntity shooter, Level level) {
		super(type, shooter, level);
	}

	protected OwnerBoundProjectileEntity(EntityType<? extends OwnerBoundProjectileEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		Entity owner = getOwner();
		if (owner != null && shouldFollowOwner()) {
			Vec3 offset = ownerOffset();
			setPos(owner.getX() + offset.x, owner.getY() + offset.y, owner.getZ() + offset.z);
			setDeltaMovement(Vec3.ZERO);
		}
		super.tick();
	}

	/**
	 * @return true when the projectile should track the owner's position each tick.
	 */
	protected abstract boolean shouldFollowOwner();

	/**
	 * @return offset from the owner's position. Default zero (sticks to owner exactly).
	 */
	protected Vec3 ownerOffset() {
		return Vec3.ZERO;
	}

	public Vec3 getOriginPoint(float partialTick) {
		return ownerPosition(partialTick, isBodyPart());
	}

	protected Vec3 ownerPosition(float partialTick) {
		return ownerPosition(partialTick, false);
	}

	protected Vec3 ownerPosition(float partialTick, boolean useBodyRotation) {
		LivingEntity owner = getOwner();
		if (owner != null) {
			float yRot = useBodyRotation
					? Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot)
					: Mth.lerp(partialTick, owner.yRotO, owner.getYRot());
			return getPos(owner, partialTick, 
					yRot, 
					Mth.lerp(partialTick, owner.xRotO, owner.getXRot()));
		}
		return getPosition(partialTick);
	}

	public boolean isBodyPart() {
		return false;
	}

	public boolean ownerInvisibility() {
		return isBodyPart();
	}

	public boolean canTickInStoppedTime() {
		return false;
	}

	@Override
	public boolean isInvisible() {
		LivingEntity owner = getOwner();
		return ownerInvisibility() && owner != null && owner.isInvisible() || super.isInvisible();
	}

	@Override
	public boolean isInvisibleTo(Player player) {
		LivingEntity owner = getOwner();
		return ownerInvisibility() && owner != null && owner.isInvisibleTo(player) || super.isInvisibleTo(player);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().expandTowards(getOriginPoint(1.0F).subtract(position()));
	}

	@Override
	protected HitResult[] rayTrace() {
		Vec3 start = getOriginPoint(1.0F);
		Vec3 end = position().add(getDeltaMovement());
		Vec3 ray = start.subtract(end);
		if (ray.lengthSqr() < 1.0E-7D) {
			return new HitResult[0];
		}

		AABB rayBox = getBoundingBox().expandTowards(ray).inflate(1.0D);
		double minDistanceSqr = ray.lengthSqr();
		double inflation = getBbWidth() / 2.0D;
		List<EntityHitResult> hits = new ArrayList<>();
		for (Entity target : level().getEntities(this, rayBox, this::canHitEntity)) {
			AABB targetBox = target.getBoundingBox().inflate(target.getPickRadius() + inflation);
			Optional<Vec3> clip = targetBox.clip(start, end);
			if (targetBox.contains(start)) {
				hits.add(new EntityHitResult(target, clip.orElse(start)));
			}
			else if (clip.isPresent()) {
				Vec3 hitPos = clip.get();
				double distanceSqr = start.distanceToSqr(hitPos);
				if (distanceSqr < minDistanceSqr || minDistanceSqr == 0.0D) {
					if (target.getRootVehicle() == getRootVehicle() && !target.canRiderInteract()) {
						if (minDistanceSqr == 0.0D) {
							hits.add(new EntityHitResult(target, hitPos));
						}
					}
					else {
						hits.add(new EntityHitResult(target, hitPos));
					}
				}
			}
		}

		if (hits.isEmpty()) {
			return new HitResult[] { level().clip(new ClipContext(start, end,
					ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this)) };
		}
		hits.sort(Comparator.comparingDouble(hit -> hit.getLocation().distanceToSqr(start)));
		return hits.toArray(HitResult[]::new);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		if (level().isClientSide()) {
			Player clientPlayer = ClientProxy.getClientPlayer();
			if (clientPlayer != null && getOwner() == clientPlayer) {
				return true;
			}
		}
		return super.shouldRenderAtSqrDistance(distance);
	}

	@Override
	public SoundSource getSoundSource() {
		LivingEntity owner = getOwner();
		return owner != null ? owner.getSoundSource() : super.getSoundSource();
	}

	@Override
	public boolean isSilent() {
		LivingEntity owner = getOwner();
		return owner != null ? owner.isSilent() : super.isSilent();
	}
}
