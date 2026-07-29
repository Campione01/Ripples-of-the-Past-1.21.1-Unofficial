package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class PillarmanRegenerationAbility extends PillarmanActionAbility {

	public PillarmanRegenerationAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 2, PillarmanMode.NONE, true, 40.0F, RegenerationInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		setIgnoresPerformerStun();
	}

	public static class RegenerationInstance extends EntityActionInstance {
		public RegenerationInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			Level world = level();
			if (world.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !(ability instanceof PillarmanRegenerationAbility regenAbility)) {
				return;
			}
			Power<?> context = regenAbility.getUserPower(user);
			if (!regenAbility.consumeEnergy(context)) {
				return;
			}
			regenAbility.setPillarmanFixedCooldown(context, 20);
			if (context != null
					&& context.getDataForAbility(
							regenAbility) != null) {
				context.getDataForAbility(regenAbility)
						.syncOnUpdate(user);
			}
			int duration = 60;
			int level = 4;
			duration = updateRegenEffectDuration(user, duration, level);
			user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, level, false, false, true));
			world.playSound(null, user, ModSoundEvents.PILLAR_MAN_STRONG_REGEN.get(), user.getSoundSource(), 1.5F, 1.2F);
		}
	}

	private static int updateRegenEffectDuration(LivingEntity entity, int duration, int level) {
		MobEffectInstance oldEffect = entity.getEffect(MobEffects.REGENERATION);
		int level0Gap = 50;
		if (oldEffect != null && level < floorLog2(level0Gap)) {
			int effectGap = level0Gap >> oldEffect.getAmplifier();
			if (effectGap > 0) {
				int oldEffectAppliesIn = oldEffect.getDuration() % effectGap;
				int newEffectGap = level0Gap >> level;
				int newEffectAppliesIn = newEffectGap > 0 ? duration % newEffectGap : 0;
				if (newEffectAppliesIn < oldEffectAppliesIn) {
					int newDuration = duration + (oldEffectAppliesIn - newEffectAppliesIn);
					while (newDuration > duration) {
						newDuration -= newEffectGap;
					}
					if (newDuration > 0) {
						duration = newDuration;
					}
				}
				else {
					duration -= newEffectAppliesIn - oldEffectAppliesIn;
				}
			}
		}
		return duration;
	}

	private static int floorLog2(int value) {
		return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(value);
	}
}
