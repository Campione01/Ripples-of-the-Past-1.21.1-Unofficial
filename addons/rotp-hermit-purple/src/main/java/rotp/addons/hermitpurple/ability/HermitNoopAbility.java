package rotp.addons.hermitpurple.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import net.minecraft.world.entity.LivingEntity;

public final class HermitNoopAbility
        extends NoPoseStandEntityAbility {
    public HermitNoopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CringeAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 cringe (061 InitStands:80) set no standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(HermitPurpleSpec.CRINGE_COOLDOWN_TICKS);
    }

    public static final class CringeAction
            extends EntityActionInstance {
        public CringeAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }
            // 1.16 shout(USER_CRINGE) went through sayVoiceLine, which the
            // user hears too; Player.playSound on the server leaves them out.
            sayShoutOf(
                    ability,
                    user,
                    HermitPurpleAddon.JOSEPH_CRINGE);
            // 1.16 StandEntityTask paid the cost after the perform and ignored
            // the result: a short bar only drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    HermitPurpleSpec.CRINGE_STAMINA,
                    false);
        }
    }
}
