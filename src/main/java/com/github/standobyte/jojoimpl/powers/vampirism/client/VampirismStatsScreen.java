package com.github.standobyte.jojoimpl.powers.vampirism.client;

import java.util.List;
import java.util.Locale;

import com.github.standobyte.jojo.client.ui.screen_jojomenu.PlaceholderScreen;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.Tab;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.TabCategory;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class VampirismStatsScreen extends PlaceholderScreen {
    private static final int TEXT_COLOR = 0x3A2B23;
    private static final int MUTED_TEXT_COLOR = 0x75665B;
    private static final int BLOOD_BAR_BORDER = 0xFF3A211F;
    private static final int BLOOD_BAR_BACKGROUND = 0xFF241A19;
    private static final int BLOOD_BAR_FILL = 0xFF8C1D24;

    public VampirismStatsScreen(Component title, TabCategory category, Tab tab) {
        super(title, category, tab);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) {
            return;
        }
        VampirismData data = PlayerPower.getPowerData(localPlayer, ModPlayerPowers.VAMPIRISM).orElse(null);

        int windowX = getWindowX(this);
        int x = windowX + 18;
        int y = getWindowY(this) + 20;
        int contentWidth = getWindowWidth() - 36;

        gui.drawCenteredString(font, Component.translatable("jojo_ripples.vampirism.stats.tab"),
                windowX + getWindowWidth() / 2, y, TEXT_COLOR);
        if (data == null) {
            drawWrapped(gui, Component.translatable("jojo_ripples.vampirism.stats.unavailable"),
                    x, y + 24, contentWidth, MUTED_TEXT_COLOR);
            return;
        }

		float currentBlood = data.getBloodLevel();
		float maxBlood = data.getMaxBlood(localPlayer);
		float bloodRatio = maxBlood > 0.0F ? Mth.clamp(currentBlood / maxBlood, 0.0F, 1.0F) : 0.0F;
        int barY = y + 24;
        gui.fill(x, barY, x + contentWidth, barY + 13, BLOOD_BAR_BORDER);
        gui.fill(x + 1, barY + 1, x + contentWidth - 1, barY + 12, BLOOD_BAR_BACKGROUND);
        int fillWidth = Mth.floor((contentWidth - 2) * bloodRatio);
        if (fillWidth > 0) {
            gui.fill(x + 1, barY + 1, x + 1 + fillWidth, barY + 12, BLOOD_BAR_FILL);
        }
		Component bloodText = Component.translatable("jojo_ripples.vampirism.stats.blood",
				String.format(Locale.ROOT, "%.1f / %.1f", currentBlood, maxBlood));
        gui.drawCenteredString(font, bloodText, windowX + getWindowWidth() / 2, barY + 2, 0xFFFFFF);

        int textY = barY + 25;
        textY = drawWrapped(gui, Component.translatable("jojo_ripples.vampirism.stats.full_power",
                statusText(data.isVampireAtFullPower())), x, textY, contentWidth, TEXT_COLOR);
        textY = drawWrapped(gui, Component.translatable("jojo_ripples.vampirism.stats.high_blood",
                statusText(data.isHighOnBlood(localPlayer))), x, textY + 3, contentWidth, TEXT_COLOR);
		textY = drawWrapped(gui, Component.translatable("jojo_ripples.vampirism.stats.curing_stage",
				data.getCuringStage(localPlayer)), x, textY + 3, contentWidth, TEXT_COLOR);
        textY = drawWrapped(gui, Component.translatable("jojo_ripples.vampirism.stats.hamon_user",
                statusText(data.isVampireHamonUser()), data.getPrevHamonCharacter()),
                x, textY + 3, contentWidth, TEXT_COLOR);
        drawWrapped(gui, Component.translatable("jojo_ripples.vampirism.stats.backbone",
                data.getBackboneTicks()), x, textY + 5, contentWidth, MUTED_TEXT_COLOR);
    }

    private Component statusText(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    private int drawWrapped(GuiGraphics gui, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (FormattedCharSequence line : lines) {
            gui.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }
}
