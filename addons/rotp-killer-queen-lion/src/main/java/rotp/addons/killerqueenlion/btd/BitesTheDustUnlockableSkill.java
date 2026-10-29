package rotp.addons.killerqueenlion.btd;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandUnlockableSkill;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;

/** Bites the Dust is awakened by a Stand Arrow, not learned in the skill menu. */
public final class BitesTheDustUnlockableSkill
        extends StandUnlockableSkill {
    public BitesTheDustUnlockableSkill() {
        super(KillerQueenLionAddon.TIME_MARK_NAME);
        withAbility(
                KillerQueenLionAddon.TIME_MARK_NAME,
                KillerQueenLionAddon.RESET_DAY_NAME,
                KillerQueenLionAddon.REMOVE_MARK_NAME,
                KillerQueenLionAddon.PUT_DUST_NAME,
                KillerQueenLionAddon.QUIT_DUST_NAME);
        setHidden();
    }

    @Override
    public ConditionCheck canUnlockFromMenu(
            Power<?> userPower, PowerData data) {
        return ConditionCheck.createNegative(
                "killer_queen_btd_requires_stand_arrow");
    }
}
