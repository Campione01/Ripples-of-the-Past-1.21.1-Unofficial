package rotp.addons.killerqueenlion.client;

import java.util.List;

import rotp.core.client.ClientGlobals;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class BubbleBlockMarkMarker extends MarkerRenderer {
    public BubbleBlockMarkMarker(Minecraft minecraft) {
        super(
                KillerQueenLionAddon.id(
                        "textures/action/bubble_block_bomb.png"),
                minecraft);
        renderThroughBlocks = true;
    }

    @Override
    protected boolean shouldRender() {
        return mc.player != null
                && mc.level != null
                && ClientGlobals.canSeeStands
                && KillerQueenLionAddon.isKillerQueenUser(mc.player)
                && KillerQueenLionAddon.state(mc.player)
                        .bubbleBlockDimension() != null
                && KillerQueenLionAddon.state(mc.player)
                        .bubbleBlockDimension()
                        .equals(mc.level.dimension().location())
                && !KillerQueenLionAddon.state(mc.player)
                        .bubbleBlockMarks().isEmpty();
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        for (BlockPos position
                : KillerQueenLionAddon.state(mc.player)
                        .bubbleBlockMarks()) {
            list.add(new MarkerInstance(
                    Vec3.atCenterOf(position), true));
        }
    }
}
