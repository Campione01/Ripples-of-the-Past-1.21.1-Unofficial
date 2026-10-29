package rotp.addons.kraftwork.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;

import rotp.addons.kraftwork.KraftWorkAddon;
import net.minecraft.network.chat.Component;

public final class KraftWorkGuardAbility
        extends StandEntityBlockAbility {
    public KraftWorkGuardAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // The slot is "guard" for the core; the name and icon stay the
        // kraft_work_block ones every locale and skin carries.
        spriteName = KraftWorkAddon.BLOCK;
        name = Component.translatable(getTranslationKey());
    }

    @Override
    public String getTranslationKey(String postfix) {
        return getResourceNamespace() + ".ability."
                + KraftWorkAddon.BLOCK + postfix;
    }
}
