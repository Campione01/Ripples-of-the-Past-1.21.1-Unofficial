package rotp.addons.ger.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.model.geom.ModelPart;

public final class GerStandRuntimeModelProbe {
    private static final String SKIN_PREFIX =
            "assets/geraddon/stand_skins/ger_stand/";
    private static final String SKIN_JSON =
            SKIN_PREFIX + "skin.json";
    private static final String MODEL_JSON =
            SKIN_PREFIX
                    + "assets/geraddon/geo/ger_stand.geo.json";
    private static final String ANIMATION_JSON =
            SKIN_PREFIX
                    + "assets/geraddon/animations/"
                    + "ger_stand.animation.json";
    private static final String TEXTURE =
            SKIN_PREFIX
                    + "assets/geraddon/textures/entity/stand/"
                    + "ger_stand.png";
    private static final String CORE_FALLBACK_MODEL =
            "assets/jojo_ripples/geo/rotp/"
                    + "stand_default.geo.json";
    private static final int EXPECTED_CORE_FALLBACK_CUBES = 22;
    private static final List<String> REQUIRED_CLIPS = List.of(
            "idle",
            "summon1",
            "summon6",
            "punch",
            "armsOnly_punch",
            "barrage",
            "guard",
            "heavy_punch",
            "heavy_charged",
            "ger_undie_punch",
            "ger_life_shot",
            "ger_life_shot_multi",
            "ger_return_to_zero",
            "ger_return_to_zero_mark",
            "ger_disabler");
    private static final Set<String> OPTIONAL_DONOR_TRACKS = Set.of(
            "leftArmXRot",
            "leftLegJoint",
            "leftLegXRot",
            "leftPartLoincloth",
            "leftString",
            "leftWing",
            "loincloth",
            "rightArmXRot",
            "rightLegXRot",
            "rightPartLoincloth",
            "rightString",
            "rightWing",
            "theThing");
    // stand_pos is the port's own root and no donor clip ever addressed it. The four
    // *_xrot anchors are not inert: 1.16's HumanoidStandModel declared leftArmXRot,
    // rightArmXRot, leftLegXRot and rightLegXRot, the donor clips drove them, and the
    // 2026-09-14 bone-name repair pointed those tracks back at the migrated geo's
    // snake_case equivalents.
    private static final Set<String> INERT_ANIMATION_ANCHORS = Set.of(
            "stand_pos");
    private static int assertions;

    private GerStandRuntimeModelProbe() {}

    public static void main(String[] args) throws Exception {
        check(args.length == 2,
                "Expected the GER project and runtime JAR paths.");
        Path project = Path.of(args[0])
                .toAbsolutePath()
                .normalize();
        Path jar = Path.of(args[1])
                .toAbsolutePath()
                .normalize();
        Path resources = project.resolve("src/main/resources");

        JsonObject skin = json(resources.resolve(SKIN_JSON));
        check("geraddon:ger_stand".equals(
                        skin.get("stand_type").getAsString()),
                "Default GER skin is not bound to geraddon:ger_stand.");

        JsonObject geometry = json(resources.resolve(MODEL_JSON));
        JsonObject model = geometry
                .getAsJsonArray("minecraft:geometry")
                .get(0)
                .getAsJsonObject();
        Map<String, String> parents =
                validateSourceGeometry(model);

        ModelPart canonicalRoot =
                GerStandModelValidator.bakeAndValidate(
                        ParseModEntityModel.parse(
                                geometry, ModelFormat.GECKO));
        assertions++;
        long canonicalCubes = countCubes(canonicalRoot);
        long fallbackCubes = validateCoreFallbackModel();

        validateAnimations(
                json(resources.resolve(ANIMATION_JSON)),
                parents.keySet());
        validateTexture(resources.resolve(TEXTURE));
        validateRuntimeJar(jar, geometry);

        System.out.println(
                "GER Stand runtime model probe passed: assertions="
                        + assertions
                        + ", bones="
                        + parents.size()
                        + ", canonicalCubes="
                        + canonicalCubes
                        + ", fallbackCubes="
                        + fallbackCubes);
    }

