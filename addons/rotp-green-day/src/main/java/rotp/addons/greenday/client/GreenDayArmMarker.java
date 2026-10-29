package rotp.addons.greenday.client;

import java.util.List;

import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.greenday.GreenDayAddon;
import rotp.addons.greenday.entity.ShootingArmEntity;
import net.minecraft.client.Minecraft;

public final class GreenDayArmMarker extends MarkerRenderer {
    public static final double RANGE = 426.6666666666667D;

    public GreenDayArmMarker(Minecraft minecraft) {
        super(
                GreenDayAddon.id(
                        "textures/ability/gd_shot_left_arm.png"),
                minecraft);
        renderThroughBlocks = true;
    }

    @Override
    protected boolean shouldRender() {
        return mc.player != null
                && mc.level != null
                && GreenDayVisuals.canRender(
                        GreenDayVisuals.fromArmOwner(
                                mc.player,
                                mc.getTimer()
                                        .getGameTimeDeltaPartialTick(
                                                true)))
                && !arms().isEmpty();
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        for (ShootingArmEntity arm : arms()) {
            list.add(new MarkerInstance(
                    entityMarkerPos(arm, partialTick),
                    true));
        }
    }

    private List<ShootingArmEntity> arms() {
        if (mc.player == null || mc.level == null) {
            return List.of();
        }
        return mc.level.getEntitiesOfClass(
                ShootingArmEntity.class,
                mc.player.getBoundingBox().inflate(RANGE),
                arm -> arm.isAlive()
                        && arm.getOwner() == mc.player);
    }
}
