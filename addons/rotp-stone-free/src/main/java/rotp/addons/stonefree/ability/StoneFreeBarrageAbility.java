package rotp.addons.stonefree.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.stonefree.StoneFreeAddon;

public final class StoneFreeBarrageAbility
        extends StandEntityBarrageAbility {
    public StoneFreeBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Ability shared = super.replaceWithSubAbility(
                context, abilities);
        if (shared != this || abilities == null) {
            return shared;
        }
        StandPower power = PowerClass.STAND.cast(context);
        StandEntity stand =
                power != null ? power.getSummonedStandEntity() : null;
        Ability userString = abilities.getContextVariation(
                StoneFreeAddon.USER_STRING_SWEEP_NAME);
        if (userString == null) {
            return this;
        }
        // 1.16 StoneFreeBarrage.replaceAction: a Stand busy with another
        // task gives the sweep even when it cannot leave that task; the sweep
        // then goes off at once in the user's Stand-power slot.
        // An open capture keeps the barrage (1.16 followUpAvailable).
        if (stand == null
                || stand.isArmsOnlyMode()
                || StoneFreeStringAbility.busyWithOtherTask(stand, this)
                        && StoneFreeStringPunchAbility.captureFollowUp(
                                context, abilities) == null) {
            return userString;
        }
        return this;
    }
}
