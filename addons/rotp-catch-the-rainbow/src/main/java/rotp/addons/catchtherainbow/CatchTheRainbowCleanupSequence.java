package rotp.addons.catchtherainbow;

/** Runs every teardown step before propagating any cleanup failure. */
public final class CatchTheRainbowCleanupSequence {
    private CatchTheRainbowCleanupSequence() {}

    public static void runUnconditional(Runnable... steps) {
        Throwable failure = null;
        for (Runnable step : steps) {
            try {
                step.run();
            }
            catch (RuntimeException | Error stepFailure) {
                if (failure == null) {
                    failure = stepFailure;
                }
                else {
                    failure.addSuppressed(stepFailure);
                }
            }
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
