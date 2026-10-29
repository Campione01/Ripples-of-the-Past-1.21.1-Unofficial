package rotp.addons.stickyfingers.client;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.menu.BlockZipperStorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class BlockZipperStorageScreen
        extends AbstractContainerScreen<BlockZipperStorageMenu> {
    private static final ResourceLocation TEXTURE =
            StickyFingersAddon.id(
                    "textures/gui/zipper_54.png");

    public BlockZipperStorageScreen(
            BlockZipperStorageMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 149;
        titleLabelX = 8;
        titleLabelY = 5;
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // AbstractContainerScreen.render draws the background and the container panel itself in 1.21; calling it here
        // as well drew both twice
        super.render(
                graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
        graphics.blit(
                TEXTURE,
                leftPos,
                topPos,
                0,
                0,
                imageWidth,
                53,
                256,
                256);
        graphics.blit(
                TEXTURE,
                leftPos,
                topPos + 53,
                0,
                126,
                imageWidth,
                96,
                256,
                256);
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        graphics.drawString(
                font,
                title,
                titleLabelX,
                titleLabelY,
                0x404040,
                false);
    }
}
