package rotp.addons.kingcrimson.client;

import java.util.Set;

import rotp.core.api.client.render.AddonPostEffect;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase3Spec;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@OnlyIn(Dist.CLIENT)
public final class EpitaphPostEffect {
    private static final ResourceLocation POST_CHAIN =
            KingCrimsonAddon.id(
                    KingCrimsonPhase3Spec.EPITAPH_POST_CHAIN_PATH);
    private static final AddonPostEffect EFFECT =
            AddonPostEffect.registerRoutes(
                    KingCrimsonAddon.id("epitaph"),
                    Set.of(POST_CHAIN),
                    POST_CHAIN,
                    RenderLevelStageEvent.Stage.AFTER_LEVEL);

    private EpitaphPostEffect() {}

    public static void syncFromLocalPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = minecraft.level != null
                && minecraft.player != null
                && minecraft.player.hasData(
                        KingCrimsonAddon.EPITAPH_STATE)
                && KingCrimsonAddon.epitaphState(minecraft.player)
                        .isArmed(minecraft.level.getGameTime());
        EFFECT.setActive(active);
    }
}
