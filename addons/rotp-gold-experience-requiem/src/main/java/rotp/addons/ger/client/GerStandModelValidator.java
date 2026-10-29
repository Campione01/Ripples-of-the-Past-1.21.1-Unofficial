package rotp.addons.ger.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public final class GerStandModelValidator {
    // Cube inventory of the default geraddon:ger_stand skin, not a runtime geometry
    // limit. It became 65 on 2026-09-12, when the user moved the default back to the
    // model migrated from the 1.16.5 source: that model's 65 donor cubes are carried
    // over unchanged and the 09-12 improvement is painted (a repainted atlas plus the
    // face UVs that point at it), so no geometry was added or removed. The previous
    // 113-cube default is kept byte-identically as the ger_classic skin.
    public static final int EXPECTED_CUBE_COUNT = 65;
    private static final float TRANSFORM_EPSILON = 0.00001F;
    public static final List<String> REQUIRED_BONES = List.of(
            "stand_pos",
            "body_rot",
            "body",
            "head",
            "head_rot",
            "torso",
            "torso_bend",
            "torso_no_arms",
            "torso_lower",
            "left_arm_xrot",
            "left_arm",
            "left_arm_bend",
            "right_arm_xrot",
            "right_arm",
            "right_arm_bend",
            "left_leg_xrot",
            "left_leg",
            "left_leg_bend",
            "right_leg_xrot",
            "right_leg",
            "right_leg_bend");
    private static final List<String> VISIBLE_ROOT_BONES = List.of(
            "head",
            "torso_no_arms",
            "torso_lower",
            "left_arm",
            "right_arm",
            "left_leg",
            "right_leg");

    private static final List<BoneRest> APPROVED_RIG = humanoidRig(12.0F, 6.0F);
    private static final List<BoneRest> DONOR_RIG = decoratedRig(12.0F, 6.0F);
    private static final List<BoneRest> LEGACY_RIG = decoratedRig(10.0F, 7.0F);
    public static final List<String> APPROVED_BONES = APPROVED_RIG.stream()
            .map(BoneRest::name).toList();
    private static final Set<String> RIG_BONE_NAMES = rigBoneNames();

    private GerStandModelValidator() {}

    public static ModelPart bakeAndValidate(
            LayerDefinition definition) {
        if (definition == null) {
            throw new IllegalStateException(
                    "GER Stand model definition is missing.");
        }
        ModelPart root = definition.bakeRoot();
        validate(root);
        return root;
    }

    public static void validate(ModelPart root) {
        if (root == null) {
            throw new IllegalStateException(
                    "GER Stand model baked to a null root.");
        }
        for (String bone : REQUIRED_BONES) {
            if (findDescendant(root, bone) == null) {
                throw new IllegalStateException(
                        "GER Stand model is missing required baked bone: "
                                + bone);
            }
        }

        validateCanonicalRootTransform(root);

        for (String bone : VISIBLE_ROOT_BONES) {
            ModelPart part = findDescendant(root, bone);
            long visibleCubes = part.getAllParts()
                    .mapToLong(child -> child.cubes.size())
                    .sum();
            if (visibleCubes == 0) {
                throw new IllegalStateException(
                        "GER Stand visibility root has no geometry: "
                                + bone);
            }
        }
    }

    private static void validateCanonicalRootTransform(
            ModelPart root) {
        ModelPart body = directChild(directChild(
                directChild(root, "stand_pos"), "body_rot"), "body");
        List<BoneRest> rig;
        if (near(body.y, 12.0F)) {
            ModelPart torsoBend = directChild(
                    directChild(body, "torso"), "torso_bend");
            ModelPart mask = directChild(directChild(
                    directChild(torsoBend, "head"), "head_rot"), "mask");
            rig = mask.children.containsKey("spike") ? DONOR_RIG : APPROVED_RIG;
        }
        else if (near(body.y, 10.0F)) {
            rig = LEGACY_RIG;
        }
        else {
            throw new IllegalStateException(
                    "GER Stand has an unsupported rest rig: body.y=" + body.y);
        }

        Map<String, ModelPart> checked = new HashMap<>();
        for (BoneRest bone : rig) {
            ModelPart parent = bone.parent() == null
                    ? root : checked.get(bone.parent());
            ModelPart part = directChild(parent, bone.name());
            requireTransform(part, bone);
            checked.put(bone.name(), part);
        }
        Set<String> found = new HashSet<>();
        collectRigBones(root, found);
        if (!found.equals(checked.keySet())) {
            throw new IllegalStateException(
                    "GER Stand mixes approved and legacy rig bones: " + found);
        }
    }

    // Baked parent-relative poses. GENERIC flips X/Y positions and X/Y rotations;
    // these values are derived from the approved 29-group authoring model.
    private static List<BoneRest> humanoidRig(float bodyY, float kneeY) {
        return List.of(
                bone("stand_pos", null, 0, 24, 0),
                bone("body_rot", "stand_pos", 0, -24, 0),
                bone("body", "body_rot", 0, bodyY, 0),
                bone("torso", "body", 0, 0, 0),
                bone("torso_bend", "torso", 0, -6, 0),
                bone("head", "torso_bend", 0, -6, 0),
                bone("head_rot", "head", 0, 0, 0),
                bone("mask", "head_rot", 0, 0, 0),
                bone("torso_no_arms", "torso_bend", 0, 0, 0),
                bone("right_arm_xrot", "torso_bend", -6, -4, 0),
                bone("right_arm", "right_arm_xrot", 0, 0, 0),
                bone("right_arm_bend", "right_arm", 0, 4, 0),
                bone("right_item", "right_arm_bend", 0, 3.75F, -2),
                bone("right_arm_joint", "right_arm", 0, 4, 0),
                bone("left_arm_xrot", "torso_bend", 6, -4, 0),
                bone("left_arm", "left_arm_xrot", 0, 0, 0),
                bone("left_arm_bend", "left_arm", 0, 4, 0),
                bone("left_item", "left_arm_bend", 0, 3.75F, -2),
                bone("left_arm_joint", "left_arm", 0, 4, 0),
                bone("torso_lower", "torso", 0, -6, 0),
                bone("the_thing", "torso_lower", 0, 5, -2.25F),
                bone("right_leg_xrot", "body", -2, 0, 0),
                bone("right_leg", "right_leg_xrot", 0, 0, 0),
                bone("right_leg_joint", "right_leg", 0, kneeY, 0),
                bone("right_leg_bend", "right_leg", 0, kneeY, 0),
                bone("left_leg_xrot", "body", 2, 0, 0),
                bone("left_leg", "left_leg_xrot", 0, 0, 0),
                bone("left_leg_joint", "left_leg", 0, kneeY, 0),
                bone("left_leg_bend", "left_leg", 0, kneeY, 0));
    }

    private static List<BoneRest> decoratedRig(float bodyY, float kneeY) {
        List<BoneRest> bones = new ArrayList<>(humanoidRig(bodyY, kneeY));
        bones.addAll(List.of(
                rotatedBone("spike", "mask", 0, -10.5F, -2.25F, -25, 0, 0),
                rotatedBone("spike2", "mask", 3.5F, -9, -2,
                        -22.48072F, 0.95645F, 2.30991F),
                rotatedBone("spike3", "mask", -3.5F, -9, -2,
                        -22.48072F, -0.95645F, -2.30991F),
                rotatedBone("spike4", "mask", 3.25F, -10, 2,
                        -12.54624F, -4.88119F, 1.08482F),
                rotatedBone("spike5", "mask", -3.25F, -10, 2,
                        -12.54624F, 4.88119F, -1.08482F),
                rotatedBone("arrow", "head_rot", 0, -6, -4, -5, 0, 0)));
        return List.copyOf(bones);
    }

    private static BoneRest bone(String name, String parent,
            float x, float y, float z) {
        return new BoneRest(name, parent, x, y, z, 0, 0, 0);
    }

    private static BoneRest rotatedBone(String name, String parent,
            float x, float y, float z, float rx, float ry, float rz) {
        float radians = (float) (Math.PI / 180.0);
        return new BoneRest(name, parent, x, y, z,
                rx * radians, ry * radians, rz * radians);
    }

    private static Set<String> rigBoneNames() {
        Set<String> names = new HashSet<>(APPROVED_BONES);
        LEGACY_RIG.forEach(bone -> names.add(bone.name()));
        return Set.copyOf(names);
    }

    private static void collectRigBones(ModelPart parent, Set<String> found) {
        parent.children.forEach((name, child) -> {
            if (RIG_BONE_NAMES.contains(name) && !found.add(name)) {
                throw new IllegalStateException("Duplicate GER rig bone: " + name);
            }
            collectRigBones(child, found);
        });
    }

    private record BoneRest(String name, String parent,
            float x, float y, float z, float xRot, float yRot, float zRot) {}

    private static ModelPart directChild(
            ModelPart parent, String name) {
        ModelPart child = parent.children.get(name);
        if (child == null) {
            throw new IllegalStateException(
                    "GER Stand canonical root hierarchy drifted at "
                            + name
                            + ".");
        }
        return child;
    }

    private static void requireTransform(ModelPart part, BoneRest bone) {
        if (!near(part.x, bone.x())
                || !near(part.y, bone.y())
                || !near(part.z, bone.z())
                || !near(part.xRot, bone.xRot())
                || !near(part.yRot, bone.yRot())
                || !near(part.zRot, bone.zRot())
                || !near(part.xScale, 1.0F)
                || !near(part.yScale, 1.0F)
                || !near(part.zScale, 1.0F)) {
            throw new IllegalStateException(
                    "GER Stand canonical root transform drifted at "
                            + bone.name()
                            + ": offset=("
                            + part.x
                            + ", "
                            + part.y
                            + ", "
                            + part.z
                            + "), rotation=("
                            + part.xRot
                            + ", "
                            + part.yRot
                            + ", "
                            + part.zRot
                            + ").");
        }
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected)
                <= TRANSFORM_EPSILON;
    }

    private static ModelPart findDescendant(
            ModelPart current, String name) {
        ModelPart direct = current.children.get(name);
        if (direct != null) {
            return direct;
        }
        for (ModelPart child : current.children.values()) {
            ModelPart found = findDescendant(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
