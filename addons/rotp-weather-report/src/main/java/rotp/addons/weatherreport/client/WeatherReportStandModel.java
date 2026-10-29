package rotp.addons.weatherreport.client;

import rotp.core.client.entityrender.stand.HumanoidPart;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState.ObstructionRenderMode;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.weatherreport.entity.WeatherReportStandEntity;
import net.minecraft.client.model.geom.ModelPart;

public final class WeatherReportStandModel extends StandEntityModel<
        WeatherReportStandEntity, WeatherReportStandRenderState> {
    private static final String[] CLOUD_PART_NAMES = {
            "cloud", "cloud1", "cloud2", "cloud3", "cloud4"
    };

    private final ModelPart rightArmJoint;
    private final ModelPart[] cloudParts;
    private boolean icicleArmHidden;

    public WeatherReportStandModel(ModelPart root) {
        super(root);
        rightArmJoint = ((Model_1_21_2plus) (Object) this)
                .jojo_ripples$getAnyDescendantWithName(
                        "right_arm_joint")
                .orElse(null);
        cloudParts = new ModelPart[CLOUD_PART_NAMES.length];
        for (int index = 0; index < CLOUD_PART_NAMES.length; index++) {
            cloudParts[index] = findPart(CLOUD_PART_NAMES[index]);
        }
    }

    @Override
    public void setupAnim(WeatherReportStandRenderState renderState) {
        super.setupAnim(renderState);
        icicleArmHidden = renderState.icicleArmHidden;
        applyIcicleArmVisibility();
        applyCloudVisibility(renderState);
    }

    @Override
    public void renderGeometryToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
        int packedOverlay,
            int color) {
        applyIcicleArmVisibility();
        applyCloudVisibility();
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
        applyIcicleArmVisibility();
        applyCloudVisibility();
        super.renderClassicLayerToBuffer(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }

    private void applyIcicleArmVisibility() {
        boolean visible = !icicleArmHidden;
        StandEntityModel.setVisible(rightArmJoint, visible);
        StandEntityModel.setVisible(right_arm_bend, visible);
    }

    private ModelPart findPart(String name) {
        return ((Model_1_21_2plus) (Object) this)
                .jojo_ripples$getAnyDescendantWithName(name)
                .orElse(null);
    }

    private void applyCloudVisibility() {
        if (RenderStateCrutches.currentStandEntityRenderState
                instanceof WeatherReportStandRenderState renderState) {
            applyCloudVisibility(renderState);
        }
    }

    private void applyCloudVisibility(
            WeatherReportStandRenderState renderState) {
        boolean visible = cloudVisibleFor(renderState);
        for (ModelPart cloudPart : cloudParts) {
            StandEntityModel.setVisible(cloudPart, visible);
        }
        renderState.cloudPartsResolved = cloudPartsResolved();
        renderState.cloudPartsVisible = cloudPartsVisible();
        renderState.armsOnlyRetainsCloudSubtree =
                armsOnlyRetainsCloudSubtree();
    }

    private static boolean cloudVisibleFor(
            WeatherReportStandRenderState renderState) {
        if (renderState.cloudShieldActive) {
            return true;
        }
        if (renderState.obstructionRenderMode
                == ObstructionRenderMode.CLASSIC_OUTLINE) {
            return false;
        }
        if (renderState.obstructionRenderMode
                == ObstructionRenderMode.CLASSIC_ARMS_ONLY) {
            return containsArm(renderState.visibleParts);
        }
        return HumanoidPart.contains(
                renderState.visibleParts, HumanoidPart.BODY);
    }

    private static boolean containsArm(HumanoidPart[] parts) {
        return HumanoidPart.contains(parts, HumanoidPart.LEFT_ARM)
                || HumanoidPart.contains(
                        parts, HumanoidPart.RIGHT_ARM);
    }

    private boolean cloudPartsResolved() {
        for (ModelPart cloudPart : cloudParts) {
            if (cloudPart == null) {
                return false;
            }
        }
        return true;
    }

    private boolean cloudPartsVisible() {
        if (!cloudPartsResolved()) {
            return false;
        }
        for (ModelPart cloudPart : cloudParts) {
            if (!cloudPart.visible) {
                return false;
            }
        }
        return true;
    }

    private static boolean armsOnlyRetainsCloudSubtree() {
        WeatherReportStandRenderState probe =
                new WeatherReportStandRenderState();
        probe.visibleParts = HumanoidPart.ARMS_ONLY;
        probe.obstructionRenderMode =
                ObstructionRenderMode.CLASSIC_ARMS_ONLY;
        return cloudVisibleFor(probe);
    }
}
