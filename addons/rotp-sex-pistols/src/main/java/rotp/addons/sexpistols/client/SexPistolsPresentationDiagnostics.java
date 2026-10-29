package rotp.addons.sexpistols.client;

public final class SexPistolsPresentationDiagnostics {
    public static final int SCHEMA_VERSION = 1;
    public static final boolean USES_POST_CHAIN = false;
    public static final boolean USES_FRAMEBUFFER = false;
    public static final boolean REWRITES_GLOBAL_RENDERER = false;

    private static long sequence;
    private static long fovFrames;
    private static float lastAimZoomModifier = 1.0F;
    private static double lastComputedFov;
    private static long clientRecoilReceipts;
    private static long clientRecoilTicks;
    private static float lastClientRecoilPitch;
    private static float lastClientRecoilYaw;
    private static long revolverPoseFrames;
    private static float lastReloadOpenProgress;
    private static int lastRenderedChamber;
    private static boolean lastFirstPersonPose;
    private static boolean lastAimingPose;
    private static boolean lastReloadPose;
    private static long hudFrames;
    private static long hudMemberIcons;
    private static long hudRemappedMemberIcons;
    private static long trajectoryFrames;
    private static long trajectoryPaths;
    private static long trajectorySegments;
    private static long trajectoryRicochets;
    private static long trajectoryMemberRedirects;
    private static long activeTrailFrames;
    private static int lastActiveTrailPoints;
    private static double lastActiveTrailDistance;
    private static long afterimagesCreated;
    private static long afterimageFrames;
    private static int liveAfterimages;
    private static long trailCleanups;
    private static String lastEvent = "";

    private SexPistolsPresentationDiagnostics() {}

    public static synchronized Snapshot snapshot() {
        return new Snapshot(
                SCHEMA_VERSION,
                USES_POST_CHAIN,
                USES_FRAMEBUFFER,
                REWRITES_GLOBAL_RENDERER,
                sequence,
                fovFrames,
                lastAimZoomModifier,
                lastComputedFov,
                clientRecoilReceipts,
                clientRecoilTicks,
                lastClientRecoilPitch,
                lastClientRecoilYaw,
                revolverPoseFrames,
                lastReloadOpenProgress,
                lastRenderedChamber,
                lastFirstPersonPose,
                lastAimingPose,
                lastReloadPose,
                hudFrames,
                hudMemberIcons,
                hudRemappedMemberIcons,
                trajectoryFrames,
                trajectoryPaths,
                trajectorySegments,
                trajectoryRicochets,
                trajectoryMemberRedirects,
                activeTrailFrames,
                lastActiveTrailPoints,
                lastActiveTrailDistance,
                afterimagesCreated,
                afterimageFrames,
                liveAfterimages,
                trailCleanups,
                lastEvent);
    }

    public static synchronized void clearReceipts() {
        sequence = 0L;
        fovFrames = 0L;
        lastAimZoomModifier = 1.0F;
        lastComputedFov = 0.0D;
        clientRecoilReceipts = 0L;
        clientRecoilTicks = 0L;
        lastClientRecoilPitch = 0.0F;
        lastClientRecoilYaw = 0.0F;
        revolverPoseFrames = 0L;
        lastReloadOpenProgress = 0.0F;
        lastRenderedChamber = 0;
        lastFirstPersonPose = false;
        lastAimingPose = false;
        lastReloadPose = false;
        hudFrames = 0L;
        hudMemberIcons = 0L;
        hudRemappedMemberIcons = 0L;
        trajectoryFrames = 0L;
        trajectoryPaths = 0L;
        trajectorySegments = 0L;
        trajectoryRicochets = 0L;
        trajectoryMemberRedirects = 0L;
        activeTrailFrames = 0L;
        lastActiveTrailPoints = 0;
        lastActiveTrailDistance = 0.0D;
        afterimagesCreated = 0L;
        afterimageFrames = 0L;
        liveAfterimages = 0;
        trailCleanups = 0L;
        lastEvent = "";
    }