    private static Map<String, String> validateSourceGeometry(
            JsonObject model) {
        JsonObject description =
                model.getAsJsonObject("description");
        check(description.get("texture_width").getAsInt() == 128
                        && description.get("texture_height").getAsInt()
                                == 128,
                "GER model UV atlas must remain 128x128.");

        Map<String, String> parents = new HashMap<>();
        Map<String, JsonArray> pivots = new HashMap<>();
        Set<String> emitted = new HashSet<>();
        int cubes = 0;
        for (var element : model.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            check(parents.put(
                            name,
                            bone.has("parent")
                                    ? bone.get("parent").getAsString()
                                    : null)
                            == null,
                    "Duplicate GER bone: " + name);
            String parent = parents.get(name);
            check(parent == null || emitted.contains(parent),
                    "GER bone must follow its parent for the current "
                            + "runtime parser: "
                            + name
                            + " -> "
                            + parent);
            emitted.add(name);
            if (bone.has("pivot")) {
                pivots.put(name, bone.getAsJsonArray("pivot"));
            }
            if (bone.has("cubes")) {
                JsonArray boneCubes =
                        bone.getAsJsonArray("cubes");
                cubes += boneCubes.size();
                for (var cube : boneCubes) {
                    check(cube.getAsJsonObject().has("uv"),
                            "GER cube is missing UV data in bone "
                                    + name);
                }
            }
        }
        check(parents.size() == 35,
                "GER source bone inventory changed.");
        check(cubes
                        == GerStandModelValidator
                                .EXPECTED_CUBE_COUNT,
                "GER source cube inventory changed.");
        for (String bone
                : GerStandModelValidator.REQUIRED_BONES) {
            check(parents.containsKey(bone),
                    "GER source model is missing required bone: "
                            + bone);
        }
        validateCanonicalStandRig(parents, pivots);
        return parents;
    }

    private static void validateCanonicalStandRig(
            Map<String, String> parents,
            Map<String, JsonArray> pivots) {
        check(parents.get("stand_pos") == null,
                "GER stand_pos must remain the root bone.");
        check("stand_pos".equals(parents.get("body_rot")),
                "GER body_rot must remain under stand_pos.");
        check("body_rot".equals(parents.get("body")),
                "GER body must remain under body_rot.");
        check("body".equals(parents.get("torso")),
                "GER torso must remain under body.");
        check("torso".equals(parents.get("torso_bend")),
                "GER torso_bend must remain under torso.");
        check("torso_bend".equals(parents.get("head")),
                "GER head must remain under torso_bend.");
        check("torso_bend".equals(
                        parents.get("torso_no_arms")),
                "GER upper torso shell must remain under torso_bend.");
        check("torso".equals(parents.get("torso_lower")),
                "GER lower torso must remain under torso.");
        check("torso_bend".equals(
                        parents.get("right_arm_xrot"))
                        && "torso_bend".equals(
                                parents.get("left_arm_xrot")),
                "GER arm anchors must remain under torso_bend.");
        check("body".equals(parents.get("right_leg_xrot"))
                        && "body".equals(
                                parents.get("left_leg_xrot")),
                "GER leg anchors must remain under body.");

        // Since 2026-09-12 the default skin is the model migrated from the 1.16.5
        // source, so these are that model's own pivots: head, torso, upperPart ->
        // torso_bend, torso_no_arms, torso_lower and both lower legs match the donor
        // geometry exactly, and the core rig anchors sit on the matching
        // GerStandModelValidator donor rig (body y=12, knee y=6). The previous
        // 1.21.1 rig (head y=26, body y=14, knee y=7) is kept as the ger_classic skin.
        checkPivot(pivots, "stand_pos", 0.0F, 0.0F, 0.0F);
        checkPivot(pivots, "head", 0.0F, 24.0F, 0.0F);
        checkPivot(pivots, "body_rot", 0.0F, 24.0F, 0.0F);
        checkPivot(pivots, "body", 0.0F, 12.0F, 0.0F);
        checkPivot(pivots, "torso", 0.0F, 12.0F, 0.0F);
        checkPivot(pivots, "torso_bend", 0.0F, 18.0F, 0.0F);
        checkPivot(pivots, "torso_no_arms", 0.0F, 18.0F, 0.0F);
        checkPivot(pivots, "torso_lower", 0.0F, 18.0F, 0.0F);
        checkPivot(
                pivots, "right_arm_xrot", -6.0F, 22.0F, 0.0F);
        checkPivot(
                pivots, "left_arm_xrot", 6.0F, 22.0F, 0.0F);
        checkPivot(
                pivots, "right_leg_xrot", -2.0F, 12.0F, 0.0F);
        checkPivot(
                pivots, "left_leg_xrot", 2.0F, 12.0F, 0.0F);
        checkPivot(
                pivots, "right_leg_bend", -2.0F, 6.0F, 0.0F);
        checkPivot(
                pivots, "left_leg_bend", 2.0F, 6.0F, 0.0F);
    }

