package rotp.addons.tusk.ability;

import java.util.EnumSet;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.state.TuskActRules;
import rotp.addons.tusk.state.TuskPortalOperations;
import net.minecraft.server.level.ServerPlayer;

final class TuskActTransition {
    private TuskActTransition() {}

    static boolean isUnlocked(
            int currentAct, int targetAct, int resolveLevel) {
        return TuskActRules.isActUnlocked(
                currentAct, targetAct, resolveLevel);
    }

    static boolean replace(ServerPlayer player, int targetAct) {
        if (!player.isAlive() || player.isRemoved()) {
            return false;
        }

        StandPower power = StandPower.get(player);
        if (power == null) {
            return false;
        }
        int currentAct = TuskAddon.actIndex(power.getPowerType());
        if (currentAct < TuskAddon.ACT_1
                || targetAct < TuskAddon.ACT_1
                || targetAct > TuskAddon.ACT_4
                || targetAct == currentAct
                || !isUnlocked(
                        currentAct,
                        targetAct,
                        power.getResolveLevel())) {
            return false;
        }

        StandInstance current = power.getStandInstance().orElse(null);
        if (current == null
                || TuskAddon.actIndex(current.getStandType()) != currentAct) {
            return false;
        }

        StandType targetType = TuskAddon.standForAct(targetAct);
        if (targetType == null) {
            return false;
        }
        StandInstance replacement = new StandInstance(targetType);
        EnumSet<StandPart> presentParts = current.getAllParts();
        for (StandPart part : StandPart.values()) {
            if (!presentParts.contains(part)) {
                replacement.removePart(part);
            }
        }
        // Each fixed Act owns a separate skin package. Without an explicit
        // cross-Act mapping, the replacement intentionally uses its default.

        boolean wasSummoned = power.isSummoned();
        // 1.16 kept one Tusk Stand whose act was a capability field, so the resolve level survived
        // every act change; the port stores resolve per Act type, so carry the level across.
        // The context overload keeps the Resolve value too (the legacy one resets it).
        int resolveLevel = power.getResolveLevel();
        StandPowerTransitions.Result result =
                StandPowerTransitions.replace(
                        power,
                        current.getStandId(),
                        replacement,
                        new StandPowerTransitions.TransitionContext(
                                TuskAddon.id("act_transition"),
                                player));
        if (!result.applied()) {
            return false;
        }
        if (power.getResolveLevel() < resolveLevel) {
            power.setResolveLevel(resolveLevel);
        }
        if (wasSummoned) {
            targetType.summon(player, power);
        }
        if (currentAct >= TuskAddon.ACT_3
                && targetAct < TuskAddon.ACT_3) {
            TuskPortalOperations.get(player.server)
                    .closeOwned(player.getUUID());
        }
        return true;
    }
}
