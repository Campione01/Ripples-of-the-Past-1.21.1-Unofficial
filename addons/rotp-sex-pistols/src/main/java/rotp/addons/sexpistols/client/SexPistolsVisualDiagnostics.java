package rotp.addons.sexpistols.client;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Read-only receipts for automated client visual acceptance. */
public final class SexPistolsVisualDiagnostics {
    public static final int SCHEMA_VERSION = 1;
    public static final int MUZZLE_CUSTOM_PARTICLES_PER_PAYLOAD = 3;
    public static final int MUZZLE_LIFETIME_TICKS = 2;
    public static final int CASING_LIFETIME_MIN_TICKS = 34;
    public static final int CASING_LIFETIME_MAX_TICKS = 49;
    public static final int BULLET_HOLE_LIFETIME_MIN_TICKS = 280;
    public static final int BULLET_HOLE_LIFETIME_MAX_TICKS = 399;
    public static final String MUZZLE_PAYLOAD_ID =
            SexPistolsIds.MOD_ID + ":"
                    + SexPistolsIds.REVOLVER_MUZZLE_FLASH_PATH;
    public static final String CASING_PAYLOAD_ID =
            SexPistolsIds.MOD_ID + ":"
                    + SexPistolsIds.REVOLVER_CASING_EJECT_PATH;
    public static final String BULLET_HOLE_PARTICLE_ID =
            SexPistolsIds.MOD_ID + ":"
                    + SexPistolsIds.BULLET_HOLE_PATH;
    public static final String CASING_PARTICLE_ID =
            SexPistolsIds.MOD_ID + ":"
                    + SexPistolsIds.REVOLVER_CASING_PATH;
    public static final String MUZZLE_PARTICLE_ID =
            SexPistolsIds.MOD_ID + ":"
                    + SexPistolsIds.REVOLVER_MUZZLE_FLASH_PATH;
    public static final String KICK_PAYLOAD_ID =
            SexPistolsIds.MOD_ID + ":"
                    + SexPistolsIds.KICK_MUZZLE_FLASH_PATH;
    public static final String KICK_PARTICLE_ID = KICK_PAYLOAD_ID;
    public static final String KICK_CRITICAL_PARTICLE_ID =
            SexPistolsIds.MOD_ID + ":"
                    + SexPistolsIds.KICK_MUZZLE_FLASH_CRITICAL_PATH;
    public static final boolean USES_VANILLA_MUZZLE_SUBSTITUTE = false;
    public static final boolean USES_VANILLA_IMPACT_SUBSTITUTE = false;
    public static final boolean CLIENT_CREATES_CASING_ITEMS = false;

    private static long sequence;
    private static long muzzleCustomPayloadReceipts;
    private static long muzzleCustomSpawnReceipts;
    private static long muzzleCustomRenderReceipts;
    private static long muzzleCustomRemovedReceipts;
    private static int muzzleLiveParticles;
    private static long muzzleSupplementalCritSpawns;
    private static long muzzleSupplementalSmokeSpawns;
    private static long casingCustomPayloadReceipts;
    private static long casingCustomSpawnReceipts;
    private static long casingCustomRenderReceipts;
    private static long casingCustomRemovedReceipts;
    private static int casingLiveParticles;
    private static long bulletHoleCustomSpawnReceipts;
    private static long bulletHoleCustomRenderReceipts;
    private static long bulletHoleCustomRemovedReceipts;
    private static long bulletHoleBlockInvalidationReceipts;
    private static long bulletHoleChunkInvalidationReceipts;
    private static int bulletHoleLiveParticles;
    private static int lastShooterEntityId = -1;
    private static int lastCasingCount;
    private static int lastCasingLifetime;
    private static int lastBulletHoleLifetime;
    private static int lastBulletHoleBlockX;
    private static int lastBulletHoleBlockY;
    private static int lastBulletHoleBlockZ;
    private static boolean lastMuzzleAiming;
    private static String lastMuzzleAnchorMode = "";
    private static String lastMuzzleCameraMode = "";
    private static double lastMuzzleAnchorX;
    private static double lastMuzzleAnchorY;
    private static double lastMuzzleAnchorZ;
    private static String lastBulletHoleFace = "";
    private static String lastBulletHoleCleanupReason = "";
    private static String lastEvent = "";
    private static long kickCustomPayloadReceipts;
    private static long kickCustomSpawnReceipts;
    private static long kickCustomRenderReceipts;
    private static long kickCustomRemovedReceipts;
    private static long kickNormalSpawnReceipts;
    private static long kickCriticalSpawnReceipts;
    private static long kickSupplementalCritSpawns;
    private static long kickSupplementalSmokeSpawns;
    private static long kickPendingQueuedReceipts;
    private static long kickPendingConsumedReceipts;
    private static long kickPendingExpiredReceipts;
    private static int kickLiveParticles;
    private static boolean lastKickBulletAnchor;
    private static boolean lastKickCritical;
    private static String lastKickVariant = "";
    private static String lastKickAnchorMode = "";
    private static String lastKickCameraMode = "";
    private static double lastKickAnchorX;
    private static double lastKickAnchorY;
    private static double lastKickAnchorZ;

