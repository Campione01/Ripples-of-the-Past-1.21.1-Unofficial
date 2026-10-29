package rotp.addons.scr.client;

import java.util.ArrayList;
import java.util.HashMap;

import rotp.core.client.entityrender.stand.StandEntityModel;

import rotp.addons.scr.entity.SilverChariotRequiemEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

/**
 * Adapts the original Next Album sibling-bend geometry to the current stand
 * model item-anchor contract without changing the model hierarchy.
 */
public final class SilverChariotRequiemStandModel
        extends StandEntityModel<
                SilverChariotRequiemEntity,
                SilverChariotRequiemRenderState> {
    public SilverChariotRequiemStandModel(ModelPart root) {
        super(root);
        requirePart(body_rot, "body_rot");
        requirePart(torso_bend, "torso_bend");
        requirePart(torso_no_arms, "torso_no_arms");
        requirePart(head, "head");
        requirePart(left_arm_xrot, "left_arm_xrot");
        requirePart(left_arm, "left_arm");
        requirePart(left_arm_bend, "left_arm_bend");
        requirePart(right_arm_xrot, "right_arm_xrot");
        requirePart(right_arm, "right_arm");
        requirePart(right_arm_bend, "right_arm_bend");
        requirePart(left_leg_xrot, "left_leg_xrot");
        requirePart(left_leg, "left_leg");
        requirePart(left_leg_bend, "left_leg_bend");
        requirePart(right_leg_xrot, "right_leg_xrot");
        requirePart(right_leg, "right_leg");
        requirePart(right_leg_bend, "right_leg_bend");
    }

    @Override
    protected void addMissingItemHoldPoints() {
        addItemHoldPoint(left_arm_bend, "left_item");
        addItemHoldPoint(right_arm_bend, "right_item");
    }

    private static void addItemHoldPoint(
            ModelPart armBend, String childName) {
        requirePart(armBend, childName + " parent");
        if (armBend.hasChild(childName)) {
            return;
        }

        ModelPart itemPoint =
                new ModelPart(new ArrayList<>(), new HashMap<>());
        itemPoint.setInitialPose(
                PartPose.offset(0.0F, 3.75F, -2.0F));
        itemPoint.resetPose();
        armBend.children.put(childName, itemPoint);
    }

    private static ModelPart requirePart(
            ModelPart part, String name) {
        if (part == null) {
            throw new IllegalStateException(
                    "Silver Chariot Requiem model is missing "
                            + "required part: " + name);
        }
        return part;
    }
}
