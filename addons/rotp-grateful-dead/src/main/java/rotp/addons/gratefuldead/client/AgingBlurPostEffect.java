package rotp.addons.gratefuldead.client;

import java.util.Set;

import rotp.core.api.client.render.AddonPostEffect;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@OnlyIn(Dist.CLIENT)
public final class AgingBlurPostEffect {
    static final ResourceLocation LIGHT =
            GratefulDeadAddon.id(
                    "shaders/post/aging_blur_light.json");
    static final ResourceLocation MEDIUM =
            GratefulDeadAddon.id(
                    "shaders/post/aging_blur_medium.json");
    static final ResourceLocation STRONG =
            GratefulDeadAddon.id(
                    "shaders/post/aging_blur_strong.json");

    private static final AddonPostEffect EFFECT =
            AddonPostEffect.registerRoutes(
                    GratefulDeadAddon.id("aging_blur"),
                    Set.of(LIGHT, MEDIUM, STRONG),
                    LIGHT,
                    RenderLevelStageEvent.Stage.AFTER_LEVEL);

    private static int currentBlurLevel;

    private AgingBlurPostEffect() {}

    public static void syncFromLocalPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null) {
            setBlurLevel(0);
            return;
        }

        float progress = minecraft.player.hasData(
                        GratefulDeadAddon.LIVING_AGING)
                ? LivingAgingState.get(
                                minecraft.player)
                        .progress()
                : 0.0F;
        setBlurLevel(
                AgingBlurStages.levelForProgress(progress));
    }

    private static void setBlurLevel(int level) {
        if (level <= 0) {
            currentBlurLevel = 0;
            EFFECT.setActive(false);
            return;
        }

        if (currentBlurLevel != level) {
            EFFECT.setPostChainLocation(
                    routeForLevel(level));
            currentBlurLevel = level;
        }
        EFFECT.setActive(true);
    }

    private static ResourceLocation routeForLevel(int level) {
        return switch (level) {
            case 3 -> STRONG;
            case 2 -> MEDIUM;
            default -> LIGHT;
        };
    }
}
