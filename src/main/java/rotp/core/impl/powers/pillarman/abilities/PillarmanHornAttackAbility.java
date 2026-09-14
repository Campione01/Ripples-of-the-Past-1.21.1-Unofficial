package rotp.core.impl.powers.pillarman.abilities;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.pillarman.PillarmanHornEntity;
import rotp.core.impl.powers.pillarman.PillarmanMode;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class PillarmanHornAttackAbility extends PillarmanActionAbility {

	public PillarmanHornAttackAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 2, PillarmanMode.NONE, true, 15.0F, HornAttackInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		setIgnoresPerformerStun();
	}

	public static class HornAttackInstance extends EntityActionInstance {
		public HornAttackInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide() || !(ability instanceof PillarmanHornAttackAbility hornAbility)) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			Power<?> context = hornAbility.getUserPower(user);
			if (!hornAbility.consumeEnergy(context)) {
				return;
			}
			PillarmanHornEntity pillarmanHorn = new PillarmanHornEntity(user, level);
			pillarmanHorn.setLifeSpan(40);
			level.addFreshEntity(pillarmanHorn);
			hornAbility.setPillarmanFixedCooldown(context, 60);
			if (context != null
					&& context.getDataForAbility(
							hornAbility) != null) {
				context.getDataForAbility(hornAbility)
						.syncOnUpdate(user);
			}
		}
	}
}
