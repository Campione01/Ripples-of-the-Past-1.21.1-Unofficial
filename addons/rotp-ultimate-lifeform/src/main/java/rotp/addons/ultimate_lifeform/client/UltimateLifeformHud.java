package rotp.addons.ultimate_lifeform.client;

import rotp.core.client.ui.hud_power.Bars;
import rotp.core.client.ui.hud_power.HudElement;
import rotp.core.client.ui.hud_power.PowerHud;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.client.ui.utils.tooltip.MultiLineScreenTooltip;
import rotp.core.compat.v1_21_4.missingmethods.ARGB;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Ultimate Lifeform's energy bar, drawn by the core's ability HUD where it draws the energy of the core's non-Stand
 * powers. 1.16 had no Hamon charge bar: BarsRendererMixin drew the power's energy bar with its border tinted yellow
 * (0xFFFF00, green - 0.2), the border's alpha following the charge ratio while the charge was above 0, and the bars
 * taken from ulf_overlay.png (its border gold) while charged.
 */
public final class UltimateLifeformHud extends HudElement {
    public static final String NAME = "energy_ultimate_lifeform";
    // 1.16 UltimateLifeformPowerType.getColor
    public static final int ENERGY_COLOR = 7693155;
    public static final ResourceLocation ULF_OVERLAY = UltimateLifeformAddon.id("textures/gui/ulf_overlay.png");
    private static final int BORDER_TINT = 0xFFCC00;

    public UltimateLifeformHud() {
        super(NAME, 81, 28, Bars.HORIZONTAL_LENGTH + 8, Bars.HORIZONTAL_WIDTH);
    }

    // the core's ability HUD is made when the GUI layers register; added once it exists
    public static void ensureAdded() {
        PowerHud.AbilityHud hud = PowerHud.abilityHUDInstance;
        if (hud != null && !hud.elements.containsKey(NAME)) {
            hud.addElement(new UltimateLifeformHud());
        }
    }

    // 1.16 BarsRendererMixin.changeColor: the border of every bar of an Ultimate Lifeform user's render (the Stand's
    // stamina bar too) took 0xFFFF00 with green - 0.2, its alpha the charge ratio while the charge was above 0, else
    // the bar's own alpha
    public static int borderTint(float charge, float barAlpha) {
        return ARGB.color(charge > 0.0F ? charge : barAlpha, BORDER_TINT);
    }

    // 1.16 BarsRendererMixin.changeTexture bound ulf_overlay.png at the head of BarsRenderer.render while the user's
    // charge was above 0, and nothing in the render bound another texture, so the energy bar and the Stand's bars all
    // took their border (the only part visibly unlike overlay.png) from it: (0, 128) 202 x 8 for the full bar, (0, 136)
    // 152 x 5 for the shortened one. The border blit of a core horizontal bar, with that tint (BarsBorderTintMixin).
    public static void blitBorder(PoseStack pose, Minecraft mc, ResourceLocation empty, float x, float y, float length,
            float width, float z, int color) {
        UltimateLifeformData data = data();
        if (data == null) {
            BlitFloat.blit(pose, mc, empty, x, y, length, width, z, color);
            return;
        }
        float charge = data.getHamonRatio();
        int tint = borderTint(charge, (color >>> 24) / 255.0F);
        int overlayV = overlayBorderV(length, width);
        if (charge > 0.0F && overlayV >= 0) {
            BlitFloat.blit(pose, mc, ULF_OVERLAY, x, y, length, width, z, 0, overlayV, length, width, 256, 256, tint);
        }
        else {
            BlitFloat.blit(pose, mc, empty, x, y, length, width, z, tint);
        }
    }

    // the row of ulf_overlay.png that holds the border of a core horizontal bar of this size, -1 for another size
    public static int overlayBorderV(float length, float width) {
        if (length == Bars.HORIZONTAL_LENGTH && width == Bars.HORIZONTAL_WIDTH) {
            return 128;
        }
        if (length == Bars.HORIZONTAL_LENGTH_MINI && width == Bars.HORIZONTAL_WIDTH_MINI) {
            return 136;
        }
        return -1;
    }

    @Override
    protected void initText() {
        tooltipText = new MultiLineScreenTooltip(
                Component.translatable("rotp_ul.hud.energy", 0, 0).withStyle(ChatFormatting.BLACK),
                Component.translatable("rotp_ul.hud.hamon", 0, 0).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.set(tooltipText);
    }

    @Override
    public boolean shouldRender() {
        return !hud.forContainerMenu.isTrue() && data() != null;
    }

    @Override
    public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        UltimateLifeformData data = data();
        if (data == null) {
            return;
        }
        float energy = Mth.clamp(data.getEnergyRatio(), 0.0F, 1.0F);
        float charge = data.getHamonRatio();
        PoseStack pose = guiGraphics.pose();
        Minecraft mc = Minecraft.getInstance();
        float x = getX() + 8;
        float y = getY();
        float length = Bars.HORIZONTAL_LENGTH;
        float width = Bars.HORIZONTAL_WIDTH;
        int border = borderTint(charge, 1.0F);
        if (charge > 0.0F) {
            BlitFloat.blit(pose, mc, ULF_OVERLAY, x, y, length, width, 0,
                    0, 128, length, width, 256, 256, border);
        }
        else {
            BlitFloat.blit(pose, mc, Bars.BAR_HORIZONTAL_EMPTY, x, y, length, width, 0, border);
        }
        if (energy > 0.0F) {
            float fill = (length - 2) * energy + 1;
            BlitFloat.blit(pose, mc, Bars.BAR_HORIZONTAL_FILL, x, y, fill, width, 0,
                    0, 0, fill, width, length, width, ARGB.color(1.0F, ENERGY_COLOR));
        }
        BlitFloat.blit(pose, mc, Bars.BAR_HORIZONTAL_SCALE, x, y, length, width, 0, BlitFloat.NO_TINT);
        UltimateLifeformClientAcceptance.recordPlayerVisual(mc.player, ClientReceipt.HUD);
    }

    @Override
    protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {
        UltimateLifeformData data = data();
        if (data != null) {
            tooltipText.setTitle(Component.translatable("rotp_ul.hud.energy",
                    Math.round(data.getEnergy()), Math.round(UltimateLifeformData.MAX_ENERGY))
                    .withStyle(ChatFormatting.BLACK));
            tooltipText.body.set(0, Component.translatable("rotp_ul.hud.hamon",
                    data.getHamonCharge(), UltimateLifeformData.MAX_HAMON_CHARGE)
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltipText.cachedTooltip = null;
        }
        super.checkTooltip(mouseX, mouseY, deltaTracker);
    }

    private static UltimateLifeformData data() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        PlayerPower power = PlayerPower.get(minecraft.player);
        if (power == null || power.getPowerType() != UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()) {
            return null;
        }
        return PlayerPower.getPowerData(minecraft.player, UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElse(null);
    }
}
