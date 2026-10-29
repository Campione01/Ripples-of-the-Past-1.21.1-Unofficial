package rotp.addons.d4c.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.d4c.D4CPhase2BService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 aa/aa/ae.java: summon_kick was a plain StandAction, not a Stand task. It did not summon the Stand (no
 * autoSummonStand) and did not touch the Stand's own task; the user performed it, paying the stamina first.
 */
public final class D4CSummonKickAbility extends Ability {
    public static final int COOLDOWN_TECHNICAL_TICKS = 60;
    public static final int COOLDOWN_ADDITIONAL_TICKS = 120;
    public static final int RESOLVE_LEVEL = 2;
    public static final float STAMINA_COST = 250.0F;

    public D4CSummonKickAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        usageGroup = AbilityUsageGroup.UTILITY;
        partsRequired(StandPart.ARMS);
        cooldown(COOLDOWN_TECHNICAL_TICKS, COOLDOWN_ADDITIONAL_TICKS, 0.0F);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        // 1.16 summon_kick did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        return D4CPhase2BService.checkSummonKick(user);
    }

    @Override
    public void onClick(
            Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        StandPower power = StandPower.get(user);
        if (power == null) {
            return;
        }
        // 1.16 StandAction.onPerform paid the cost before perform, the
        // result ignored; a short bar just drops to 0.
        StandAbilityStamina.consume(
                this, power, STAMINA_COST, false);
        D4CPhase2BService.summonKick(serverLevel, user);
    }
}
