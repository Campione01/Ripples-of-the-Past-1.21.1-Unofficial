package rotp.core.customobjects.entity_projectile;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.item.ClackersItem;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ClackersEntity extends ModdedProjectileEntity {
	private static final double RETARGET_INFLATE = 2.5D;
	private static final EntityDataAccessor<Boolean> IN_GROUND = SynchedEntityData.defineId(ClackersEntity.class, EntityDataSerializers.BOOLEAN);

	private float hamonDmg;
	private float hamonEnergySpent;
	private boolean boomerangHit;
	private boolean clientBlockHit;
	@Nullable
	private BlockState lastSupportState;
	// 1.16 arrow default: a Clackers without a player thrower (summon, mob) cannot be picked up
	private AbstractArrow.Pickup pickup = AbstractArrow.Pickup.DISALLOWED;
	private int life;
	private int inGroundTime;
	// The 1.16 arrow kept this as its own motion while stuck; here a stuck Clackers reports zero motion.
	private Vec3 groundedMovement = Vec3.ZERO;
	private ItemStack pickupItem = ItemStack.EMPTY;

	public ClackersEntity(EntityType<? extends ClackersEntity> type, Level level) {
		super(type, level);
	}

	public ClackersEntity(Level level, LivingEntity thrower, ItemStack stack) {
		super(ModEntityTypes.CLACKERS.get(), thrower, level);
		this.pickupItem = stack.copy();
		this.pickupItem.setCount(1);
		// repeated here: the field initializer runs after the super constructor's setOwner
		this.pickup = pickupFor(thrower, AbstractArrow.Pickup.DISALLOWED);
	}

	private static AbstractArrow.Pickup pickupFor(@Nullable Entity owner, AbstractArrow.Pickup current) {
		if (owner instanceof Player player) {
			return player.getAbilities().instabuild ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
		}
		return current;
	}

	@Override
	public void setOwner(Entity owner) {
		super.setOwner(owner);
		pickup = pickupFor(owner, pickup);
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
	public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
		super.shoot(x, y, z, velocity, inaccuracy);
		life = 0;
	}

	@Override
	public void lerpMotion(double x, double y, double z) {
		super.lerpMotion(x, y, z);
		life = 0;
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
		rotateTowardsMovement(0.2F);
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
			boolean serverGrounded = !level().isClientSide() && !isRemoved() && isInGround() && !noPhysics;
			if (serverGrounded && lastSupportState != level().getBlockState(blockPosition())
					&& level().noCollision(new AABB(position(), position()).inflate(0.06D))) {
				entityData.set(IN_GROUND, false);
				setDeltaMovement(groundedMovement.multiply((double) (random.nextFloat() * 0.2F),
						(double) (random.nextFloat() * 0.2F), (double) (random.nextFloat() * 0.2F)));
				groundedMovement = Vec3.ZERO;
				life = 0;
			}
			else {
				setDeltaMovement(Vec3.ZERO);
				if (serverGrounded && pickup != AbstractArrow.Pickup.ALLOWED && ++life >= 1200) {
					discard();
					return;
				}
			}
			inGroundTime = serverGrounded ? inGroundTime + 1 : 0;
			if (inGroundTime > 10) {
				boomerangHit = false;
			}
			return;
		}
		inGroundTime = 0;
		super.tick();
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		// 1.16 ItemProjectileEntity.onHitEntity: set before the hit, even a refused one
		if (owner != null) {
			owner.setLastHurtMob(target);
		}
		// 1.16 ItemProjectileEntity.onHitEntity: lit before the hit, except an Enderman, which dodges
		int prevTargetFireTimer = target.getRemainingFireTicks();
		if (isOnFire() && target.getType() != EntityType.ENDERMAN) {
			target.igniteForSeconds(5);
		}
		boolean projectileAttack = target.hurt(getDamageSource(owner), (float) (getDeltaMovement().length() * 2.0D));
		boolean hamonAttack = false;
		// 1.16 dealHamonDamage with no thrower: a hit of the Clackers alone
		if (target instanceof LivingEntity livingTarget && hamonDmg > 0.0F) {
			hamonAttack = HamonAbilityHelpers.hamonHurt(livingTarget, hamonDmg, this, owner);
		}
		boolean hitTarget = projectileAttack || hamonAttack;
		if (!hitTarget) {
			target.setRemainingFireTicks(prevTargetFireTimer);
		}
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
	protected boolean ignitesTargetAfterHit() {
		return false;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		// 1.16 AbstractArrow.tick: the tracker sends the motion after an impact at once
		hasImpulse = true;
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

	// 1.16 ItemProjectileEntity.onHitEntity: a refused hit only rebounds weakly
	@Override
	protected void afterEntityHit(EntityHitResult result, boolean entityHurt) {
		if (entityHurt) {
			// an Enderman that teleported away leaves the Clackers flying on
			if (result.getEntity().getType() == EntityType.ENDERMAN) {
				return;
			}
			playImpactSound(SoundEvents.ARROW_HIT);
			changeMovementAfterHit();
			return;
		}
		setDeltaMovement(getDeltaMovement().scale(-0.1D));
		setYRot(getYRot() + 180.0F);
		yRotO += 180.0F;
		if (getDeltaMovement().lengthSqr() < 1.0E-7D) {
			changeMovementAfterHit();
		}
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		lastSupportState = level().getBlockState(result.getBlockPos());
		lastSupportState.onProjectileHit(level(), lastSupportState, result, this);
		// 1.16 arrow: the rest of this tick's move, then this tick's drag and gravity
		groundedMovement = result.getLocation().subtract(position())
				.scale(isInWater() ? 0.6F : 0.99F).add(0.0D, -(double) 0.05F, 0.0D);
		setPos(result.getLocation());
		setDeltaMovement(Vec3.ZERO);
		if (!level().isClientSide()) {
			setNoGravity(false);
			entityData.set(IN_GROUND, true);
			// 1.16 ItemProjectileEntity.onHit: the break sound of the block, in place of the arrow's own
			playImpactSound(lastSupportState.getSoundType(level(), result.getBlockPos(), this).getBreakSound());
		}
		else {
			// A predicted hit must not latch the server-owned landing state.
			clientBlockHit = true;
		}
	}

	// 1.16 AbstractArrow hit sound, sent as Entity.playSound did
	private void playImpactSound(SoundEvent sound) {
		if (!isSilent()) {
			level().playSound(null, getX(), getY(), getZ(), sound, getSoundSource(),
					1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
		}
	}

	private void changeMovementAfterHit() {
		if (level().isClientSide()) {
			return;
		}
		Entity owner = getOwner();
		if (owner == null) {
			dampMovementAfterHit();
			return;
		}
		// A 1.16 arrow's rotation is atan2(x, z) and atan2(y, h), so the donor's look ray is the heading mirrored in X and Y.
		Vec3 look = getLookAngle();
		List<ActionTarget> onLookRay = HitResultUtil.clipMultipleEntities(getEyePosition(), new Vec3(-look.x, -look.y, look.z),
				distanceTo(owner), level(), this, entity -> !entity.is(owner) && canHitEntity(entity), RETARGET_INFLATE, 0.0D);
		if (onLookRay.isEmpty()) {
			setDeltaMovement(getDeltaMovement().reverse());
		}
		else {
			Entity target = onLookRay.get(0).getEntity();
			setDeltaMovement(target.getEyePosition().subtract(position()).normalize().scale(getDeltaMovement().length()));
		}
		if (boomerangHit) {
			setDeltaMovement(owner.getEyePosition().subtract(position()).normalize().scale(getDeltaMovement().length() / 2.0D));
		}
		else {
			dampMovementAfterHit();
		}
	}

	// 1.16 ItemProjectileEntity.changeMovementAfterHit
	private void dampMovementAfterHit() {
		setDeltaMovement(getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
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

	// As the 1.16 arrow: no extra pick margin, no melee target, damage refused.
	@Override
	public float getPickRadius() {
		return 0.0F;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!isInvulnerableTo(source)) {
			markHurt();
		}
		return false;
	}

	@Override
	public void playerTouch(Player player) {
		if (level().isClientSide()) {
			return;
		}
		Entity owner = getOwner();
		boolean grounded = isInGround();
		boolean mayTake = pickup == AbstractArrow.Pickup.ALLOWED
				|| pickup == AbstractArrow.Pickup.CREATIVE_ONLY && player.getAbilities().instabuild;
		boolean ownPickup = (grounded || owner == null || player.is(owner)) && mayTake && (grounded || noPhysics || leftOwner);
		// 1.16 AbstractArrow.playerTouch, the fallback of the item projectile; it has no voice line
		boolean arrowPickup = !ownPickup && (grounded || noPhysics) && (mayTake || noPhysics && owner != null && player.is(owner));
		if (!ownPickup && !arrowPickup) {
			return;
		}
		if (pickup == AbstractArrow.Pickup.ALLOWED && !player.addItem(getPickupItem())) {
			return;
		}
		// LivingEntity.take sends the collect packet for items, arrows and orbs only; the 1.16 Clackers were arrows
		((ServerLevel) level()).getChunkSource().broadcast(this, new ClientboundTakeItemEntityPacket(getId(), player.getId(), 1));
		if (ownPickup && boomerangHit) {
			JojoModUtil.sayVoiceLine(player, ModSoundEvents.JOSEPH_CLACKER_BOOMERANG);
		}
		discard();
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
		if (!level().isClientSide() && reason == RemovalReason.KILLED && pickup == AbstractArrow.Pickup.ALLOWED) {
			dropPickupItem();
		}
		super.remove(reason);
	}

	@Override
	protected boolean shouldExpire(@Nullable Entity owner) {
		return false;
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
		nbt.putShort("life", (short) life);
		nbt.putFloat("HamonDamage", hamonDmg);
		nbt.putFloat("HamonSpent", hamonEnergySpent);
		nbt.putBoolean("BoomerangHit", boomerangHit);
		nbt.putBoolean("InGround", isInGround());
		if (lastSupportState != null) {
			nbt.put("inBlockState", NbtUtils.writeBlockState(lastSupportState));
		}
		if (isInGround()) {
			nbt.put("GroundedMotion", newDoubleList(groundedMovement.x, groundedMovement.y, groundedMovement.z));
		}
		nbt.putByte("pickup", (byte) pickup.ordinal());
		// still written for saves read by builds from before the "pickup" key
		nbt.putBoolean("CreativeOnlyPickup", pickup == AbstractArrow.Pickup.CREATIVE_ONLY);
		if (!pickupItem.isEmpty()) {
			nbt.put("PickupItem", pickupItem.save(registryAccess()));
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		life = nbt.contains("life", 99) ? nbt.getShort("life") : 0;
		hamonDmg = nbt.getFloat("HamonDamage");
		hamonEnergySpent = nbt.getFloat("HamonSpent");
		boomerangHit = nbt.getBoolean("BoomerangHit");
		entityData.set(IN_GROUND, nbt.getBoolean("InGround"));
		lastSupportState = nbt.contains("inBlockState", 10)
				? NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), nbt.getCompound("inBlockState"))
				: null;
		// a stuck 1.16 arrow saved this vector as its Motion, so that is the fallback
		ListTag grounded = nbt.getList("GroundedMotion", 6);
		groundedMovement = grounded.size() == 3
				? new Vec3(grounded.getDouble(0), grounded.getDouble(1), grounded.getDouble(2))
				: getDeltaMovement();
		if (nbt.contains("pickup", 99)) {
			pickup = AbstractArrow.Pickup.byOrdinal(nbt.getByte("pickup"));
		}
		else if (nbt.contains("CreativeOnlyPickup")) {
			pickup = nbt.getBoolean("CreativeOnlyPickup") ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
		}
		if (nbt.contains("PickupItem")) {
			pickupItem = ItemStack.parseOptional(registryAccess(), nbt.getCompound("PickupItem"));
		}
	}
}
