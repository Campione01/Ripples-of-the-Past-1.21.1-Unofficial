package rotp.addons.heavensdoor.client;

public final class BookScreenSessionRules {
    private BookScreenSessionRules() {}

    public static boolean shouldRemainOpen(
            long clientGameTime,
            long sessionExpiresAt,
            boolean targetLoaded,
            boolean targetAlive,
            double targetDistanceSquared,
            double maxDistance) {
        return clientGameTime <= sessionExpiresAt
                && targetLoaded
                && targetAlive
                && Double.isFinite(targetDistanceSquared)
                && targetDistanceSquared >= 0.0D
                && maxDistance >= 0.0D
                && targetDistanceSquared
                        <= maxDistance * maxDistance;
    }
}
