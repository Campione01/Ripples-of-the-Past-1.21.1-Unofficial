package rotp.addons.ger.client;

import java.util.UUID;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerRtzVfxSettings.Mode;
import rotp.addons.ger.network.GerRtzPayloads.StartPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class GerRtzClientState {
    private static StartPayload active;
    private static ClientLevel activeLevel;
    private static Mode effectiveMode = Mode.OFF;
    private static int elapsedTicks;

    private GerRtzClientState() {}

    public static void syncMark(
            Player player, UUID owner, boolean available) {
        if (player != null && player.getUUID().equals(owner)) {
            GoldExperienceRequiemAddon.playerState(player)
                    .setReturnToZeroMarkAvailable(available);
        }
    }

    public static void start(StartPayload payload) {
        clearActive();
        active = payload;
        activeLevel = Minecraft.getInstance().level;
        effectiveMode = GerRtzVfxSettings.modeFor(
                payload.mode() == 1);
        elapsedTicks = Math.min(
                payload.elapsedTicks(),
                presentationTotalTicks(payload));
        GerRtzDiagnostics.begin(payload, effectiveMode);
        updateDiagnostics(0.0F);
        if (elapsedTicks < payload.keepTicks()) {
            GerRtzDiagnostics.recordLoopSoundPlayRequest();
            Minecraft.getInstance()
                    .getSoundManager()
                    .play(new GerReturnToZeroSound(payload));
        }
        setLocalPresentationFlag(payload.owner(), true);
    }

    public static void stop(UUID owner) {
        if (active != null && active.owner().equals(owner)) {
            clearActive();
        }
        setLocalPresentationFlag(owner, false);
    }

    public static void tick() {
        if (active == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.level != activeLevel) {
            reset();
            return;
        }
        Player owner = minecraft.level.getPlayerByUUID(
                active.owner());
        Entity source = sourceEntity();
        updateDiagnostics(0.0F);
        if (owner == null
                || !owner.isAlive()
                || source == null
                || !source.isAlive()) {
            reset();
            return;
        }
        elapsedTicks++;
        updateDiagnostics(0.0F);
        if (elapsedTicks >= presentationTotalTicks(active)) {
            stop(active.owner());
        }
    }

    public static StartPayload active() {
        return active;
    }

    public static int elapsedTicks() {
        return elapsedTicks;
    }

    public static Mode activeMode() {
        return active != null ? effectiveMode : Mode.OFF;
    }

    public static float lifecycleAlpha(float partialTick) {
        if (active == null) {
            return 0.0F;
        }
        float elapsed = elapsedTicks + partialTick;
        float alpha = active.fadeInTicks() > 0
                ? Mth.clamp(
                        elapsed / active.fadeInTicks(),
                        0.0F,
                        1.0F)
                : 1.0F;
        float remaining =
                presentationTotalTicks(active) - elapsed;
        float fadeOutStart =
                active.expandTicks() + active.keepTicks();
        if (active.fadeOutTicks() > 0) {
            alpha = Math.min(
                    alpha,
                    Mth.clamp(
                            elapsed >= fadeOutStart
                                    ? remaining
                                            / active.fadeOutTicks()
                                    : 1.0F,
                            0.0F,
                            1.0F));
        }
        return alpha;
    }

    static float normalStormAlpha(float partialTick) {
        if (active == null || activeMode() != Mode.NORMAL) {
            return 0.0F;
        }
        float remaining = presentationTotalTicks(active)
                - (elapsedTicks + partialTick);
        return Mth.clamp(remaining / 5.0F, 0.0F, 1.0F);
    }

    static int presentationTotalTicks(
            StartPayload payload) {
        long ticks = (long) payload.expandTicks()
                + payload.keepTicks()
                + Math.max(
                        payload.closeTicks(),
                        payload.fadeOutTicks());
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(1L, ticks));
    }

    public static boolean isCurrent(StartPayload trigger) {
        return active == trigger;
    }

    public static void reset() {
        clearActive();
    }

    private static void setLocalPresentationFlag(
            UUID owner, boolean active) {
        Player player = Minecraft.getInstance().player;
        if (player != null
                && player.getUUID().equals(owner)) {
            GoldExperienceRequiemAddon.playerState(player)
                    .setReturnToZeroPresentationActive(active);
        }
    }

    static Entity sourceEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        return active != null && minecraft.level != null
                ? minecraft.level.getEntity(
                        active.sourceEntityId())
                : null;
    }

    private static void updateDiagnostics(float partialTick) {
        if (active == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean ownerResolved = minecraft.level != null
                && minecraft.level.getPlayerByUUID(
                        active.owner()) != null;
        boolean sourceResolved = sourceEntity() != null;
        GerRtzDiagnostics.updateState(
                elapsedTicks,
                lifecycleAlpha(partialTick),
                ownerResolved,
                sourceResolved);
    }

    private static void clearActive() {
        if (active != null) {
            GerRtzDiagnostics.recordLoopSoundStopReceipt();
            setLocalPresentationFlag(active.owner(), false);
        }
        active = null;
        activeLevel = null;
        effectiveMode = Mode.OFF;
        elapsedTicks = 0;
        GerReturnToZeroPostEffect.reset();
        GerRtzDiagnostics.end();
    }
}
