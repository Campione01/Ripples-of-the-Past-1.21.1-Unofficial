package rotp.addons.hermitpurple.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import rotp.addons.hermitpurple.network.HermitTargetEntry;
import rotp.addons.hermitpurple.network.OpenHermitTargetPayload;
import rotp.addons.hermitpurple.network.SelectHermitTargetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HermitTargetSelectionScreen extends Screen {
    private static final int PAGE_SIZE = 8;

    private final List<HermitTargetEntry> entries;
    private final List<Button> entryButtons = new ArrayList<>();
    private EditBox search;
    private Button previous;
    private Button next;
    private int page;

    public HermitTargetSelectionScreen(
            OpenHermitTargetPayload payload) {
        super(Component.translatable("rotp_zhp.target.select"));
        entries = payload.entries();
    }

    @Override
    protected void init() {
        search = new EditBox(
                font,
                width / 2 - 110,
                34,
                220,
                20,
                Component.translatable("gui.search"));
        search.setResponder(value -> {
            page = 0;
            rebuildEntries();
        });
        addRenderableWidget(search);
        previous = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> {
                            page = Math.max(0, page - 1);
                            rebuildEntries();
                        })
                .bounds(width / 2 - 110, height - 34, 24, 20)
                .build());
        next = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> {
                            page++;
                            rebuildEntries();
                        })
                .bounds(width / 2 + 86, height - 34, 24, 20)
                .build());
        rebuildEntries();
        setInitialFocus(search);
    }

    private void rebuildEntries() {
        entryButtons.forEach(this::removeWidget);
        entryButtons.clear();
        List<HermitTargetEntry> filtered = filtered();
        int maxPage = Math.max(
                0,
                (filtered.size() - 1) / PAGE_SIZE);
        page = Math.min(page, maxPage);
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filtered.size());
        for (int index = from; index < to; index++) {
            HermitTargetEntry entry = filtered.get(index);
            int y = 62 + (index - from) * 22;
            Button button = Button.builder(
                            Component.literal(entry.label()),
                            ignored -> select(entry))
                    .bounds(width / 2 - 150, y, 300, 20)
                    .build();
            entryButtons.add(addRenderableWidget(button));
        }
        previous.active = page > 0;
        next.active = page < maxPage;
    }

    private List<HermitTargetEntry> filtered() {
        String query = search == null
                ? ""
                : search.getValue()
                        .trim()
                        .toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(entry -> query.isEmpty()
                        || entry.label()
                                .toLowerCase(Locale.ROOT)
                                .contains(query)
                        || entry.id()
                                .toLowerCase(Locale.ROOT)
                                .contains(query))
                .toList();
    }

    private void select(HermitTargetEntry entry) {
        PacketDistributor.sendToServer(
                new SelectHermitTargetPayload(
                        entry.mode(), entry.id()));
        onClose();
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // Screen.render draws the background itself in 1.21; calling it here as well blurred and dimmed the world twice
        super.render(graphics, mouseX, mouseY, partialTick);
        // after super.render, whose background pass would otherwise blur the title
        graphics.drawCenteredString(
                font, title, width / 2, 16, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
