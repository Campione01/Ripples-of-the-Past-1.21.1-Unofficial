package rotp.addons.whitesnake.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disguise.DisguiseTypePolicy;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
import rotp.addons.whitesnake.network.DisguiseSelectionPayload;
import rotp.addons.whitesnake.network.OpenDisguiseSelectionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DisguiseSelectionScreen extends Screen {
    private static final ResourceLocation BUTTON_ATLAS =
            WhitesnakeAddon.id("textures/gui/fmcui_bg.png");
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_GAP = 4;
    private static final int MAX_COLUMNS = 4;
    private static final int DONOR_BUTTON_SIZE = 24;
    private static final int DONOR_HOVER_U = 24;
    private static final int DONOR_ATLAS_SIZE = 128;

    private final List<EntityType<?>> allChoices = new ArrayList<>();
    private final List<EntityType<?>> filteredChoices =
            new ArrayList<>();
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
    private boolean choicesLoaded;
    private final UUID sessionId;
    private final long sessionExpiryGameTick;

    public DisguiseSelectionScreen(
            OpenDisguiseSelectionPayload payload) {
        super(Component.translatable(
                "rotp_whitesnake.disguise.title"));
        sessionId = payload.sessionId();
        sessionExpiryGameTick = payload.expiryGameTick();
    }

    public static void open(OpenDisguiseSelectionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof DisguiseSelectionScreen screen)
                || !screen.hasSession(payload.sessionId())) {
            minecraft.setScreen(new DisguiseSelectionScreen(payload));
        }
    }

    public boolean hasSession(UUID candidateSessionId) {
        return sessionId.equals(candidateSessionId);
    }

    public UUID sessionId() {
        return sessionId;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null || minecraft.level == null) {
            onClose();
            return;
        }
        localExpiry = sessionExpiryGameTick;
        loadChoices();

        int searchWidth = Math.min(260, Math.max(120, width - 40));
        searchBox = addRenderableWidget(new EditBox(
                font,
                (width - searchWidth) / 2,
                28,
                searchWidth,
                18,
                Component.translatable(
                        "rotp_whitesnake.disguise.search")));
        searchBox.setMaxLength(80);
        searchBox.setResponder(value -> {
            scrollRow = 0;
            refilter(value);
        });

        gridTop = 54;
        gridWidth = Math.min(620, Math.max(120, width - 30));
        columns = Mth.clamp(gridWidth / 145, 1, MAX_COLUMNS);
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

        choiceButtons.clear();
        choiceIndexes.clear();
        int visibleSlots = columns * rows;
        for (int slot = 0; slot < visibleSlots; slot++) {
            final int buttonSlot = slot;
            int column = slot % columns;
            int row = slot / columns;
            Button button = addRenderableWidget(Button.builder(
                            Component.empty(),
                            ignored -> select(buttonSlot))
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
        if (choicesLoaded || minecraft == null
                || minecraft.level == null) {
            return;
        }
        allChoices.addAll(
                DisguiseTypePolicy.collectAllowed(minecraft.level));
        allChoices.sort(Comparator
                .comparing(
                        (EntityType<?> type) ->
                                type.getDescription()
                                        .getString()
                                        .toLowerCase(Locale.ROOT))
                .thenComparing(type ->
                        EntityType.getKey(type).toString()));
        choicesLoaded = true;
    }

    private void refilter(String query) {
        String normalized = query == null
                ? ""
                : query.strip().toLowerCase(Locale.ROOT);
        filteredChoices.clear();
        for (EntityType<?> type : allChoices) {
            ResourceLocation id = EntityType.getKey(type);
            String name = type.getDescription()
                    .getString()
                    .toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()
                    || name.contains(normalized)
                    || id.toString().contains(normalized)) {
                filteredChoices.add(type);
            }
        }
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
        updateButtons();
    }

    private void updateButtons() {
        if (choiceButtons.isEmpty()) {
            return;
        }
        int firstChoice = scrollRow * columns;
        for (int slot = 0; slot < choiceButtons.size(); slot++) {
            Button button = choiceButtons.get(slot);
            int choiceIndex = firstChoice + slot;
            if (choiceIndex >= filteredChoices.size()) {
                choiceIndexes.set(slot, -1);
                button.visible = false;
                button.active = false;
                button.setMessage(Component.empty());
                continue;
            }

            EntityType<?> type = filteredChoices.get(choiceIndex);
            String label = font.plainSubstrByWidth(
                    type.getDescription().getString(),
                    Math.max(8, buttonWidth - 30));
            choiceIndexes.set(slot, choiceIndex);
            button.visible = true;
            button.active = true;
            button.setMessage(Component.literal("    " + label));
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
        EntityType<?> type = filteredChoices.get(choiceIndex);
        PacketDistributor.sendToServer(
                new DisguiseSelectionPayload(
                        sessionId, EntityType.getKey(type)));
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        WhitesnakeEntity whitesnake = currentWhitesnake();
        if (minecraft == null
                || minecraft.level == null
                || minecraft.level.getGameTime() > localExpiry
                || whitesnake == null
                || whitesnake.isDisguised()) {
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
                    scrollRow + direction, 0, maxScrollRow());
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
        super.render(graphics, mouseX, mouseY, partialTick);
        // after super.render, whose background pass would otherwise blur the title
        graphics.drawCenteredString(
                font, title, width / 2, 10, 0xFFFFFF);

        Button hovered = null;
        EntityType<?> hoveredType = null;
        for (int slot = 0; slot < choiceButtons.size(); slot++) {
            int choiceIndex = choiceIndexes.get(slot);
            if (choiceIndex < 0
                    || choiceIndex >= filteredChoices.size()) {
                continue;
            }
            Button button = choiceButtons.get(slot);
            EntityType<?> type = filteredChoices.get(choiceIndex);
            renderIcon(graphics, button, type);
            if (button.isHovered()) {
                hovered = button;
                hoveredType = type;
            }
        }
        renderScrollbar(graphics);

        if (filteredChoices.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "rotp_whitesnake.disguise.empty"),
                    width / 2,
                    gridTop + 8,
                    0xA0A0A0);
        }
        if (hovered != null && hoveredType != null) {
            graphics.renderTooltip(
                    font,
                    Component.literal(
                                    hoveredType.getDescription()
                                            .getString()
                                            + " ["
                                            + EntityType.getKey(
                                                    hoveredType)
                                            + "]")
                            .withStyle(ChatFormatting.GRAY),
                    mouseX,
                    mouseY);
        }
    }

    private void renderIcon(
            GuiGraphics graphics,
            Button button,
            EntityType<?> type) {
        // 1.16 FormButton enabled blending for its frame (fmcui_bg.png is semi-transparent); a plain blit does not
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(
                BUTTON_ATLAS,
                button.getX(),
                button.getY(),
                button.isHoveredOrFocused()
                        ? DONOR_HOVER_U
                        : 0,
                0,
                DONOR_BUTTON_SIZE,
                DONOR_BUTTON_SIZE,
                DONOR_ATLAS_SIZE,
                DONOR_ATLAS_SIZE);
        RenderSystem.disableBlend();
        SpawnEggItem spawnEgg = SpawnEggItem.byId(type);
        if (spawnEgg != null) {
            graphics.renderItem(
                    new ItemStack(spawnEgg),
                    button.getX() + 3,
                    button.getY() + 3);
            return;
        }

        int left = button.getX() + 4;
        int top = button.getY() + 4;
        graphics.fill(left, top, left + 14, top + 14, 0xFF505050);
        String name = type.getDescription().getString();
        String initial = name.isEmpty()
                ? "?"
                : name.substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.drawCenteredString(
                font, initial, left + 7, top + 3, 0xFFFFFF);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int totalRows = (filteredChoices.size()
                + columns - 1) / columns;
        if (totalRows <= rows) {
            return;
        }
        int trackHeight = rows * (BUTTON_HEIGHT + BUTTON_GAP)
                - BUTTON_GAP;
        int trackX = Math.min(width - 5, gridLeft + gridWidth + 4);
        int thumbHeight = Math.max(
                8, trackHeight * rows / totalRows);
        int thumbY = gridTop
                + (trackHeight - thumbHeight)
                        * scrollRow / maxScrollRow();
        graphics.fill(
                trackX, gridTop, trackX + 2,
                gridTop + trackHeight, 0x80606060);
        graphics.fill(
                trackX, thumbY, trackX + 2,
                thumbY + thumbHeight, 0xFFE0E0E0);
    }

    private int maxScrollRow() {
        if (columns <= 0 || rows <= 0) {
            return 0;
        }
        int totalRows = (filteredChoices.size()
                + columns - 1) / columns;
        return Math.max(0, totalRows - rows);
    }

    private WhitesnakeEntity currentWhitesnake() {
        if (minecraft == null
                || minecraft.player == null
                || !WhitesnakeAddon.isActiveWhitesnakeUser(
                        minecraft.player, "disguise")) {
            return null;
        }
        StandPower power = StandPower.get(minecraft.player);
        return power != null
                        && power.getSummonedStandEntity()
                                instanceof WhitesnakeEntity whitesnake
                ? whitesnake
                : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
