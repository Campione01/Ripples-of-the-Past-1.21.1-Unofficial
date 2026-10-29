package rotp.addons.tusk.client;

import com.mojang.blaze3d.systems.RenderSystem;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.tusk.TuskAddon;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = TuskAddon.MOD_ID, value = Dist.CLIENT)
public final class TuskActHud implements LayeredDraw.Layer {
    private static final ResourceLocation SLOT_TEXTURE =
            TuskAddon.id("textures/gui/act_gui.png");
    private static final ResourceLocation[] ACT_ICONS = {
        skinIcon("tusk"),
        skinIcon("tusk_act_2"),
        skinIcon("tusk_act_3"),
        skinIcon("tusk_act_4")
    };
    private static final int SLOT_SIZE = 25;
    private static final int SLOT_SPACING = 30;
    private static final int ICON_SIZE = 16;

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                TuskAddon.id("act_selector"),
                new TuskActHud());
    }

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        StandPower power = StandPower.get(minecraft.player);
        int act = power != null
                ? TuskAddon.actIndex(power.getPowerType())
                : -1;
        if (act < TuskAddon.ACT_1 || act > TuskAddon.ACT_4) {
            return;
        }
        int visibleActs = Math.clamp(
                power.getResolveLevel() + 1, 1, ACT_ICONS.length);
        int width = visibleActs * SLOT_SPACING
                - (SLOT_SPACING - SLOT_SIZE);
        int startX = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 52;
        // the slot frames have semi-transparent pixels; a plain blit does not turn blending on by itself
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int index = 0; index < visibleActs; index++) {
            int x = startX + index * SLOT_SPACING;
            graphics.blit(
                    SLOT_TEXTURE,
                    x,
                    y,
                    0,
                    75,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    128,
                    128);
            graphics.blit(
                    ACT_ICONS[index],
                    x + 4,
                    y + 4,
                    0,
                    0,
                    ICON_SIZE,
                    ICON_SIZE,
                    ICON_SIZE,
                    ICON_SIZE);
            if (index == act) {
                graphics.blit(
                        SLOT_TEXTURE,
                        x,
                        y,
                        25,
                        75,
                        SLOT_SIZE,
                        SLOT_SIZE,
                        128,
                        128);
            }
        }
        RenderSystem.disableBlend();
    }

    private static ResourceLocation skinIcon(String skin) {
        return TuskAddon.id(
                "stand_skins/" + skin
                        + "/assets/rotp_t/textures/stand_icon.png");
    }
}
