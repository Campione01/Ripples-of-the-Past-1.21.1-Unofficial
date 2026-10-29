package rotp.addons.ger;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.addons.ger.client.GerStandModelValidator;
import net.minecraft.client.model.geom.ModelPart;

// Uses the same mapped Minecraft runtime as the existing model bake probe.
public final class GerApprovedSkinProbe {
    private static final String SKIN_ROOT = "assets/geraddon/stand_skins/";
    // The sculpted GENERIC model this probe calls "approved" was the default from
    // 2026-09-09 until 2026-09-12, when the user moved the default back to the model
    // migrated from the 1.16.5 source and kept the sculpt as the optional ger_custom
    // skin. The sculpt itself is untouched - same 74 cubes, 87 meshes, 29 rig groups
    // and 2048x512 atlas - so everything below still checks the same model, in the
    // skin that now owns it.
    private static final String SCULPT_ROOT = SKIN_ROOT + "ger_custom/";
    private static final String DEFAULT_ROOT = SKIN_ROOT + "ger_stand/";
    private static final String MODEL = SCULPT_ROOT
            + "assets/geraddon/bb/ger_stand.bb.json";
    private static final String LEGACY_MODEL = "assets/geraddon/geo/ger_stand.geo.json";
    private static final String TEXTURE = "assets/geraddon/textures/entity/stand/ger_stand.png";
    private static final String ANIMATION = "assets/geraddon/animations/ger_stand.animation.json";
    private static final String REFINED_ROOT = SKIN_ROOT + "ger_refined/";
    private static final int LOGICAL_WIDTH = 1024;
    private static final int LOGICAL_HEIGHT = 256;
    private static int assertions;

    private GerApprovedSkinProbe() {}

