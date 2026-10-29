package rotp.addons.yellowtemperance.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import rotp.core.core.JojoRegistries;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.DisguisePolicy;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import rotp.addons.yellowtemperance.network.DisguiseSelectionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DisguiseSelectionScreen extends Screen {
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 4;
    private static final int MAX_COLUMNS = 4;

    private final DisguiseMode mode;
    private final List<Choice> allChoices = new ArrayList<>();
    private final List<Choice> filteredChoices = new ArrayList<>();
    private final List<Button> choiceButtons = new ArrayList<>();
    private final List<Integer> choiceIndexes = new ArrayList<>();
    private EditBox searchBox;
    private int columns;
    private int rows;
    private int scrollRow;
    private int gridLeft;
    private int gridTop;
    private int gridWidth;
    private int buttonWidth;
    private long localExpiry;

    private DisguiseSelectionScreen(DisguiseMode mode) {
        super(Component.translatable(
                "rotp_yellowtemperance.disguise.title."
                        + mode.name().toLowerCase(Locale.ROOT)));
        this.mode = mode;
    }

    public static void open(DisguiseMode mode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (mode != DisguiseMode.NONE
                && minecraft.screen == null) {
            minecraft.setScreen(
                    new DisguiseSelectionScreen(mode));
        }
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null || minecraft.level == null) {
            onClose();
            return;
        }
        localExpiry = minecraft.level.getGameTime()
                + YellowTemperanceControllerEntity
                        .SELECTION_WINDOW_TICKS;
        loadChoices();

        int searchWidth = Math.min(
                260, Math.max(120, width - 40));
        searchBox = addRenderableWidget(new EditBox(
                font,
                (width - searchWidth) / 2,
                28,
                searchWidth,
                18,
                Component.translatable(
                        "rotp_yellowtemperance.disguise.search")));
        searchBox.setMaxLength(80);
        searchBox.setResponder(value -> {
            scrollRow = 0;
            refilter(value);
        });

        gridTop = 54;
        gridWidth = Math.min(
                620, Math.max(120, width - 30));
        columns = Mth.clamp(
                gridWidth / 145, 1, MAX_COLUMNS);
        rows = Math.max(
                1,
                (height - gridTop - 30)
                        / (BUTTON_HEIGHT + BUTTON_GAP));
        buttonWidth = (gridWidth
                - (columns - 1) * BUTTON_GAP)
                / columns;
        gridWidth = buttonWidth * columns
                + (columns - 1) * BUTTON_GAP;
        gridLeft = (width - gridWidth) / 2;

        int visibleSlots = columns * rows;
        for (int slot = 0; slot < visibleSlots; slot++) {
            final int buttonSlot = slot;
            int column = slot % columns;
            int row = slot / columns;
            Button button = addRenderableWidget(
                    Button.builder(
                                    Component.empty(),
                                    ignored ->
                                            select(buttonSlot))
                            .pos(
                                    gridLeft
                                            + column
                                                    * (buttonWidth
                                                            + BUTTON_GAP),
                                    gridTop
                                            + row
                                                    * (BUTTON_HEIGHT
                                                            + BUTTON_GAP))
                            .size(buttonWidth, BUTTON_HEIGHT)
                            .build());
            choiceButtons.add(button);
            choiceIndexes.add(-1);
        }
        refilter("");
    }

    private void loadChoices() {
        switch (mode) {
            case ENTITY -> {
                for (EntityType<?> type :
                        BuiltInRegistries.ENTITY_TYPE) {
                    if (DisguisePolicy.isAllowedEntity(
                            type, minecraft.level)) {
                        ResourceLocation id =
                                EntityType.getKey(type);
                        allChoices.add(new Choice(
                                type.getDescription().getString(),
                                id.toString()));
                    }
                }
            }
            case PLAYER -> {
                var connection = minecraft.getConnection();
                if (connection != null) {
                    connection.getOnlinePlayers().forEach(info ->
                            allChoices.add(new Choice(
                                    info.getProfile().getName(),
                                    info.getProfile().getId()
                                            .toString())));
                }
            }
            case STAND -> JojoRegistries.DEFAULT_STANDS_REG
                    .entrySet().forEach(entry -> {
                        ResourceLocation id =
                                entry.getKey().location();
                        if (DisguisePolicy.isAllowedStand(
                                        id, entry.getValue())
                                && !id.equals(
                                        YellowTemperanceAddon.id(
                                                "yellow_temperance"))) {
                            allChoices.add(new Choice(
                                    readable(id), id.toString()));
                        }
                    });
            case NONE -> {
            }
        }
        allChoices.sort(Comparator
                .comparing(
                        (Choice choice) ->
                                choice.label()
                                        .toLowerCase(Locale.ROOT))
                .thenComparing(Choice::value));
    }

    private void refilter(String query) {
        String normalized = query == null
                ? ""
                : query.strip().toLowerCase(Locale.ROOT);
        filteredChoices.clear();
        for (Choice choice : allChoices) {
            if (normalized.isEmpty()
                    || choice.label()
                            .toLowerCase(Locale.ROOT)
                            .contains(normalized)
                    || choice.value().contains(normalized)) {
                filteredChoices.add(choice);
            }
        }
        scrollRow = Mth.clamp(
                scrollRow, 0, maxScrollRow());
        updateButtons();
    }

    private void updateButtons() {
        int firstChoice = scrollRow * columns;
        for (int slot = 0;
                slot < choiceButtons.size();
                slot++) {
            Button button = choiceButtons.get(slot);
            int choiceIndex = firstChoice + slot;
            if (choiceIndex >= filteredChoices.size()) {
                choiceIndexes.set(slot, -1);
                button.visible = false;
                button.active = false;
                button.setMessage(Component.empty());
                continue;
            }
            Choice choice = filteredChoices.get(choiceIndex);
            choiceIndexes.set(slot, choiceIndex);
            button.visible = true;
            button.active = true;
            button.setMessage(Component.literal(
                    font.plainSubstrByWidth(
                            choice.label(),
                            Math.max(8, buttonWidth - 10))));
        }
    }

    private void select(int slot) {
        if (slot < 0 || slot >= choiceIndexes.size()) {
            return;
        }
        int choiceIndex = choiceIndexes.get(slot);
        if (choiceIndex < 0
                || choiceIndex >= filteredChoices.size()) {
            return;
        }
        Choice choice = filteredChoices.get(choiceIndex);
        PacketDistributor.sendToServer(
                new DisguiseSelectionPayload(
                        mode.id(), choice.value()));
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        YellowTemperanceControllerEntity controller =
                minecraft != null && minecraft.player != null
                        ? YellowTemperanceAddon.controller(
                                minecraft.player)
                        : null;
        if (minecraft == null
                || minecraft.level == null
                || minecraft.level.getGameTime() > localExpiry
                || controller == null
                || controller.isDisguised()) {
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        if (scrollY != 0.0D) {
            int direction = scrollY > 0.0D ? -1 : 1;
            int next = Mth.clamp(
                    scrollRow + direction,
                    0,
                    maxScrollRow());
            if (next != scrollRow) {
                scrollRow = next;
                updateButtons();
                return true;
            }
        }
        return super.mouseScrolled(
                mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // Screen.render draws the background itself in 1.21; calling it here as well blurred and dimmed the world twice
        super.render(
                graphics, mouseX, mouseY, partialTick);
        // after super.render, whose background pass would otherwise blur the title
        graphics.drawCenteredString(
                font, title, width / 2, 10, 0xFFFFFF);
        renderScrollbar(graphics);
        if (filteredChoices.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "rotp_yellowtemperance.disguise.empty"),
                    width / 2,
                    gridTop + 8,
                    0xA0A0A0);
        }
        for (int slot = 0;
                slot < choiceButtons.size();
                slot++) {
            int choiceIndex = choiceIndexes.get(slot);
            if (choiceIndex >= 0
                    && choiceIndex < filteredChoices.size()
                    && choiceButtons.get(slot).isHovered()) {
                Choice choice =
                        filteredChoices.get(choiceIndex);
                graphics.renderTooltip(
                        font,
                        Component.literal(
                                        choice.label()
                                                + " ["
                                                + choice.value()
                                                + "]")
                                .withStyle(ChatFormatting.GRAY),
                        mouseX,
                        mouseY);
                break;
            }
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int totalRows = (filteredChoices.size()
                + columns - 1) / columns;
        if (totalRows <= rows) {
            return;
        }
        int trackHeight = rows
                * (BUTTON_HEIGHT + BUTTON_GAP)
                - BUTTON_GAP;
        int trackX = Math.min(
                width - 5, gridLeft + gridWidth + 4);
        int thumbHeight = Math.max(
                8, trackHeight * rows / totalRows);
        int thumbY = gridTop
                + (trackHeight - thumbHeight)
                        * scrollRow / maxScrollRow();
        graphics.fill(
                trackX,
                gridTop,
                trackX + 2,
                gridTop + trackHeight,
                0x80606060);
        graphics.fill(
                trackX,
                thumbY,
                trackX + 2,
                thumbY + thumbHeight,
                0xFFE0E0E0);
    }

    private int maxScrollRow() {
        if (columns <= 0 || rows <= 0) {
            return 0;
        }
        int totalRows = (filteredChoices.size()
                + columns - 1) / columns;
        return Math.max(0, totalRows - rows);
    }

    private static String readable(ResourceLocation id) {
        String[] words = id.getPath().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            if (!word.isEmpty()) {
                result.append(
                        Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }
        }
        return result.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Choice(String label, String value) {}
}
