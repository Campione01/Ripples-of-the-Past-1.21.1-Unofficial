package rotp.addons.killerqueen.client;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.btd.BitesTheDustService;
import rotp.addons.killerqueen.state.KillerQueenState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

public final class BitesTheDustCountdownOverlay
        implements LayeredDraw.Layer {
    private static final ResourceLocation TEXTURE_PAYLOAD =
            KillerQueenAddon.id(
                    "textures/misc/bites_za_dust.bin");
    private static final int TEXTURE_SIZE = 256;
    private static final Logger LOGGER = LogUtils.getLogger();

    private static long renderedFrames;
    private static int lastRenderedTicks = -1;
    @Nullable
    private static ResourceLocation runtimeTexture;
    private static boolean textureLoadAttempted;

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.options.hideGui) {
            return;
        }
        KillerQueenState state =
                KillerQueenAddon.state(minecraft.player);
        int triggerTicks = state.bitesTheDustTriggerTicks();
        if (!state.hasBitesTheDustTrigger()
                || triggerTicks
                        > BitesTheDustService.TRIGGER_DELAY_TICKS) {
            return;
        }
        ResourceLocation texture = texture(minecraft);
        if (texture == null) {
            return;
        }

        int x = (graphics.guiWidth() - TEXTURE_SIZE) / 2;
        // every pixel of this image is semi-transparent (at most 75%); without blending it drew as a solid block
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(
                texture,
                x,
                0,
                0,
                0,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
        RenderSystem.disableBlend();
        renderedFrames++;
        lastRenderedTicks = triggerTicks;
    }

    public static long renderedFrames() {
        return renderedFrames;
    }

    public static int lastRenderedTicks() {
        return lastRenderedTicks;
    }

    @Nullable
    private static ResourceLocation texture(Minecraft minecraft) {
        if (runtimeTexture != null || textureLoadAttempted) {
            return runtimeTexture;
        }
        textureLoadAttempted = true;
        Resource resource = minecraft.getResourceManager()
                .getResource(TEXTURE_PAYLOAD)
                .orElse(null);
        if (resource == null) {
            LOGGER.error(
                    "Missing Bites the Dust countdown texture payload {}",
                    TEXTURE_PAYLOAD);
            return null;
        }
        NativeImage image = null;
        try (InputStream stream = resource.open()) {
            image = NativeImage.read(stream);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            runtimeTexture = minecraft.getTextureManager().register(
                    "rotp_zkq_bites_the_dust", dynamicTexture);
            image = null;
            return runtimeTexture;
        }
        catch (IOException | RuntimeException error) {
            if (image != null) {
                image.close();
            }
            LOGGER.error(
                    "Could not load Bites the Dust countdown texture",
                    error);
            return null;
        }
    }
}
