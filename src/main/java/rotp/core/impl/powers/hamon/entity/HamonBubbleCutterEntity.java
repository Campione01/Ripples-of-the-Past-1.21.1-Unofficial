package rotp.core.impl.powers.hamon.entity;

import javax.annotation.Nullable;

import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.client.sound.HamonSparksLoopSound;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class HamonBubbleCutterEntity extends ModdedProjectileEntity {
	private boolean gliding;
	private float hamonStatPoints;

	public HamonBubbleCutterEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.HAMON_BUBBLE_CUTTER.get(), shooter, level);
	}

	public HamonBubbleCutterEntity(EntityType<? extends HamonBubbleCutterEntity> type, Level level) {
		super(type, level);
	}

	public HamonBubbleCutterEntity setGliding(boolean gliding) {
		this.gliding = gliding;
		return this;
	}

	public void setHamonStatPoints(float points) {
		this.hamonStatPoints = points;
	}

	@Override
	public void tick() {
		super.tick();
		// 1.16: the bubble cutter crackles and sheds Hamon sparks in flight
		if (level().isClientSide()) {
			HamonSparksLoopSound.playSparkSound(this, position(), 0.25F);
			CustomParticlesHelper.createHamonSparkParticles(this, position(), 1);
		}
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (target instanceof LivingEntity living && owner != null) {
			HamonAbilityHelpers.hamonHurt(living, owner, getBaseDamage());
			return true;
		}
		return false;
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt) {
			addStrengthPoints();
		}
	}

	private void addStrengthPoints() {
		LivingEntity owner = getOwner();
		if (owner != null && hamonStatPoints > 0.0F) {
			PlayerPower.getPowerData(owner, HamonPowerType.HAMON).ifPresent(hamon -> {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, hamonStatPoints);
				hamon.syncOnUpdate(owner);
			});
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult blockHit) {
		if (gliding && blockHit.getDirection().getAxis() == Direction.Axis.Y) {
			// Gliding keeps the incoming speed and bypasses ordinary block-hit callbacks/destruction.
			Vec3 movement = getDeltaMovement();
			Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
			double horizontalLengthSqr = horizontal.lengthSqr();
			setDeltaMovement(horizontalLengthSqr > 0.0D
					? horizontal.scale(Math.sqrt(movement.lengthSqr() / horizontalLengthSqr)) : Vec3.ZERO);
			return;
		}
		super.onHitBlock(blockHit);
	}

	@Override
	public int ticksLifespan() {
		return 100;
	}

	@Override
	protected float getBaseDamage() {
		return 1.0F;
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
		nbt.putBoolean("Gliding", gliding);
		nbt.putFloat("HamonStatPoints", hamonStatPoints);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		gliding = nbt.getBoolean("Gliding");
		hamonStatPoints = nbt.getFloat("HamonStatPoints");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeBoolean(gliding);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		gliding = additionalData.readBoolean();
	}
}
