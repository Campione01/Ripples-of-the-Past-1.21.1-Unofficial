package rotp.core.impl.powers.hamon;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers.HamonAttackProperties;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

public class HamonCharge {
	private static final float CHARGE_KNOCKBACK = 0.75F;
	private float damage;
	private final int chargeTicksInitial;
	private int chargeTicks;
	@Nullable private UUID hamonUserId;
	@Nullable private Entity hamonUser;
	private boolean gavePoints;
	private boolean doLargeChargeDmg = true;
	private float energySpent;

	public HamonCharge(float damage, int chargeTicks, @Nullable LivingEntity hamonUser, float energySpent) {
		this.damage = Math.max(damage, 0.0F);
		this.chargeTicksInitial = Math.max(chargeTicks, 0);
		this.chargeTicks = this.chargeTicksInitial;
		if (hamonUser != null) {
			this.hamonUserId = hamonUser.getUUID();
			this.hamonUser = hamonUser;
		}
		this.energySpent = Math.max(energySpent, 0.0F);
	}

	public float getDamage() {
		return damage;
	}

	public int getTicks() {
		return chargeTicks;
	}

	public int getInitialTicks() {
		return chargeTicksInitial;
	}

	public void decreaseTicks(int ticks) {
		chargeTicks -= Math.max(ticks, 0);
	}

	public boolean shouldBeRemoved() {
		return chargeTicks < 0;
	}