    private SexPistolsVisualDiagnostics() {}

    public static synchronized Snapshot localSnapshot() {
        return snapshot();
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(
                SCHEMA_VERSION,
                sequence,
                MUZZLE_PAYLOAD_ID,
                CASING_PAYLOAD_ID,
                MUZZLE_PARTICLE_ID,
                CASING_PARTICLE_ID,
                BULLET_HOLE_PARTICLE_ID,
                USES_VANILLA_MUZZLE_SUBSTITUTE,
                USES_VANILLA_IMPACT_SUBSTITUTE,
                CLIENT_CREATES_CASING_ITEMS,
                muzzleCustomPayloadReceipts,
                muzzleCustomSpawnReceipts,
                muzzleCustomRenderReceipts,
                muzzleCustomRemovedReceipts,
                muzzleLiveParticles,
                muzzleSupplementalCritSpawns,
                muzzleSupplementalSmokeSpawns,
                casingCustomPayloadReceipts,
                casingCustomSpawnReceipts,
                casingCustomRenderReceipts,
                casingCustomRemovedReceipts,
                casingLiveParticles,
                bulletHoleCustomSpawnReceipts,
                bulletHoleCustomRenderReceipts,
                bulletHoleCustomRemovedReceipts,
                bulletHoleBlockInvalidationReceipts,
                bulletHoleChunkInvalidationReceipts,
                bulletHoleLiveParticles,
                lastShooterEntityId,
                lastCasingCount,
                lastCasingLifetime,
                lastBulletHoleLifetime,
                lastBulletHoleBlockX,
                lastBulletHoleBlockY,
                lastBulletHoleBlockZ,
                lastMuzzleAiming,
                lastMuzzleAnchorMode,
                lastMuzzleCameraMode,
                lastMuzzleAnchorX,
                lastMuzzleAnchorY,
                lastMuzzleAnchorZ,
                lastBulletHoleFace,
                lastBulletHoleCleanupReason,
                lastEvent,
                KICK_PAYLOAD_ID,
                KICK_PARTICLE_ID,
                KICK_CRITICAL_PARTICLE_ID,
                kickCustomPayloadReceipts,
                kickCustomSpawnReceipts,
                kickCustomRenderReceipts,
                kickCustomRemovedReceipts,
                kickNormalSpawnReceipts,
                kickCriticalSpawnReceipts,
                kickSupplementalCritSpawns,
                kickSupplementalSmokeSpawns,
                kickPendingQueuedReceipts,
                kickPendingConsumedReceipts,
                kickPendingExpiredReceipts,
                kickLiveParticles,
                lastKickBulletAnchor,
                lastKickCritical,
                lastKickVariant,
                lastKickAnchorMode,
                lastKickCameraMode,
                lastKickAnchorX,
                lastKickAnchorY,
                lastKickAnchorZ);
    }

