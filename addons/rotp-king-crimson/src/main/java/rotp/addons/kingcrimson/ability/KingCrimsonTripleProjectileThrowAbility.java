package rotp.addons.kingcrimson.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

public final class KingCrimsonTripleProjectileThrowAbility
        extends KingCrimsonProjectileThrowAbility {
    public KingCrimsonTripleProjectileThrowAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(abilityType, abilityId, 3);
        // 1.16 SHIFT variation of the throw: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
    }
}
