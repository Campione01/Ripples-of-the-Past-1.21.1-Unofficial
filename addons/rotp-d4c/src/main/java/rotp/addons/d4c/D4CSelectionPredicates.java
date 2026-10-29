package rotp.addons.d4c;

public final class D4CSelectionPredicates {
    private D4CSelectionPredicates() {}

    public static boolean donorGreenSelection(
            String abilityName,
            int ownedMinionCount,
            boolean aimedOwnedMinion,
            boolean conditionPositive) {
        return ("summon_player_minion".equals(abilityName)
                        && ownedMinionCount
                                < D4CPhase2BSpec.MAX_MINIONS)
                || ("swap_with_minion".equals(abilityName)
                        && aimedOwnedMinion)
                || ("summon_kick".equals(abilityName)
                        && conditionPositive);
    }
}
