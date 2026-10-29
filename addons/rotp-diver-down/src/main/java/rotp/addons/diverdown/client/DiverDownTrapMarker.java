package rotp.addons.diverdown.client;

import java.util.List;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.KineticTrapEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class DiverDownTrapMarker extends MarkerRenderer {
    public DiverDownTrapMarker(Minecraft minecraft) {
        super(
                DiverDownAddon.id(
                        "textures/action/"
                                + "diver_down_kinetic_trap.png"),
                minecraft);
    }

    @Override
    protected boolean shouldRender() {
        return mc.player != null
                && mc.level != null
                && ClientGlobals.canSeeStands
                && DiverDownAddon.activeStand(mc.player) != null;
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof KineticTrapEntity trap)
                    || !mc.player.getUUID().equals(
                            trap.getOwnerUuid())) {
                continue;
            }
            StandVisualContext visualContext =
                    DiverDownAdditionalVisuals.ownerContext(
                            trap.level(),
                            trap.getOwnerUuid(),
                            partialTick);
            if (visualContext != null
                    && visualContext.effectiveAlpha() > 0.0F) {
                list.add(new MarkerInstance(
                        entityMarkerPos(trap, partialTick)));
            }
        }
    }
}
