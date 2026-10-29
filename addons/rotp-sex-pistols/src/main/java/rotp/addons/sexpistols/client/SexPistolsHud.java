package rotp.addons.sexpistols.client;

import java.util.Locale;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.state.RevolverMemberAttachments;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers.ShotType;
import rotp.addons.sexpistols.content.revolver.state.RevolverState;
import rotp.addons.sexpistols.content.revolver.state.RevolverState.Chamber;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class SexPistolsHud implements LayeredDraw.Layer {
    private static final ResourceLocation AMMO_EMPTY =
            SexPistolsIds.id("textures/gui/ammo_empty.png");
    private static final ResourceLocation AMMO_LIVE =
            SexPistolsIds.id("textures/gui/ammo_shell.png");
    private static final ResourceLocation AMMO_SPENT =
            SexPistolsIds.id(
                    "textures/gui/ammo_shell_casing.png");
    private static final ResourceLocation AMMO_HIGHLIGHT =
            SexPistolsIds.id(
                    "textures/gui/ammo_highlight.png");
    private static final ResourceLocation
            AMMO_HIGHLIGHT_ENCIRCLEMENT_SELECTED =
                    SexPistolsIds.id(
                            "textures/gui/ammo_highlight_big.png");
    private static final ResourceLocation
            AMMO_HIGHLIGHT_ENCIRCLEMENT =
                    SexPistolsIds.id(
                            "textures/gui/"
                                    + "ammo_highlight_encirclement.png");
    private static final ResourceLocation
            AMMO_HIGHLIGHT_PIERCING =
                    SexPistolsIds.id(
                            "textures/gui/"
                                    + "ammo_highlight_piercing.png");
    private static final ResourceLocation
            AMMO_HIGHLIGHT_SPLITTING =
                    SexPistolsIds.id(
                            "textures/gui/"
                                    + "ammo_highlight_splitting.png");
    private static final int ICON_SIZE = 16;
    private static final int PISTOL_ICON_SIZE = 10;
    private static final int ICON_STEP = 18;
    private static final int TOTAL_WIDTH = 106;

    @Override
    public void render(
            GuiGraphics graphics,
            DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui
                || minecraft.player == null) {
            return;
        }
        renderRevolver(
                graphics, minecraft, minecraft.player);
        renderSexPistols(
                graphics, minecraft, minecraft.player);
    }

    private static void renderRevolver(
            GuiGraphics graphics,
            Minecraft minecraft,
            Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(SexPistolsItems.REVOLVER.get())
                || !RevolverItem.state(stack).reloadMode()) {
            return;
        }
        RevolverState state = RevolverItem.state(stack);
        int x = (graphics.guiWidth() - TOTAL_WIDTH) / 2;
        // Vanilla's selected-item name starts 59 GUI pixels above the bottom.
        int y = graphics.guiHeight() - 64 - ICON_SIZE;
        int renderedMemberIcons = 0;
        int remappedMemberIcons = 0;
        SexPistolsCoordinator coordinator =
                player.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)
                        ? SexPistolsAttachments.coordinator(player)
                        : null;
        for (int chamber = 0;
                chamber < RevolverState.CHAMBER_COUNT;
                chamber++) {
            int iconX = x + chamber * ICON_STEP;
            graphics.blit(
                    AMMO_EMPTY,
                    iconX,
                    y,
                    0,
                    0,
                    ICON_SIZE,
                    ICON_SIZE,
                    ICON_SIZE,
                    ICON_SIZE);
            ResourceLocation chamberTexture =
                    switch (state.chamber(chamber)) {
                        case LIVE -> AMMO_LIVE;
                        case SPENT -> AMMO_SPENT;
                        case EMPTY -> null;
                    };
            if (chamberTexture != null) {
                graphics.blit(
                        chamberTexture,
                        iconX,
                        y,
                        0,
                        0,
                        ICON_SIZE,
                        ICON_SIZE,
                        ICON_SIZE,
                        ICON_SIZE);
            }
            // 1.16 (RevolverAmmoHudClientEvents) drew every special round primed in the chamber, and for the selected
            // chamber the big encirclement frame, else the piercing, else the splitting highlight, else the plain one.
            // A chamber can carry more than one flag; the port had drawn only the one the chamber fires with.
            boolean encirclement = RevolverSpecialChambers.has(
                    stack, chamber, ShotType.ENCIRCLEMENT);
            boolean piercing = RevolverSpecialChambers.has(
                    stack, chamber, ShotType.PIERCING);
            boolean splitting = RevolverSpecialChambers.has(
                    stack, chamber, ShotType.SPLITTING);
            if (encirclement) {
                blitIcon(graphics, AMMO_HIGHLIGHT_ENCIRCLEMENT, iconX, y);
            }
            if (piercing) {
                blitIcon(graphics, AMMO_HIGHLIGHT_PIERCING, iconX, y);
            }
            if (splitting) {
                blitIcon(graphics, AMMO_HIGHLIGHT_SPLITTING, iconX, y);
            }
            if (chamber == state.selectedChamber()) {
                blitIcon(
                        graphics,
                        encirclement
                                ? AMMO_HIGHLIGHT_ENCIRCLEMENT_SELECTED
                                : piercing
                                        ? AMMO_HIGHLIGHT_PIERCING
                                        : splitting
                                                ? AMMO_HIGHLIGHT_SPLITTING
                                                : AMMO_HIGHLIGHT,
                        iconX,
                        y);
            }
            int memberMask = RevolverMemberAttachments
                    .chamberMemberMask(stack, chamber);
            int renderedInChamber = 0;
            for (int slot = 0;
                    slot < SexPistolsIds.SEX_PISTOLS_NUMBERS.length;
                    slot++) {
                if ((memberMask & 1 << slot) == 0) {
                    continue;
                }
                int row = renderedInChamber / 2;
                int column = renderedInChamber % 2;
                int iconsInRow = Math.min(
                        2,
                        Integer.bitCount(memberMask) - row * 2);
                int iconXInChamber = iconX
                        + (ICON_SIZE
                                - iconsInRow * PISTOL_ICON_SIZE)
                                / 2
                        + column * PISTOL_ICON_SIZE;
                int iconY = y - 2
                        - (row + 1) * PISTOL_ICON_SIZE;
                SexPistolsClientTextures.ResolvedTexture icon =
                        SexPistolsClientTextures.memberIcon(
                                coordinator != null
                                        ? coordinator.skin()
                                        : java.util.Optional.empty(),
                                SexPistolsIds
                                        .SEX_PISTOLS_NUMBERS[slot]);
                graphics.blit(
                        icon.texture(),
                        iconXInChamber,
                        iconY,
                        0,
                        0,
                        PISTOL_ICON_SIZE,
                        PISTOL_ICON_SIZE,
                        PISTOL_ICON_SIZE,
                        PISTOL_ICON_SIZE);
                renderedInChamber++;
                renderedMemberIcons++;
                if (icon.remapped()) {
                    remappedMemberIcons++;
                }
            }
        }
        SexPistolsPresentationDiagnostics.recordHud(
                renderedMemberIcons, remappedMemberIcons);
        if (state.reloadMode()) {
            Component reload = Component.translatable(
                    "rotp_sex_pistols.hud.revolver.reload");
            graphics.drawString(
                    minecraft.font,
                    reload,
                    (graphics.guiWidth()
                            - minecraft.font.width(reload))
                            / 2,
                    y - 10,
                    0xFFE4A64B,
                    true);
        }
    }

    private static void blitIcon(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y) {
        graphics.blit(
                texture,
                x,
                y,
                0,
                0,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE);
    }

    private static void renderSexPistols(
            GuiGraphics graphics,
            Minecraft minecraft,
            Player player) {
        StandPower standPower = StandPower.get(player);
        if (standPower == null
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !player.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return;
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(player);
        ResourceLocation selectedIcon =
                SexPistolsClientTextures.memberIcon(
                        coordinator.skin(),
                        coordinator.selectedNumber())
                        .texture();
        Component text = Component.translatable(
                "rotp_sex_pistols.hud.sex_pistols",
                coordinator.selectedNumber(),
                Component.translatable(
                        "rotp_sex_pistols.sex_pistols.target."
                                + coordinator.targetMode()
                                        .name()
                                        .toLowerCase(
                                                Locale.ROOT)),
                Component.translatable(
                        "rotp_sex_pistols.sex_pistols.transfer."
                                + coordinator.transferOrder()
                                        .name()
                                        .toLowerCase(
                                                Locale.ROOT)));
        int textX = graphics.guiWidth()
                - minecraft.font.width(text) - 8;
        graphics.blit(
                selectedIcon,
                Math.max(4, textX - ICON_SIZE - 3),
                4,
                0,
                0,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE);
        graphics.drawString(
                minecraft.font,
                text,
                textX,
                8,
                0xFFF4D96A,
                true);
    }

}
