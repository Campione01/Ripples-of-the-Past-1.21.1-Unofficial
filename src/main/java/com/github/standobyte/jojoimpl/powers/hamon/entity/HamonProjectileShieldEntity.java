package com.github.standobyte.jojoimpl.powers.hamon.entity;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.util.functions.MathUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class HamonProjectileShieldEntity extends Entity implements IEntityWithComplexSpawn {
	private static final int REFRESH_LIFETIME = 10;
	private int ownerId = -1;
	@Nullable private LivingEntity owner;
	private float shieldWidth = 8.0F;
	private float shieldHeight = 4.0F;

	public HamonProjectileShieldEntity(Level level, LivingEntity owner, float width, float height) {
		this(ModEntityTypes.HAMON_PROJECTILE_SHIELD.get(), level);
		setOwner(owner);
		this.shieldWidth = width;
		this.shieldHeight = height;
		updateShieldPos();
	}

	public HamonProjectileShieldEntity(EntityType<?> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public void refresh(LivingEntity owner, float width, float height) {
		setOwner(owner);
		this.shieldWidth = width;
		this.shieldHeight = height;
		this.tickCount = 0;
		updateShieldPos();
	}

	private void setOwner(@Nullable LivingEntity owner) {
		this.owner = owner;
		this.ownerId = owner != null ? owner.getId() : -1;
	}

	@Nullable
	public LivingEntity getOwnerEntity() {
		if (owner == null && ownerId >= 0) {
			Entity entity = level().getEntity(ownerId);
			if (entity instanceof LivingEntity living) {
				owner = living;
			}
		}
		return owner;
	}

	public float getShieldWidth() {
		return shieldWidth;
	}

	public float getShieldHeight() {
		return shieldHeight;
	}

	@Override
	public void tick() {
		super.tick();
		LivingEntity owner = getOwnerEntity();
		if (owner == null || !owner.isAlive()) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		updateShieldPos();
		if (!level().isClientSide()) {
			deflectProjectiles(owner);
			if (tickCount > REFRESH_LIFETIME) {
				discard();
			}
		}
	}

	private void updateShieldPos() {
		LivingEntity owner = getOwnerEntity();
		if (owner == null) {
			return;
		}
		setYRot(owner.getYRot());
		setXRot(owner.getXRot());
		Vec3 offset = new Vec3(0.0D, -shieldHeight * 0.15D, 2.0D)
				.xRot(-owner.getXRot() * MathUtil.DEG_TO_RAD)
				.yRot(-owner.getYRot() * MathUtil.DEG_TO_RAD);
		setPos(owner.getX() + offset.x, owner.getY(0.5D) + offset.y, owner.getZ() + offset.z);
	}

	private void deflectProjectiles(LivingEntity owner) {
		AABB bounds = getBoundingBox().inflate(shieldWidth * 0.5D, shieldHeight * 0.5D, shieldWidth * 0.5D);
		for (Projectile projectile : level().getEntitiesOfClass(Projectile.class, bounds, Entity::isAlive)) {
			if (projectile.getOwner() == owner) {
				continue;
			}
			if (projectile instanceof ModdedProjectileEntity moddedProjectile && !moddedProjectile.canBeDeflected(this)) {
				continue;
			}
			Vec3 motion = projectile.getDeltaMovement();
			if (motion.lengthSqr() > 1.0E-6D) {
				Vec3 deflected = motion.reverse().scale(0.75D);
				projectile.setDeltaMovement(deflected);
				projectile.hurtMarked = true;
				if (projectile instanceof ModdedProjectileEntity moddedProjectile) {
					moddedProjectile.setIsDeflected(deflected, projectile.position());
				}
			}
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		shieldWidth = nbt.getFloat("Width");
		shieldHeight = nbt.getFloat("Height");
		ownerId = nbt.getInt("OwnerId");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		nbt.putFloat("Width", shieldWidth);
		nbt.putFloat("Height", shieldHeight);
		nbt.putInt("OwnerId", ownerId);
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeFloat(shieldWidth);
		buffer.writeFloat(shieldHeight);
		buffer.writeVarInt(ownerId);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		shieldWidth = additionalData.readFloat();
		shieldHeight = additionalData.readFloat();
		ownerId = additionalData.readVarInt();
		owner = null;
	}
}
