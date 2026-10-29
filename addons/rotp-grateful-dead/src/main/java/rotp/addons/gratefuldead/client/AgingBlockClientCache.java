package rotp.addons.gratefuldead.client;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.service.AgingService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Thread-safe read side for section-build workers and main-thread updates.
 */
public final class AgingBlockClientCache {
    public static final float SMOOTH_STEP_PER_TICK = 0.025F;
    public static final float RENDER_THRESHOLD = 0.04F;
    public static final float SNAP_TO_TARGET_EPSILON = 0.001F;
    private static final float AURA_VISUAL_FLOOR = 0.12F;
    private static final float AURA_FIELD_ONSET_STEP_PER_TICK = 0.01F;
    private static final float AURA_FIELD_AGING_STEP_PER_TICK =
            0.00041666668F;
    private static final float AURA_FIELD_FADE_STEP_PER_TICK = 0.02F;
    private static final float AURA_FIELD_RENDER_THRESHOLD = 0.03F;
    private static final double AURA_EDGE_SOFTNESS = 8.0D;
    private static final int AURA_CHUNK_RERENDERS_PER_TICK = 12;

    private static final Map<BlockPos, Entry> ENTRIES =
            new ConcurrentHashMap<>();
    private static final Map<UUID, AuraField> AURA_FIELDS =
            new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Long>
            AURA_RERENDER_QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<Long> AURA_RERENDER_QUEUED =
            ConcurrentHashMap.newKeySet();

    private AgingBlockClientCache() {}

