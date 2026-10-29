package rotp.addons.speedking.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;

import rotp.addons.speedking.entity.SpeedKingEntity;
import net.minecraft.client.model.geom.ModelPart;

public final class SpeedKingModel extends StandEntityModel<
        SpeedKingEntity, StandEntityRenderState> {
    public SpeedKingModel(ModelPart root) {
        super(root);
        Model_1_21_2plus model = (Model_1_21_2plus) (Object) this;
        body_rot = part(model, "body");
        head = part(model, "head");
        head_rot = head;
        torso_bend = part(model, "upperPart");
        torso_no_arms = part(model, "torso");
        torso_lower = torso_no_arms;
        left_arm_xrot = part(model, "leftArm");
        left_arm = part(model, "leftArm");
        left_arm_bend = part(model, "leftForeArm");
        right_arm_xrot = part(model, "rightArm");
        right_arm = part(model, "rightArm");
        right_arm_bend = part(model, "rightForeArm");
        left_leg_xrot = part(model, "leftLegXRot");
        left_leg = part(model, "leftLeg");
        left_leg_bend = part(model, "leftLowerLeg");
        right_leg_xrot = part(model, "rightLegXRot");
        right_leg = part(model, "rightLeg");
        right_leg_bend = part(model, "rightLowerLeg");
    }

    @Override
    public void setupAnim(StandEntityRenderState state) {
        super.setupAnim(state);
        String animation = state.action.animId != null
                ? state.action.animId.name() : "";
        switch (animation) {
            case "pickaxeThrow" -> poseArms(-0.5F, -1.1F, -0.78F, 1.57F);
            case "heat_injection" ->
                    poseArms(-0.3F, -1.3F, -0.4F, 0.2F);
            case "heat_vision" ->
                    poseArms(-0.8F, -0.8F, -0.35F, 0.35F);
            case "heat_cook" ->
                    poseArms(-1.3F, 0.1F, -0.1F, 0.25F);
            case "heat_grab" ->
                    poseArms(-1.4F, -1.4F, -0.1F, 0.1F);
            case "heat_trap" ->
                    poseArms(-0.2F, -0.3F, -0.3F, 0.2F);
            default -> {
            }
        }
    }

    private void poseArms(
            float leftX, float rightX, float leftZ, float rightZ) {
        if (left_arm_xrot != null) {
            left_arm_xrot.xRot = leftX;
            left_arm_xrot.zRot = leftZ;
        }
        if (right_arm_xrot != null) {
            right_arm_xrot.xRot = rightX;
            right_arm_xrot.zRot = rightZ;
        }
    }

    private static ModelPart part(
            Model_1_21_2plus model, String name) {
        return model.jojo_ripples$getAnyDescendantWithName(name)
                .orElse(null);
    }
}
