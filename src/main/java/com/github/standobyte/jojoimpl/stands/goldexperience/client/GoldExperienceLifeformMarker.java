package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojoimpl.stands.goldexperience.GECreatedLifeformEffect;
import com.github.standobyte.jojoimpl.stands.goldexperience.GETransformationEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public class GoldExperienceLifeformMarker extends MarkerRenderer {
    public static final double LIFEFORM_MARKER_RANGE = 128.0;

    public GoldExperienceLifeformMarker(Minecraft mc) {
        super(JojoMod.resLoc("textures/icons/soul_cloud.png"), mc);
        renderThroughBlocks = false;
        useStandSkinColor = true;
    }

    @Override
    protected boolean shouldRender() {
        return !GoldExperienceLifeformRevertMarker.shouldShowRevertMarkers();
    }

    @Override
    protected void updatePositions(List<MarkerInstance> list, float partialTick) {
        updateGELifeformMarkers(list, partialTick, mc, false);
    }

    @Override
    protected void renderIcon(PoseStack poseStack, MarkerInstance marker, float partialTick, StandSkin standSkin) {
    }

    @Override
    protected void renderIconOnBorder(PoseStack poseStack, MarkerInstance marker, float partialTick) {
        getCreatedLifeformTarget(marker).ifPresent(target -> {
            if (target instanceof GETransformationEntity transformation) {
                Entity transformationTarget = transformation.getTransformationTarget();
                if (transformationTarget != null) {
                    renderTransformationProgress(poseStack, transformation, partialTick);
                    EntityTypeIcon.renderIcon(transformationTarget.getType(), poseStack, 0, 0);
                }
            }
            else {
                EntityTypeIcon.renderIcon(target.getType(), poseStack, 0, 0);
            }
        });
    }

    private static void renderTransformationProgress(PoseStack poseStack, GETransformationEntity transformation, float partialTick) {
        int duration = transformation.getDuration();
        if (duration <= 0) {
            return;
        }
        float ratio = Math.min(transformation.getTfProgressTime(partialTick) / duration, 1.0F);
        BlitFloat.blitRadial(poseStack, Minecraft.getInstance(), MarkerRenderer.MARKER_BORDER.file,
                -8, -4, 32, 32, 0, -(float) Math.PI / 2, ratio, 0x99FFFFFF);
    }

    static void updateGELifeformMarkers(List<MarkerInstance> list, float partialTick, Minecraft mc, boolean highlightLookedAt) {
        fillWithStandEffectTargets(list, partialTick, ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get(),
                LIFEFORM_MARKER_RANGE, mc, highlightLookedAt);

        for (MarkerInstance marker : list) {
            getCreatedLifeformTarget(marker).ifPresent(target -> {
                if (target instanceof GETransformationEntity transformation) {
                    Entity transformationTarget = transformation.getTransformationTarget();
                    if (transformationTarget != null) {
                        setMarkerPos(marker, transformation.getPosition(partialTick)
                                .add(0, transformationTarget.getBbHeight() * 1.1, 0));
                    }
                }
            });
        }
    }

    static Optional<Entity> getCreatedLifeformTarget(MarkerInstance marker) {
        return getMarkerStandEffect(marker)
                .filter(GECreatedLifeformEffect.class::isInstance)
                .map(GECreatedLifeformEffect.class::cast)
                .map(GECreatedLifeformEffect::getTarget);
    }
}
