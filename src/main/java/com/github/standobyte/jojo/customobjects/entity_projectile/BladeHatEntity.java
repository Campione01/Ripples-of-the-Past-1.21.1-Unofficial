package com.github.standobyte.jojo.customobjects.entity_projectile;

import com.github.standobyte.jojo.client.sound.sounds.BladeHatSoundInstance;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModSoundEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class BladeHatEntity extends AbstractArrow implements IEntityWithComplexSpawn {
	private static final EntityDataAccessor<Boolean> RETURNING_TO_OWNER =
			SynchedEntityData.defineId(BladeHatEntity.class, EntityDataSerializers.BOOLEAN);

	public BladeHatEntity(EntityType<? extends BladeHatEntity> type, Level level) {
		super(type, level);
		initBladeHat();
	}

	public BladeHatEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntityTypes.BLADE_HAT.get(), owner, level, stack.copyWithCount(1), stack.copyWithCount(1));
		if (owner instanceof Player player) {
			pickup = player.getAbilities().instabuild ? Pickup.CREATIVE_ONLY : Pickup.ALLOWED;
		}
		initBladeHat();
	}

	public BladeHatEntity(Level level, double x, double y, double z, ItemStack stack) {
		super(ModEntityTypes.BLADE_HAT.get(), x, y, z, level, stack.copyWithCount(1), stack.copyWithCount(1));
		initBladeHat();
	}

	private void initBladeHat() {
		setNoGravity(true);
		setBaseDamage(6.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(RETURNING_TO_OWNER, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (!isReturningToOwner() && shouldReturn()) {
			changeMovementAfterHit();
		}
		if (tickCount > 100) {
			setNoGravity(false);
		}
		else if (!inGround && !level().isClientSide()) {
			cutSoftBlocksOnPath();
		}
	}

	protected boolean shouldReturn() {
		return tickCount > 30;
	}

	protected void changeMovementAfterHit() {
		if (!isReturningToOwner()) {
			setDeltaMovement(getDeltaMovement().reverse());
			setReturningToOwner(true);
		}
	}

	private void cutSoftBlocksOnPath() {
		Vec3 position = position();
		HitResult hitResult = level().clip(new ClipContext(position, position.add(getDeltaMovement()),
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
		if (hitResult.getType() == HitResult.Type.BLOCK) {
			cutSoftBlock(((BlockHitResult) hitResult).getBlockPos());
		}
	}

	private boolean cutSoftBlock(BlockPos blockPos) {
		Block block = level().getBlockState(blockPos).getBlock();
		if (block == Blocks.COBWEB || block == Blocks.TRIPWIRE || block instanceof BushBlock) {
			level().destroyBlock(blockPos, true, null);
			return true;
		}
		return false;
	}

	@Override
	protected void onHit(HitResult hitResult) {
		boolean discardAfterHit = false;
		Entity owner = getOwner();
		if (level() instanceof ServerLevel serverLevel && owner instanceof LivingEntity livingOwner) {
			ItemStack pickupStack = getPickupItemStackOrigin();
			pickupStack.hurtAndBreak(1, serverLevel, livingOwner, item -> {});
			discardAfterHit = pickupStack.isEmpty();
		}
		super.onHit(hitResult);
		if (discardAfterHit) {
			discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		Entity target = hitResult.getEntity();
		Entity owner = getOwner();
		if (owner instanceof LivingEntity livingOwner) {
			livingOwner.setLastHurtMob(target);
		}

		boolean enderman = target.getType() == EntityType.ENDERMAN;
		int previousFireTicks = target.getRemainingFireTicks();
		if (isOnFire() && !enderman) {
			target.igniteForSeconds(5.0F);
		}

		DamageSource damageSource = damageSources().arrow(this, owner != null ? owner : this);
		float baseDamage = (float) getBaseDamage();
		ItemStack weaponStack = getWeaponItem();
		if (weaponStack == null) {
			weaponStack = getPickupItemStackOrigin();
		}
		if (level() instanceof ServerLevel serverLevel) {
			baseDamage = EnchantmentHelper.modifyDamage(serverLevel, weaponStack, target, damageSource, baseDamage);
		}
		float damage = (float) (getDeltaMovement().length() * baseDamage);

		if (target.hurt(damageSource, damage)) {
			if (enderman) {
				return;
			}
			if (target instanceof LivingEntity livingTarget) {
				if (level() instanceof ServerLevel serverLevel) {
					EnchantmentHelper.doPostAttackEffectsWithItemSource(
							serverLevel, livingTarget, damageSource, weaponStack);
				}
				doPostHurtEffects(livingTarget);
			}
			playSound(getHitGroundSoundEvent(), 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
			changeMovementAfterHit();
		}
		else {
			target.setRemainingFireTicks(previousFireTicks);
			setDeltaMovement(getDeltaMovement().scale(-0.1D));
			setYRot(getYRot() + 180.0F);
			yRotO += 180.0F;
			if (!level().isClientSide() && getDeltaMovement().lengthSqr() < 1.0E-7D) {
				changeMovementAfterHit();
			}
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hitResult) {
		if (!level().isClientSide() && cutSoftBlock(hitResult.getBlockPos())) {
			return;
		}
		BlockState blockState = level().getBlockState(hitResult.getBlockPos());
		setSoundEvent(blockState.getSoundType(level(), hitResult.getBlockPos(), this).getBreakSound());
		super.onHitBlock(hitResult);
		shakeTime = 0;
		setNoGravity(false);
		setSoundEvent(getDefaultHitGroundSoundEvent());
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		if (!super.canHitEntity(entity)) {
			return false;
		}
		Entity owner = getOwner();
		if (entity.is(owner)) {
			return false;
		}
		if (owner != null && entity instanceof Projectile projectile) {
			Entity projectileOwner = projectile.getOwner();
			if (projectileOwner != null && projectileOwner.is(owner)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public void playerTouch(Player player) {
		if (!level().isClientSide() && player.is(getOwner()) && leftOwner && tryPickup(player)) {
			player.take(this, 1);
			discard();
			return;
		}
		super.playerTouch(player);
	}

	@Override
	protected void tickDespawn() {
		if (pickup != Pickup.ALLOWED) {
			super.tickDespawn();
		}
	}

	public boolean isInGround() {
		return inGround;
	}

	public boolean isReturningToOwner() {
		return entityData.get(RETURNING_TO_OWNER);
	}

	private void setReturningToOwner(boolean returning) {
		entityData.set(RETURNING_TO_OWNER, returning);
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(ModItems.BLADE_HAT.get());
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return ModSoundEvents.BLADE_HAT_ENTITY_HIT.get();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putInt("Age", tickCount);
		nbt.putBoolean("Returning", isReturningToOwner());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains("Age")) {
			tickCount = nbt.getInt("Age");
		}
		setReturningToOwner(nbt.getBoolean("Returning"));
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		BladeHatSoundInstance.play(this);
	}
}
