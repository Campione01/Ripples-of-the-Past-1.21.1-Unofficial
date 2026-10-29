package rotp.addons.catchtherainbow.ability;

import java.util.Locale;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.impl.stands.theworld.TimeStopAbility;

import net.minecraft.network.chat.Component;

public class RainStopAbility extends TimeStopAbility {
    public RainStopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        resolveLevelToUnlock(1);
        // 1.16 built Rain Stop as a TheWorldTimeStop: a hit during the
        // charge stops it.
        setCancelHeldOnGettingAttacked();
        // Its builder also called ignoresPerformerStun().
        setIgnoresPerformerStun();
    }

    // TheWorldTimeStop: the 30-tick charge takes 20 from Resolve 4.
    @Override
    protected boolean shortensChargeAtResolveFour(StandPower power) {
        return true;
    }

    @Override
    public Component getName(Power<?> context) {
        StandPower power = PowerClass.STAND.cast(context);
        float seconds =
                TimeStopLearning.getTimeStopTicks(power) / 20.0F;
        return Component.translatable(
                "rotp_ctr.ability.catch_the_rainbow_rain_stop",
                String.format(
                        Locale.ROOT, "%.2f", seconds));
    }
}
