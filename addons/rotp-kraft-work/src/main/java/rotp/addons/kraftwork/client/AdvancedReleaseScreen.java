package rotp.addons.kraftwork.client;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;

import rotp.addons.kraftwork.KraftWorkAddon;
import rotp.addons.kraftwork.network.SelectReleasePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.16 AdvancedRelease: a 184x184 window with six 32x32 icon buttons in a ring. The window part of
 * admin_projectile_button.png is transparent; the art is the icon rows below it (v=192, hovered v=224), one
 * 32-pixel column per button, and a button's label only shows as its hover tooltip.
 */
public final class AdvancedReleaseScreen extends Screen {
    private static final ResourceLocation WINDOW =
            KraftWorkAddon.id(
                    "textures/gui/admin_projectile_button.png");
    private static final int WINDOW_SIZE = 184;
    private static final int WINDOW_BORDER = 10;
    private static final int ATLAS_SIZE = 256;
    private static final int ICON_SIZE = 32;
    private static final int ICON_V = 192;
    private static final int ICON_HOVERED_V = 224;
    private final UUID sessionId;

    public AdvancedReleaseScreen(UUID sessionId) {
        super(Component.translatable(
                "rotp_kraft_work.screen.advanced_release"));
        this.sessionId = Objects.requireNonNull(sessionId);
    }

    @Override
    protected void init() {
        int x = windowX();
        int y = windowY();
        // 1.16 AdvancedRelease.initWidgets: same order, offsets and atlas columns
        addReleaseButton(x + 5, y + 120, 96, 3, "ender_pearl");
        addReleaseButton(x + 5, y + 32, 0, 1, "ready");
        addReleaseButton(x + 76, y, 64, 0, "all");
        addReleaseButton(x + 147, y + 32, 32, 2, "not_ready");
        addReleaseButton(x + 147, y + 120, 128, 4, "beneficial");
        addRenderableWidget(new ReleaseIconButton(
                x + 76,
                y + 152,
                160,
                "exit",
                button -> onClose()));
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // 1.16 AdvancedRelease.renderWindow drew the window with blending on
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(
                WINDOW,
                windowX(),
                windowY(),
                0.0F,
                0.0F,
                WINDOW_SIZE,
                WINDOW_SIZE,
                ATLAS_SIZE,
                ATLAS_SIZE);
        RenderSystem.disableBlend();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 1.16 AdvancedRelease (the window over the world) drew no background: the world stays clear behind it. In 1.21
     * Screen.render draws the blurred, dimmed background by itself, and the port also called it first, so it ran
     * twice; it is switched off here the way the host's own GameModeSwitcherScreen does it.
     */
    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
    }

    /**
     * 1.16 AdvancedRelease.mouseClicked passed a click to its buttons only inside the window's 10-pixel border, so the
     * outer strip of the edge icons shows the hover state but does not press.
     */
    @Override
    public boolean mouseClicked(
            double mouseX, double mouseY, int button) {
        boolean pressed = mouseInsideWindow(mouseX, mouseY)
                && super.mouseClicked(mouseX, mouseY, button);
        setDragging(false);
        return pressed;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int windowX() {
        return (width - WINDOW_SIZE) / 2;
    }

    private int windowY() {
        return (height - WINDOW_SIZE) / 2;
    }

    private boolean mouseInsideWindow(double mouseX, double mouseY) {
        int x = windowX();
        int y = windowY();
        return mouseX > x + WINDOW_BORDER
                && mouseX < x + WINDOW_SIZE - WINDOW_BORDER
                && mouseY > y + WINDOW_BORDER
                && mouseY < y + WINDOW_SIZE - WINDOW_BORDER;
    }

    private void addReleaseButton(
            int x, int y, int texX, int selection, String key) {
        addRenderableWidget(new ReleaseIconButton(
                x, y, texX, key, button -> select(selection)));
    }

    private void select(int selection) {
        PacketDistributor.sendToServer(
                new SelectReleasePayload(sessionId, selection));
        onClose();
    }

    /** 1.16 AdvancedReleaseButton. */
    private final class ReleaseIconButton extends Button {
        private final int texX;

        private ReleaseIconButton(
                int x,
                int y,
                int texX,
                String key,
                Button.OnPress onPress) {
            super(
                    x,
                    y,
                    ICON_SIZE,
                    ICON_SIZE,
                    Component.translatable(
                            "rotp_kraft_work.screen."
                                    + "advanced_release."
                                    + key),
                    onPress,
                    DEFAULT_NARRATION);
            this.texX = texX;
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            // 1.16 isHovered() also counted keyboard focus; the label is the tooltip, drawn at the mouse
            boolean hovered = active && isHoveredOrFocused();
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            graphics.blit(
                    WINDOW,
                    getX(),
                    getY(),
                    texX,
                    hovered ? ICON_HOVERED_V : ICON_V,
                    getWidth(),
                    getHeight(),
                    ATLAS_SIZE,
                    ATLAS_SIZE);
            RenderSystem.disableBlend();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (hovered) {
                AdvancedReleaseScreen.this.setTooltipForNextRenderPass(
                        List.of(getMessage().getVisualOrderText()));
            }
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            soundManager.play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.5F, 0.1F));
        }
    }
}
