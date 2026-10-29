package rotp.addons.kingcrimson.client;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class KingCrimsonTemporalClientState {
    private static long newestTimeSkipStartedAt = Long.MIN_VALUE;
    private static long newestTimeSkipExpiresAt = Long.MIN_VALUE;
    private static long timeEraseTransitionStartedAt = Long.MIN_VALUE;
    private static long flashedTimeSkipStartedAt = Long.MIN_VALUE;

    private KingCrimsonTemporalClientState() {}

    public static void sync() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        Player localPlayer = minecraft.player;
        newestTimeSkipStartedAt = Long.MIN_VALUE;
        newestTimeSkipExpiresAt = Long.MIN_VALUE;
        double radiusSqr = KingCrimsonPhase5Spec
                .TIME_SKIP_PRESENTATION_RADIUS
                * KingCrimsonPhase5Spec
                        .TIME_SKIP_PRESENTATION_RADIUS;
        for (Player player : minecraft.level.players()) {
            if (!player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                    || player.distanceToSqr(localPlayer) > radiusSqr) {
                continue;
            }
            KingCrimsonTemporalState state =
                    KingCrimsonAddon.temporalState(player);
            if (state.isSkipping(gameTime)
                    && state.startedAt()
                            > newestTimeSkipStartedAt) {
                newestTimeSkipStartedAt = state.startedAt();
                newestTimeSkipExpiresAt = state.expiresAt();
            }
        }
        // 1.16 showed every Time Skip with the same 375 ms, sixteen-frame wall-clock flash as Time Erase
        // (TimerPutPacket -> TimeSkipHandler). The port had stretched the frames over the skip's 10 game ticks,
        // which dropped six of the sixteen frames and held the opaque one twice as long.
        if (newestTimeSkipStartedAt != Long.MIN_VALUE
                && newestTimeSkipStartedAt != flashedTimeSkipStartedAt) {
            flashedTimeSkipStartedAt = newestTimeSkipStartedAt;
            startTimeEraseTransition();
        }
    }

    public static boolean localTimeErase() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && localTimeErase(minecraft.level);
    }

    static boolean localTimeErase(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;
        return minecraft.level == level
                && localPlayer != null
                && localPlayer.hasData(
                        KingCrimsonAddon.TEMPORAL_STATE)
                && KingCrimsonAddon.temporalState(localPlayer)
                        .isErasing(level.getGameTime());
    }

    public static boolean timeSkipActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && newestTimeSkipStartedAt != Long.MIN_VALUE
                && minecraft.level.getGameTime()
                        < newestTimeSkipExpiresAt;
    }

    public static void startTimeEraseTransition() {
        timeEraseTransitionStartedAt = Util.getMillis();
    }

    public static boolean timeEraseTransitionActive() {
        if (timeEraseTransitionStartedAt == Long.MIN_VALUE) {
            return false;
        }
        long elapsed = Math.max(
                0L,
                Util.getMillis() - timeEraseTransitionStartedAt);
        if (elapsed >= KingCrimsonPhase5Spec
                .TIME_ERASE_TRANSITION_MILLIS) {
            timeEraseTransitionStartedAt = Long.MIN_VALUE;
            return false;
        }
        return true;
    }

    public static int timeEraseTransitionFrame() {
        if (!timeEraseTransitionActive()) {
            return 0;
        }
        long elapsed = Math.max(
                0L,
                Util.getMillis() - timeEraseTransitionStartedAt);
        long remaining = Math.max(
                0L,
                KingCrimsonPhase5Spec.TIME_ERASE_TRANSITION_MILLIS
                        - elapsed);
        return Mth.clamp(
                (int) (remaining
                        / KingCrimsonPhase5Spec
                                .TIME_ERASE_TRANSITION_FRAME_MILLIS),
                0,
                15);
    }

    private static void reset() {
        newestTimeSkipStartedAt = Long.MIN_VALUE;
        newestTimeSkipExpiresAt = Long.MIN_VALUE;
        timeEraseTransitionStartedAt = Long.MIN_VALUE;
        flashedTimeSkipStartedAt = Long.MIN_VALUE;
    }
}
