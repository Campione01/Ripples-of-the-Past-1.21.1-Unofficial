package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojoimpl.powers.hamon.EntityHamonChargeState;
import com.github.standobyte.jojoimpl.powers.hamon.HamonCharge;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HamonProtectionAbility extends Ability {

	public HamonProtectionAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		HamonData hamon = getHamonData(context);
		if (hamon == null) {
			return ConditionCheck.NEGATIVE;
		}
		return hamon.isProtectionEnabled() || hamon.hasEnergy(1.0F)
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("some_energy");
	}

	@Override
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
		if (level.isClientSide()) {
			return;
		}
		Power<?> power = getUserPower(user);
		HamonData hamon = getHamonData(power);
		if (hamon != null) {
			hamon.toggleHamonProtection();
			hamon.syncOnUpdate(user);
		}
	}

	@Override
	public String getSpriteName(Power<?> context) {
		return isProtectionEnabled(context) ? "hamon_protection_on" : super.getSpriteName(context);
	}

	private static boolean isProtectionEnabled(Power<?> context) {
		HamonData hamon = getHamonData(context);
		return hamon != null && hamon.isProtectionEnabled();
	}

	public static float reduceDamageAmount(Power<?> power, LivingEntity user, DamageSource dmgSource, float dmgAmount) {
		HamonData hamon = getHamonData(power);
		if (hamon == null || !hamon.isProtectionEnabled() || user == null || dmgAmount <= 0.0F) {
			return dmgAmount;
		}

		float energyCost = dmgAmount * 75.0F;
		float efficiency = hamon.getHamonEnergyUsageEfficiency(energyCost, true, user);
		if (efficiency <= 0.0F) {
			return dmgAmount;
		}

		float controlRatio = (float) hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL;
		float baseReduction = 0.4F + controlRatio * 0.2F;
		float damageReductionMult = Mth.clamp(baseReduction * efficiency, 0.0F, 1.0F);
		float damageReduced = dmgAmount * damageReductionMult;
		hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, energyCost * efficiency);
		hamon.syncOnUpdate(user);
		HamonUtil.emitHamonSparkParticles(user.level(), null, damageSparkPosition(user, dmgSource), damageReduced * 0.25F);
		return dmgAmount - damageReduced;
	}

	public static boolean preventBlockDamage(LivingEntity user, DamageSource dmgSource, float dmgAmount) {
		if (user == null || user.level().isClientSide() || dmgAmount <= 0.0F) {
			return false;
		}
		boolean damagePrevented = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).map(hamon -> {
			float energyCost = dmgAmount * 0.5F;
			float energy = hamon.getEnergy();
			if (energy >= energyCost) {
				hamon.setEnergy(energy - energyCost);
				hamon.syncOnUpdate(user);
				return true;
			}
			if (energy > 0.0F) {
				hamon.setEnergy(0.0F);
				hamon.syncOnUpdate(user);
			}
			return false;
		}).orElse(false);

		if (!damagePrevented) {
			HamonCharge charge = EntityHamonChargeState.get(user).getHamonCharge();
			if (charge != null) {
				charge.decreaseTicks(Math.max((int) dmgAmount, 1));
				damagePrevented = true;
			}
		}

		if (damagePrevented) {
			HamonUtil.emitHamonSparkParticles(user.level(), null, damageSparkPosition(user, dmgSource), Math.min(dmgAmount * 0.25F, 1.0F));
		}
		return damagePrevented;
	}

	private static Vec3 damageSparkPosition(LivingEntity user, DamageSource dmgSource) {
		Entity sourceEntity = dmgSource.getDirectEntity();
		if (sourceEntity == null) {
			return user.getBoundingBox().getCenter();
		}
		Vec3 sourcePos = sourceEntity.getEyePosition(1.0F);
		AABB userHitbox = user.getBoundingBox();
		if (userHitbox.contains(sourcePos)) {
			return sourcePos;
		}
		return userHitbox.clip(sourcePos, sourcePos.add(sourceEntity.getLookAngle().scale(16.0D)))
				.orElse(user.getEyePosition(1.0F));
	}

	private static HamonData getHamonData(Power<?> context) {
		return context != null
				&& context.getDataForPowerType(
						ModPlayerPowers.HAMON.get().getId())
						instanceof HamonData hamon
								? hamon
								: null;
	}
}