    public static synchronized void clear() {
        sequence = 0L;
        muzzleCustomPayloadReceipts = 0L;
        muzzleCustomSpawnReceipts = 0L;
        muzzleCustomRenderReceipts = 0L;
        muzzleCustomRemovedReceipts = 0L;
        muzzleLiveParticles = 0;
        muzzleSupplementalCritSpawns = 0L;
        muzzleSupplementalSmokeSpawns = 0L;
        casingCustomPayloadReceipts = 0L;
        casingCustomSpawnReceipts = 0L;
        casingCustomRenderReceipts = 0L;
        casingCustomRemovedReceipts = 0L;
        casingLiveParticles = 0;
        bulletHoleCustomSpawnReceipts = 0L;
        bulletHoleCustomRenderReceipts = 0L;
        bulletHoleCustomRemovedReceipts = 0L;
        bulletHoleBlockInvalidationReceipts = 0L;
        bulletHoleChunkInvalidationReceipts = 0L;
        bulletHoleLiveParticles = 0;
        lastShooterEntityId = -1;
        lastCasingCount = 0;
        lastCasingLifetime = 0;
        lastBulletHoleLifetime = 0;
        lastBulletHoleBlockX = 0;
        lastBulletHoleBlockY = 0;
        lastBulletHoleBlockZ = 0;
        lastMuzzleAiming = false;
        lastMuzzleAnchorMode = "";
        lastMuzzleCameraMode = "";
        lastMuzzleAnchorX = 0.0D;
        lastMuzzleAnchorY = 0.0D;
        lastMuzzleAnchorZ = 0.0D;
        lastBulletHoleFace = "";
        lastBulletHoleCleanupReason = "";
        lastEvent = "";
        kickCustomPayloadReceipts = 0L;
        kickCustomSpawnReceipts = 0L;
        kickCustomRenderReceipts = 0L;
        kickCustomRemovedReceipts = 0L;
        kickNormalSpawnReceipts = 0L;
        kickCriticalSpawnReceipts = 0L;
        kickSupplementalCritSpawns = 0L;
        kickSupplementalSmokeSpawns = 0L;
        kickPendingQueuedReceipts = 0L;
        kickPendingConsumedReceipts = 0L;
        kickPendingExpiredReceipts = 0L;
        kickLiveParticles = 0;
        lastKickBulletAnchor = false;
        lastKickCritical = false;
        lastKickVariant = "";
        lastKickAnchorMode = "";
        lastKickCameraMode = "";
        lastKickAnchorX = 0.0D;
        lastKickAnchorY = 0.0D;
        lastKickAnchorZ = 0.0D;
    }

    public static synchronized void clearReceipts() {
        clear();
    }

    static synchronized void recordMuzzlePayload(
            int shooterEntityId, boolean aiming) {
        muzzleCustomPayloadReceipts++;
        lastShooterEntityId = shooterEntityId;
        lastMuzzleAiming = aiming;
        event("muzzle_custom_payload");
    }

    static synchronized void recordMuzzleAnchor(
            Vec3 anchor, boolean aiming, boolean firstPerson) {
        lastMuzzleAiming = aiming;
        lastMuzzleAnchorMode = aiming ? "AIMED" : "HIP";
        lastMuzzleCameraMode = firstPerson
                ? "FIRST_PERSON" : "THIRD_PERSON";
        lastMuzzleAnchorX = anchor.x;
        lastMuzzleAnchorY = anchor.y;
        lastMuzzleAnchorZ = anchor.z;
        event("muzzle_anchor");
    }

    static synchronized void recordMuzzleSpawn() {
        muzzleCustomSpawnReceipts++;
        muzzleLiveParticles++;
        event("muzzle_custom_spawn");
    }

    static synchronized void recordMuzzleSupplementalSpawns() {
        muzzleSupplementalCritSpawns++;
        muzzleSupplementalSmokeSpawns++;
        event("muzzle_supplemental_spawn");
    }

    static synchronized void recordMuzzleRender() {
        muzzleCustomRenderReceipts++;
        event("muzzle_custom_render");
    }

    static synchronized void recordMuzzleRemoved() {
        muzzleCustomRemovedReceipts++;
        muzzleLiveParticles = Math.max(0, muzzleLiveParticles - 1);
        event("muzzle_custom_removed");
    }

