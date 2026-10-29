package rotp.addons.stonefree.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;

public final class StoneFreeChargedHeavyAbility
        extends StandEntityHeavyPunchChargedAbility {
    private static final ActionAnimIdentifier RELEASE_ANIM =
            ActionAnimIdentifier.getOrCreate("heavy_punch", false);

    public StoneFreeChargedHeavyAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        if (action != null && (action.getPhase() == ActionPhase.PERFORM
                || action.getPhase() == ActionPhase.RECOVERY)) {
            // The dedicated charged clip contains only the held windup pose.
            return RELEASE_ANIM;
        }
        return super.getEntityAnim(action);
    }
}