    public static void main(String[] args) throws Exception {
        check(args.length == 2, "Expected [moduleDir, jarPath].");
        Path resources = Path.of(args[0]).toAbsolutePath().normalize()
                .resolve("src/main/resources");
        Path jarPath = Path.of(args[1]).toAbsolutePath().normalize();

        validateSkin(json(resources.resolve(SCULPT_ROOT + "skin.json")));
        validateSkin(json(resources.resolve(DEFAULT_ROOT + "skin.json")));
        check(!Files.exists(resources.resolve(SCULPT_ROOT + LEGACY_MODEL)),
                "A GEO competes with the sculpted GENERIC model in ger_custom.");
        check(!Files.exists(resources.resolve(SCULPT_ROOT
                        + "assets/geraddon/bb/ger_stand.bbmodel")),
                "Two GENERIC files compete for the ger_custom model ID.");
        check(Files.exists(resources.resolve(DEFAULT_ROOT + LEGACY_MODEL)),
                "The default ger_stand skin lost its migrated GEO.");
        bake(json(resources.resolve(DEFAULT_ROOT + LEGACY_MODEL)),
                ModelFormat.GECKO);

        JsonObject approved = json(resources.resolve(MODEL));
        int faces = validateGenericSource(approved);
        ModelPart approvedRoot = bake(approved, ModelFormat.GENERIC);
        check(countGeometry(approvedRoot) == 161,
                "Approved GENERIC bake must retain all 74 cubes and 87 meshes.");
        validateTexture(resources.resolve(SCULPT_ROOT + TEXTURE));

        JsonObject refined = json(resources.resolve(REFINED_ROOT + LEGACY_MODEL));
        check(refined.getAsJsonArray("minecraft:geometry").get(0)
                        .getAsJsonObject().getAsJsonArray("bones").size() == 35,
                "Retained ger_refined must keep its 35 source bones.");
        bake(refined, ModelFormat.GECKO);
        validateRejections(approved, refined);

        List<String> compared = new ArrayList<>(List.of(
                MODEL, SCULPT_ROOT + "skin.json", SCULPT_ROOT + TEXTURE,
                DEFAULT_ROOT + "skin.json", DEFAULT_ROOT + LEGACY_MODEL,
                DEFAULT_ROOT + TEXTURE, DEFAULT_ROOT + ANIMATION,
                REFINED_ROOT + "skin.json",
                REFINED_ROOT + LEGACY_MODEL, REFINED_ROOT + TEXTURE));
        String classicRoot = SKIN_ROOT + "ger_classic/";
        if (Files.exists(resources.resolve(classicRoot + LEGACY_MODEL))) {
            bake(json(resources.resolve(classicRoot + LEGACY_MODEL)), ModelFormat.GECKO);
            compared.addAll(List.of(classicRoot + "skin.json",
                    classicRoot + LEGACY_MODEL, classicRoot + TEXTURE));
        }
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            for (String resource : compared) {
                check(Arrays.equals(Files.readAllBytes(resources.resolve(resource)),
                                jarBytes(jar, resource)),
                        "Packaged resource differs from source: " + resource);
            }
            check(jar.getEntry(SCULPT_ROOT + LEGACY_MODEL) == null,
                    "A GEO is packaged alongside the sculpted GENERIC model.");
            check(jar.getEntry(SCULPT_ROOT + "assets/geraddon/bb/ger_stand.bbmodel") == null,
                    "A second sculpted GENERIC model is still packaged.");
            JsonObject packaged = JsonParser.parseString(new String(
                    jarBytes(jar, MODEL), StandardCharsets.UTF_8)).getAsJsonObject();
            check(countGeometry(bake(packaged, ModelFormat.GENERIC)) == 161,
                    "Packaged GENERIC bake lost approved geometry.");
        }
        System.out.println("GER sculpted skin probe passed: assertions=" + assertions
                + ", sourceBones=29, sourceCubes=74, sourceMeshes=87, bakedGeometry=161"
                + ", sourceFaces=" + faces + ", atlas=1024x256/2048x512"
                + ", matchedJarResources=" + compared.size());
    }

    private static void validateSkin(JsonObject skin) {
        check("geraddon:ger_stand".equals(skin.get("stand_type").getAsString()),
                "A GER skin is not bound to the geraddon:ger_stand Stand type.");
    }

    private static int validateGenericSource(JsonObject model) {
        JsonObject resolution = model.getAsJsonObject("resolution");
        check(resolution.get("width").getAsInt() == LOGICAL_WIDTH
                        && resolution.get("height").getAsInt() == LOGICAL_HEIGHT,
                "Approved atlas logical resolution must be 1024x256.");
        if (model.has("textures")) {
            check(model.getAsJsonArray("textures").size() == 1,
                    "Runtime GENERIC metadata may describe only the merged atlas.");
        }
        Map<String, JsonObject> elements = new HashMap<>();
        int cubes = 0;
        int meshes = 0;
        int faces = 0;
        for (JsonElement value : model.getAsJsonArray("elements")) {
            JsonObject element = value.getAsJsonObject();
            String id = element.get("uuid").getAsString();
            check(elements.put(id, element) == null, "Duplicate element UUID: " + id);
            check(!element.has("export") || element.get("export").getAsBoolean(),
                    "An approved element is excluded from export: " + id);
            String type = element.get("type").getAsString();
            if ("cube".equals(type)) {
                cubes++;
            }
            else {
                check("mesh".equals(type), "Unsupported approved element type: " + type);
                meshes++;
            }
            JsonObject elementFaces = element.getAsJsonObject("faces");
            check(elementFaces != null && !elementFaces.isEmpty(),
                    "Approved element has no faces: " + id);
            faces += elementFaces.size();
            for (var faceEntry : elementFaces.entrySet()) {
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                JsonElement texture = face.get("texture");
                if (texture == null || texture.isJsonNull()) {
                    check("cube".equals(type), "A mesh face has no atlas binding: " + id);
                    continue;
                }
                check(texture.isJsonPrimitive()
                                && texture.getAsJsonPrimitive().isNumber()
                                && texture.getAsDouble() == 0.0,
                        "A face still references an unmerged texture: " + id);
                if ("cube".equals(type)) {
                    JsonArray uv = face.getAsJsonArray("uv");
                    check(uv.size() == 4, "Cube face UV must have four coordinates.");
                    validateUv(uv.get(0).getAsDouble(), uv.get(1).getAsDouble());
                    validateUv(uv.get(2).getAsDouble(), uv.get(3).getAsDouble());
                }
                else {
                    JsonArray vertices = face.getAsJsonArray("vertices");
                    check(vertices.size() >= 3, "A mesh face has fewer than three vertices.");
                    JsonObject uv = face.getAsJsonObject("uv");
                    for (JsonElement vertex : vertices) {
                        String vertexId = vertex.getAsString();
                        check(element.getAsJsonObject("vertices").has(vertexId),
                                "Mesh face references a missing vertex: " + vertexId);
                        JsonArray point = uv.getAsJsonArray(vertexId);
                        check(point != null && point.size() == 2,
                                "Mesh vertex is missing its atlas UV: " + vertexId);
                        validateUv(point.get(0).getAsDouble(), point.get(1).getAsDouble());
                    }
                }
            }
        }
        check(cubes == 74 && meshes == 87 && elements.size() == 161,
                "Approved source must retain exactly 74 cubes and 87 meshes.");
        Set<String> groups = new HashSet<>();
        Set<String> groupIds = new HashSet<>();
        Set<String> referenced = new HashSet<>();
        visitOutliner(model.getAsJsonArray("outliner"), elements.keySet(),
                groups, groupIds, referenced);
        check(groups.size() == 29
                        && groups.equals(new HashSet<>(GerStandModelValidator.APPROVED_BONES)),
                "Approved source must preserve its exact 29 named rig bones.");
        check(referenced.equals(elements.keySet()),
                "Some approved elements are orphaned outside the outliner.");
        return faces;
    }

    private static void visitOutliner(JsonArray nodes, Set<String> elements,
            Set<String> groups, Set<String> groupIds, Set<String> referenced) {
        for (JsonElement node : nodes) {
            if (node.isJsonPrimitive()) {
                String id = node.getAsString();
                check(elements.contains(id) && referenced.add(id),
                        "Missing or multiply referenced outliner element: " + id);
                continue;
            }
            JsonObject group = node.getAsJsonObject();
            check(group.has("name") && group.has("origin") && group.has("children"),
                    "Blockbench 5 group metadata was not expanded into the outliner.");
            check(groups.add(group.get("name").getAsString())
                            && groupIds.add(group.get("uuid").getAsString()),
                    "Duplicate named outliner group.");
            check(!group.has("export") || group.get("export").getAsBoolean(),
                    "A required rig group is excluded from export.");
            visitOutliner(group.getAsJsonArray("children"), elements,
                    groups, groupIds, referenced);
        }
    }

    private static void validateUv(double u, double v) {
        check(Double.isFinite(u) && Double.isFinite(v)
                        && u >= -0.00001 && u <= LOGICAL_WIDTH + 0.00001
                        && v >= -0.00001 && v <= LOGICAL_HEIGHT + 0.00001,
                "UV escapes the merged atlas: " + u + ", " + v);
    }

    private static void validateTexture(Path path) throws Exception {
        BufferedImage image = ImageIO.read(path.toFile());
        check(image != null && image.getWidth() == 2048 && image.getHeight() == 512,
                "Approved atlas must be a readable 2048x512 PNG.");
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    assertions++;
                    return;
                }
            }
        }
        throw new IllegalStateException("Approved atlas is entirely transparent.");
    }

    private static void validateRejections(JsonObject approved, JsonObject refined) {
        ModelPart root = bake(approved, ModelFormat.GENERIC);
        part(root, "body").y = 11;
        expectRejected(root, "unknown body offset");
        root = bake(approved, ModelFormat.GENERIC);
        part(root, "left_leg_bend").y = 7;
        expectRejected(root, "approved body with legacy knee");
        root = bake(refined, ModelFormat.GECKO);
        part(root, "right_leg_joint").y = 6;
        expectRejected(root, "legacy body with approved knee");
        root = bake(approved, ModelFormat.GENERIC);
        part(root, "left_item").z = 0;
        expectRejected(root, "item anchor drift");
        root = bake(approved, ModelFormat.GENERIC);
        part(root, "head_rot").yRot = 0.1F;
        expectRejected(root, "head rest rotation drift");
        root = bake(approved, ModelFormat.GENERIC);
        part(root, "head").children.remove("head_rot");
        expectRejected(root, "missing required bone");
        root = bake(approved, ModelFormat.GENERIC);
        ModelPart bend = part(root, "left_arm").children.remove("left_arm_bend");
        part(root, "body").children.put("left_arm_bend", bend);
        expectRejected(root, "wrong direct parent");
        expectRejected(withoutGeometry(bake(approved, ModelFormat.GENERIC)), "empty geometry");
    }

    private static ModelPart withoutGeometry(ModelPart original) {
        Map<String, ModelPart> children = new HashMap<>();
        original.children.forEach((name, child) -> children.put(name, withoutGeometry(child)));
        ModelPart empty = new ModelPart(List.of(), children);
        empty.loadPose(original.getInitialPose());
        return empty;
    }

    private static void expectRejected(ModelPart invalid, String reason) {
        try {
            GerStandModelValidator.validate(invalid);
        }
        catch (IllegalStateException expected) {
            assertions++;
            return;
        }
        throw new IllegalStateException("Validator accepted " + reason + ".");
    }

    private static ModelPart part(ModelPart root, String name) {
        ModelPart direct = root.children.get(name);
        if (direct != null) return direct;
        for (ModelPart child : root.children.values()) {
            ModelPart found = part(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static ModelPart bake(JsonObject model, ModelFormat format) {
        ModelPart root = GerStandModelValidator.bakeAndValidate(
                ParseModEntityModel.parse(model, format));
        assertions++;
        return root;
    }

    private static long countGeometry(ModelPart root) {
        return root.getAllParts().mapToLong(part -> part.cubes.size()).sum();
    }

    private static JsonObject json(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static byte[] jarBytes(ZipFile jar, String path) throws Exception {
        check(jar.stream().filter(entry -> path.equals(entry.getName())).count() == 1,
                "Missing or duplicate JAR resource: " + path);
        ZipEntry entry = jar.getEntry(path);
        try (InputStream stream = jar.getInputStream(entry)) {
            return stream.readAllBytes();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        assertions++;
    }
}