    static synchronized void recordCasingPayload(
            int shooterEntityId, int count) {
        casingCustomPayloadReceipts++;
        lastShooterEntityId = shooterEntityId;
        lastCasingCount = count;
        event("casing_custom_payload");
    }

    static synchronized void recordCasingSpawn(int lifetime) {
        casingCustomSpawnReceipts++;
        casingLiveParticles++;
        lastCasingLifetime = lifetime;
        event("casing_custom_spawn");
    }

    static synchronized void recordCasingRender() {
        casingCustomRenderReceipts++;
        event("casing_custom_render");
    }

    static synchronized void recordCasingRemoved() {
        casingCustomRemovedReceipts++;
        casingLiveParticles = Math.max(0, casingLiveParticles - 1);
        event("casing_custom_removed");
    }

    static synchronized void recordBulletHoleSpawn(
            Direction face, BlockPos blockPos, int lifetime) {
        bulletHoleCustomSpawnReceipts++;
        bulletHoleLiveParticles++;
        lastBulletHoleFace = face.name();
        lastBulletHoleLifetime = lifetime;
        lastBulletHoleBlockX = blockPos.getX();
        lastBulletHoleBlockY = blockPos.getY();
        lastBulletHoleBlockZ = blockPos.getZ();
        event("bullet_hole_custom_spawn");
    }

    static synchronized void recordBulletHoleRender() {
        bulletHoleCustomRenderReceipts++;
        event("bullet_hole_custom_render");
    }

    static synchronized void recordBulletHoleRemoved(String reason) {
        bulletHoleCustomRemovedReceipts++;
        bulletHoleLiveParticles = Math.max(
                0, bulletHoleLiveParticles - 1);
        lastBulletHoleCleanupReason = reason;
        if ("BLOCK_CHANGED".equals(reason)) {
            bulletHoleBlockInvalidationReceipts++;
        }
        else if ("CHUNK_UNLOADED".equals(reason)) {
            bulletHoleChunkInvalidationReceipts++;
        }
        event("bullet_hole_custom_removed");
    }

    static synchronized void recordKickPayload(
            int memberEntityId,
            boolean bulletAnchor,
            boolean critical) {
        kickCustomPayloadReceipts++;
        lastShooterEntityId = memberEntityId;
        lastKickBulletAnchor = bulletAnchor;
        lastKickCritical = critical;
        lastKickVariant = critical ? "CRITICAL" : "NORMAL";
        event("kick_custom_payload");
    }

    static synchronized void recordKickAnchor(
            Vec3 anchor,
            boolean bulletAnchor,
            boolean firstPerson,
            boolean critical) {
        lastKickBulletAnchor = bulletAnchor;
        lastKickCritical = critical;
        lastKickVariant = critical ? "CRITICAL" : "NORMAL";
        lastKickAnchorMode = bulletAnchor
                ? "REVOLVER_BULLET" : "GENERIC_PROJECTILE";
        lastKickCameraMode = firstPerson
                ? "FIRST_PERSON" : "THIRD_PERSON";
        lastKickAnchorX = anchor.x;
        lastKickAnchorY = anchor.y;
        lastKickAnchorZ = anchor.z;
        event("kick_anchor");
    }

    static synchronized void recordKickSpawn(boolean critical) {
        kickCustomSpawnReceipts++;
        kickLiveParticles++;
        lastKickCritical = critical;
        lastKickVariant = critical ? "CRITICAL" : "NORMAL";
        if (critical) {
            kickCriticalSpawnReceipts++;
        }
        else {
            kickNormalSpawnReceipts++;
        }
        event("kick_custom_spawn");
    }

    static synchronized void recordKickSupplementalSpawns() {
        kickSupplementalCritSpawns++;
        kickSupplementalSmokeSpawns++;
        event("kick_supplemental_spawn");
    }

    static synchronized void recordKickRender() {
        kickCustomRenderReceipts++;
        event("kick_custom_render");
    }

