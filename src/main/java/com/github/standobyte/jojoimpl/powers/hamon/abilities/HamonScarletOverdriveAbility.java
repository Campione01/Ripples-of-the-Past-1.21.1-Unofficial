package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.mechanics.KnockbackCollisionImpact;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

public class HamonScarletOverdriveAbility extends HamonSunlightYellowOverdriveAbility {

	public HamonScarletOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, ScarletOverdriveInstance::new, 8, 32, 9);
		setDefaultPhaseLength(com.github.standobyte.jojo.powersystem.entityaction.ActionPhase.RECOVERY, 4);
	}

	@Override
	protected InteractionHand getRequiredFreeHand() {
		return InteractionHand.OFF_HAND;
	}

	@Override
	protected InteractionHand getSwingHand() {
		return InteractionHand.OFF_HAND;
	}

	@Override
	protected ParticleOptions getPunchParticles() {
		return ModParticles.HAMON_SPARK_RED.get();
	}

	public static class ScarletOverdriveInstance extends HamonSunlightYellowOverdriveAbility.SYOverdrive {
		public ScarletOverdriveInstance(com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType ability) {
			super(ability);
		}

		@Override
		protected void doHamonAttack(LivingEntity user, LivingEntity target, HamonSunlightYellowOverdriveAbility ability) {
			if (userHamon == null) {
				return;
			}
			float efficiency = userHamon.getActionEfficiency(0.0F, true, ModHamonSkills.SCARLET_OVERDRIVE.get(), user);
			float damage = (2.5F + 5.0F * energySpentRatio) * efficiency;
			int fireSeconds = Mth.floor(2.0F + 8.0F * userHamon.getHamonStrengthLevel() / (float) HamonData.MAX_STAT_LEVEL * efficiency);
			int fireTicks = fireSeconds * 20;
			boolean hurt = DamageUtil.dealDamageAndSetOnFire(target,
					entity -> entity instanceof LivingEntity living
							&& HamonAbilityHelpers.hamonHurtWithParticles(living, user, damage, ability.getPunchParticles(), 12),
					fireTicks, false);
			if (hurt) {
				target.level().playSound(null, target, ModSoundEvents.HAMON_SYO_PUNCH.get(),
						target.getSoundSource(), energySpentRatio, 1.0F);
				userHamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH,
						getActualMaxEnergy(userHamon) * energySpentRatio * efficiency);
				DamageUtil.knockback3d(target, 2.0F, -5.0F, user.getYRot());
				if (userHamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get())) {
					KnockbackCollisionImpact.getHandler(target)
							.onPunchSetKnockbackImpact(target.getDeltaMovement(), user)
							.hamonDamage(damage, Math.max(fireTicks / 2, 20), ability.getPunchParticles());
				}
				userHamon.syncOnUpdate(user);
			}
		}
	}
}