    private static void checkPivot(
            Map<String, JsonArray> pivots,
            String bone,
            float x,
            float y,
            float z) {
        JsonArray pivot = pivots.get(bone);
        check(pivot != null
                        && pivot.size() == 3
                        && pivot.get(0).getAsFloat() == x
                        && pivot.get(1).getAsFloat() == y
                        && pivot.get(2).getAsFloat() == z,
                "GER canonical pivot changed for bone " + bone + ".");
    }

    private static void validateAnimations(
            JsonObject root, Set<String> geometryBones) {
        JsonObject animations =
                root.getAsJsonObject("animations");
        for (String clip : REQUIRED_CLIPS) {
            check(animations.has(clip),
                    "GER animation clip is missing: " + clip);
        }
        for (var clipEntry : animations.entrySet()) {
            JsonObject clip = clipEntry.getValue()
                    .getAsJsonObject();
            if (!clip.has("bones")) {
                continue;
            }
            for (var track : clip.getAsJsonObject("bones")
                    .entrySet()) {
                String bone = track.getKey();
                check(!INERT_ANIMATION_ANCHORS.contains(bone),
                        "GER animation activated an identity-only "
                                + "compatibility anchor: "
                                + clipEntry.getKey()
                                + " -> "
                                + bone);
                check(geometryBones.contains(bone)
                                || OPTIONAL_DONOR_TRACKS
                                        .contains(bone),
                        "GER animation "
                                + clipEntry.getKey()
                                + " targets an unclassified bone: "
                                + bone);
            }
        }
        JsonObject idle = animations.getAsJsonObject("idle");
        JsonObject idleBones = idle.getAsJsonObject("bones");
        check(idleBones.has("body")
                        && idleBones.has("body_rot")
                        && idleBones.has("torso_bend")
                        && !idleBones.has("donor_root")
                        && idleBones.has("the_thing")
                        && !idleBones.has("theThing"),
                "GER idle does not target the current upright root "
                        + "chain.");
        checkRotation(
                idle, "right_leg", -5.5F, 0.0F, 8.0F);
        checkRotation(
                idle, "right_leg_bend", 15.0F, 0.0F, 0.0F);
        checkRotation(
                idle, "left_leg", -5.5F, 0.0F, -8.0F);
        checkRotation(
                idle, "left_leg_bend", 15.0F, 0.0F, 0.0F);
        check(idle.equals(
                        animations.getAsJsonObject(
                                "ger_return_to_zero")),
                "GER Return to Zero no longer shares the upright idle "
                        + "baseline.");

        JsonObject shoot = animations.getAsJsonObject("shoot");
        JsonObject shootBones = shoot.getAsJsonObject("bones");
        check(shootBones.has("the_thing")
                        && !shootBones.has("theThing")
                        && shootBones.has("left_leg_joint")
                        && !shootBones.has("leftLegJoint"),
                "GER resolved donor tracks no longer match the exact "
                        + "legacy geometry names.");
        checkRotation(shoot, "body_rot", 0.0F, 0.0F, 0.0F);
        checkRotation(shoot, "torso", 0.0F, 0.0F, 0.0F);
        checkVector(
                shoot.getAsJsonObject("bones")
                        .getAsJsonObject("body_rot")
                        .getAsJsonObject("position")
                        .getAsJsonArray("vector"),
                "shoot body_rot position",
                0.0F,
                0.0F,
                0.0F);

        JsonObject summonOneBones = animations
                .getAsJsonObject("summon1")
                .getAsJsonObject("bones");
        check(summonOneBones.has("left_leg_joint")
                        && !summonOneBones.has("leftLegJoint"),
                "GER summon1 knee track is not on the geo's "
                        + "left_leg_joint bone.");
        JsonObject barrageBones = animations
                .getAsJsonObject("barrage")
                .getAsJsonObject("bones");
        for (String xRotAnchor : List.of(
                "left_arm_xrot",
                "right_arm_xrot",
                "left_leg_xrot",
                "right_leg_xrot")) {
            check(barrageBones.has(xRotAnchor)
                            && !barrageBones.has(
                                    donorTrackName(xRotAnchor)),
                    "GER donor barrage lost resolved track "
                            + xRotAnchor
                            + ".");
        }
    }

