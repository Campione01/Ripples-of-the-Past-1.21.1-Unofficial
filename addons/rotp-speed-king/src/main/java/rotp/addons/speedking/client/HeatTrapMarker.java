package rotp.addons.speedking.client;

import java.util.List;

import rotp.core.client.ClientGlobals;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.speedking.SpeedKingAddon;
import rotp.addons.speedking.state.SpeedKingState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class HeatTrapMarker extends MarkerRenderer {
    public HeatTrapMarker(Minecraft minecraft) {
        super(
                SpeedKingAddon.id(
                        "textures/action/heat_trap_icon.png"),
                minecraft);
    }

    @Override
    protected boolean shouldRender() {
        if (mc.player == null
                || mc.level == null
                || !ClientGlobals.canSeeStands
                || !SpeedKingAddon.isUser(mc.player)) {
            return false;
        }
        SpeedKingState state = SpeedKingAddon.state(mc.player);
        return state.hasHeatTrap()
                && state.heatTrapDimension().equals(
                        mc.level.dimension().location().toString());
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        SpeedKingState state = SpeedKingAddon.state(mc.player);
        list.add(new MarkerInstance(
                Vec3.atCenterOf(state.heatTrapPos()), true));
    }
}
