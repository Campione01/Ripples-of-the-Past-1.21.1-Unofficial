package rotp.addons.aquanecklace.client;

import java.util.List;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.client.ui.utils.GuiIcon;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.aquanecklace.AquaNecklaceAddon;
import rotp.addons.aquanecklace.entity.AquaNecklaceEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class AquaNecklaceHostMarker
        extends MarkerRenderer {
    private static final ResourceLocation MARKER_TEXTURE =
            AquaNecklaceAddon.id(
                    "textures/power/aqua_necklace.png");

    public AquaNecklaceHostMarker(Minecraft minecraft) {
        super(MARKER_TEXTURE, minecraft);
        renderThroughBlocks = true;
    }

    @Override
    protected boolean shouldRender() {
        AquaNecklaceEntity stand = mc.player != null
                ? AquaNecklaceAddon.activeStand(mc.player)
                : null;
        if (mc.level == null
                || !ClientGlobals.canSeeStands
                || stand == null) {
            return false;
        }
        float lifecycleAlpha = stand.clientStuff != null
                ? stand.clientStuff.getAlpha(stand, 1.0F)
                : 1.0F;
        StandVisualContext visualContext =
                StandVisualContext.world(
                        stand, lifecycleAlpha);
        return stand.isPossessing()
                && visualContext.effectiveAlpha() > 0.0F;
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        AquaNecklaceEntity stand =
                AquaNecklaceAddon.activeStand(mc.player);
        LivingEntity target = stand != null
                ? stand.getPossessedTarget()
                : null;
        if (target != null
                && !AquaNecklaceEntity
                        .isSkeletonHost(target)) {
            list.add(new MarkerInstance(
                    entityMarkerPos(target, partialTick)));
        }
    }

    @Override
    protected void renderIcon(
            PoseStack poseStack,
            MarkerInstance marker,
            float partialTick,
            StandSkin standSkin) {
        ResourceLocation texture = standSkin != null
                ? standSkin.getTexture(
                        MARKER_TEXTURE, MARKER_TEXTURE)
                : MARKER_TEXTURE;
        new GuiIcon(texture, 16, 16)
                .render(poseStack, 0, 0);
    }
}
