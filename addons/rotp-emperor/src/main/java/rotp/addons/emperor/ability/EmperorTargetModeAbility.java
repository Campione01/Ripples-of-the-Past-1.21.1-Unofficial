package rotp.addons.emperor.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;

import rotp.addons.emperor.EmperorAddon;
import rotp.addons.emperor.item.EmperorItem;
import rotp.addons.emperor.state.EmperorMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class EmperorTargetModeAbility extends Ability {
    public static final int RESOLVE_LEVEL = 1;

    public EmperorTargetModeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        // 1.16 target did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        return activePlayer(context) != null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "emperor_not_summoned");
    }

    @Override
    public Component getName(Power<?> context) {
        return Component.translatable(
                "rotp_zemperor.ability.target."
                        + mode(context).name().toLowerCase(
                                java.util.Locale.ROOT));
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return switch (mode(context)) {
            case NONE -> "target_none";
            case HOSTILE -> "target_hostile";
            case PLAYERS_AND_STANDS -> "target_player";
            case ANY_LIVING -> "target";
        };
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()
                || !(user instanceof Player player)
                || activePlayerPower(player) == null) {
            return;
        }
        EmperorMode next = EmperorAddon.modeState(player).mode()
                .cycle(player.isShiftKeyDown());
        EmperorAddon.modeState(player).setMode(next);
        EmperorAddon.syncMode(player);
    }

    static Player activePlayer(Power<?> context) {
        return context != null
                && context.getUser() instanceof Player player
                && activePlayerPower(player) != null
                ? player
                : null;
    }

    private static rotp.core.powersystem.standpower
            .StandPower activePlayerPower(Player player) {
        rotp.core.powersystem.standpower.StandPower power =
                rotp.core.powersystem.standpower
                        .StandPower.get(player);
        return power != null
                && power.getPowerType() == EmperorAddon.THE_EMPEROR.get()
                && power.isSummoned()
                && EmperorAddon.controller(player) != null
                && EmperorItem.isOwnedGunInHand(player)
                ? power
                : null;
    }

    private static EmperorMode mode(Power<?> context) {
        return context != null
                && context.getUser() instanceof Player player
                ? EmperorAddon.modeState(player).mode()
                : EmperorMode.NONE;
    }
}
