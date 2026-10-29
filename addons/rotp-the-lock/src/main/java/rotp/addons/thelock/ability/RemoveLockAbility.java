package rotp.addons.thelock.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.thelock.TheLockAddon;
import rotp.addons.thelock.state.LockStateService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class RemoveLockAbility extends Ability {
    public static final float STAMINA_COST = 100.0F;

    public RemoveLockAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 remove_lock did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        return user == null || !TheLockAddon.isTheLockUser(user)
                ? ConditionCheck.NEGATIVE
                : ConditionCheck.POSITIVE;
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            return;
        }
        if (!TheLockAddon.isTheLockUser(user)) {
            return;
        }
        LivingEntity target =
                TheLockTargeting.raytraceStateTarget(user);
        if (target == null
                || (!TheLockAddon.lockState(target).active()
                        && !target.hasEffect(
                                TheLockAddon.LOCK_EFFECT))) {
            if (user instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable(
                                "rotp_tl.message.no_locked_target"),
                        true);
            }
            return;
        }
        // 1.16 StandAction.onPerform paid first with the result ignored;
        // a short bar just drops to 0.
        StandAbilityStamina.consume(
                this, StandPower.get(user), STAMINA_COST, false);
        LockStateService.clear(target);
    }
}
