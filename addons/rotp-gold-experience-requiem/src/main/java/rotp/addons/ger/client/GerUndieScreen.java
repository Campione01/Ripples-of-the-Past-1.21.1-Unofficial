package rotp.addons.ger.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Stands in for the death screen during an Undie loop death. Like the death
 * screen it holds back the keybinds and the Stand keys, but it shows nothing,
 * so the dying camera stays in view, and only the revive closes it.
 */
public final class GerUndieScreen extends Screen {
    public GerUndieScreen() {
        super(Component.empty());
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
