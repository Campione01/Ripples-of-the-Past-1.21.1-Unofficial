package rotp.addons.stonefree.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.stonefree.StoneFreeAddon;

public final class StoneFreePunchAbility
        extends StandEntityPunchAbility {
    public StoneFreePunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    /**
     * Donor StoneFreePunch.replaceAction: the user throws a string instead
     * of the Stand punching only while no Stand entity is summoned, or while
     * the summoned Stand is busy with an action that is neither a light punch
     * nor the barrage. An idle summoned Stand keeps its own light punch.
     */
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Ability shared = super.replaceWithSubAbility(
                context, abilities);
        if (shared != this || abilities == null) {
            return shared;
        }
        Ability userString = abilities.getContextVariation(
                StoneFreeAddon.USER_STRING_ATTACK_NAME);
        if (userString == null) {
            return this;
        }
        StandPower power = PowerClass.STAND.cast(context);
        StandEntity stand =
                power != null ? power.getSummonedStandEntity() : null;
        if (stand == null) {
            return userString;
        }
        EntityActionInstance current = stand.getCurStandAction();
        if (current != null
                && !(current.ability instanceof StandEntityPunchAbility)
                && !(current.ability instanceof StoneFreeBarrageAbility)
                // 1.16 followUpAvailable: an open capture keeps the Stand's punch.
                && StoneFreeStringPunchAbility.captureFollowUp(
                        context, abilities) == null) {
            return userString;
        }
        return this;
    }
}
