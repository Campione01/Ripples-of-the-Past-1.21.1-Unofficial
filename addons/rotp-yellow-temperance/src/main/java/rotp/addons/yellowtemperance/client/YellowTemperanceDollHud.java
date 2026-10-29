package rotp.addons.yellowtemperance.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The 1.16 "Check Disguise Status" doll ({@code com.TheChaYe.rotp_yellowtemperance.events.DollRenderer}): while
 * Yellow Temperance is out and the doll toggle is on, the player's own model — wearing whatever disguise is active —
 * is drawn in first person at 50% of the screen width and 90% of its height, 10 GUI pixels per block, facing the
 * viewer, full-bright and without a shadow.
 *
 * <p>Drawn through the vanilla inventory entity path. Its depth flip keeps the model's handedness under the 1.21 GUI
 * projection (the donor's unflipped transform shows a mirrored doll there), so the pose turns an extra half circle
 * to keep the front toward the viewer.</p>
 */
public final class YellowTemperanceDollHud
        implements LayeredDraw.Layer {
    private static final float X_POS = 0.5F;
    private static final float Y_POS = 0.9F;
    private static final float SCALE = 10.0F;

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui
                || player == null
                || player.isSpectator()
                || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(
                        player,
                        deltaTracker.getGameTimeDeltaPartialTick(false));
        if (state == null
                || !state.controller().isDollEnabled()) {
            return;
        }
        Quaternionf pose = new Quaternionf()
                .rotateZ(Mth.PI)
                .rotateY((player.getYRot() + 180.0F) * Mth.DEG_TO_RAD);
        InventoryScreen.renderEntityInInventory(
                graphics,
                graphics.guiWidth() * X_POS,
                graphics.guiHeight() * Y_POS,
                SCALE,
                new Vector3f(),
                pose,
                null,
                player);
    }
}