	public void tick(@Nullable Entity chargedEntity, @Nullable BlockPos chargedBlock, Level level, AABB aabb) {
		if (level.isClientSide()) {
			return;
		}
		if (chargedEntity != null && !chargedEntity.isAlive()) {
			chargeTicks = -1;
			return;
		}

		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, aabb, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
		for (LivingEntity target : targets) {
			if (target.is(chargedEntity) || !target.isAlive() || hamonUserId != null && target.getUUID().equals(hamonUserId)) {
				continue;
			}
			Entity user = getUser(level);
			LivingEntity userLiving = user instanceof LivingEntity living ? living : null;
			float damageAmount = doLargeChargeDmg ? damage : damage * 0.1F;
			if (dealHamonDamage(target, chargedEntity, user, damageAmount)) {
				if (!target.isAlive() && user instanceof ServerPlayer player) {
					ModCriteriaTriggers.triggerHamonChargeKill(player, target, chargedEntity);
				}
				givePoints(userLiving);
				Vec3 chargePos = chargePosition(chargedEntity, chargedBlock);
				if (chargePos != null && doLargeChargeDmg) {
					HamonUtil.emitHamonSparkParticles(level, null, chargePos, 4.0F);
					knockbackFromCharge(target, chargePos);
				}
				if (userLiving != null && chargedEntity instanceof LivingEntity
						&& HamonUtil.isLiving(target)
						&& !ModStatusEffects.isStunned(target)) {
					PlayerPower.getPowerData(userLiving, ModPlayerPowers.HAMON).ifPresent(hamon -> {
						if (hamon.isSkillLearned(ModHamonSkills.HAMON_SHOCK.get())) {
							target.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModStatusEffects.HAMON_SHOCK, 30, 0, false, false));
						}
					});
				}
				if (chargedBlock != null) {
					if (doLargeChargeDmg && !level.getBlockState(chargedBlock).is(Blocks.COBWEB)) {
						chargeTicks = 0;
					}
				}
				else if (chargedEntity != null) {
					doLargeChargeDmg = false;
				}
				else {
					chargeTicks = 0;
				}
			}
		}
		--chargeTicks;
	}

	private boolean dealHamonDamage(LivingEntity target, @Nullable Entity chargedEntity, @Nullable Entity user, float baseDamage) {
		float amount = HamonAbilityHelpers.hamonDamageAmount(target, baseDamage);
		if (amount <= 0.0F) {
			return false;
		}
		// 1.16 HamonCharge: a charged living entity is itself the attacker, any other charged entity
		// hurts on the Hamon user's behalf, and a charged block uses the anonymous Hamon source.
		Entity causingEntity = chargedEntity instanceof LivingEntity ? chargedEntity
				: chargedEntity != null ? user : null;
		var source = HamonAbilityHelpers.hamonDamageSource(target.level(), chargedEntity, causingEntity);
		if (source instanceof DamageSourceModified modified) {
			// 1.16 hurt() knocked back only from a source entity; 1.21 pushes in a random direction without one
			modified.jojo_ripples$modifyKnockback(0.0F, chargedEntity != null ? 1.0F : 0.0F);
		}
		return HamonAbilityHelpers.hamonHurtWithAmount(target, amount, source,
				HamonAttackProperties.NO_SOURCE_ENTITY_HAMON_MULTIPLIER);
	}

	private void givePoints(@Nullable LivingEntity userLiving) {
		if (!gavePoints && userLiving != null) {
			PlayerPower.getPowerData(userLiving, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, energySpent);
				hamon.syncOnUpdate(userLiving);
			});
			gavePoints = true;
		}
	}

	@Nullable
	private Vec3 chargePosition(@Nullable Entity chargedEntity, @Nullable BlockPos chargedBlock) {
		if (chargedBlock != null) {
			return Vec3.atCenterOf(chargedBlock);
		}
		return chargedEntity != null ? chargedEntity.getBoundingBox().getCenter() : null;
	}

	private void knockbackFromCharge(LivingEntity target, Vec3 chargePos) {
		Vec3 direction = new Vec3(chargePos.x - target.getX(), 0.0D, chargePos.z - target.getZ()).normalize();
		if (direction.lengthSqr() > 0.0D) {
			target.knockback(CHARGE_KNOCKBACK, direction.x, direction.z);
			return;
		}
		// 1.16 knockback with the zero direction of a target on the charge: the speed halves and a grounded target
		// hops; 1.21 knockback would pick a random direction instead.
		LivingKnockBackEvent event = CommonHooks.onLivingKnockBack(target, CHARGE_KNOCKBACK, 0.0D, 0.0D);
		if (event.isCanceled()) {
			return;
		}
		double strength = event.getStrength() * (1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		if (strength > 0.0D) {
			target.hasImpulse = true;
			Vec3 speed = target.getDeltaMovement();
			Vec3 push = new Vec3(event.getRatioX(), 0.0D, event.getRatioZ()).normalize().scale(strength);
			target.setDeltaMovement(speed.x / 2.0D - push.x,
					target.onGround() ? Math.min(0.4D, speed.y / 2.0D + strength) : speed.y,
					speed.z / 2.0D - push.z);
		}
	}

	@Nullable
	public Entity getUser(Level level) {
		if (hamonUser == null && hamonUserId != null && level instanceof ServerLevel serverLevel) {
			hamonUser = serverLevel.getEntity(hamonUserId);
		}
		return hamonUser;
	}

	public static HamonCharge fromNBT(CompoundTag tag) {
		HamonCharge charge = new HamonCharge(tag.getFloat("Charge"), tag.getInt("ChargeTicksInitial"), null, tag.getFloat("EnergySpent"));
		charge.chargeTicks = tag.getInt("ChargeTicks");
		if (tag.hasUUID("HamonUser")) {
			charge.hamonUserId = tag.getUUID("HamonUser");
		}
		charge.gavePoints = tag.getBoolean("GavePoints");
		charge.doLargeChargeDmg = !tag.contains("LargeDmg") || tag.getBoolean("LargeDmg");
		return charge;
	}

	public CompoundTag toNBT() {
		CompoundTag tag = new CompoundTag();
		tag.putFloat("Charge", damage);
		tag.putInt("ChargeTicksInitial", chargeTicksInitial);
		tag.putInt("ChargeTicks", chargeTicks);
		if (hamonUserId != null) {
			tag.putUUID("HamonUser", hamonUserId);
		}
		tag.putBoolean("GavePoints", gavePoints);
		tag.putFloat("EnergySpent", energySpent);
		tag.putBoolean("LargeDmg", doLargeChargeDmg);
		return tag;
	}
}
