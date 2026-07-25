package com.github.standobyte.jojo.client.ui.screen_jojomenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.input.InputHandler;
import com.github.standobyte.jojo.client.input.controlscheme.ClientKey;
import com.github.standobyte.jojo.client.ui.ScreenLetsUseWASD;
import com.github.standobyte.jojo.network.c2s.ClGELifeformButtonPacket;
import com.github.standobyte.jojo.network.c2s.ClGELifeformUiPacket;
import com.github.standobyte.jojo.util.mc.entitysubtype.EntitySubtype;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeformState;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeforms;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

public class GoldExperienceChooseLifeformScreen extends Screen implements ScreenLetsUseWASD {
    private static final int LIST_ROWS_PER_COLUMN = 5;
    private static final int LIST_PAGE_SIZE = LIST_ROWS_PER_COLUMN * 2;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_ROWS = 4;
    private static final int GRID_PAGE_SIZE = GRID_COLUMNS * GRID_ROWS;
    private static String savedSearchFilter = "";
    private static FilterMode savedFilterMode = FilterMode.ALL;
    private static ViewMode savedViewMode = ViewMode.GRID;

    @Nullable private final ClientKey keyHeld;
    private final List<Button> choiceButtons = new ArrayList<>();
    private final List<String> choiceButtonIds = new ArrayList<>();
    private List<EntitySubtype<?>> visibleChoices = List.of();
    @Nullable private String selectedLifeformId;
    @Nullable private EditBox searchBox;
    private int page;
    private int ticksKeyHeld;
    private boolean holdsButton = true;
    private boolean holdMode;
    private boolean selectionDirty;
    private Button previousPageButton;
    private Button nextPageButton;
    private Button doneButton;
    private Button favoriteButton;
    private Button clearNewButton;
    private Button clearSearchButton;
    private Button unlockAllButton;
    private Button allFilterButton;
    private Button favoriteFilterButton;
    private Button newFilterButton;
    private Button viewModeButton;

    private enum FilterMode {
        ALL("jojo.ui.lifeform_ui_mode.all"),
        FAVORITES("jojo.ui.lifeform_ui_mode.favs"),
        NEW("jojo.ui.lifeform_ui_mode.new");

        private final String translationKey;

        FilterMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private Component label() {
            return Component.translatable(translationKey);
        }
    }

    private enum ViewMode {
        GRID("jojo.ge_lifeform.view.grid"),
        LIST("jojo.ge_lifeform.view.list");

        private final String translationKey;

        ViewMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private Component label() {
            return Component.translatable(translationKey);
        }

        private ViewMode opposite() {
            return this == GRID ? LIST : GRID;
        }
    }

    public GoldExperienceChooseLifeformScreen() {
        this(InputHandler.lastActionKey);
    }

    private GoldExperienceChooseLifeformScreen(@Nullable ClientKey keyHeld) {
        super(Component.translatable("jojo_ripples.ability.choose_lifeform"));
        this.keyHeld = keyHeld;
    }

