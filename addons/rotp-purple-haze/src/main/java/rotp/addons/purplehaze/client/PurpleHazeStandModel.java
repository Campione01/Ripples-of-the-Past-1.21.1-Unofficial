package rotp.addons.purplehaze.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class PurpleHazeStandModel extends StandEntityModel<
        PurpleHazeEntity,
        PurpleHazeRenderState> {
    private final ModelPart[] capsules = new ModelPart[6];
    private int capsuleCount = PurpleHazeEntity.MAX_CAPSULES;

    public PurpleHazeStandModel(ModelPart root) {
        super(root);
        Model_1_21_2plus descendants =
                (Model_1_21_2plus) (Object) this;
        for (int index = 0; index < capsules.length; index++) {
            capsules[index] = descendants
                    .jojo_ripples$getAnyDescendantWithName(
                            "capsule" + (index + 1))
                    .orElse(null);
        }
    }

    @Override
    public void setupAnim(PurpleHazeRenderState renderState) {
        super.setupAnim(renderState);
        capsuleCount = Mth.clamp(
                renderState.capsuleCount,
                0,
                PurpleHazeEntity.MAX_CAPSULES);
        applyCapsuleVisibility();
    }

    @Override
    public void renderGeometryToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        applyCapsuleVisibility();
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
        applyCapsuleVisibility();
        super.renderClassicLayerToBuffer(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }

    private void applyCapsuleVisibility() {
        for (int index = 0; index < capsules.length; index++) {
            StandEntityModel.setVisible(
                    capsules[index],
                    capsuleCount > 5 - index);
        }
    }
}
