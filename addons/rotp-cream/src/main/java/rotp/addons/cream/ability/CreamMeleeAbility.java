package rotp.addons.cream.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.cream.CreamConfig;
import rotp.addons.cream.entity.CreamEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class CreamMeleeAbility
        extends StandEntityPunchAbility {
    private final Style style;

    public CreamMeleeAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Style style) {
        super(abilityType, abilityId);
        this.style = style;
        if (style == Style.CHOP) {
            resolveLevelToUnlock(2);
        }
    }

    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        if (style == Style.CHOP) {
            // 1.16 CreamChop set this timer directly with setCooldownTimer, only for a user not in Creative, so
            // Resolve never shortened it and no technical part was left for Creative.
            cooldown(0, CreamConfig.CHOP_COOLDOWN.get(), 1.0F);
        }
        return super.getCooldown(context, ticksHeld);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        return CreamEntity.isInVoidState(context.getUser())
                ? ConditionCheck.NEGATIVE
                : super.checkSpecificConditions(context);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 CreamChop: getStandWindupTicks 8, the light attack's perform,
        // and getStandRecoveryTicks = getHeavyAttackRecovery(attack speed, last
        // heavy finisher value)
        if (style == Style.CHOP) {
            action.phasesLength.put(ActionPhase.WINDUP, 8.0F);
            if (!level.isClientSide()
                    && performer instanceof StandEntity stand) {
                action.phasesLength.put(ActionPhase.RECOVERY,
                        StandStatFormulas.getHeavyAttackRecovery(
                                stand.getAttackSpeed(),
                                stand.getLastHeavyFinisherValue()));
            }
        }
    }

    public enum Style {
        PUNCH,
        CLAW,
        CHOP
    }
}
