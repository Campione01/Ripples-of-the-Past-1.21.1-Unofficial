package rotp.addons.ger.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

/*
 * No Life Shot of its own: the core Life Shot takes the key of whichever
 * heavy attack the running Life Shot punch is the finisher variation of,
 * and only while the key is that punch, as 1.16's follow-up did.
 */
public final class GerStandHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    public GerStandHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return GerActionAnimations.resolve(
                getAbilityId(), super.getEntityAnim(action));
    }
}