    public static void openWindowOnClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            ClientProxy.openScreen(new GoldExperienceChooseLifeformScreen(InputHandler.lastActionKey));
        }
    }

    @Override
    protected void init() {
        super.init();
        selectionDirty = false;
        page = 0;
        choiceButtons.clear();
        choiceButtonIds.clear();
        GoldExperienceLifeformState state = playerState();
        if (state != null && Minecraft.getInstance().level != null) {
            visibleChoices = state.visibleLifeforms(Minecraft.getInstance().level);
            selectedLifeformId = state.selectedOrFirstMetId(Minecraft.getInstance().level);
        }
        else {
            visibleChoices = List.of();
            selectedLifeformId = null;
        }

        addSearchAndFilterWidgets();

        for (int slot = 0; slot < pageSize(); slot++) {
            final int buttonSlot = slot;
            Button button = addRenderableWidget(Button.builder(Component.empty(), b -> {
                String lifeformId = choiceButtonIds.get(buttonSlot);
                if (lifeformId != null) {
                    selectedLifeformId = lifeformId;
                    selectionDirty = true;
                    updateChoiceButtons();
                }
            }).pos(choiceButtonX(slot), choiceButtonY(slot)).size(choiceButtonWidth(), choiceButtonHeight()).build());
            choiceButtons.add(button);
            choiceButtonIds.add(null);
        }

        previousPageButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (page > 0) {
                page--;
                updateChoiceButtons();
            }
        }).pos(width / 2 - 55, height / 2 + 106).size(20, 20).build());

        nextPageButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if ((page + 1) * pageSize() < visibleChoices.size()) {
                page++;
                updateChoiceButtons();
            }
        }).pos(width / 2 + 35, height / 2 + 106).size(20, 20).build());

        favoriteButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            GoldExperienceLifeformState currentState = playerState();
            if (currentState == null || selectedLifeformId == null || !currentState.hasMetLifeform(selectedLifeformId)) {
                return;
            }
            if (currentState.isFavorite(selectedLifeformId)) {
                PacketDistributor.sendToServer(ClGELifeformUiPacket.removeFavorite(selectedLifeformId));
                currentState.setFavoriteFromSync(selectedLifeformId, false);
            }
            else {
                PacketDistributor.sendToServer(ClGELifeformUiPacket.addFavorite(selectedLifeformId));
                currentState.setFavoriteFromSync(selectedLifeformId, true);
            }
            updateChoiceButtons();
        }).pos(width / 2 - 125, height / 2 + 106).size(68, 20).build());

        clearNewButton = addRenderableWidget(Button.builder(Component.translatable("jojo_ripples.lifeform.clear_new"), button -> {
            GoldExperienceLifeformState currentState = playerState();
            if (currentState != null) {
                PacketDistributor.sendToServer(ClGELifeformUiPacket.clearUnseen());
                currentState.clearNewUnseenLifeformsFromSync();
                updateChoiceButtons();
            }
        }).pos(width / 2 + 57, height / 2 + 106).size(68, 20).build());

        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            if (selectedLifeformId != null) {
                PacketDistributor.sendToServer(new ClGELifeformButtonPacket(selectedLifeformId));
                GoldExperienceLifeformState currentState = playerState();
                if (currentState != null) {
                    currentState.setSelectedFromSync(selectedLifeformId);
                }
            }
            onClose();
        }).pos(width / 2 - 80, height / 2 + 130).size(72, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .pos(width / 2 + 8, height / 2 + 130)
                .size(72, 20)
                .build());

        updateChoiceButtons();
    }

    private void addSearchAndFilterWidgets() {
        int topY = height / 2 - 76;
        searchBox = addRenderableWidget(new EditBox(font, width / 2 - 125, topY, 168, 18,
                Component.translatable("jojo.ge_lifeform.search_field")));
        searchBox.setMaxLength(80);
        searchBox.setValue(savedSearchFilter);
        searchBox.setResponder(value -> {
            savedSearchFilter = value;
            page = 0;
            updateChoiceButtons();
        });

        clearSearchButton = addRenderableWidget(Button.builder(Component.literal("X"), button -> {
            if (searchBox != null) {
                searchBox.setValue("");
            }
        }).pos(width / 2 + 47, topY).size(20, 18).build());

        unlockAllButton = addRenderableWidget(Button.builder(Component.translatable("jojo.ge_lifeform.unlock_all"), button -> {
            PacketDistributor.sendToServer(ClGELifeformUiPacket.unlockAll());
            Minecraft mc = Minecraft.getInstance();
            GoldExperienceLifeformState currentState = playerState();
            if (currentState != null && mc.level != null && mc.player != null && mc.player.getAbilities().instabuild) {
                currentState.learnAllValidLifeforms(mc.level);
                updateChoiceButtons();
            }
        }).pos(width / 2 + 71, topY).size(90, 18).build());

        int filterY = topY + 22;
        allFilterButton = addRenderableWidget(Button.builder(FilterMode.ALL.label(),
                button -> setFilterMode(FilterMode.ALL))
                .pos(width / 2 - 125, filterY).size(80, 18).build());
        favoriteFilterButton = addRenderableWidget(Button.builder(FilterMode.FAVORITES.label(),
                button -> setFilterMode(FilterMode.FAVORITES))
                .pos(width / 2 - 41, filterY).size(80, 18).build());
        newFilterButton = addRenderableWidget(Button.builder(FilterMode.NEW.label(),
                button -> setFilterMode(FilterMode.NEW))
                .pos(width / 2 + 43, filterY).size(80, 18).build());

        viewModeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            savedViewMode = savedViewMode.opposite();
            page = 0;
            Minecraft.getInstance().setScreen(new GoldExperienceChooseLifeformScreen(null));
        }).pos(width / 2 + 127, filterY).size(62, 18).build());
    }

    private void setFilterMode(FilterMode filterMode) {
        if (savedFilterMode != filterMode) {
            savedFilterMode = filterMode;
            page = 0;
            updateChoiceButtons();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (holdsButton) {
            if (!isKeyBeingHeld()) {
                holdsButton = false;
            }
            else if (++ticksKeyHeld == 5) {
                holdMode = true;
            }
        }
        GoldExperienceLifeformState state = playerState();
        if (!selectionDirty && state != null && Minecraft.getInstance().level != null) {
            String syncedId = state.selectedOrFirstMetId(Minecraft.getInstance().level);
            if (syncedId != null && !syncedId.equals(selectedLifeformId)) {
                selectedLifeformId = syncedId;
                updateChoiceButtons();
            }
        }
    }

    private boolean isKeyBeingHeld() {
        if (keyHeld == null) {
            return false;
        }

        InputConstants.Key vanillaKey = keyHeld.getVanillaKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (vanillaKey.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, vanillaKey.getValue()) == GLFW.GLFW_PRESS;
            case KEYSYM -> GLFW.glfwGetKey(window, vanillaKey.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    private void updateChoiceButtons() {
        GoldExperienceLifeformState state = playerState();
        if (state != null && Minecraft.getInstance().level != null) {
            visibleChoices = filterChoices(state.visibleLifeforms(Minecraft.getInstance().level), state);
        }
        else {
            visibleChoices = List.of();
        }
        if (selectedLifeformId == null && state != null && Minecraft.getInstance().level != null) {
            selectedLifeformId = state.selectedOrFirstMetId(Minecraft.getInstance().level);
        }

        int maxPage = Math.max((visibleChoices.size() - 1) / pageSize(), 0);
        if (page > maxPage) {
            page = maxPage;
        }

        for (int slot = 0; slot < choiceButtons.size(); slot++) {
            Button button = choiceButtons.get(slot);
            int choiceIndex = page * pageSize() + slot;
            if (choiceIndex < visibleChoices.size()) {
                EntitySubtype<?> choice = visibleChoices.get(choiceIndex);
                String lifeformId = choice.getId().toString();
                choiceButtonIds.set(slot, lifeformId);
                button.visible = true;
                button.active = !lifeformId.equals(selectedLifeformId);
                button.setMessage(choiceButtonLabel(state, choice, lifeformId.equals(selectedLifeformId), choiceButtonWidth() - 8));
            }
            else {
                choiceButtonIds.set(slot, null);
                button.visible = false;
                button.active = false;
                button.setMessage(Component.empty());
            }
        }

        boolean hasSelectedMet = state != null && selectedLifeformId != null && state.hasMetLifeform(selectedLifeformId);
        if (doneButton != null) {
            doneButton.active = hasSelectedMet;
        }
        if (favoriteButton != null) {
            favoriteButton.active = hasSelectedMet;
            boolean favorite = hasSelectedMet && state.isFavorite(selectedLifeformId);
            favoriteButton.setMessage(Component.translatable(favorite
                    ? "jojo_ripples.lifeform.unfavorite"
                    : "jojo_ripples.lifeform.favorite"));
        }
        if (clearNewButton != null) {
            clearNewButton.active = state != null && state.hasAnyNewUnseenLifeforms();
        }
        if (previousPageButton != null) {
            previousPageButton.active = page > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = (page + 1) * pageSize() < visibleChoices.size();
        }
        if (clearSearchButton != null) {
            clearSearchButton.active = searchBox != null && !searchBox.getValue().isEmpty();
        }
        if (unlockAllButton != null) {
            Minecraft mc = Minecraft.getInstance();
            unlockAllButton.visible = mc.player != null && mc.player.getAbilities().instabuild;
        }
        if (allFilterButton != null) {
            allFilterButton.active = savedFilterMode != FilterMode.ALL;
        }
        if (favoriteFilterButton != null) {
            favoriteFilterButton.active = savedFilterMode != FilterMode.FAVORITES;
        }
        if (newFilterButton != null) {
            newFilterButton.active = savedFilterMode != FilterMode.NEW;
        }
        if (viewModeButton != null) {
            viewModeButton.setMessage(Component.translatable("jojo.ge_lifeform.view.toggle", savedViewMode.label()));
        }
    }

    private List<EntitySubtype<?>> filterChoices(List<EntitySubtype<?>> choices, GoldExperienceLifeformState state) {
        return choices.stream()
                .filter(choice -> filterModeMatches(state, choice))
                .filter(choice -> searchMatches(choice, savedSearchFilter))
                .toList();
    }

    private static boolean filterModeMatches(GoldExperienceLifeformState state, EntitySubtype<?> choice) {
        String id = choice.getId().toString();
        return switch (savedFilterMode) {
            case ALL -> true;
            case FAVORITES -> state.isFavorite(id);
            case NEW -> state.isNewUnseen(id);
        };
    }

    private static boolean searchMatches(EntitySubtype<?> choice, String rawSearch) {
        if (rawSearch.isBlank()) {
            return true;
        }

        String[] words = rawSearch.toLowerCase(Locale.ROOT).split("\\s+");
        List<String> modFilters = new ArrayList<>();
        StringBuilder nameFilter = new StringBuilder();
        boolean maybeTypingKeyword = !rawSearch.endsWith(" ");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.startsWith("mod:")) {
                String modSearch = word.substring("mod:".length());
                if (!modSearch.isBlank()) {
                    modFilters.add(modSearch);
                }
            }
            else {
                boolean isTypingKeyword = maybeTypingKeyword && i == words.length - 1 && "mod".startsWith(word);
                if (!isTypingKeyword) {
                    if (nameFilter.length() > 0) {
                        nameFilter.append(' ');
                    }
                    nameFilter.append(word);
                }
            }
        }

        String nameNeedle = nameFilter.toString();
        String description = choice.getDescription().getString().toLowerCase(Locale.ROOT);
        String id = choice.getId().toString().toLowerCase(Locale.ROOT);
        boolean nameMatches = nameNeedle.isBlank() || description.contains(nameNeedle) || id.contains(nameNeedle);

        String modName = modName(choice).toLowerCase(Locale.ROOT);
        boolean modMatches = modFilters.isEmpty() || modFilters.stream().anyMatch(modName::contains);
        return nameMatches && modMatches;
    }

    private static String modName(EntitySubtype<?> choice) {
        String namespace = choice.getId().withoutSubtype().getNamespace();
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    @Nullable
    private static GoldExperienceLifeformState playerState() {
        return Minecraft.getInstance().player != null
                ? GoldExperienceLifeformState.get(Minecraft.getInstance().player)
                : null;
    }

    private int pageSize() {
        return savedViewMode == ViewMode.GRID ? GRID_PAGE_SIZE : LIST_PAGE_SIZE;
    }

    private int choiceButtonWidth() {
        return savedViewMode == ViewMode.GRID ? 60 : 120;
    }

    private int choiceButtonHeight() {
        return savedViewMode == ViewMode.GRID ? 20 : 20;
    }

    private int choiceButtonX(int slot) {
        if (savedViewMode == ViewMode.GRID) {
            int gridWidth = GRID_COLUMNS * choiceButtonWidth() + (GRID_COLUMNS - 1) * 4;
            return width / 2 - gridWidth / 2 + (slot % GRID_COLUMNS) * (choiceButtonWidth() + 4);
        }

        boolean leftColumn = slot < LIST_ROWS_PER_COLUMN;
        return leftColumn ? width / 2 - 125 : width / 2 + 5;
    }

    private int choiceButtonY(int slot) {
        if (savedViewMode == ViewMode.GRID) {
            return height / 2 - 18 + (slot / GRID_COLUMNS) * 22;
        }

        int row = slot < LIST_ROWS_PER_COLUMN ? slot : slot - LIST_ROWS_PER_COLUMN;
        return height / 2 - 22 + row * 22;
    }

    private Component choiceButtonLabel(@Nullable GoldExperienceLifeformState state, EntitySubtype<?> choice, boolean selected, int maxWidth) {
        String id = choice.getId().toString();
        String prefix = selected ? "> " : "";
        if (state != null && state.isFavorite(id)) {
            prefix += "* ";
        }
        if (state != null && state.isNewUnseen(id)) {
            prefix += "! ";
        }
        String name = choice.getDescription().getString();
        String trimmed = font.plainSubstrByWidth(name, Math.max(12, maxWidth - font.width(prefix)));
        return Component.literal(prefix + trimmed);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(font, title, width / 2, height / 2 - 96, 0xFFFFFF);
        Component selectedText = visibleChoices.isEmpty() || selectedLifeformId == null
                ? Component.translatable("jojo_ripples.lifeform.none")
                : Component.translatable("jojo_ripples.lifeform.selected", GoldExperienceLifeforms.displayName(selectedLifeformId));
        gui.drawCenteredString(font, selectedText, width / 2, height / 2 + 90, 0xFFFFFF);
        if (!holdsButton && holdMode) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (savedViewMode == ViewMode.GRID && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            for (int slot = 0; slot < choiceButtons.size(); slot++) {
                Button choiceButton = choiceButtons.get(slot);
                String lifeformId = choiceButtonIds.get(slot);
                if (choiceButton.visible && lifeformId != null && choiceButton.isMouseOver(mouseX, mouseY)) {
                    toggleFavorite(lifeformId);
                    return true;
                }
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return closeOnSecondClick(ClientKey.make(InputConstants.Type.MOUSE, button));
    }

    private void toggleFavorite(String lifeformId) {
        GoldExperienceLifeformState currentState = playerState();
        if (currentState == null || !currentState.hasMetLifeform(lifeformId)) {
            return;
        }
        if (currentState.isFavorite(lifeformId)) {
            PacketDistributor.sendToServer(ClGELifeformUiPacket.removeFavorite(lifeformId));
            currentState.setFavoriteFromSync(lifeformId, false);
        }
        else {
            PacketDistributor.sendToServer(ClGELifeformUiPacket.addFavorite(lifeformId));
            currentState.setFavoriteFromSync(lifeformId, true);
        }
        updateChoiceButtons();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closeOnSecondClick(ClientKey.make(keyCode == GLFW.GLFW_KEY_UNKNOWN
                ? InputConstants.Type.SCANCODE
                : InputConstants.Type.KEYSYM, keyCode == GLFW.GLFW_KEY_UNKNOWN ? scanCode : keyCode))) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean closeOnSecondClick(ClientKey keyPressed) {
        if (!holdMode && keyHeld != null && keyHeld.equals(keyPressed)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        GoldExperienceLifeformState state = playerState();
        if (state != null && state.hasAnyNewUnseenLifeforms()) {
            PacketDistributor.sendToServer(ClGELifeformUiPacket.clearUnseen());
            state.clearNewUnseenLifeformsFromSync();
        }
        super.onClose();
    }
}
