package rotp.addons.speedking.ability;

import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.speedking.SpeedKingAddon;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 1.16 099 InitStands :61: example_stand_punch was the plain core StandEntityLightAttack (punchSound
 * STAND_PUNCH_LIGHT, the default): the core light punch, its damage from the Stand's strength, its finisher meter,
 * timing, cancel rules and arm-only summon.
 */
public final class SpeedKingPunchAbility extends StandEntityPunchAbility {
    public SpeedKingPunchAbility(AbilityType<?> type, AbilityId id) {
        super(type, id);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (base.isPositive() && !(context instanceof StandPower power
                && SpeedKingAddon.isUser(power.getUser()))) {
            return ConditionCheck.NEGATIVE;
        }
        return base;
    }

    // Speed King's own clips: punch for the right arm, punch2 for the left
    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        if (action instanceof StandEntityPunch punch
                && action.getPerformer() instanceof StandEntity stand) {
            HumanoidArm side = punch.getPunchingHand() == InteractionHand.MAIN_HAND
                    ? stand.getMainArm() : stand.getMainArm().getOpposite();
            return ActionAnimIdentifier.getOrCreate(
                    side == HumanoidArm.LEFT ? "punch2" : "punch", false);
        }
        return ActionAnimIdentifier.getOrCreate("punch", false);
    }
}
