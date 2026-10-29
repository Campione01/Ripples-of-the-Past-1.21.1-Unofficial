package rotp.addons.cream.client;

final class CreamHudVisibilityLease {
    private boolean leased;
    private boolean restoreHideGui;

    boolean beginFrame(
            boolean effectActive, boolean currentHideGui) {
        boolean effectiveHideGui = release(currentHideGui);
        if (!effectActive) {
            return effectiveHideGui;
        }

        leased = true;
        restoreHideGui = effectiveHideGui;
        return false;
    }

    boolean endFrame(boolean currentHideGui) {
        return release(currentHideGui);
    }

    boolean reset(boolean currentHideGui) {
        return release(currentHideGui);
    }

    boolean isLeased() {
        return leased;
    }

    private boolean release(boolean currentHideGui) {
        if (!leased) {
            return currentHideGui;
        }

        leased = false;
        if (currentHideGui) {
            return true;
        }
        return restoreHideGui;
    }
}
