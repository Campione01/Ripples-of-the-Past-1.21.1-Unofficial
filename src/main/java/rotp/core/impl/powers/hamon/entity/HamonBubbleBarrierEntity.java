package rotp.core.impl.powers.hamon.entity;

import javax.annotation.Nullable;

import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonBubbleBarrierAbility;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
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
		// 1.16: the trap lasts 100 ticks times the user's Bubble Barrier efficiency.
		if (shooter != null) {
			barrierMaxTicks = (int) (100F * PlayerPower.getPowerData(shooter, ModPlayerPowers.HAMON)
					.map(hamon -> hamon.getActionEfficiency(0, true, ModHamonSkills.BUBBLE_BARRIER.get(), shooter))
					.orElse(1F));
		}
	}

	public int getBarrierMaxTicks() {
		return barrierMaxTicks;
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
			// 1.16: the barrier pops on timeout or once its captive is out.
			else if (barrier && (barrierTicks++ >= barrierMaxTicks || !isVehicle())) {
				discard();
			}
			// 1.16: the captive takes a trickle of Hamon damage on 3 of every 5 ticks.
			else if (barrier && tickCount % 5 % 2 == 0 && getFirstPassenger() instanceof LivingEntity captive) {
				HamonAbilityHelpers.hamonHurt(captive, 0.002F, this, getOwner());
			}
		}
		else {
			// 1.16 HamonBubbleBarrierEntity.tick: on every client tick one spark on the bubble's surface, with the
			// spark sound at 0.2 (HamonUtil.emitHamonSparkParticles at intensity 0.1).
			Vec3 sparkPos = Vec3.directionFromRotation(random.nextFloat() * 360F, random.nextFloat() * 360F)
					.scale(getBbWidth() / 2).add(getX(), getY(0.5D), getZ());
			CustomParticlesHelper.createHamonSparkParticles(null, sparkPos, 1);
			level().playLocalSound(sparkPos.x, sparkPos.y, sparkPos.z, ModSoundEvents.HAMON_SPARK.get(), SoundSource.AMBIENT,
					0.2F, 1.0F + (level().random.nextFloat() - 0.5F) * 0.15F, false);
		}
	}

	// 1.16 onRemovedFromWorld: removing the barrier frees its captive from the stun (before passengers are ejected).
	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide()) {
			for (Entity passenger : getPassengers()) {
				if (passenger instanceof LivingEntity living) {
					living.removeEffect(ModStatusEffects.STUN);
				}
			}
		}
		super.remove(reason);
	}

	private boolean isHeldByOwnerCharge() {
		LivingEntity owner = getOwner();
		return owner != null
				&& LivingComponentAction.getCurEntityAction(owner) instanceof HamonBubbleBarrierAbility.BubbleBarrierInstance action
				&& action.isChargingBarrier(this);
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (target instanceof LivingEntity living) {
			// 1.16: no trap, stun or training unless the Hamon damage lands; with no owner it is a hit of the barrier alone
			return HamonAbilityHelpers.hamonHurt(living, 0.1F, this, owner);
		}
		return false;
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt) {
			Entity target = entityRayTraceResult.getEntity();
			if (target instanceof LivingEntity living && target.startRiding(this)) {
				barrier = true;
				// 1.16: the captive is stunned for the whole trap.
				living.addEffect(new MobEffectInstance(ModStatusEffects.STUN, barrierMaxTicks));
				setDeltaMovement(new Vec3(0.0D, 0.05D, 0.0D));
			}
			HamonBubbleEntity.giveStrengthPointsForHit(getOwner());
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
		// 1.16: a trapping barrier ends at tick 100 at the latest; barrierTicks caps the trap itself.
		return barrier ? 100 : 100 + barrierMaxTicks;
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
	protected void positionRider(Entity passenger, MoveFunction callback) {
		if (hasPassenger(passenger)) {
			callback.accept(passenger, getX(),
					getY() + (getBbHeight() - passenger.getBbHeight()) * 0.5D, getZ());
		}
	}

	@Override
	public Vec3 getPassengerRidingPosition(Entity passenger) {
		return position().add(0.0D, getBbHeight() * 0.5D, 0.0D);
	}

	@Override
	public boolean shouldRiderSit() {
		return false;
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
