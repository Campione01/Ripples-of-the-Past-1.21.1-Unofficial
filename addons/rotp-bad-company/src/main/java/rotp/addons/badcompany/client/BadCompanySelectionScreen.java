package rotp.addons.badcompany.client;

import java.util.ArrayList;
import java.util.List;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import rotp.addons.badcompany.network.BadCompanySelectionKind;
import rotp.addons.badcompany.network.OpenBadCompanySelectionPayload;
import rotp.addons.badcompany.network.SetBadCompanySelectionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BadCompanySelectionScreen
        extends Screen {
    private static final int TEXTURE_SIZE = 128;
    private static final int PANEL_WIDTH = 125;
    private static final int PANEL_HEIGHT = 75;
    private static final int SLOT_SIZE = 25;
    private static final int SLOT_GAP = 5;
    private static final ResourceLocation MENU =
            BadCompanyAddon.id("textures/gui/troop_gui.png");

    private final BadCompanySelectionKind kind;
    private final int selected;
    private final int optionCount;
    private final List<SelectionButton> buttons =
            new ArrayList<>();
    private ResourceLocation menuTexture = MENU;

    public BadCompanySelectionScreen(
            OpenBadCompanySelectionPayload payload) {
        super(Component.translatable(
                payload.kind()
                                == BadCompanySelectionKind.UNIT
                                        .networkId()
                        ? "action.rotp_zbc.units"
                        : "action.rotp_zbc.random"));
        kind = BadCompanySelectionKind.fromNetworkId(
                        payload.kind())
                .orElseThrow();
        selected = payload.selected();
        optionCount = payload.optionCount();
    }

    @Override
    protected void init() {
        menuTexture = remap(MENU);
        buttons.clear();
        int totalWidth = optionCount * SLOT_SIZE
                + Math.max(0, optionCount - 1) * SLOT_GAP;
        int x = (width - totalWidth) / 2;
        int y = height / 2 - 30;
        for (int option = 0;
                option < optionCount;
                option++) {
            SelectionButton button =
                    new SelectionButton(
                            x,
                            y,
                            option,
                            option == selected,
                            optionLabel(option),
                            remap(optionIcon(option)));
            buttons.add(button);
            addRenderableWidget(button);
            x += SLOT_SIZE + SLOT_GAP;
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // 1.16 ChangeTroopScreen / ChangeFormationScreen enabled blending before the panel and the slots: the panel
        // texture is mostly semi-transparent, and a plain blit does not blend by itself
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = height / 2 - 57;
        graphics.blit(
                menuTexture,
                panelX,
                panelY,
                0,
                0,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
        super.render(
                graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(
                font,
                title,
                width / 2,
                height / 2 + 5,
                0xFFFFFF);
        for (SelectionButton button : buttons) {
            if (button.isHoveredOrFocused()) {
                graphics.drawCenteredString(
                        font,
                        button.getMessage(),
                        width / 2,
                        height / 2 - 50,
                        0xFFFFFF);
                break;
            }
        }
        RenderSystem.disableBlend();
    }

    /**
     * 1.16 ChangeTroopScreen, like the host's game-mode switcher it copies, drew no background: the world stays clear
     * behind the panel. In 1.21 Screen.render draws the blurred, dimmed background by itself, and the port also called
     * it first, so it ran twice (and the second pass blurred the panel drawn in between); it is switched off here the
     * way the host's own GameModeSwitcherScreen does it.
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

    private void select(int option) {
        if (option < 0 || option >= optionCount) {
            return;
        }
        PacketDistributor.sendToServer(
                new SetBadCompanySelectionPayload(
                        kind.networkId(), option));
        onClose();
    }

    private Component optionLabel(int option) {
        if (kind == BadCompanySelectionKind.UNIT) {
            return Component.translatable(switch (option) {
                case 0 -> "entity.rotp_zbc.all_c";
                case 1 -> "entity.rotp_zbc.bad_soldier";
                case 2 -> "entity.rotp_zbc.bad_tank";
                case 3 -> "entity.rotp_zbc.bad_helicopter";
                default -> "entity.rotp_zbc.all_c";
            });
        }
        return Component.translatable(switch (option) {
            case 1 -> "gui.rotp_zbc.circle";
            case 2 -> "gui.rotp_zbc.line";
            default -> "gui.rotp_zbc.random";
        });
    }

    private ResourceLocation optionIcon(int option) {
        String path;
        if (kind == BadCompanySelectionKind.UNIT) {
            path = switch (option) {
                case 1 -> "textures/action/soldier.png";
                case 2 -> "textures/action/tank.png";
                case 3 -> "textures/action/copter.png";
                default ->
                        "textures/power/bad_company.png";
            };
        } else {
            path = switch (option) {
                case 1 -> "textures/action/circle.png";
                case 2 -> "textures/action/line.png";
                default -> "textures/action/random.png";
            };
        }
        return BadCompanyAddon.id(path);
    }

    private ResourceLocation remap(
            ResourceLocation texture) {
        if (minecraft == null
                || minecraft.player == null) {
            return texture;
        }
        StandPower power = StandPower.get(minecraft.player);
        BadCompanyStandEntity coordinator =
                power != null
                        && power.getSummonedStandEntity()
                                instanceof BadCompanyStandEntity
                                        stand
                        ? stand
                        : null;
        return BadCompanyVisuals.texture(
                coordinator, texture);
    }

    private final class SelectionButton extends Button {
        private final boolean selectedButton;
        private final ResourceLocation icon;

        private SelectionButton(
                int x,
                int y,
                int option,
                boolean selectedButton,
                Component label,
                ResourceLocation icon) {
            super(Button.builder(
                            label,
                            button -> select(option))
                    .bounds(x, y, SLOT_SIZE, SLOT_SIZE));
            this.selectedButton = selectedButton;
            this.icon = icon;
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            graphics.blit(
                    menuTexture,
                    getX(),
                    getY(),
                    0,
                    75,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE);
            graphics.blit(
                    icon,
                    getX() + 4,
                    getY() + 4,
                    0,
                    0,
                    16,
                    16,
                    16,
                    16);
            if (selectedButton
                    || isHoveredOrFocused()) {
                graphics.blit(
                        menuTexture,
                        getX(),
                        getY(),
                        25,
                        75,
                        SLOT_SIZE,
                        SLOT_SIZE,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE);
            }
        }
    }
}
