package rotp.addons.diverdown.client;

import java.util.List;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

public final class DiverDownInsideMarker extends MarkerRenderer {
    public DiverDownInsideMarker(Minecraft minecraft) {
        super(
                DiverDownAddon.id(
                        "textures/action/"
                                + "diver_down_entity_phasing.png"),
                minecraft);
    }

    @Override
    protected boolean shouldRender() {
        DiverDownStandEntity stand = mc.player != null
                ? DiverDownAddon.activeStand(mc.player)
                : null;
        if (!ClientGlobals.canSeeStands || stand == null) {
            return false;
        }
        float lifecycleAlpha = stand.clientStuff != null
                ? stand.clientStuff.getAlpha(stand, 1.0F)
                : 1.0F;
        StandVisualContext visualContext =
                StandVisualContext.world(
                        stand, lifecycleAlpha);
        return stand.isEmbedded()
                && visualContext.effectiveAlpha() > 0.0F;
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        DiverDownStandEntity stand =
                DiverDownAddon.activeStand(mc.player);
        LivingEntity target = stand != null
                ? stand.getEmbeddedTarget()
                : null;
        if (target != null) {
            list.add(new MarkerInstance(
                    entityMarkerPos(target, partialTick)));
        }
    }
}
