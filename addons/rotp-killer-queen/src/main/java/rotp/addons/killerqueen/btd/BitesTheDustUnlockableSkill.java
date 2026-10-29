package rotp.addons.killerqueen.btd;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandUnlockableSkill;

import rotp.addons.killerqueen.KillerQueenAddon;

/** Bites the Dust is awakened by a Stand Arrow, not learned in the skill menu. */
public final class BitesTheDustUnlockableSkill
        extends StandUnlockableSkill {
    public BitesTheDustUnlockableSkill() {
        super(KillerQueenAddon.TIME_MARK_NAME);
        withAbility(
                KillerQueenAddon.TIME_MARK_NAME,
                KillerQueenAddon.RESET_DAY_NAME,
                KillerQueenAddon.REMOVE_MARK_NAME,
                KillerQueenAddon.PUT_DUST_NAME,
                KillerQueenAddon.QUIT_DUST_NAME);
        setHidden();
    }

    @Override
    public ConditionCheck canUnlockFromMenu(
            Power<?> userPower, PowerData data) {
        return ConditionCheck.createNegative(
                "killer_queen_btd_requires_stand_arrow");
    }
}
