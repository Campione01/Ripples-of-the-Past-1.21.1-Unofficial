package rotp.addons.kingcrimson.client;

import net.minecraft.client.Minecraft;
import rotp.core.api.client.render.AddonPostEffect;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@OnlyIn(Dist.CLIENT)
public final class KingCrimsonTemporalPostEffect {
    static final ResourceLocation TIME_SKIP =
            KingCrimsonAddon.id(
                    KingCrimsonPhase5Spec.TIME_SKIP_POST_CHAIN_PATH);

    private static final AddonPostEffect EFFECT =
            AddonPostEffect.register(
                    KingCrimsonAddon.id("time_skip"),
                    TIME_SKIP,
                    RenderLevelStageEvent.Stage.AFTER_LEVEL);

    private KingCrimsonTemporalPostEffect() {}

    // The desaturation is the port's own addition to the Time Skip; it follows the flash's 1.16 audience, the
    // skipping player and nearby players whose own Stand is King Crimson.
    public static void sync() {
        Minecraft minecraft = Minecraft.getInstance();
        EFFECT.setActive(
                KingCrimsonTemporalClientState.timeSkipActive()
                        && minecraft.player != null
                        && KingCrimsonAddon.isKingCrimsonUser(minecraft.player));
    }
}
