package rotp.addons.stonefree.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import net.minecraft.client.model.geom.ModelPart;

public final class StoneFreeStandModel extends StandEntityModel<
        StoneFreeStandEntity, StoneFreeRenderState> {
    private final ModelPart forearm;
    private final ModelPart shortForearm;
    private boolean forearmPresent = true;
    private boolean shortForearmPresent;

    public StoneFreeStandModel(ModelPart root) {
        super(root);
        Model_1_21_2plus descendants =
                (Model_1_21_2plus) (Object) this;
        forearm = descendants
                .jojo_ripples$getAnyDescendantWithName(
                        "fore_arm")
                .orElse(null);
        shortForearm = descendants
                .jojo_ripples$getAnyDescendantWithName(
                        "short_fore_arm")
                .orElse(null);
    }

    @Override
    public void setupAnim(StoneFreeRenderState renderState) {
        super.setupAnim(renderState);
        forearmPresent = renderState.forearmPresent;
        shortForearmPresent = renderState.shortForearm;
        applyForearmVisibility();
    }

    @Override
    public void renderGeometryToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        applyForearmVisibility();
        super.renderGeometryToBuffer(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }

    @Override
    public void renderClassicLayerToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        applyForearmVisibility();
        super.renderClassicLayerToBuffer(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }

    private void applyForearmVisibility() {
        StandEntityModel.setVisible(forearm, forearmPresent);
        StandEntityModel.setVisible(
                shortForearm, shortForearmPresent);
    }
}
