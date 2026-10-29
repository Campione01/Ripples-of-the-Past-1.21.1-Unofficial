package rotp.addons.metallica.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

import rotp.addons.metallica.MetallicaPhase1Spec;

public final class MetallicaMultipleKnifeThrowAbility
        extends MetallicaKnifeThrowAbility {
    public static final int PROJECTILE_COUNT = 5;

    public MetallicaMultipleKnifeThrowAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                PROJECTILE_COUNT,
                MetallicaPhase1Spec
                        .MULTIPLE_SCALPEL_STAMINA,
                MetallicaPhase1Spec
                        .MULTIPLE_SCALPEL_COOLDOWN_TICKS);
    }
}
