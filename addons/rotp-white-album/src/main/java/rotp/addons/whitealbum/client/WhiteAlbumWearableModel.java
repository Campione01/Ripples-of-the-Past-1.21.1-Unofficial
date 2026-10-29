package rotp.addons.whitealbum.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * Donor-equivalent player-attached White Album geometry. The legacy addon
 * constructed an inflated PlayerModel over the canonical 64x64 player UV
 * layout; the hidden Stand entity was never the visible suit.
 */
public final class WhiteAlbumWearableModel<T extends LivingEntity>
        extends PlayerModel<T> {
    public static final int TEXTURE_SIZE = 64;
    public static final float SUIT_INFLATION = 0.20F;
    public static final float SHIELD_INFLATION = 0.60F;

    public static final ModelLayerLocation SUIT_DEFAULT =
            layer("white_album_suit_default");
    public static final ModelLayerLocation SUIT_SLIM =
            layer("white_album_suit_slim");
    public static final ModelLayerLocation SHIELD_DEFAULT =
            layer("white_album_shield_default");
    public static final ModelLayerLocation SHIELD_SLIM =
            layer("white_album_shield_slim");

    private final boolean slim;
    private final DonorLayer donorLayer;
    private final DonorGeometryProof donorGeometryProof;

    public WhiteAlbumWearableModel(
            ModelPart root,
            boolean slim) {
        this(root, slim, DonorLayer.SUIT);
    }

    public WhiteAlbumWearableModel(
            ModelPart root,
            boolean slim,
            DonorLayer donorLayer) {
        super(root, slim);
        this.slim = slim;
        this.donorLayer = donorLayer;
        this.donorGeometryProof = compareWithDonorGeometry(
                root, slim, donorLayer);
    }

    public static LayerDefinition createSuitDefaultLayer() {
        return createLayer(SUIT_INFLATION, false);
    }

    public static LayerDefinition createSuitSlimLayer() {
        return createLayer(SUIT_INFLATION, true);
    }

    public static LayerDefinition createShieldDefaultLayer() {
        return createLayer(SHIELD_INFLATION, false);
    }

    public static LayerDefinition createShieldSlimLayer() {
        return createLayer(SHIELD_INFLATION, true);
    }

    public boolean slim() {
        return slim;
    }

    public DonorLayer donorLayer() {
        return donorLayer;
    }

    public DonorGeometryProof donorGeometryProof() {
        return donorGeometryProof;
    }

    private static LayerDefinition createLayer(
            float inflation,
            boolean slim) {
        MeshDefinition mesh = PlayerModel.createMesh(
                new CubeDeformation(inflation), slim);
        return LayerDefinition.create(
                mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static DonorGeometryProof compareWithDonorGeometry(
            ModelPart actualRoot,
            boolean slim,
            DonorLayer donorLayer) {
        ModelPart expectedRoot = createLayer(
                donorLayer.inflation(), slim).bakeRoot();
        GeometrySnapshot actual = geometrySnapshot(actualRoot);
        GeometrySnapshot expected = geometrySnapshot(expectedRoot);
        boolean matched = actual.sha256().equals(expected.sha256());
        return new DonorGeometryProof(
                matched,
                donorLayer.name(),
                slim,
                donorLayer.inflation(),
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                actual.partCount(),
                actual.cubeCount(),
                actual.sha256(),
                expected.sha256(),
                matched ? "none" : "baked_part_definition_mismatch");
    }

    private static GeometrySnapshot geometrySnapshot(ModelPart root) {
        StringBuilder canonical = new StringBuilder(8192);
        int[] counts = new int[2];
        appendPart(canonical, "root", root, counts);
        return new GeometrySnapshot(
                sha256(canonical.toString()),
                counts[0],
                counts[1]);
    }

    private static void appendPart(
            StringBuilder canonical,
            String path,
            ModelPart part,
            int[] counts) {
        counts[0]++;
        canonical.append("part:").append(path).append('|');
        appendPose(canonical, part.getInitialPose());
        canonical.append("|cubes:").append(part.cubes.size());
        for (int cubeIndex = 0;
                cubeIndex < part.cubes.size();
                cubeIndex++) {
            counts[1]++;
            ModelPart.Cube cube = part.cubes.get(cubeIndex);
            canonical.append("|cube:").append(cubeIndex);
            appendFloat(canonical, cube.minX);
            appendFloat(canonical, cube.minY);
            appendFloat(canonical, cube.minZ);
            appendFloat(canonical, cube.maxX);
            appendFloat(canonical, cube.maxY);
            appendFloat(canonical, cube.maxZ);

            List<ModelPart.Polygon> polygons = new ArrayList<>(
                    List.of(cube.polygons));
            polygons.sort(Comparator.comparing(
                    WhiteAlbumWearableModel::polygonSortKey));
            canonical.append("|polygons:").append(polygons.size());
            for (ModelPart.Polygon polygon : polygons) {
                appendFloat(canonical, polygon.normal.x());
                appendFloat(canonical, polygon.normal.y());
                appendFloat(canonical, polygon.normal.z());
                canonical.append(':').append(polygon.vertices.length);
                for (ModelPart.Vertex vertex : polygon.vertices) {
                    appendFloat(canonical, vertex.pos.x());
                    appendFloat(canonical, vertex.pos.y());
                    appendFloat(canonical, vertex.pos.z());
                    appendFloat(canonical, vertex.u);
                    appendFloat(canonical, vertex.v);
                }
            }
        }

        List<String> childNames = new ArrayList<>(
                part.children.keySet());
        childNames.sort(String::compareTo);
        canonical.append("|children:").append(childNames.size());
        for (String childName : childNames) {
            appendPart(
                    canonical,
                    path + '/' + childName,
                    part.children.get(childName),
                    counts);
        }
    }

    private static String polygonSortKey(ModelPart.Polygon polygon) {
        return Float.floatToIntBits(polygon.normal.x()) + ":"
                + Float.floatToIntBits(polygon.normal.y()) + ":"
                + Float.floatToIntBits(polygon.normal.z());
    }

    private static void appendPose(
            StringBuilder canonical,
            PartPose pose) {
        appendFloat(canonical, pose.x);
        appendFloat(canonical, pose.y);
        appendFloat(canonical, pose.z);
        appendFloat(canonical, pose.xRot);
        appendFloat(canonical, pose.yRot);
        appendFloat(canonical, pose.zRot);
    }

    private static void appendFloat(
            StringBuilder canonical,
            float value) {
        canonical.append(':').append(Integer.toHexString(
                Float.floatToIntBits(value)));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.", exception);
        }
    }

    private static ModelLayerLocation layer(String path) {
        return new ModelLayerLocation(
                WhiteAlbumAddon.id(path), "main");
    }

    public enum DonorLayer {
        SUIT(SUIT_INFLATION),
        SHIELD(SHIELD_INFLATION);

        private final float inflation;

        DonorLayer(float inflation) {
            this.inflation = inflation;
        }

        public float inflation() {
            return inflation;
        }
    }

    public record DonorGeometryProof(
            boolean matched,
            String donorLayer,
            boolean slim,
            float inflation,
            int textureWidth,
            int textureHeight,
            int partCount,
            int cubeCount,
            String actualSha256,
            String expectedSha256,
            String mismatch) {}

    private record GeometrySnapshot(
            String sha256,
            int partCount,
            int cubeCount) {}
}
