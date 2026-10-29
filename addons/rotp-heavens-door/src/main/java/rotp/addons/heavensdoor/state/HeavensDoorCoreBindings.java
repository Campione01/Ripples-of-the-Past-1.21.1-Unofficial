package rotp.addons.heavensdoor.state;

import rotp.core.api.control.PlayerOperation;
import rotp.core.api.control.PlayerOperationDecision;
import rotp.core.api.control.PlayerOperationPolicies;
import rotp.core.api.leap.LeapAccessPolicies;
import rotp.core.api.rps.RpsCheatRegistrations;
import rotp.core.api.rps.RpsCheatSpec;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class HeavensDoorCoreBindings {
    private static boolean initialized;

    private HeavensDoorCoreBindings() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        PlayerOperationPolicies.register(
                HeavensDoorAddon.id("forgotten_player_operations"),
                query -> {
                    PlayerOperation operation = query.operation();
                    if (isMenuOperation(operation)
                            && HeavensDoorRestrictionRules
                                    .blocksContainerOpen(
                                            query.player())) {
                        return PlayerOperationDecision.deny(
                                Component.translatable(
                                        "message.rotp_hd."
                                                + "forgot_use_containers"));
                    }
                    if (isCraftingOperation(operation)
                            && HeavensDoorRestrictionRules
                                    .blocksCraftingResult(
                                            query.player())) {
                        return PlayerOperationDecision.deny(
                                Component.translatable(
                                        "message.rotp_hd."
                                                + "forgot_craft"));
                    }
                    return PlayerOperationDecision.pass();
                });

        RpsCheatRegistrations.registerStand(
                HeavensDoorAddon.id("rps_mind_read"),
                HeavensDoorAddon.HEAVENS_DOOR::get,
                RpsCheatSpec.mindRead(
                        () -> SoundEvents.VILLAGER_WORK_LIBRARIAN));

        LeapAccessPolicies.register(
                HeavensDoorAddon.id("forgotten_leap_access"),
                query -> HeavensDoorRestrictionRules
                        .blocksPoweredLeap(query.user()));
    }

    private static boolean isMenuOperation(
            PlayerOperation operation) {
        return operation == PlayerOperation.MENU_OPEN_STANDARD
                || operation
                        == PlayerOperation.MENU_OPEN_AS_NON_PLAYER
                || operation == PlayerOperation.MENU_OPEN_EXTERNAL;
    }

    private static boolean isCraftingOperation(
            PlayerOperation operation) {
        return operation
                        == PlayerOperation.CRAFT_RESULT_RECOMPUTE
                || operation == PlayerOperation.CRAFT_RESULT_TAKE;
    }
}
