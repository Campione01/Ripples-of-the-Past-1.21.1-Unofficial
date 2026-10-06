package rotp.core.customobjects.entity_projectile;

import javax.annotation.Nullable;

import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.item.ClackersItem;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ClackersEntity extends ModdedProjectileEntity {
	private static final double RETARGET_RANGE = 2.5D;
	private static final EntityDataAccessor<Boolean> IN_GROUND = SynchedEntityData.defineId(ClackersEntity.class, EntityDataSerializers.BOOLEAN);

	private float hamonDmg;
	private float hamonEnergySpent;
	private boolean boomerangHit;
	private boolean clientBlockHit;
	private boolean creativeOnlyPickup;
	private ItemStack pickupItem = ItemStack.EMPTY;

	public ClackersEntity(EntityType<? extends ClackersEntity> type, Level level) {
		super(type, level);
	}

	public ClackersEntity(Level level, LivingEntity thrower, ItemStack stack) {
		super(ModEntityTypes.CLACKERS.get(), thrower, level);
		this.pickupItem = stack.copy();
		this.pickupItem.setCount(1);
		if (thrower instanceof Player player && player.getAbilities().instabuild) {
			this.creativeOnlyPickup = true;
		}
	}

	public void setHamonDamage(float hamonDmg) {
		this.hamonDmg = hamonDmg;
	}

	public void setHamonEnergySpent(float energy) {
		this.hamonEnergySpent = energy;
	}

	public boolean isInGround() {
		return entityData.get(IN_GROUND);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(IN_GROUND, false);
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return new Vec3(0.0D, -(double) 0.1F, 0.0D);
	}

	@Override
	protected boolean hasGravity() {
		return true;
	}

	@Override
	public void shootFromRotation(Entity shooter, float xRot, float yRot, float yAxisRotOffset, float velocity, float inaccuracy) {
		super.shootFromRotation(shooter, xRot, yRot, yAxisRotOffset, velocity, inaccuracy);
		Vec3 ownerMovement = shooter.getDeltaMovement();
		setDeltaMovement(getDeltaMovement().add(ownerMovement.x,
				shooter.onGround() ? 0.0D : ownerMovement.y, ownerMovement.z));
	}

	@Override
	protected void moveProjectile() {
		if (isInGround() || clientBlockHit) {
			clientBlockHit = false;
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		// The donor Arrow moves with incoming velocity, then applies drag followed by gravity.
		Vec3 movement = getDeltaMovement();
		Vec3 nextPosition = position().add(movement);
		rotateTowardsMovement(0.25F);
		float inertia = 0.99F;
		if (isInWater()) {
			for (int i = 0; i < 4; i++) {
				level().addParticle(ParticleTypes.BUBBLE,
						nextPosition.x - movement.x * 0.25D,
						nextPosition.y - movement.y * 0.25D,
						nextPosition.z - movement.z * 0.25D,
						movement.x, movement.y, movement.z);
			}
			inertia = 0.6F;
		}
		Vec3 nextMovement = movement.scale(inertia);
		if (!isNoGravity() && !noPhysics) {
			nextMovement = nextMovement.add(0.0D, -(double) 0.05F, 0.0D);
		}
		setDeltaMovement(nextMovement);
		xo = xOld = getX();
		yo = yOld = getY();
		zo = zOld = getZ();
		setPos(nextPosition.x, nextPosition.y, nextPosition.z);
	}

	@Override
	public void tick() {
		if (isInGround()) {
			super.tick();
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		super.tick();
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		boolean projectileAttack = target.hurt(getDamageSource(owner), (float) (getDeltaMovement().length() * 2.0D));
		boolean hamonAttack = false;
		if (target instanceof LivingEntity livingTarget && owner != null && hamonDmg > 0.0F) {
			hamonAttack = HamonAbilityHelpers.hamonHurt(livingTarget, hamonDmg, this, owner);
		}
		boolean hitTarget = projectileAttack || hamonAttack;
		if (!level().isClientSide() && hitTarget) {
			LivingEntity shooter = getOwner();
			if (shooter != null && hamonEnergySpent > 0.0F) {
				PlayerPower.getPowerData(shooter, HamonPowerType.HAMON).ifPresent(hamon -> {
					hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, hamonEnergySpent);
					hamon.syncOnUpdate(shooter);
				});
			}
			boomerangHit = true;
		}
		return hitTarget;
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		Entity entity = result.getEntity();
		if (entity instanceof ClackersEntity otherClackers) {
			this.hamonDmg += otherClackers.hamonDmg;
			otherClackers.hamonDmg = 0.0F;
			changeMovementAfterHit();
			return;
		}
		super.onHitEntity(result);
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (targetType == TargetType.ENTITY) {
			changeMovementAfterHit();
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		setPos(result.getLocation());
		setDeltaMovement(Vec3.ZERO);
		if (!level().isClientSide()) {
			setNoGravity(false);
			entityData.set(IN_GROUND, true);
			level().playSound(null, getX(), getY(), getZ(),
					ModSoundEvents.CLACKERS.get(), SoundSource.NEUTRAL, 0.5F, 0.9F + random.nextFloat() * 0.2F);
		}
		else {
			// A predicted hit must not latch the server-owned landing state.
			clientBlockHit = true;
		}
	}

	private void changeMovementAfterHit() {
		if (level().isClientSide()) {
			return;
		}
		Entity owner = getOwner();
		double speed = Math.max(getDeltaMovement().length(), 0.1D);
		if (owner != null && boomerangHit) {
			setDeltaMovement(owner.getEyePosition().subtract(position()).normalize().scale(speed / 2.0D));
			return;
		}
		LivingEntity target = findRetarget(owner);
		if (target != null) {
			setDeltaMovement(target.getEyePosition().subtract(position()).normalize().scale(speed));
		}
		else {
			setDeltaMovement(getDeltaMovement().reverse());
		}
		rotateTowardsMovement(1.0F);
	}

	@Nullable
	private LivingEntity findRetarget(@Nullable Entity owner) {
		double speed = Math.max(getDeltaMovement().length(), 1.0D);
		AABB area = getBoundingBox().inflate(RETARGET_RANGE + speed);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class, area,
				entity -> entity.isAlive() && entity.isPickable() && (owner == null || !entity.is(owner)))) {
			if (!canHitEntity(candidate)) {
				continue;
			}
			double distance = candidate.distanceToSqr(getX(), getY(), getZ());
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		if (entity instanceof ClackersEntity) {
			return entity != this;
		}
		return !entity.is(getOwner()) && super.canHitEntity(entity);
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public void playerTouch(Player player) {
		if (level().isClientSide()) {
			return;
		}
		Entity owner = getOwner();
		boolean canTryPickup = isInGround() || owner == null || player.is(owner) && leftOwner;
		if (!canTryPickup || creativeOnlyPickup && !player.getAbilities().instabuild) {
			return;
		}
		ItemStack pickup = getPickupItem();
		boolean pickedUp = creativeOnlyPickup || player.addItem(pickup);
		if (pickedUp) {
			player.take(this, 1);
			if (boomerangHit) {
				JojoModUtil.sayVoiceLine(player, ModSoundEvents.JOSEPH_CLACKER_BOOMERANG);
			}
			discard();
		}
	}

	private ItemStack getPickupItem() {
		if (pickupItem.isEmpty()) {
			pickupItem = new ItemStack(ModItems.CLACKERS.get());
		}
		ItemStack pickup = pickupItem.copy();
		pickup.setCount(1);
		return pickup;
	}

	private void dropPickupItem() {
		ItemStack pickup = getPickupItem();
		if (!pickup.isEmpty()) {
			Vec3 pos = position().add(ClackersItem.projectilePickupOffset(this));
			ItemEntity item = new ItemEntity(level(), pos.x, pos.y, pos.z, pickup, 0.0D, 0.0D, 0.0D);
			Entity owner = getOwner();
			if (owner != null) {
				item.setThrower(owner);
			}
			level().addFreshEntity(item);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && reason == RemovalReason.KILLED && !creativeOnlyPickup) {
			dropPickupItem();
		}
		super.remove(reason);
	}

	@Override
	protected boolean shouldExpire(@Nullable Entity owner) {
		return creativeOnlyPickup && super.shouldExpire(owner);
	}

	@Override
	public int ticksLifespan() {
		return 1200;
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
		return false;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putFloat("HamonDamage", hamonDmg);
		nbt.putFloat("HamonSpent", hamonEnergySpent);
		nbt.putBoolean("BoomerangHit", boomerangHit);
		nbt.putBoolean("InGround", isInGround());
		nbt.putBoolean("CreativeOnlyPickup", creativeOnlyPickup);
		if (!pickupItem.isEmpty()) {
			nbt.put("PickupItem", pickupItem.save(registryAccess()));
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		hamonDmg = nbt.getFloat("HamonDamage");
		hamonEnergySpent = nbt.getFloat("HamonSpent");
		boomerangHit = nbt.getBoolean("BoomerangHit");
		entityData.set(IN_GROUND, nbt.getBoolean("InGround"));
		creativeOnlyPickup = nbt.getBoolean("CreativeOnlyPickup");
		if (nbt.contains("PickupItem")) {
			pickupItem = ItemStack.parseOptional(registryAccess(), nbt.getCompound("PickupItem"));
		}
	}
}
