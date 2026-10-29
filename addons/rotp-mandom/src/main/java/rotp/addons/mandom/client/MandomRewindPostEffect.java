package rotp.addons.mandom.client;

import java.util.List;
import java.util.Set;

import rotp.core.api.client.render.AddonPostEffect;

import rotp.addons.mandom.MandomAddon;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@OnlyIn(Dist.CLIENT)
public final class MandomRewindPostEffect {
    private static final ResourceLocation DEFAULT =
            MandomAddon.id("shaders/post/mandom.json");
    private static final List<ResourceLocation> ROUTES = List.of(
            DEFAULT,
            skinRoute("acid"),
            skinRoute("gray"),
            skinRoute("purple"));
    private static final AddonPostEffect EFFECT =
            AddonPostEffect.registerRoutes(
                    MandomAddon.id("time_rewind"),
                    Set.copyOf(ROUTES),
                    DEFAULT,
                    RenderLevelStageEvent.Stage.AFTER_LEVEL);

    private static int ticksRemaining;
    private static ResourceLocation currentRoute = DEFAULT;

    private MandomRewindPostEffect() {}

    public static void trigger(int ticks, int route) {
        ResourceLocation selected = ROUTES.get(
                Math.max(0, Math.min(route, ROUTES.size() - 1)));
        if (!selected.equals(currentRoute)) {
            EFFECT.setPostChainLocation(selected);
            currentRoute = selected;
        }
        ticksRemaining = Math.max(ticksRemaining, Math.max(0, ticks));
        EFFECT.setActive(ticksRemaining > 0);
    }

    public static void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
        EFFECT.setActive(ticksRemaining > 0);
    }

    public static void reset() {
        ticksRemaining = 0;
        EFFECT.setActive(false);
    }

    private static ResourceLocation skinRoute(String skin) {
        return MandomAddon.id(
                "stand_skins/" + skin
                        + "/assets/rotp_mandom/shaders/post/mandom.json");
    }
}