    public static void handleUpdate(
            BlockPos position, float progress) {
        if (position == null) {
            return;
        }
        BlockPos key = position.immutable();
        float clamped = progress <= 0.0F
                ? 0.0F
                : Math.min(progress, 1.0F);
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            if (clamped <= 0.0F) {
                return;
            }
            Entry created = new Entry();
            Entry existing = ENTRIES.putIfAbsent(key, created);
            entry = existing != null ? existing : created;
        }
        entry.target = clamped;
        if (entry.display == 0.0F
                && clamped > 0.0F
                && clamped < 0.05F) {
            entry.display = clamped;
            renderIfNeeded(key, entry);
        }
    }

    public static float getProgress(BlockPos position) {
        if (position == null) {
            return 0.0F;
        }
        Entry entry = ENTRIES.get(position);
        float tracked = entry != null ? entry.display : 0.0F;
        return Math.max(
                tracked, getAuraVisualProgress(position));
    }

    public static void clear() {
        ENTRIES.clear();
        AURA_FIELDS.clear();
        AURA_RERENDER_QUEUE.clear();
        AURA_RERENDER_QUEUED.clear();
    }

    public static void tickClient() {
        updateAuraFields();
        tickBlockEntries();
        processAuraRerenderQueue();
    }

    static boolean hasActiveAura(Player player) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        StandPower power = StandPower.get(player);
        return power != null
                && power.userStandEffects
                        .getEffectOfType(
                                GratefulDeadAddon
                                        .AGING_AURA_EFFECT.get())
                        .isPresent();
    }

    static boolean hasWarmActiveAura(Player player) {
        return hasActiveAura(player)
                && !AgingService.isCold(
                        player.level(),
                        player.blockPosition());
    }

    private static void tickBlockEntries() {
        for (Map.Entry<BlockPos, Entry> mapEntry :
                ENTRIES.entrySet()) {
            Entry entry = mapEntry.getValue();
            float difference = entry.target - entry.display;
            if (Math.abs(difference)
                    <= SNAP_TO_TARGET_EPSILON) {
                if (entry.target <= 0.0F) {
                    if (entry.lastRendered > 0.0F) {
                        entry.display = 0.0F;
                        renderIfNeeded(
                                mapEntry.getKey(), entry);
                    }
                    ENTRIES.remove(
                            mapEntry.getKey(), entry);
                }
                continue;
            }
            entry.display =
                    Math.abs(difference)
                                    <= SMOOTH_STEP_PER_TICK
                            ? entry.target
                            : Mth.clamp(
                                    entry.display
                                            + Math.signum(difference)
                                                    * SMOOTH_STEP_PER_TICK,
                                    0.0F,
                                    1.0F);
            renderIfNeeded(mapEntry.getKey(), entry);
        }
    }

    private static void updateAuraFields() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            AURA_FIELDS.clear();
            AURA_RERENDER_QUEUE.clear();
            AURA_RERENDER_QUEUED.clear();
            return;
        }
        Set<UUID> active = ConcurrentHashMap.newKeySet();
        for (Player player : level.players()) {
            if (!hasWarmActiveAura(player)) {
                continue;
            }
            UUID id = player.getUUID();
            active.add(id);
            AuraField field = AURA_FIELDS.computeIfAbsent(
                    id, ignored -> new AuraField());
            updateActiveAuraField(player, field);
        }
        for (Map.Entry<UUID, AuraField> entry :
                AURA_FIELDS.entrySet()) {
            if (active.contains(entry.getKey())) {
                continue;
            }
            AuraField field = entry.getValue();
            float oldDisplay = field.display;
            field.display = Math.max(
                    0.0F,
                    field.display
                            - AURA_FIELD_FADE_STEP_PER_TICK);
            if (shouldRenderAuraField(field, oldDisplay)) {
                enqueueAuraArea(
                        field.x, field.z, field.range);
                field.snappedProgress = field.display;
                field.lastRenderedProgress = field.display;
            }
            if (field.display <= 0.0F
                    && AURA_FIELDS.remove(
                            entry.getKey(), field)) {
                enqueueAuraArea(
                        field.x, field.z, field.range);
            }
        }
    }

    private static void updateActiveAuraField(
            Player player, AuraField field) {
        double oldX = field.x;
        double oldZ = field.z;
        double oldRange = field.range;
        boolean hadPosition = field.initialized;
        float oldDisplay = field.display;
        field.x = player.getX();
        field.y = player.getY();
        field.z = player.getZ();
        field.range = Math.max(
                1.0D, AgingService.AURA_RADIUS);
        field.initialized = true;
        if (hadPosition
                && (chunkCoord(oldX) != chunkCoord(field.x)
                        || chunkCoord(oldZ)
                                != chunkCoord(field.z)
                        || Math.abs(oldRange - field.range)
                                >= 4.0D)) {
            enqueueAuraArea(oldX, oldZ, oldRange);
            enqueueAuraArea(
                    field.x, field.z, field.range);
        }
        field.display = field.display < AURA_VISUAL_FLOOR
                ? Math.min(
                        AURA_VISUAL_FLOOR,
                        field.display
                                + AURA_FIELD_ONSET_STEP_PER_TICK)
                : Math.min(
                        1.0F,
                        field.display
                                + AURA_FIELD_AGING_STEP_PER_TICK);
        if (shouldRenderAuraField(field, oldDisplay)) {
            field.snappedProgress = field.display;
            enqueueAuraArea(
                    field.x, field.z, field.range);
            field.lastRenderedProgress = field.display;
            field.lastRenderedChunkX = chunkCoord(field.x);
            field.lastRenderedChunkZ = chunkCoord(field.z);
            field.lastRenderedRange = field.range;
        }
    }

    private static boolean shouldRenderAuraField(
            AuraField field, float oldDisplay) {
        if (!field.initialized) {
            return false;
        }
        return field.lastRenderedProgress < 0.0F
                || Math.abs(
                                field.display
                                        - field.lastRenderedProgress)
                        >= AURA_FIELD_RENDER_THRESHOLD
                || chunkCoord(field.x)
                        != field.lastRenderedChunkX
                || chunkCoord(field.z)
                        != field.lastRenderedChunkZ
                || Math.abs(
                                field.range
                                        - field.lastRenderedRange)
                        >= 4.0D
                || oldDisplay > 0.0F
                        && field.display <= 0.0F;
    }

    private static float getAuraVisualProgress(
            BlockPos position) {
        if (AURA_FIELDS.isEmpty()) {
            return 0.0F;
        }
        double x = position.getX() + 0.5D;
        double y = position.getY() + 0.5D;
        double z = position.getZ() + 0.5D;
        float strongest = 0.0F;
        for (AuraField field : AURA_FIELDS.values()) {
            float renderValue = field.snappedProgress;
            if (renderValue <= 0.0F
                    || field.range <= 0.0D) {
                continue;
            }
            double dx = x - field.x;
            double dy = y - field.y;
            double dz = z - field.z;
            double distanceSquared =
                    dx * dx + dy * dy + dz * dz;
            double rangeSquared =
                    field.range * field.range;
            if (distanceSquared > rangeSquared) {
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            float edge =
                    distance
                                    <= field.range
                                            - AURA_EDGE_SOFTNESS
                            ? 1.0F
                            : (float) Math.max(
                                    0.0D,
                                    (field.range - distance)
                                            / AURA_EDGE_SOFTNESS);
            strongest = Math.max(
                    strongest, renderValue * edge);
        }
        return Math.min(1.0F, strongest);
    }

    private static void enqueueAuraArea(
            double x, double z, double range) {
        if (range <= 0.0D) {
            return;
        }
        int centerChunkX = chunkCoord(x);
        int centerChunkZ = chunkCoord(z);
        int chunkRadius =
                (int) Math.ceil(range / 16.0D) + 1;
        double inflated = range + 24.0D;
        double inflatedSquared = inflated * inflated;
        for (int dx = -chunkRadius;
                dx <= chunkRadius;
                dx++) {
            for (int dz = -chunkRadius;
                    dz <= chunkRadius;
                    dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                double distanceX =
                        (chunkX << 4) + 8.0D - x;
                double distanceZ =
                        (chunkZ << 4) + 8.0D - z;
                if (distanceX * distanceX
                                + distanceZ * distanceZ
                        <= inflatedSquared) {
                    enqueueChunk(chunkX, chunkZ);
                }
            }
        }
    }

    private static void enqueueChunk(
            int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (AURA_RERENDER_QUEUED.add(key)) {
            AURA_RERENDER_QUEUE.add(key);
        }
    }

    private static void processAuraRerenderQueue() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.levelRenderer == null) {
            return;
        }
        for (int index = 0;
                index < AURA_CHUNK_RERENDERS_PER_TICK;
                index++) {
            Long key = AURA_RERENDER_QUEUE.poll();
            if (key == null) {
                break;
            }
            AURA_RERENDER_QUEUED.remove(key);
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) (long) key;
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            minecraft.levelRenderer.setBlocksDirty(
                    minX,
                    minecraft.level.getMinBuildHeight(),
                    minZ,
                    minX + 15,
                    minecraft.level.getMaxBuildHeight() - 1,
                    minZ + 15);
        }
    }

    private static void renderIfNeeded(
            BlockPos position, Entry entry) {
        boolean crossesZero = entry.lastRendered > 0.0F
                && entry.display <= 0.0F;
        boolean reachesFull = entry.lastRendered < 1.0F
                && entry.display >= 0.999F;
        if (Math.abs(
                                entry.display
                                        - entry.lastRendered)
                        >= RENDER_THRESHOLD
                || crossesZero
                || reachesFull) {
            rerender(position);
            entry.lastRendered = entry.display;
        }
    }

    private static void rerender(BlockPos position) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.levelRenderer == null) {
            return;
        }
        var state = minecraft.level.getBlockState(position);
        minecraft.levelRenderer.setBlockDirty(
                position, state, state);
    }

    private static int chunkCoord(double blockCoordinate) {
        return (int) Math.floor(blockCoordinate) >> 4;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32
                ^ chunkZ & 0xFFFFFFFFL;
    }

    private static final class Entry {
        private volatile float target;
        private volatile float display;
        private volatile float lastRendered;
    }

    private static final class AuraField {
        private volatile double x;
        private volatile double y;
        private volatile double z;
        private volatile double range;
        private volatile float display;
        private volatile float snappedProgress;
        private volatile float lastRenderedProgress = -1.0F;
        private volatile int lastRenderedChunkX;
        private volatile int lastRenderedChunkZ;
        private volatile double lastRenderedRange;
        private volatile boolean initialized;
    }
}
