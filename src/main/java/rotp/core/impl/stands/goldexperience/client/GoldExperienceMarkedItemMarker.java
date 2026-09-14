package rotp.core.impl.stands.goldexperience.client;

import java.util.List;
import java.util.Optional;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.itemtracking.ItemTracker;
import rotp.core.impl.stands.goldexperience.GEItemMarkEffect;
import rotp.core.impl.stands.goldexperience.GoldExperienceMarkItemAbility;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class GoldExperienceMarkedItemMarker extends MarkerRenderer {
    public GoldExperienceMarkedItemMarker(Minecraft mc) {
        super(JojoMod.resLoc("textures/icons/soul_cloud.png"), mc);
        renderThroughBlocks = false;
        useStandSkinColor = true;
    }

    @Override
    protected boolean shouldRender() {
        return true;
    }

    @Override
    protected void renderIcon(PoseStack poseStack, MarkerInstance marker, float partialTick, StandSkin standSkin) {
        getStandEffect(marker)
                .filter(GEItemMarkEffect.class::isInstance)
                .map(GEItemMarkEffect.class::cast)
                .map(effect -> effect.getItemTracker(true))
                .map(ItemTracker::getItem)
                .filter(item -> item != null && !item.isEmpty())
                .ifPresentOrElse(item -> renderItem(poseStack, item, partialTick),
                        () -> super.renderIcon(poseStack, marker, partialTick, standSkin));
    }

    @Override
    protected void updatePositions(List<MarkerInstance> list, float partialTick) {
        StandPower stand = ClientPowerCache.getPower(PowerClass.STAND);
        if (stand == null || mc.player == null || mc.level == null) {
            return;
        }

        GEItemMarkEffect outlined = GoldExperienceMarkItemAbility.getTargetedEffect(stand, mc.player);
        double rangeSqr = GoldExperienceMarkItemAbility.MARKED_ITEM_TARGET_RANGE
                * GoldExperienceMarkItemAbility.MARKED_ITEM_TARGET_RANGE;
        stand.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_ITEM_MARK.get()).forEach(effect -> {
            ItemTracker tracker = effect.getItemTracker(true);
            Vec3 markerPos = tracker != null ? tracker.getPos(mc.level, partialTick) : null;
            if (markerPos != null && markerPos.distanceToSqr(mc.player.position()) < rangeSqr) {
                list.add(new MarkerInstance(markerPos, effect == outlined, Optional.of(effect)));
            }
        });
    }
}
