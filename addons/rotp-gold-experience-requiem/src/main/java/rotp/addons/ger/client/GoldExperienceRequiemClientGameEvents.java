package rotp.addons.ger.client;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(
        modid = GoldExperienceRequiemAddon.MOD_ID,
        value = Dist.CLIENT)
public final class GoldExperienceRequiemClientGameEvents {
    private GoldExperienceRequiemClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        GerDisablerPostEffect.syncFromLevel();
        if (net.minecraft.client.Minecraft
                        .getInstance().level == null) {
            GerRtzClientState.reset();
            return;
        }
        GerRtzClientState.tick();
        GerReturnToZeroPostEffect.tick();
    }

    @SubscribeEvent
    public static void onPlaySoundSource(
            PlaySoundSourceEvent event) {
        if (event.getSound() instanceof GerReturnToZeroSound) {
            GerRtzDiagnostics.recordLoopSoundPlayback(
                    event.getSound());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureDisablerScreenTarget(
            RenderLevelStageEvent event) {
        GerDisablerPostEffect.captureScreenTarget(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderLateRtzGeometry(
            RenderLevelStageEvent event) {
        GerRtzNormalRenderer.render(event);
        GerRtzGhostTrailRenderer.render(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void computeRtzFogColor(
            ViewportEvent.ComputeFogColor event) {
        GerRtzGoldenFog.applyColor(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderRtzFog(
            ViewportEvent.RenderFog event) {
        GerRtzGoldenFog.applyDensity(event);
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        GerRtzClientState.reset();
        GerDisablerClientState.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            GerRtzClientState.reset();
        }
    }

    @SubscribeEvent
    public static void registerClientCommands(
            RegisterClientCommandsEvent event) {
        GerRtzVfxClientCommand.register(event);
    }
}
