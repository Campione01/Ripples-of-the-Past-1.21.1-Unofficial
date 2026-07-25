package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.client.ClientPowerCache;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracker;
import com.github.standobyte.jojoimpl.stands.goldexperience.GEItemMarkEffect;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceMarkItemAbility;
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
