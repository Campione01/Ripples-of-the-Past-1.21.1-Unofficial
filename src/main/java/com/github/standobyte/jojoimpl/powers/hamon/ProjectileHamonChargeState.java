package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.customobjects.entity_projectile.MolotovEntity;
import com.github.standobyte.jojo.entityattachment.SynchronizableEntityData;
import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers.HamonAttackProperties;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public class ProjectileHamonChargeState implements TickingEntityData, SynchronizableEntityData, INBTSerializable<CompoundTag> {
	private final Projectile projectile;
	private float hamonBaseDmg;
	private int tickCount;
	private int maxChargeTicks;
	private boolean hasCharge;
	private boolean multiplyWithUserStrength;
	private float spentEnergy;

	public ProjectileHamonChargeState(Projectile projectile) {
		this.projectile = projectile;
		addTicking(projectile);
		addSynchronization(projectile);
	}

	public void setBaseDmg(float damage) {
		this.hamonBaseDmg = Math.max(damage, 0.0F);
		setHasCharge(this.hamonBaseDmg > 0.0F);
	}

	public void setMaxChargeTicks(int ticks) {
		this.maxChargeTicks = Math.max(ticks, 0);
	}

	public void setInfiniteChargeTime() {
		this.maxChargeTicks = -1;
	}

	public void setSpentEnergy(float spentEnergy) {
		this.spentEnergy = Math.max(spentEnergy, 0.0F);
	}

	public void setMultiplyWithUserStrength(boolean multiplyWithUserStrength) {
		this.multiplyWithUserStrength = multiplyWithUserStrength;
	}

	public boolean hasHamonCharge() {
		return hasCharge;
	}

	public float getHamonDamage() {
		return 3.0F * hamonBaseDmg * chargeWearOffMultiplier();
	}

	@Override
	public void tick() {
		if (!hasCharge || projectile.isRemoved()) {
			if (projectile.isRemoved()) {
				setHasCharge(false);
			}
			return;
		}

		if (projectile.level().isClientSide()) {
			if (!chargeWearsOff() || tickCount++ <= maxChargeTicks) {
				tickClientChargeEffects();
			}
			return;
		}

		if (chargeWearsOff() && tickCount++ > maxChargeTicks) {
			setHasCharge(false);
		}
	}

	private void tickClientChargeEffects() {
		float chargeWearOff = chargeWearOffMultiplier();
		Vec3 pos = projectile.position();
		HamonSparksLoopSound.playSparkSound(projectile, pos, chargeWearOff, true);
		if (chargeWearOff >= 1.0F || projectile.level().random.nextFloat() < chargeWearOff) {
			CustomParticlesHelper.createHamonSparkParticles(projectile, pos.x, pos.y, pos.z, 1);
		}
	}

	private float chargeWearOffMultiplier() {
		if (chargeWearsOff()) {
			return Mth.clamp((float) (maxChargeTicks - tickCount) / (float) maxChargeTicks, 0.25F, 1.0F);
		}
		return 1.0F;
	}

	private boolean chargeWearsOff() {
		return maxChargeTicks >= 0;
	}

	public void onTargetHit(HitResult target) {
		Level level = projectile.level();
		if (!hasCharge || level.isClientSide()) {
			return;
		}

		if (projectile instanceof ThrownPotion potion) {
			if (HamonUtil.isWaterBottle(potion)) {
				Vec3 pos = projectile.getBoundingBox().getCenter();
				AABB waterSplashArea = projectile.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
				for (LivingEntity targetEntity : level.getEntitiesOfClass(LivingEntity.class, waterSplashArea,
						EntitySelector.ENTITY_STILL_ALIVE.and(EntitySelector.NO_SPECTATORS)
								.and(entity -> !entity.is(potion.getOwner())))) {
					double distSqr = targetEntity.distanceToSqr(pos);
					if (distSqr < 16.0D) {
						dealHamonDamageToTarget(targetEntity, getHamonDamage() * (1.0F - (float) (distSqr / 16.0D)));
					}
				}
				level.playSound(null, pos.x, pos.y, pos.z, ModSoundEvents.HAMON_SPARK.get(),
						SoundSource.AMBIENT, 0.1F, 1.0F + (level.random.nextFloat() - 0.5F) * 0.15F);
				if (level instanceof ServerLevel serverLevel) {
					serverLevel.sendParticles(ModParticles.HAMON_SPARK.get(), pos.x, pos.y, pos.z,
							32, 0.75D, 0.05D, 0.75D, 0.25D);
				}
			}
			return;
		}

		if (target.getType() == HitResult.Type.ENTITY && target instanceof EntityHitResult entityTarget) {
			dealHamonDamageToTarget(entityTarget.getEntity(), getHamonDamage());
		}

		if (projectile instanceof ThrownEgg egg && level instanceof ServerLevel serverLevel) {
			serverLevel.getData(ModDataAttachmentTypes.CHARGED_HAMON_EGGS.get()).addChargedEggEntity(egg);
		}
		else if (projectile instanceof MolotovEntity molotov) {
			molotov.onHitWithHamonCharge(target, this);
		}
	}

	private boolean dealHamonDamageToTarget(Entity entity, float damage) {
		if (damage <= 0.0F || !(entity instanceof LivingEntity livingTarget)) {
			return false;
		}
		Entity owner = projectile.getOwner();
		float damageAmount = HamonAbilityHelpers.hamonDamageAmount(livingTarget, damage);
		if (damageAmount <= 0.0F) {
			return false;
		}
		var source = HamonAbilityHelpers.hamonDamageSource(livingTarget.level(), projectile, owner);
		if (source instanceof DamageSourceModified modified) {
			modified.jojo_ripples$modifyKnockback(0.0F, 1.0F);
		}
		boolean hurt = multiplyWithUserStrength
				? HamonAbilityHelpers.hamonHurtWithAmount(livingTarget, damageAmount, source)
				: HamonAbilityHelpers.hamonHurtWithAmount(livingTarget, damageAmount, source,
						HamonAttackProperties.NO_SOURCE_ENTITY_HAMON_MULTIPLIER);
		if (hurt && multiplyWithUserStrength && owner instanceof LivingEntity hamonUser) {
			PlayerPower.getPowerData(hamonUser, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, spentEnergy);
				hamon.syncOnUpdate(hamonUser);
			});
		}
		return hurt;
	}

	public void handleMsgFromServer(TrHamonEntityChargePacket msg) {
		this.hasCharge = msg.hasCharge();
		this.tickCount = msg.tickCount();
		this.maxChargeTicks = msg.maxTicks();
	}

	@Override
	public void syncToTracking(ServerPlayer trackingPlayer) {
		if (hasCharge) {
			PacketDistributor.sendToPlayer(trackingPlayer,
					TrHamonEntityChargePacket.projectileCharge(projectile.getId(), true, tickCount, maxChargeTicks));
		}
	}

	private void setHasCharge(boolean hasCharge) {
		if (this.hasCharge == hasCharge) {
			return;
		}
		this.hasCharge = hasCharge;
		syncToTrackingAndSelf();
	}

	private void syncToTrackingAndSelf() {
		if (!projectile.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(projectile,
					TrHamonEntityChargePacket.projectileCharge(projectile.getId(), hasCharge, tickCount, maxChargeTicks));
		}
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		if (hasCharge) {
			tag.putBoolean("HasCharge", true);
			tag.putFloat("HamonDamage", hamonBaseDmg);
			tag.putInt("ChargeAge", tickCount);
			tag.putInt("ChargeTicks", maxChargeTicks);
			tag.putFloat("SpentEnergy", spentEnergy);
			tag.putBoolean("MultiplyWithUserStrength", multiplyWithUserStrength);
		}
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		this.hamonBaseDmg = tag.getFloat("HamonDamage");
		this.tickCount = tag.getInt("ChargeAge");
		this.maxChargeTicks = tag.getInt("ChargeTicks");
		this.spentEnergy = tag.getFloat("SpentEnergy");
		this.multiplyWithUserStrength = tag.getBoolean("MultiplyWithUserStrength");
		this.hasCharge = tag.getBoolean("HasCharge")
				|| hamonBaseDmg > 0.0F && (!chargeWearsOff() || tickCount <= maxChargeTicks);
	}

	public static ProjectileHamonChargeState get(Projectile projectile) {
		return projectile.getData(ModDataAttachmentTypes.PROJECTILE_HAMON_CHARGE);
	}

	public static void handleClientPacket(Entity entity, TrHamonEntityChargePacket msg) {
		if (entity instanceof Projectile projectile) {
			get(projectile).handleMsgFromServer(msg);
		}
	}

	public static void transferEggChargeToChicken(Chicken chicken, ThrownEgg egg) {
		ProjectileHamonChargeState eggCharge = get(egg);
		if (!eggCharge.hasHamonCharge()) {
			return;
		}
		Entity eggThrower = egg.getOwner();
		LivingEntity throwerLiving = eggThrower instanceof LivingEntity living ? living : null;
		float userMultiplier = eggCharge.multiplyWithUserStrength && throwerLiving != null
				? PlayerPower.getPowerData(throwerLiving, ModPlayerPowers.HAMON)
						.map(hamon -> hamon.getHamonDamageMultiplier() * hamon.getBloodstreamEfficiency(throwerLiving))
						.orElse(1.0F)
				: 1.0F;
		EntityHamonChargeState.get(chicken).setHamonCharge(eggCharge.getHamonDamage() * userMultiplier,
				Integer.MAX_VALUE, throwerLiving, 0.0F);
	}
}
