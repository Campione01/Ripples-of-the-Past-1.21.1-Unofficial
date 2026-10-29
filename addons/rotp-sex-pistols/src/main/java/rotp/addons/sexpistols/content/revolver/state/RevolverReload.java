package rotp.addons.sexpistols.content.revolver.state;

public final class RevolverReload {
    private RevolverReload() {}

    public static int selectedLiveChamber(RevolverState state) {
        int selected = state.selectedChamber();
        return state.chamber(selected) == RevolverState.Chamber.LIVE
                ? selected
                : -1;
    }

    public static int selectionOnOpen(
            int selectedChamber,
            int firstEmptyChamber,
            boolean preserveSelectedChamber) {
        return !preserveSelectedChamber && firstEmptyChamber >= 0
                ? firstEmptyChamber
                : selectedChamber;
    }

    public static int selectionAfterLoad(
            int selectedChamber,
            int nextEmptyChamber,
            int lastLoadedChamber,
            boolean preserveSelectedChamber) {
        if (preserveSelectedChamber) {
            return selectedChamber;
        }
        return nextEmptyChamber >= 0
                ? nextEmptyChamber
                : Math.floorMod(
                        lastLoadedChamber + 1,
                        RevolverState.CHAMBER_COUNT);
    }
}