    /** The 1.16 HumanoidStandModel field name a migrated anchor came from. */
    private static String donorTrackName(String anchor) {
        StringBuilder donor = new StringBuilder();
        boolean upper = false;
        for (char c : anchor.toCharArray()) {
            if (c == '_') {
                upper = true;
                continue;
            }
            donor.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return donor.toString().replace("Xrot", "XRot");
    }

    private static void checkRotation(
            JsonObject clip,
            String bone,
            float x,
            float y,
            float z) {
        JsonArray vector = clip.getAsJsonObject("bones")
                .getAsJsonObject(bone)
                .getAsJsonObject("rotation")
                .getAsJsonArray("vector");
        checkVector(
                vector,
                "rotation for " + bone,
                x,
                y,
                z);
    }

    private static void checkVector(
            JsonArray vector,
            String name,
            float x,
            float y,
            float z) {
        check(vector.size() == 3
                        && vector.get(0).getAsFloat() == x
                        && vector.get(1).getAsFloat() == y
                        && vector.get(2).getAsFloat() == z,
                "GER animation vector changed for " + name + ".");
    }

    private static void validateTexture(Path texture)
            throws Exception {
        BufferedImage image = ImageIO.read(texture.toFile());
        check(image != null
                        && image.getWidth() == 256
                        && image.getHeight() == 256,
                "GER entity texture must be a readable 256x256 PNG.");
        int opaquePixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    opaquePixels++;
                }
            }
        }
        check(opaquePixels > 0,
                "GER entity texture is fully transparent.");
    }

    private static long validateCoreFallbackModel()
            throws Exception {
        JsonObject fallbackGeometry;
        try (InputStream stream = GerStandRuntimeModelProbe.class
                .getClassLoader()
                .getResourceAsStream(CORE_FALLBACK_MODEL)) {
            check(stream != null,
                    "Core Stand fallback model is missing from the "
                            + "runtime classpath.");
            fallbackGeometry = JsonParser.parseString(new String(
                            stream.readAllBytes(),
                            StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }

        ModelPart fallbackRoot = ParseModEntityModel.parse(
                        fallbackGeometry, ModelFormat.GECKO)
                .bakeRoot();
        long fallbackCubes = countCubes(fallbackRoot);
        check(fallbackCubes == EXPECTED_CORE_FALLBACK_CUBES,
                "Core Stand fallback baked cube inventory changed: "
                        + fallbackCubes);

        String validationFailure = null;
        try {
            GerStandModelValidator.validate(fallbackRoot);
        }
        catch (IllegalStateException exception) {
            validationFailure = exception.getMessage();
        }
        // GerStandModelValidator stopped gating on the cube count when the default
        // skin changed; it still refuses the core Stand fallback because that model
        // carries none of GER's own head geometry, so head_rot has no mask child.
        check(("GER Stand canonical root hierarchy drifted at mask.")
                        .equals(validationFailure),
                "GER canonical validation no longer distinguishes the "
                        + "core Stand fallback: " + validationFailure);
        return fallbackCubes;
    }

    private static long countCubes(ModelPart root) {
        return root.getAllParts()
                .mapToLong(part -> part.cubes.size())
                .sum();
    }

    private static void validateRuntimeJar(
            Path jar, JsonObject sourceGeometry)
            throws Exception {
        check(Files.isRegularFile(jar),
                "GER runtime JAR is missing: " + jar);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String entry : List.of(
                    SKIN_JSON,
                    MODEL_JSON,
                    ANIMATION_JSON,
                    TEXTURE,
                    "rotp/addons/ger/client/"
                            + "GerStandRenderer.class",
                    "rotp/addons/ger/client/"
                            + "GerStandModelValidator.class",
                    "rotp/addons/ger/client/"
                            + "GerStandRuntimeModelProbe.class",
                    "rotp/addons/ger/entity/"
                            + "GerStandEntity.class")) {
                check(zip.getEntry(entry) != null,
                        "GER runtime JAR entry is missing: "
                                + entry);
            }
            JsonObject packagedGeometry =
                    json(zip, MODEL_JSON);
            check(sourceGeometry.equals(packagedGeometry),
                    "Packaged GER geometry differs from source.");
        }
    }

    private static JsonObject json(Path path)
            throws Exception {
        check(Files.isRegularFile(path),
                "Required GER resource is missing: " + path);
        return JsonParser.parseString(
                        Files.readString(path))
                .getAsJsonObject();
    }

    private static JsonObject json(
            ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        check(entry != null,
                "Required GER JAR resource is missing: " + name);
        try (InputStream stream = zip.getInputStream(entry)) {
            return JsonParser.parseString(new String(
                            stream.readAllBytes(),
                            StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static void check(
            boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
