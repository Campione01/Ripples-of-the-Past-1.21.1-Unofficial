package rotp.addons.tickettoride.state;

import java.util.Map;
import java.util.WeakHashMap;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.tickettoride.TicketToRideAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public final class TearsWoundService {
    // Per level, the latest expiry a loaded entity's mark may have, so that
    // the per-tick mob searches can stop at once in a level with no mark.
    // Keyed by level, holding no entity.
    private static final Map<Level, MarkWindow> MARK_WINDOWS =
            new WeakHashMap<>();

    private static final class MarkWindow {
        private long until = Long.MIN_VALUE;
        private long scannedAt = Long.MIN_VALUE;
    }

    private TearsWoundService() {}

    public static LivingEntity stateTarget(LivingEntity target) {
        if (target instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return target;
    }

    public static boolean mark(
            LivingEntity rawTarget,
            LivingEntity owner,
            int durationTicks) {
        LivingEntity target = stateTarget(rawTarget);
        if (target == null
                || owner == null
                || target.level().isClientSide()
                || !target.isAlive()
                || durationTicks <= 0) {
            return false;
        }
        long expiresAt =
                target.level().getGameTime() + durationTicks;
        TearsWoundState state =
                TicketToRideAddon.woundState(target);
        state.mark(owner.getUUID(), expiresAt);
        TicketToRideAddon.syncWoundState(target);
        openMarkWindow(target.level(), expiresAt);
        return true;
    }

    // A mark loaded from disk or brought from another dimension.
    public static void onJoin(LivingEntity entity) {
        if (isMarked(entity)) {
            openMarkWindow(
                    entity.level(),
                    TicketToRideAddon.woundState(entity).expiresAt());
        }
    }

    /**
     * False only when no loaded entity of this server level can be marked.
     * Once a tick while a mark may exist, the level's entities are checked
     * again, so the window closes the tick after the last mark is gone.
     */
    public static boolean anyMarked(Level level) {
        MarkWindow window = MARK_WINDOWS.get(level);
        long now = level.getGameTime();
        if (window == null || now > window.until) {
            return false;
        }
        if (window.scannedAt != now
                && level instanceof ServerLevel serverLevel) {
            window.scannedAt = now;
            window.until = Long.MIN_VALUE;
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && isMarked(living)) {
                    window.until = Math.max(
                            window.until,
                            TicketToRideAddon.woundState(living)
                                    .expiresAt());
                }
            }
        }
        return now <= window.until;
    }

    // A single-player server outlives the worlds it runs, so a level that
    // unloads leaves the map at once instead of whenever a collection runs.
    // Only the server thread touches the map.
    public static void onLevelUnload(LevelAccessor level) {
        if (!level.isClientSide()) {
            MARK_WINDOWS.remove(level);
        }
    }

    /** Whether a mark window is held for this level. */
    public static boolean holdsWindow(Level level) {
        return MARK_WINDOWS.containsKey(level);
    }

    private static void openMarkWindow(Level level, long expiresAt) {
        MarkWindow window = MARK_WINDOWS.computeIfAbsent(
                level, key -> new MarkWindow());
        window.until = Math.max(window.until, expiresAt);
    }

    // 1.16 TearsBlade.isEntityMarked read the entity's own data, so a Stand
    // never counted as carrying its user's mark.
    public static boolean isMarked(LivingEntity target) {
        if (target == null
                || !target.hasData(
                        TicketToRideAddon.TEARS_WOUND_STATE)) {
            return false;
        }
        TearsWoundState state =
                TicketToRideAddon.woundState(target);
        return state.active()
                && target.level().getGameTime()
                        <= state.expiresAt();
    }

    public static boolean tick(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)
                || !target.hasData(
                        TicketToRideAddon.TEARS_WOUND_STATE)) {
            return false;
        }
        TearsWoundState state =
                TicketToRideAddon.woundState(target);
        if (!state.active()) {
            return false;
        }
        if (!target.isAlive()
                || target.level().getGameTime()
                        > state.expiresAt()) {
            return clear(target);
        }
        return true;
    }

    public static boolean clear(LivingEntity rawTarget) {
        LivingEntity target = stateTarget(rawTarget);
        if (target == null || target.level().isClientSide()) {
            return false;
        }
        boolean changed =
                target.hasData(
                        TicketToRideAddon.TEARS_WOUND_STATE)
                && TicketToRideAddon.woundState(target).clear();
        if (changed) {
            TicketToRideAddon.syncWoundState(target);
        }
        return changed;
    }
}
