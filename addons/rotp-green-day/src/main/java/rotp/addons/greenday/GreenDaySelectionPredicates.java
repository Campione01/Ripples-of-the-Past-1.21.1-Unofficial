package rotp.addons.greenday;

public final class GreenDaySelectionPredicates {
    private GreenDaySelectionPredicates() {}

    public static boolean donorGreenSelection(
            String abilityName, boolean moldActive) {
        return "mold".equals(abilityName) && moldActive;
    }
}
