package rotp.addons.killerqueen.client;

import java.util.List;
import java.util.UUID;

import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.state.KillerQueenState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

public final class BitesTheDustMarker extends MarkerRenderer {
    public static final double MARKER_RANGE = 100.0D;

    private static long resolvedMarkerFrames;

    public BitesTheDustMarker(Minecraft minecraft) {
        super(
                KillerQueenAddon.id(
                        "textures/power/bites_za_dust.png"),
                minecraft);
        renderThroughBlocks = true;
    }

    @Override
    protected boolean shouldRender() {
        if (mc.player == null
                || mc.level == null
                || !KillerQueenAddon.isKillerQueenUser(mc.player)
                || !mc.player.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        KillerQueenState state = KillerQueenAddon.state(mc.player);
        return state.hasBitesTheDustVictim()
                && mc.level.dimension().location().equals(
                        state.bitesTheDustVictimDimension());
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        UUID victimUuid = KillerQueenAddon.state(mc.player)
                .bitesTheDustVictimUuid();
        if (victimUuid == null) {
            return;
        }
        for (LivingEntity victim :
                mc.level.getEntitiesOfClass(
                        LivingEntity.class,
                        mc.player.getBoundingBox()
                                .inflate(MARKER_RANGE),
                        entity -> victimUuid.equals(
                                entity.getUUID()))) {
            list.add(new MarkerInstance(
                    entityMarkerPos(victim, partialTick), true));
            resolvedMarkerFrames++;
        }
    }

    public static long resolvedMarkerFrames() {
        return resolvedMarkerFrames;
    }
}
