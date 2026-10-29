package rotp.addons.lovers.client;

import java.util.List;
import java.util.UUID;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.ui.hud_power.PowerHud;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.lovers.LoversAddon;
import rotp.addons.lovers.entity.LoversStandEntity;
import rotp.addons.lovers.state.LoversLinkState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

public final class LoversTargetMarker extends MarkerRenderer {
    public static final double MARKER_RANGE = 100.0D;

    public LoversTargetMarker(Minecraft minecraft) {
        super(
                LoversAddon.id("textures/power/lovers.png"),
                minecraft);
        renderThroughBlocks = true;
    }

    @Override
    protected boolean shouldRender() {
        LoversStandEntity stand = mc.player != null
                ? LoversAddon.activeStand(mc.player)
                : null;
        if (mc.level == null
                || !ClientGlobals.canSeeStands
                || stand == null
                || PowerHud.abilityHUDInstance == null) {
            return false;
        }

        LoversLinkState state = LoversAddon.linkState(mc.player);
        if (!state.isActive()
                || !mc.level.dimension().location().equals(
                        state.dimension())) {
            return false;
        }

        float lifecycleAlpha = stand.clientStuff != null
                ? stand.clientStuff.getAlpha(stand, 1.0F)
                : 1.0F;
        StandVisualContext visualContext =
                StandVisualContext.world(stand, lifecycleAlpha);
        return visualContext.effectiveAlpha() > 0.0F
                && (PowerHud.abilityHUDInstance
                        .isAbilitySelected(
                                LoversAddon.INTO_ENTITY_NAME)
                    || PowerHud.abilityHUDInstance
                        .isAbilitySelected(
                                LoversAddon.RETRACT_NAME));
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        UUID targetUuid =
                LoversAddon.linkState(mc.player).targetUuid();
        if (targetUuid == null) {
            return;
        }

        for (LivingEntity target
                : mc.level.getEntitiesOfClass(
                        LivingEntity.class,
                        mc.player.getBoundingBox()
                                .inflate(MARKER_RANGE),
                        entity -> targetUuid.equals(
                                entity.getUUID()))) {
            list.add(new MarkerInstance(
                    entityMarkerPos(target, partialTick), true));
        }
    }
}
