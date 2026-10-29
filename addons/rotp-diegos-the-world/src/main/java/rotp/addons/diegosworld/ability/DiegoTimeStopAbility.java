package rotp.addons.diegosworld.ability;

import java.util.Locale;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.impl.stands.theworld.TimeStopAbility;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class DiegoTimeStopAbility extends TimeStopAbility {
    public DiegoTimeStopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    protected int getRequiredResolveLevelToUnlock(
            Power<?> context) {
        return 1;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        action.phasesLength.put(
                ActionPhase.BUTTON_CHARGE,
                0);
    }

    @Override
    public Component getName(Power<?> context) {
        StandPower power = PowerClass.STAND.cast(context);
        float seconds =
                TimeStopLearning.getTimeStopTicks(power) / 20.0F;
        return Component.translatable(
                "rotp_theworld.ability.time_stop",
                String.format(Locale.ROOT, "%.2f", seconds));
    }
}
