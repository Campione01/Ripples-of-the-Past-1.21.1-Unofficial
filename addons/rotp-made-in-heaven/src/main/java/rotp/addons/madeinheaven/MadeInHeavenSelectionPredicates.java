package rotp.addons.madeinheaven;

public final class MadeInHeavenSelectionPredicates {
    private MadeInHeavenSelectionPredicates() {}

    public static boolean donorGreenSelection(
            String abilityName, boolean activeOwner) {
        return activeOwner
                && (MadeInHeavenAddon.TIME_SYSTEM_NAME.equals(
                                abilityName)
                        || MadeInHeavenAddon.TIME_SYSTEM_CLEAR_NAME
                                .equals(abilityName));
    }
}
