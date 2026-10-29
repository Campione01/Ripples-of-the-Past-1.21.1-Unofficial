package rotp.addons.thesun.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.thesun.entity.TheSunEntity;
import net.minecraft.client.model.geom.ModelPart;

public final class TheSunModel extends StandEntityModel<
        TheSunEntity, StandEntityRenderState> {
    private final ModelPart core;
    private final ModelPart outline;

    public TheSunModel(ModelPart root) {
        super(root);
        Model_1_21_2plus model =
                (Model_1_21_2plus) (Object) this;
        core = model.jojo_ripples$getAnyDescendantWithName(
                        "the_sun")
                .orElse(null);
        outline = model.jojo_ripples$getAnyDescendantWithName(
                        "outline")
                .orElse(null);
    }

    @Override
    public void setupAnim(StandEntityRenderState renderState) {
        // The Sun has no humanoid parts for the base visibility mask to restore.
        super.setupAnim(renderState);
        setAllVisible(true);
    }

    @Override
    public void renderGeometryToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        boolean outlineVisible =
                outline != null && outline.visible;
        if (outline != null) {
            outline.visible = false;
        }
        try {
            super.renderGeometryToBuffer(
                    poseStack,
                    buffer,
                    packedLight,
                    packedOverlay,
                    color);
        }
        finally {
            if (outline != null) {
                outline.visible = outlineVisible;
            }
        }
    }

    public void renderOutlineToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        boolean coreSkipDraw = core != null && core.skipDraw;
        boolean outlineVisible =
                outline != null && outline.visible;
        if (core != null) {
            core.skipDraw = true;
        }
        if (outline != null) {
            outline.visible = true;
        }
        try {
            ((Model_1_21_2plus) (Object) this)
                    .jojo_ripples$root()
                    .render(
                            poseStack,
                            buffer,
                            packedLight,
                            packedOverlay,
                            color);
        }
        finally {
            if (core != null) {
                core.skipDraw = coreSkipDraw;
            }
            if (outline != null) {
                outline.visible = outlineVisible;
            }
        }
    }
}
