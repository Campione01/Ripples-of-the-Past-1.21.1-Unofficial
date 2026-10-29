package rotp.addons.killerqueenlion.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.condition.ConditionCheck;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import net.minecraft.world.entity.player.Player;

/**
 * 1.16 rotp_zkq8: while the user has a Bites the Dust victim, checkConditions of the punches, the block and the
 * bomb actions is NEGATIVE with no message (BitesZaDustHandler.userToVictim).
 */
public final class BitesTheDustLock {
    private BitesTheDustLock() {}

    public static boolean applies(@Nullable Power<?> context) {
        return context != null
                && context.getUser() instanceof Player player
                && KillerQueenLionAddon.state(player).hasBitesTheDustVictim();
    }

    // 1.16 tickHeldAction re-ran the KQ action's checkConditions on every
    // held tick, and it asked for nothing but the bound victim
    static ConditionCheck heldCheck(@Nullable Power<?> context) {
        return applies(context)
                ? ConditionCheck.NEGATIVE
                : ConditionCheck.POSITIVE;
    }
}
