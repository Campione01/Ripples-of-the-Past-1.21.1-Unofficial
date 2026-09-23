package rotp.core.impl.powers.hamon.entity;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonBubbleBarrierAbility;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class HamonBubbleBarrierEntity extends ModdedProjectileEntity {
	private int barrierMaxTicks = 100;
	private boolean barrier;
	private int barrierTicks;
	private boolean charging;

	public HamonBubbleBarrierEntity(Level level, LivingEntity shooter) {
		super(ModEntityTypes.HAMON_BUBBLE_BARRIER.get(), shooter, level);
	}

	public HamonBubbleBarrierEntity(EntityType<? extends HamonBubbleBarrierEntity> type, Level level) {
		super(type, level);
	}

	// 1.16: the barrier spawned when the hold started stays where it appeared until the charge fires.
	public HamonBubbleBarrierEntity setCharging() {
		this.charging = true;
		return this;
	}

	public void shootFromCharge(LivingEntity user) {
		this.charging = false;
		shootFromRotation(user, 1.0F, 0.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			// 1.16 HamonBubbleBarrierEntity.tick: a barrier whose hold ended before it fired is removed.
			if (charging && !isHeldByOwnerCharge()) {
				discard();
			}
			else if (barrier && barrierTicks++ >= barrierMaxTicks) {
				discard();
			}
		}
	}

	private boolean isHeldByOwnerCharge() {
		LivingEntity owner = getOwner();
		return owner != null
				&& LivingComponentAction.getCurEntityAction(owner) instanceof HamonBubbleBarrierAbility.BubbleBarrierInstance action
				&& action.isChargingBarrier(this);
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (target instanceof LivingEntity living && owner != null) {
			HamonAbilityHelpers.hamonHurt(living, owner, 0.1F);
			return true;
		}
		return false;
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt) {
			Entity target = entityRayTraceResult.getEntity();
			if (target instanceof LivingEntity && target.startRiding(this)) {
				barrier = true;
				setDeltaMovement(new Vec3(0.0D, 0.05D, 0.0D));
			}
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult blockRayTraceResult) {
		super.onHitBlock(blockRayTraceResult);
		if (blockRayTraceResult.getDirection().getAxis() == Direction.Axis.Y) {
			setDeltaMovement(getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
		}
		else {
			setDeltaMovement(getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
		}
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (targetType != TargetType.ENTITY && !isVehicle()) {
			super.breakProjectile(targetType, hitTarget);
		}
	}

	@Override
	public int ticksLifespan() {
		return barrier ? barrierMaxTicks : 100 + barrierMaxTicks;
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

	public float getSize(float partialTick) {
		return Math.min((tickCount + partialTick) / 20.0F, 1.0F);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putBoolean("Barrier", barrier);
		nbt.putInt("BarrierTicks", barrierTicks);
		nbt.putInt("BarrierMaxTicks", barrierMaxTicks);
		nbt.putBoolean("Charging", charging);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		barrier = nbt.getBoolean("Barrier");
		barrierTicks = nbt.getInt("BarrierTicks");
		barrierMaxTicks = nbt.getInt("BarrierMaxTicks");
		// A charge does not survive a reload, so a barrier saved mid-charge is removed on its first tick.
		charging = nbt.getBoolean("Charging");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeVarInt(barrierMaxTicks);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		barrierMaxTicks = additionalData.readVarInt();
	}
}
