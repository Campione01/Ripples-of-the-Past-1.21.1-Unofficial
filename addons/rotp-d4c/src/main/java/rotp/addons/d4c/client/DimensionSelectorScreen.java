package rotp.addons.d4c.client;

import java.util.List;
import java.util.UUID;

import rotp.addons.d4c.network.OpenDimensionSelectorPayload;
import rotp.addons.d4c.network.SelectDimensionPayload;
import rotp.addons.d4c.travel.D4CDimensionTargets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DimensionSelectorScreen extends Screen {
    private static final int BUTTON_WIDTH = 156;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private final UUID sessionId;
    private final long expiryGameTick;
    private final List<ResourceLocation> targets;
    private final ResourceLocation selected;

    public DimensionSelectorScreen(
            OpenDimensionSelectorPayload payload) {
        super(Component.translatable(
                "rotp_d4c.screen.dimension_selector"));
        sessionId = payload.sessionId();
        expiryGameTick = payload.expiryGameTick();
        targets = payload.targets();
        selected = payload.selected();
    }

    @Override
    protected void init() {
        int columns = width >= BUTTON_WIDTH * 2 + 52 ? 2 : 1;
        int rows = (targets.size() + columns - 1) / columns;
        int totalWidth = columns * BUTTON_WIDTH
                + (columns - 1) * GAP;
        int startX = (width - totalWidth) / 2;
        int startY = Math.max(
                42,
                (height - rows * (BUTTON_HEIGHT + GAP)) / 2);

        for (int index = 0; index < targets.size(); index++) {
            ResourceLocation target = targets.get(index);
            int column = index % columns;
            int row = index / columns;
            Component label = target.equals(selected)
                    ? Component.literal("* ").append(
                            D4CDimensionTargets.label(target))
                    : D4CDimensionTargets.label(target);
            addRenderableWidget(Button.builder(
                            label,
                            button -> select(target))
                    .bounds(
                            startX + column * (BUTTON_WIDTH + GAP),
                            startY + row * (BUTTON_HEIGHT + GAP),
                            BUTTON_WIDTH,
                            BUTTON_HEIGHT)
                    .build());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null
                && minecraft.level != null
                && minecraft.level.getGameTime() > expiryGameTick) {
            onClose();
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        graphics.drawCenteredString(
                font,
                title,
                width / 2,
                18,
                0xFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "rotp_d4c.screen.dimension_selector.current",
                        D4CDimensionTargets.label(selected)),
                width / 2,
                30,
                0xB8C7D9);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * The 1.16 dimension selector (a title over its buttons) drew no background: the world stays clear behind it. In
     * 1.21 Screen.render draws the blurred, dimmed background by itself, and the port also called it first, so it ran
     * twice; it is switched off here the way the host's own GameModeSwitcherScreen does it.
     */
    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void select(ResourceLocation target) {
        if (!targets.contains(target)) {
            return;
        }
        PacketDistributor.sendToServer(
                new SelectDimensionPayload(sessionId, target));
        onClose();
    }
}
