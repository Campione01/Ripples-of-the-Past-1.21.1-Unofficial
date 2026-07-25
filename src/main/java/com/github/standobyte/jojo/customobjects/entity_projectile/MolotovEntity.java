package com.github.standobyte.jojo.customobjects.entity_projectile;

import java.util.List;

import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ProjectileHamonChargeState;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonSendoOverdriveEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class MolotovEntity extends ThrowableItemProjectile {
	public MolotovEntity(EntityType<? extends MolotovEntity> type, Level level) {
		super(type, level);
	}

	public MolotovEntity(Level level, LivingEntity shooter, ItemStack item) {
		super(ModEntityTypes.MOLOTOV.get(), shooter, level);
		setItem(item);
	}

	public MolotovEntity(Level level, double x, double y, double z, ItemStack item) {
		super(ModEntityTypes.MOLOTOV.get(), x, y, z, level);
		setItem(item);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.MOLOTOV.get();
	}

	@Override
	protected double getDefaultGravity() {
		return 0.05D;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide()) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.SPLASH_POTION_BREAK,
					SoundSource.NEUTRAL, 1.0F, random.nextFloat() * 0.1F + 0.9F);
			discard();
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (!level().isClientSide()) {
			setBlocksOnFire(result.getBlockPos(), 3);
			setEntitiesOnFire(result.getBlockPos(), 3.0D);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (!level().isClientSide()) {
			Entity entity = result.getEntity();
			entity.hurt(molotovDamageSource(), 2.0F);
			entity.igniteForSeconds(10.0F);
			setBlocksOnFire(blockPosition(), 2);
			setEntitiesOnFire(blockPosition(), 2.0D);
		}
	}

	private DamageSource molotovDamageSource() {
		Entity owner = getOwner();
		return new DamageSource(DamageUtil.type(level(), ModDamageTypes.MOD_PROJECTILE_FIRE), this, owner != null ? owner : this);
	}

	private void setBlocksOnFire(BlockPos center, int radius) {
		Level level = level();
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					if (Math.abs(x) + Math.abs(y) + Math.abs(z) <= radius) {
						BlockPos pos = center.offset(x, y, z);
						if (level.isEmptyBlock(pos) && BaseFireBlock.canBePlacedAt(level, pos, net.minecraft.core.Direction.UP)) {
							level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
						}
					}
				}
			}
		}
	}

	private void setEntitiesOnFire(BlockPos center, double radius) {
		List<Entity> targets = level().getEntities(this, new AABB(center).inflate(radius));
		for (Entity target : targets) {
			if (target.distanceToSqr(center.getX(), center.getY(), center.getZ()) < radius * radius) {
				target.igniteForSeconds(4.0F);
			}
		}
	}

	public void onHitWithHamonCharge(HitResult target, ProjectileHamonChargeState bottleCharge) {
		if (level().isClientSide() || target.getType() != HitResult.Type.BLOCK || !(target instanceof BlockHitResult blockTarget)) {
			return;
		}
		LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
		Direction face = blockTarget.getDirection();
		spawnSendoOverdrive(owner, blockTarget.getBlockPos(), face, 3.0F, bottleCharge.getHamonDamage() / 4.0F, 2);
		spawnSendoOverdrive(owner, blockTarget.getBlockPos(), face, 2.0F, bottleCharge.getHamonDamage() / 4.0F, 4);
	}

	private void spawnSendoOverdrive(LivingEntity owner, BlockPos blockPos, Direction face, float radius, float damage, int waves) {
		HamonSendoOverdriveEntity sendoOverdrive = new HamonSendoOverdriveEntity(level(), owner, face.getAxis())
				.setRadius(radius)
				.setWaveDamage(damage)
				.setWavesCount(waves);
		sendoOverdrive.moveTo(Vec3.atCenterOf(blockPos).subtract(0.0D, sendoOverdrive.getBbHeight() * 0.5D, 0.0D));
		sendoOverdrive.setBlockTarget(blockPos, face);
		level().addFreshEntity(sendoOverdrive);
	}
}
