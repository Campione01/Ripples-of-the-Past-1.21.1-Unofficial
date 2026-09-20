package rotp.core.config.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

final class ClientSettingsList extends ContainerObjectSelectionList<ClientSettingsList.Row> {
    ClientSettingsList(Minecraft minecraft, int width, int screenHeight,
            List<AbstractWidget> options, List<ClientModSettingsScreen.Title> categories) {
        super(minecraft, width, Math.max(24, screenHeight - 68), 32, 24);
        centerListVertically = false;
        Map<Integer, List<AbstractWidget>> byY = new TreeMap<>();
        for (AbstractWidget option : options) {
            byY.computeIfAbsent(option.getY(), ignored -> new ArrayList<>()).add(option);
        }
        Map<Integer, Row> rows = new TreeMap<>();
        byY.forEach((y, buttons) -> {
            buttons.sort(Comparator.comparingInt(AbstractWidget::getX));
            rows.put(y, new Row(minecraft, buttons, null));
        });
        for (int i = 0; i < categories.size(); i++) {
            ClientModSettingsScreen.Title category = categories.get(i);
            int nextY = i + 1 < categories.size() ? categories.get(i + 1).y() : Integer.MAX_VALUE;
            if (byY.keySet().stream().anyMatch(y -> y > category.y() && y < nextY)) {
                rows.put(category.y(), new Row(minecraft, List.of(), category.title()));
            }
        }
        rows.values().forEach(this::addEntry);
    }

    @Override
    public int getRowWidth() {
        return Math.min(310, getWidth() - 40);
    }

    @Override
    protected void renderListBackground(GuiGraphics graphics) {
        // The parent screen already draws the world/menu background.
    }

    static final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Minecraft minecraft;
        private final List<AbstractWidget> buttons;
        @Nullable private final Component title;

        Row(Minecraft minecraft, List<AbstractWidget> buttons, @Nullable Component title) {
            this.minecraft = minecraft;
            this.buttons = List.copyOf(buttons);
            this.title = title;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovered, float partialTick) {
            int rowLeft = left - 2;
            if (title != null) {
                graphics.drawCenteredString(minecraft.font, title, rowLeft + width / 2, top + 6, 0xC0C0C0);
            }
            int buttonWidth = (width - 10) / 2;
            for (int i = 0; i < buttons.size(); i++) {
                AbstractWidget button = buttons.get(i);
                button.setWidth(buttonWidth);
                button.setPosition(rowLeft + i * (buttonWidth + 10), top);
                button.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return buttons;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return buttons;
        }
    }
}
