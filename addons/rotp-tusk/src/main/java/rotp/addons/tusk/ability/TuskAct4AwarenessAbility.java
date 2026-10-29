package rotp.addons.tusk.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

import rotp.addons.tusk.TuskAddon;
import net.minecraft.world.entity.LivingEntity;

public final class TuskAct4AwarenessAbility extends Ability {
    public TuskAct4AwarenessAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        spriteName = "tusk_select_act";
    }

    @Override
    public boolean addToControlSchemeEditing() {
        return false;
    }

    // 1.16 had no Action here (a TimeStopHandler mixin), so it neither
    // ended a held action nor was refused by one.
    @Override
    public boolean endsOtherHoldOnPress() {
        return false;
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return super.isAbilityAvailable(context)
                && context != null
                && context.getUser() != null
                && TuskAddon.isAct4User(context.getUser());
    }

    @Override
    public boolean canUserSeeInStoppedTime(
            LivingEntity user, Power<?> context) {
        return user != null && TuskAddon.isAct4User(user);
    }
}