    static synchronized void recordFov(
            float zoomModifier, double fov) {
        fovFrames++;
        lastAimZoomModifier = zoomModifier;
        lastComputedFov = fov;
        event("aim_fov");
    }

    static synchronized void recordClientRecoil(
            float pitch, float yaw) {
        clientRecoilReceipts++;
        lastClientRecoilPitch = pitch;
        lastClientRecoilYaw = yaw;
        event("client_recoil_received");
    }

    static synchronized void recordClientRecoilTick(
            float pitch, float yaw) {
        clientRecoilTicks++;
        lastClientRecoilPitch = pitch;
        lastClientRecoilYaw = yaw;
        event("client_recoil_tick");
    }

    static synchronized void recordRevolverPose(
            boolean firstPerson,
            boolean aiming,
            boolean reload,
            float reloadOpenProgress,
            int chamber) {
        revolverPoseFrames++;
        lastFirstPersonPose = firstPerson;
        lastAimingPose = aiming;
        lastReloadPose = reload;
        lastReloadOpenProgress = reloadOpenProgress;
        lastRenderedChamber = chamber;
        event("revolver_pose");
    }

    static synchronized void recordHud(
            int memberIcons, int remappedIcons) {
        hudFrames++;
        hudMemberIcons += Math.max(0, memberIcons);
        hudRemappedMemberIcons += Math.max(0, remappedIcons);
        event("revolver_hud");
    }

    static synchronized void recordTrajectory(
            int paths,
            int segments,
            int ricochets,
            int memberRedirects) {
        trajectoryFrames++;
        trajectoryPaths += Math.max(0, paths);
        trajectorySegments += Math.max(0, segments);
        trajectoryRicochets += Math.max(0, ricochets);
        trajectoryMemberRedirects += Math.max(0, memberRedirects);
        event("trajectory_preview");
    }

    static synchronized void recordActiveTrail(
            int points, double distance) {
        activeTrailFrames++;
        lastActiveTrailPoints = Math.max(0, points);
        lastActiveTrailDistance = Math.max(0.0D, distance);
        event("bullet_trail");
    }

    static synchronized void recordAfterimageCreated(int points) {
        afterimagesCreated++;
        liveAfterimages++;
        lastActiveTrailPoints = Math.max(0, points);
        event("bullet_afterimage_created");
    }

    static synchronized void recordAfterimageFrame(int count) {
        afterimageFrames++;
        liveAfterimages = Math.max(0, count);
        event("bullet_afterimage_frame");
    }

    static synchronized void recordTrailCleanup(
            int activeTrails, int afterimages) {
        trailCleanups++;
        liveAfterimages = 0;
        event("bullet_trail_cleanup_"
                + Math.max(0, activeTrails)
                + "_"
                + Math.max(0, afterimages));
    }

    private static void event(String event) {
        sequence++;
        lastEvent = event;
    }

    public record Snapshot(
            int schemaVersion,
            boolean usesPostProcessingChain,
            boolean usesFramebuffer,
            boolean rewritesGlobalRenderer,
            long sequence,
            long fovFrames,
            float lastAimZoomModifier,
            double lastComputedFov,
            long clientRecoilReceipts,
            long clientRecoilTicks,
            float lastClientRecoilPitch,
            float lastClientRecoilYaw,
            long revolverPoseFrames,
            float lastReloadOpenProgress,
            int lastRenderedChamber,
            boolean lastFirstPersonPose,
            boolean lastAimingPose,
            boolean lastReloadPose,
            long hudFrames,
            long hudMemberIcons,
            long hudRemappedMemberIcons,
            long trajectoryFrames,
            long trajectoryPaths,
            long trajectorySegments,
            long trajectoryRicochets,
            long trajectoryMemberRedirects,
            long activeTrailFrames,
            int lastActiveTrailPoints,
            double lastActiveTrailDistance,
            long afterimagesCreated,
            long afterimageFrames,
            int liveAfterimages,
            long trailCleanups,
            String lastEvent) {}
}
