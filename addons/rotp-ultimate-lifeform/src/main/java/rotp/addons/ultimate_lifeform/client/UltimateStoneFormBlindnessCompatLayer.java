package rotp.addons.ultimate_lifeform.client;

import rotp.core.api.client.render.ClientRenderCompatibility;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;

/** Restores vanilla Blindness presentation when a shader pack omits its fog. */
public final class UltimateStoneFormBlindnessCompatLayer
        implements LayeredDraw.Layer {
    private static final ResourceLocation VIGNETTE =
            UltimateLifeformAddon.id(
                    "textures/gui/stone_form_blindness.png");

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || !minecraft.player.hasEffect(MobEffects.BLINDNESS)
                || !ClientRenderCompatibility.snapshot()
                        .irisShaderPackInUse()) {
            return;
        }

        PlayerPower power = PlayerPower.get(minecraft.player);
        if (power == null
                || power.getPowerType()
                        != UltimateLifeformAddon
                                .ULTIMATE_LIFEFORM.get()) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        try {
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(
                    VIGNETTE,
                    0,
                    0,
                    -90,
                    0.0F,
                    0.0F,
                    width,
                    height,
                    width,
                    height);
        }
        finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }
}
