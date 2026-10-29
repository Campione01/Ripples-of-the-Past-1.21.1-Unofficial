package rotp.addons.stickyfingers.client;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.menu.EntityZipperStorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EntityZipperStorageScreen
        extends AbstractContainerScreen<
                EntityZipperStorageMenu> {
    private static final ResourceLocation TEXTURE =
            StickyFingersAddon.id(
                    "textures/gui/zipper_storage.png");

    public EntityZipperStorageScreen(
            EntityZipperStorageMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        titleLabelX = 62;
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
                imageHeight,
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
