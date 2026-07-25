package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.customobjects.AfterimageEntity;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class HamonSpeedBoostAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 600F;

	public HamonSpeedBoostAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, SpeedBoostInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 4);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 3);
	}

	@Override
	protected boolean consumeRuntimeOnPerform(LivingEntity user) {
		Power<?> context = getUserPower(user);
		HamonData hamon = getHamonData(context);
		if (hamon == null) {
			return false;
		}
		if (hamon.isAbilityOnCooldown(name()) || !hasHamonEnergy(context, hamon)) {
			return false;
		}
		float efficiency = hamon.getActionEfficiency(ENERGY_COST, false, ModHamonSkills.SPEED_BOOST.get(), user);
		if (!isCreative(context) && hamon.getHamonEnergyUsageEfficiency(ENERGY_COST, true, user) <= 0.0F) {
			return false;
		}

		int cooldown = getHamonCooldown(context, -1);
		if (cooldown > 0) {
			hamon.setAbilityCooldown(name(), cooldown);
		}
		applySpeedBoost(user.level(), user, hamon, efficiency);
		if (!isCreative(context) || cooldown > 0) {
			hamon.syncOnUpdate(user);
		}
		playHamonShout(user, hamon);
		return true;
	}

	private void applySpeedBoost(Level level, LivingEntity user, HamonData hamon, float efficiency) {
		float effectStr = (float) hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL * efficiency;
		int speedLvl = Mth.floor(1.5F * effectStr);
		int hasteLvl = Mth.floor(1.5F * effectStr);
		boolean afterimages = hamon.isSkillLearned(ModHamonSkills.AFTERIMAGES.get());
		if (afterimages) {
			speedLvl++;
			hasteLvl++;
		}

		if (!level.isClientSide()) {
			int duration = 20 + Mth.floor(180F * effectStr);
			if (efficiency >= 1.0F && afterimages) {
				AfterimageEntity.addAfterimages(user, Math.min((int) (effectStr * 7F / 1.5F), 7), duration);
			}
			if (!user.hasEffect(MobEffects.MOVEMENT_SPEED)) {
				hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, ENERGY_COST);
			}
			user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, speedLvl, false, true));
			user.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, hasteLvl, false, true));
			HamonUtil.emitHamonSparkParticles(level, user instanceof Player player ? player : null, user.position(), (speedLvl + 1) * 0.25F);
		}
	}

	public static class SpeedBoostInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		public SpeedBoostInstance(EntityActionType ability) { super(ability); }

		@Override
		public void actionPerformStart() {
		}
	}
}

