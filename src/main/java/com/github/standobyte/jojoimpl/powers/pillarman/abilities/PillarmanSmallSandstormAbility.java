package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanDivineSandstormEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class PillarmanSmallSandstormAbility extends PillarmanActionAbility {

	public PillarmanSmallSandstormAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.WIND, false, 20.0F, SmallSandstormInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	public static class SmallSandstormInstance extends EntityActionInstance {
		public SmallSandstormInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide() || !(ability instanceof PillarmanSmallSandstormAbility sandstormAbility)) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			Power<?> context = sandstormAbility.getUserPower(user);
			if (!sandstormAbility.consumeEnergy(context)) {
				return;
			}
			user.swing(InteractionHand.MAIN_HAND, true);
			PillarmanDivineSandstormEntity sandstormWave = new PillarmanDivineSandstormEntity(level, user, 0)
					.setAtmospheric(false)
					.setRadius(1.5F)
					.setDamage(6.0F)
					.setDuration(40);
			sandstormWave.shootFromRotation(user, 1.5F, 1.0F);
			level.addFreshEntity(sandstormWave);
			level.playSound(null, user.getX(), user.getY(), user.getZ(),
					ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get(), SoundSource.AMBIENT, 0.2F, 1.0F);
			sandstormAbility.setPillarmanFixedCooldown(context, 10);
			if (context != null
					&& context.getDataForAbility(
							sandstormAbility) != null) {
				context.getDataForAbility(sandstormAbility)
						.syncOnUpdate(user);
			}
		}
	}
}