    static synchronized void recordKickRemoved() {
        kickCustomRemovedReceipts++;
        kickLiveParticles = Math.max(0, kickLiveParticles - 1);
        event("kick_custom_removed");
    }

    static synchronized void recordKickPendingQueued() {
        kickPendingQueuedReceipts++;
        event("kick_pending_queued");
    }

    static synchronized void recordKickPendingConsumed() {
        kickPendingConsumedReceipts++;
        event("kick_pending_consumed");
    }

    static synchronized void recordKickPendingExpired() {
        kickPendingExpiredReceipts++;
        event("kick_pending_expired");
    }

    static synchronized void recordLevelUnloadCleanup() {
        muzzleCustomRemovedReceipts += muzzleLiveParticles;
        casingCustomRemovedReceipts += casingLiveParticles;
        kickCustomRemovedReceipts += kickLiveParticles;
        bulletHoleCustomRemovedReceipts += bulletHoleLiveParticles;
        if (bulletHoleLiveParticles > 0) {
            bulletHoleChunkInvalidationReceipts +=
                    bulletHoleLiveParticles;
            lastBulletHoleCleanupReason = "LEVEL_UNLOADED";
        }
        muzzleLiveParticles = 0;
        casingLiveParticles = 0;
        kickLiveParticles = 0;
        bulletHoleLiveParticles = 0;
        event("client_level_unload_cleanup");
    }

    private static void event(String name) {
        sequence++;
        lastEvent = name;
    }

    public record Snapshot(
            int schemaVersion,
            long sequence,
            String muzzlePayloadId,
            String casingPayloadId,
            String muzzleParticleId,
            String casingParticleId,
            String bulletHoleParticleId,
            boolean usesVanillaMuzzleSubstitute,
            boolean usesVanillaImpactSubstitute,
            boolean clientCreatesCasingItems,
            long muzzleCustomPayloadReceipts,
            long muzzleCustomSpawnReceipts,
            long muzzleCustomRenderReceipts,
            long muzzleCustomRemovedReceipts,
            int muzzleLiveParticles,
            long muzzleSupplementalCritSpawns,
            long muzzleSupplementalSmokeSpawns,
            long casingCustomPayloadReceipts,
            long casingCustomSpawnReceipts,
            long casingCustomRenderReceipts,
            long casingCustomRemovedReceipts,
            int casingLiveParticles,
            long bulletHoleCustomSpawnReceipts,
            long bulletHoleCustomRenderReceipts,
            long bulletHoleCustomRemovedReceipts,
            long bulletHoleBlockInvalidationReceipts,
            long bulletHoleChunkInvalidationReceipts,
            int bulletHoleLiveParticles,
            int lastShooterEntityId,
            int lastCasingCount,
            int lastCasingLifetime,
            int lastBulletHoleLifetime,
            int lastBulletHoleBlockX,
            int lastBulletHoleBlockY,
            int lastBulletHoleBlockZ,
            boolean lastMuzzleAiming,
            String lastMuzzleAnchorMode,
            String lastMuzzleCameraMode,
            double lastMuzzleAnchorX,
            double lastMuzzleAnchorY,
            double lastMuzzleAnchorZ,
            String lastBulletHoleFace,
            String lastBulletHoleCleanupReason,
            String lastEvent,
            String kickPayloadId,
            String kickParticleId,
            String kickCriticalParticleId,
            long kickCustomPayloadReceipts,
            long kickCustomSpawnReceipts,
            long kickCustomRenderReceipts,
            long kickCustomRemovedReceipts,
            long kickNormalSpawnReceipts,
            long kickCriticalSpawnReceipts,
            long kickSupplementalCritSpawns,
            long kickSupplementalSmokeSpawns,
            long kickPendingQueuedReceipts,
            long kickPendingConsumedReceipts,
            long kickPendingExpiredReceipts,
            int kickLiveParticles,
            boolean lastKickBulletAnchor,
            boolean lastKickCritical,
            String lastKickVariant,
            String lastKickAnchorMode,
            String lastKickCameraMode,
            double lastKickAnchorX,
            double lastKickAnchorY,
            double lastKickAnchorZ) {}
}
