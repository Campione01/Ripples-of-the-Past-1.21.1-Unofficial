package rotp.addons.sexpistols.content.revolver.entity;

final class RevolverBulletDistanceSafety {
    static final double CHUNK_LOAD_CHECK_DISTANCE_SQR = 96.0D * 96.0D;
    static final double HARD_MAX_DISTANCE_SQR = 192.0D * 192.0D;

    private RevolverBulletDistanceSafety() {}

    static boolean shouldDiscard(
            double ownerDistanceSqr,
            boolean currentChunkLoaded,
            boolean nextChunkLoaded) {
        if (ownerDistanceSqr < CHUNK_LOAD_CHECK_DISTANCE_SQR) {
            return false;
        }
        return ownerDistanceSqr >= HARD_MAX_DISTANCE_SQR
                || !currentChunkLoaded
                || !nextChunkLoaded;
    }
}
