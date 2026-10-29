package rotp.addons.thelock.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.thelock.state.LockRules;
import rotp.addons.thelock.TheLockAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class CheckTargetAbility extends Ability {
    public static final float STAMINA_COST = 100.0F;

    public CheckTargetAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 check_target did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        return TheLockTargeting.owningUser(context) == null
                ? ConditionCheck.NEGATIVE
                : ConditionCheck.POSITIVE;
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput) {
        if (level.isClientSide() || !TheLockAddon.isTheLockUser(user)) {
            return;
        }
        LivingEntity rawTarget =
                TheLockTargeting.raytraceRawTarget(user);
        if (!(rawTarget instanceof Player target)) {
            if (user instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable(
                                "rotp_tl.message.player_target_required"),
                        true);
            }
            return;
        }
        // 1.16 StandAction.onPerform paid first with the result ignored;
        // a short bar just drops to 0.
        StandAbilityStamina.consume(
                this, StandPower.get(user), STAMINA_COST, false);

        int valuableSlots = 0;
        for (int slot = 0;
                slot < target.getInventory().getContainerSize();
                slot++) {
            if (LockRules.isValuable(
                    target.getInventory().getItem(slot))) {
                valuableSlots++;
            }
        }
        if (user instanceof Player player) {
            player.displayClientMessage(
                    Component.translatable(
                            "rotp_tl.message.valuable_slots",
                            target.getDisplayName(),
                            valuableSlots),
                    true);
        }
    }
}
