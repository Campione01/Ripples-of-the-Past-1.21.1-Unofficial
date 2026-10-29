package rotp.addons.ovatheworld.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

// 1.16 standSound(DIO_OVA_DIE): a Stand sound at PERFORM start, set as
// heavyPunchPerformSound in OvaTheWorldAddon, not a user voice line.
public final class OvaHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    public OvaHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }
}
