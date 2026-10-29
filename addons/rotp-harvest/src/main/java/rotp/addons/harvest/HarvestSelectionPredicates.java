package rotp.addons.harvest;

import java.util.UUID;

public final class HarvestSelectionPredicates {
    private HarvestSelectionPredicates() {}

    public static boolean donorGreenSelection(
            String abilityName,
            boolean closeFormation,
            boolean carryUpActive) {
        return (HarvestAddon.BE_CLOSER_NAME.equals(abilityName)
                        && closeFormation)
                || (HarvestAddon.CARRY_UP_NAME.equals(abilityName)
                        && carryUpActive);
    }

    public static boolean isValidMemberSlot(
            int slot, int memberCap) {
        return slot >= 0 && slot < memberCap;
    }

    public static boolean memberUuidMatches(
            boolean clientSide,
            UUID expected,
            UUID actual) {
        return clientSide || actual.equals(expected);
    }
}
