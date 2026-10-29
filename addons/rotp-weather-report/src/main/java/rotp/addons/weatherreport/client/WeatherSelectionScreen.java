package rotp.addons.weatherreport.client;

import java.util.UUID;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.network.OpenWeatherSelectionPayload;
import rotp.addons.weatherreport.network.SelectWeatherPayload;
import rotp.addons.weatherreport.network.WeatherChoice;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WeatherSelectionScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            WeatherReportAddon.id(
                    "textures/gui/better_weather_change_gui.png");
    private static final int TEXTURE_SIZE = 128;
    private static final int SLOT_SIZE = 25;
    private static final int SLOT_GAP = 3;
    private static final int SLOT_U = 0;
    private static final int SLOT_V = 0;
    private static final int SELECTED_U = 25;
    private static final int SELECTED_V = 0;

    private final UUID sessionId;
    private final long expiryGameTick;
    private final byte allowedChoices;

    public WeatherSelectionScreen(
            OpenWeatherSelectionPayload payload) {
        super(Component.translatable(
                "rotp_wr.select_weather_type"));
        sessionId = payload.sessionId();
        expiryGameTick = payload.expiryGameTick();
        allowedChoices = payload.allowedChoices();
    }

    public boolean hasSession(UUID candidateSessionId) {
        return sessionId.equals(candidateSessionId);
    }

    public UUID sessionId() {
        return sessionId;
    }

    @Override
    protected void init() {
        int count = 0;
        for (WeatherChoice choice : WeatherChoice.values()) {
            if (isAllowed(choice)) {
                count++;
            }
        }
        int totalWidth = count * SLOT_SIZE
                + Math.max(0, count - 1) * SLOT_GAP;
        int x = (width - totalWidth) / 2;
        int y = (height - SLOT_SIZE) / 2;
        for (WeatherChoice choice : WeatherChoice.values()) {
            if (!isAllowed(choice)) {
                continue;
            }
            addRenderableWidget(new WeatherButton(
                    x,
                    y,
                    choice,
                    this::select));
            x += SLOT_SIZE + SLOT_GAP;
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
        // both 1.16 weather-change menus enabled blending before the slots; a plain blit does not blend by itself and
        // the slot frames are semi-transparent
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        super.render(graphics, mouseX, mouseY, partialTick);
        RenderSystem.disableBlend();
        graphics.drawCenteredString(
                font,
                title,
                width / 2,
                height / 2 + SLOT_SIZE / 2 + 12,
                0xFFFFFF);
        // both 1.16 menus named the hovered weather above the slots
        for (var child : children()) {
            if (child instanceof WeatherButton button
                    && button.isHoveredOrFocused()) {
                graphics.drawCenteredString(
                        font,
                        button.getMessage(),
                        width / 2,
                        height / 2 - 50,
                        0xFFFFFF);
                break;
            }
        }
    }

    /**
     * The 1.16 weather-change menus, copies of the host's game-mode switcher, drew no background: the world stays clear
     * behind the slots. In 1.21 Screen.render draws the blurred, dimmed background by itself, and the port also called
     * it first, so it ran twice; it is switched off here the way the host's own GameModeSwitcherScreen does it.
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

    private boolean isAllowed(WeatherChoice choice) {
        return (allowedChoices & choice.mask()) != 0;
    }

    private void select(WeatherChoice choice) {
        if (!isAllowed(choice)) {
            return;
        }
        PacketDistributor.sendToServer(
                new SelectWeatherPayload(
                        sessionId,
                        choice.networkId()));
        onClose();
    }

    private static final class WeatherButton extends Button {
        private final WeatherChoice choice;

        private WeatherButton(
                int x,
                int y,
                WeatherChoice choice,
                java.util.function.Consumer<WeatherChoice> onSelect) {
            super(Button.builder(
                            label(choice),
                            button -> onSelect.accept(choice))
                    .bounds(x, y, SLOT_SIZE, SLOT_SIZE));
            this.choice = choice;
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            graphics.blit(
                    TEXTURE,
                    getX(),
                    getY(),
                    SLOT_U,
                    SLOT_V,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE);
            int[] icon = iconUv(choice);
            graphics.blit(
                    TEXTURE,
                    getX(),
                    getY(),
                    icon[0],
                    icon[1],
                    SLOT_SIZE,
                    SLOT_SIZE,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE);
            if (isHoveredOrFocused()) {
                graphics.blit(
                        TEXTURE,
                        getX(),
                        getY(),
                        SELECTED_U,
                        SELECTED_V,
                        SLOT_SIZE,
                        SLOT_SIZE,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE);
            }
        }

        private static Component label(WeatherChoice choice) {
            return Component.translatable(
                    "rotp_wr.weather_type."
                            + choice.name().toLowerCase(
                                    java.util.Locale.ROOT));
        }

        private static int[] iconUv(WeatherChoice choice) {
            return switch (choice) {
                case CLEAR -> new int[] {3, 48};
                case RAIN -> new int[] {3, 71};
                case THUNDER -> new int[] {2, 26};
            };
        }
    }
}
