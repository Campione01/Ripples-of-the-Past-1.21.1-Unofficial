package rotp.addons.d4c.client;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.network.SelectMinionAiModePayload;
import rotp.core.client.standskin.StandSkinsLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.16 minion AI selector (015 com/mrlang/rotp_d4c/ad/ad/ab.java), opened by minion_ai_control: six square buttons, one
 * per minion mode, each with an item (D4C's icon for the Stand attack), the mode's name and description on hover; a
 * click sends the mode to the server and closes the screen.
 */
public final class MinionAiSelectorScreen extends Screen {
    // 1.16 drew textures/power/d4c.png; the port keeps those bytes as the default skin's stand icon
    private static final ResourceLocation STAND_ICON = StandSkinsLoader.remap(
            D4CAddon.id("textures/stand_icon.png"), D4CAddon.D4C.getId());

    public MinionAiSelectorScreen() {
        super(Component.translatable(MinionAiSelectorLayout.TITLE_KEY));
    }

    // ab.java:30: the Colt, a diamond sword, an orange bed, iron boots, a barrier (drawn as D4C's icon), an enchanted book
    private static ItemStack[] icons() {
        return new ItemStack[] {
                new ItemStack(D4CAddon.COLT_CUSTOM.get()),
                new ItemStack(Items.DIAMOND_SWORD),
                new ItemStack(Items.ORANGE_BED),
                new ItemStack(Items.IRON_BOOTS),
                new ItemStack(Items.BARRIER),
                new ItemStack(Items.ENCHANTED_BOOK)};
    }

    @Override
    protected void init() {
        ItemStack[] icons = icons();
        int[][] positions = MinionAiSelectorLayout.buttonPositions(width, height);
        for (int mode = 0; mode < positions.length; mode++) {
            int chosen = mode;
            addRenderableWidget(new ModeButton(positions[mode][0], positions[mode][1], mode, icons[mode],
                    button -> select(chosen)));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, title, width / 2,
                height / 2 - MinionAiSelectorLayout.TITLE_ABOVE_CENTRE, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (GuiEventListener child : children()) {
            if (child instanceof ModeButton button && button.isHoveredOrFocused()) {
                graphics.renderComponentTooltip(font, List.of(
                        Component.translatable(MinionAiSelectorLayout.MODE_KEYS[button.mode]),
                        Component.translatable(MinionAiSelectorLayout.DESC_KEYS[button.mode])),
                        mouseX, mouseY);
                break;
            }
        }
    }

    // 1.16's screen drew no background: the world stays clear behind it (as DimensionSelectorScreen)
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void select(int mode) {
        PacketDistributor.sendToServer(new SelectMinionAiModePayload(mode));
        onClose();
    }

    private static final class ModeButton extends Button {
        private final int mode;
        private final ItemStack icon;

        private ModeButton(int x, int y, int mode, ItemStack icon, OnPress onPress) {
            super(x, y, MinionAiSelectorLayout.BUTTON_SIZE, MinionAiSelectorLayout.BUTTON_SIZE,
                    CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
            this.mode = mode;
            this.icon = icon;
        }

        // ab.java:158-186
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            int x = getX();
            int y = getY();
            int right = x + getWidth();
            int bottom = y + getHeight();
            graphics.fill(x, y, right, bottom,
                    hovered ? MinionAiSelectorLayout.FILL_HOVERED : MinionAiSelectorLayout.FILL);
            int border = hovered ? MinionAiSelectorLayout.BORDER_HOVERED : MinionAiSelectorLayout.BORDER;
            graphics.fill(x, y, right, y + 1, border);
            graphics.fill(x, bottom - 1, right, bottom, border);
            graphics.fill(x, y, x + 1, bottom, border);
            graphics.fill(right - 1, y, right, bottom, border);
            int inset = MinionAiSelectorLayout.ICON_INSET;
            if (mode == MinionAiSelectorLayout.STAND_ICON_BUTTON) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                graphics.blit(STAND_ICON, x + inset, y + inset, 0.0F, 0.0F, 16, 16, 16, 16);
                RenderSystem.disableBlend();
            }
            else {
                graphics.renderItem(icon, x + inset, y + inset);
            }
        }
    }
}
