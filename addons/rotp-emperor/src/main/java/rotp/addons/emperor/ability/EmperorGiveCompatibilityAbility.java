package rotp.addons.emperor.ability;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.world.entity.LivingEntity;

/**
 * Preserves the donor's hidden, behavior-free {@code give_emp} action.
 */
public final class EmperorGiveCompatibilityAbility
        extends NoPoseStandEntityAbility {
    public static final float STAMINA_COST = 1.0F;

    public EmperorGiveCompatibilityAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                EmperorGiveCompatibilityAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
    }

    public static final class EmperorGiveCompatibilityAction
            extends EntityActionInstance {
        public EmperorGiveCompatibilityAction(
                EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (!level().isClientSide()
                    && ability
                            instanceof EmperorGiveCompatibilityAbility
                            current
                    && user != null
                    && power != null) {
                // 1.16 StandEntityTask paid staminaCost(1) at perform start and ignored the result
                StandAbilityStamina.consume(
                        (Ability) current,
                        power,
                        STAMINA_COST,
                        false);
            }
        }
    }
}
