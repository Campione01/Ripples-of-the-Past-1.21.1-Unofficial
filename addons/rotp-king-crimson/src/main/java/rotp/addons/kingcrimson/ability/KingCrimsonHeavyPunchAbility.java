package rotp.addons.kingcrimson.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

public final class KingCrimsonHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    public KingCrimsonHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Ability sharedVariation =
                super.replaceWithSubAbility(context, abilities);
        if (sharedVariation != this) {
            return sharedVariation;
        }

        StandPower standPower = PowerClass.STAND.cast(context);
        StandEntity stand = standPower != null
                ? standPower.getSummonedStandEntity()
                : null;
        if (stand == null || stand.isArmsOnlyMode()) {
            return abilities.getContextVariationOrDisable(
                    name(), "kingcrimson_groundslam");
        }
        return this;
    }
}
