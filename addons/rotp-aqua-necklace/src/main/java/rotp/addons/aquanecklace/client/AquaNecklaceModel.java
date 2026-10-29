package rotp.addons.aquanecklace.client;

import rotp.core.client.entityrender.ModelPartWithName;
import rotp.core.client.entityrender.ModelWithExtraFeatures;
import rotp.core.client.entityrender.stand.StandEntityModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.aquanecklace.entity.AquaNecklaceEntity;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Keeps the liquid puddle visible in every Stand visibility mode.
 *
 * <p>The 1.16 donor parented the 12x12x12 {@code water} cube directly to
 * {@code body}, and {@code HumanoidStandModel.updatePartsVisibility} only ever
 * toggled {@code head}, {@code torso}, the arms and the legs. {@code body} was
 * never hidden, so the puddle drew in ARMS_ONLY and NONE just as it did in ALL.
 *
 * <p>The current core hides the whole model and re-shows only the subtrees
 * named by {@code HumanoidPart}, so no geometry parent can reproduce "always
 * visible". This model re-shows the {@code water} part and its ancestor chain
 * after the core visibility pass, the same way the Weather Report module
 * re-shows its cloud subtree. Every ancestor on that chain is cubeless, so
 * forcing it visible adds no geometry of its own.
 */
public final class AquaNecklaceModel
        extends StandEntityModel<
                AquaNecklaceEntity, AquaNecklaceRenderState> {
    private static final String WATER_PART = "water";

    private ModelPartWithName[] waterPath;
    private boolean waterPathResolved;

    public AquaNecklaceModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(AquaNecklaceRenderState renderState) {
        super.setupAnim(renderState);
        applyWaterVisibility();
    }

    @Override
    public void renderGeometryToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        applyWaterVisibility();
        super.renderGeometryToBuffer(
                poseStack, buffer, packedLight, packedOverlay, color);
    }

    @Override
    public void renderClassicLayerToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        applyWaterVisibility();
        super.renderClassicLayerToBuffer(
                poseStack, buffer, packedLight, packedOverlay, color);
    }

    private void applyWaterVisibility() {
        for (ModelPartWithName entry : waterPath()) {
            StandEntityModel.setVisible(entry.part(), true);
        }
    }

    private ModelPartWithName[] waterPath() {
        if (!waterPathResolved) {
            waterPath = ((ModelWithExtraFeatures) (Object) this)
                    .jojo_ripples$getPathToModelPart(WATER_PART);
            waterPathResolved = true;
        }
        return waterPath != null
                ? waterPath
                : new ModelPartWithName[0];
    }
}
