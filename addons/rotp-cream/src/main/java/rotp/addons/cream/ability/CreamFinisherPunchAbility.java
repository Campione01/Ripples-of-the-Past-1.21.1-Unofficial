package rotp.addons.cream.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** cream_finisher_punch: 1.16 CreamDoublePunchFinisher.getStandWindupTicks returned 10 whatever the Stand's speed. */
public final class CreamFinisherPunchAbility
        extends StandEntityHeavyPunchAbility {
    public static final int WINDUP_TICKS = 10;

    public CreamFinisherPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        if (!level.isClientSide()) {
            action.phasesLength.put(
                    ActionPhase.WINDUP, (float) WINDUP_TICKS);
        }
    }
}
