package rotp.addons.catchtherainbow.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.catchtherainbow.client.RainBladeDonorGeometry.BladeVertex;
import rotp.addons.catchtherainbow.client.RainBladeDonorGeometry.DropletSpec;
import net.minecraft.core.Direction;

/** Pure validation records for one complete rain-blade model submission. */
public final class RainBladeGeometryReceipt {
    public static final int DONOR_BLADE_MESH_COUNT = 1;
    public static final int DONOR_DROPLET_CUBE_COUNT = 15;
    public static final int VERTICES_PER_SEGMENT = 24;
    public static final int TOTAL_DONOR_VERTEX_COUNT = 384;
    public static final String BLADE_MESH_SEGMENT_ID = "blade_mesh";
    public static final List<String> DROPLET_SEGMENT_IDS =
            RainBladeDonorGeometry.DROPLETS.stream()
                    .map(DropletSpec::segmentId)
                    .toList();

    private static final double POSITION_GRID = 640.0D;
    private static final double UV_GRID = 1_600.0D;
    private static final double NORMAL_GRID = 100_000.0D;
    private static final long FNV_OFFSET_BASIS =
            0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;
    private static final List<VertexReceipt> EXACT_BLADE_MESH_VERTICES =
            createBladeMeshVertices();
    private static final List<SegmentBaseline> EXACT_SEGMENT_BASELINE =
            createExactSegmentBaseline();

    private RainBladeGeometryReceipt() {}

    public enum SegmentKind {
        CUSTOM_MESH,
        CUBOID
    }

    public record ModelGeometryReceipt(
            int submittedBladeMeshCount,
            int submittedDropletCubeCount,
            int bladeMeshVertexCount,
            int dropletVertexCount,
            boolean bladeGeometryFullyInspectedAndFinite,
            boolean dropletGeometryFullyInspectedAndFinite,
            List<SegmentReceipt> segments) {
        public ModelGeometryReceipt {
            segments = segments != null
                    ? List.copyOf(segments) : List.of();
        }

        public static ModelGeometryReceipt from(
                List<SegmentReceipt> segments) {
            List<SegmentReceipt> copied = segments != null
                    ? List.copyOf(segments) : List.of();
            int bladeMeshes = 0;
            int dropletCubes = 0;
            int bladeVertices = 0;
            int dropletVertices = 0;
            boolean bladeInspected = true;
            boolean dropletsInspected = true;
            for (SegmentReceipt segment : copied) {
                if (segment != null
                        && segment.kind() == SegmentKind.CUSTOM_MESH) {
                    bladeMeshes++;
                    bladeVertices += segment.vertexCount();
                    bladeInspected &= segment.fullyInspectedAndFinite();
                }
                else if (segment != null
                        && segment.kind() == SegmentKind.CUBOID) {
                    dropletCubes++;
                    dropletVertices += segment.vertexCount();
                    dropletsInspected &=
                            segment.fullyInspectedAndFinite();
                }
            }
            return new ModelGeometryReceipt(
                    bladeMeshes,
                    dropletCubes,
                    bladeVertices,
                    dropletVertices,
                    bladeMeshes > 0 && bladeInspected,
                    dropletCubes > 0 && dropletsInspected,
                    copied);
        }

        public boolean accepted() {
            List<SegmentBaseline> expected = exactSegmentBaseline();
            List<String> actualIds = new ArrayList<>(segments.size());
            Set<String> uniqueIds = new HashSet<>();
            Set<Long> uniqueFingerprints = new HashSet<>();
            if (segments.size() != expected.size()) {
                return false;
            }
            for (int index = 0; index < segments.size(); index++) {
                SegmentReceipt segment = segments.get(index);
                if (!SegmentReceipt.accepted(segment)
                        || !expected.get(index).matches(segment)) {
                    return false;
                }
                actualIds.add(segment.segmentId());
                if (!uniqueIds.add(segment.segmentId())
                        || !uniqueFingerprints.add(
                                segment.geometryFingerprint())) {
                    return false;
                }
            }
            return actualIds.equals(expectedSegmentIds())
                    && submittedBladeMeshCount
                            == DONOR_BLADE_MESH_COUNT
                    && submittedDropletCubeCount
                            == DONOR_DROPLET_CUBE_COUNT
                    && bladeMeshVertexCount == VERTICES_PER_SEGMENT
                    && dropletVertexCount
                            == DONOR_DROPLET_CUBE_COUNT
                                    * VERTICES_PER_SEGMENT
                    && bladeMeshVertexCount + dropletVertexCount
                            == TOTAL_DONOR_VERTEX_COUNT
                    && bladeGeometryFullyInspectedAndFinite
                    && dropletGeometryFullyInspectedAndFinite;
        }

        public static List<String> expectedSegmentIds() {
            List<String> ids = new ArrayList<>(
                    DONOR_BLADE_MESH_COUNT
                            + DONOR_DROPLET_CUBE_COUNT);
            ids.addAll(DROPLET_SEGMENT_IDS);
            ids.add(BLADE_MESH_SEGMENT_ID);
            return List.copyOf(ids);
        }

        public static List<SegmentBaseline> exactSegmentBaseline() {
            return EXACT_SEGMENT_BASELINE;
        }

        public static ModelGeometryReceipt empty() {
            return new ModelGeometryReceipt(
                    0, 0, 0, 0, false, false, List.of());
        }
    }

    public record VertexReceipt(
            float x,
            float y,
            float z,
            float u,
            float v,
            float normalX,
            float normalY,
            float normalZ) {}

    public record SegmentBaseline(
            String segmentId,
            SegmentKind kind,
            int vertexCount,
            int faceCount,
            float minX,
            float maxX,
            float minY,
            float maxY,
            float minZ,
            float maxZ,
            float minU,
            float maxU,
            float minV,
            float maxV,
            long geometryFingerprint,
            long normalFingerprint,
            long windingFingerprint) {
        public boolean matches(SegmentReceipt candidate) {
            return candidate != null
                    && segmentId.equals(candidate.segmentId())
                    && kind == candidate.kind()
                    && candidate.vertexCount() == vertexCount
                    && candidate.positionCount() == vertexCount
                    && candidate.uvCount() == vertexCount
                    && candidate.normalCount() == vertexCount
                    && candidate.uninspectedVertexCount() == 0
                    && candidate.allPositionsFinite()
                    && candidate.allUvsFinite()
                    && candidate.allNormalsFinite()
                    && candidate.windingFullyInspected()
                    && candidate.windingFaceCount() == faceCount
                    && exact(minX, candidate.minX())
                    && exact(maxX, candidate.maxX())
                    && exact(minY, candidate.minY())
                    && exact(maxY, candidate.maxY())
                    && exact(minZ, candidate.minZ())
                    && exact(maxZ, candidate.maxZ())
                    && exact(minU, candidate.minU())
                    && exact(maxU, candidate.maxU())
                    && exact(minV, candidate.minV())
                    && exact(maxV, candidate.maxV())
                    && geometryFingerprint
                            == candidate.geometryFingerprint()
                    && normalFingerprint
                            == candidate.normalFingerprint()
                    && windingFingerprint
                            == candidate.windingFingerprint();
        }

        public SegmentReceipt toReceipt() {
            return new SegmentReceipt(
                    segmentId,
                    kind,
                    vertexCount,
                    vertexCount,
                    vertexCount,
                    vertexCount,
                    0,
                    true,
                    true,
                    true,
                    true,
                    faceCount,
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ,
                    minU,
                    maxU,
                    minV,
                    maxV,
                    geometryFingerprint,
                    normalFingerprint,
                    windingFingerprint);
        }

        private static boolean exact(float expected, float actual) {
            return Float.floatToRawIntBits(expected)
                    == Float.floatToRawIntBits(actual);
        }
    }

    public record SegmentReceipt(
            String segmentId,
            SegmentKind kind,
            int vertexCount,
            int positionCount,
            int uvCount,
            int normalCount,
            int uninspectedVertexCount,
            boolean allPositionsFinite,
            boolean allUvsFinite,
            boolean allNormalsFinite,
            boolean windingFullyInspected,
            int windingFaceCount,
            float minX,
            float maxX,
            float minY,
            float maxY,
            float minZ,
            float maxZ,
            float minU,
            float maxU,
            float minV,
            float maxV,
            long geometryFingerprint,
            long normalFingerprint,
            long windingFingerprint) {
        private static final float UV_EPSILON = 0.001F;

        public static SegmentReceipt fromVertices(
                String segmentId,
                SegmentKind kind,
                List<VertexReceipt> vertices) {
            List<VertexReceipt> copied = vertices != null
                    ? List.copyOf(vertices) : List.of();
            int count = copied.size();
            boolean positionsFinite = true;
            boolean uvsFinite = true;
            boolean normalsFinite = true;
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            float minU = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            for (VertexReceipt vertex : copied) {
                positionsFinite &= Float.isFinite(vertex.x())
                        && Float.isFinite(vertex.y())
                        && Float.isFinite(vertex.z());
                uvsFinite &= Float.isFinite(vertex.u())
                        && Float.isFinite(vertex.v());
                normalsFinite &= Float.isFinite(vertex.normalX())
                        && Float.isFinite(vertex.normalY())
                        && Float.isFinite(vertex.normalZ());
                minX = Math.min(minX, vertex.x());
                maxX = Math.max(maxX, vertex.x());
                minY = Math.min(minY, vertex.y());
                maxY = Math.max(maxY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxZ = Math.max(maxZ, vertex.z());
                minU = Math.min(minU, vertex.u());
                maxU = Math.max(maxU, vertex.u());
                minV = Math.min(minV, vertex.v());
                maxV = Math.max(maxV, vertex.v());
            }
            WindingSummary winding = windingSummary(copied);
            return new SegmentReceipt(
                    segmentId,
                    kind,
                    count,
                    count,
                    count,
                    count,
                    0,
                    positionsFinite,
                    uvsFinite,
                    normalsFinite,
                    winding.fullyInspected(),
                    winding.faceCount(),
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ,
                    minU,
                    maxU,
                    minV,
                    maxV,
                    RainBladeGeometryReceipt.geometryFingerprint(
                            copied, 0),
                    RainBladeGeometryReceipt.normalFingerprint(copied),
                    winding.fingerprint());
        }

        public boolean fullyInspectedAndFinite() {
            return vertexCount > 0
                    && vertexCount % 4 == 0
                    && positionCount == vertexCount
                    && uvCount == vertexCount
                    && normalCount == vertexCount
                    && uninspectedVertexCount == 0
                    && allPositionsFinite
                    && allUvsFinite
                    && allNormalsFinite
                    && windingFullyInspected
                    && windingFaceCount == vertexCount / 4
                    && finiteOrdered(minX, maxX)
                    && finiteOrdered(minY, maxY)
                    && finiteOrdered(minZ, maxZ)
                    && finiteOrdered(minU, maxU)
                    && finiteOrdered(minV, maxV)
                    && minU >= -UV_EPSILON
                    && minU <= 1.0F + UV_EPSILON
                    && maxU >= -UV_EPSILON
                    && maxU <= 1.0F + UV_EPSILON
                    && minV >= -UV_EPSILON
                    && minV <= 1.0F + UV_EPSILON
                    && maxV >= -UV_EPSILON
                    && maxV <= 1.0F + UV_EPSILON
                    && geometryFingerprint != 0L
                    && normalFingerprint != 0L
                    && windingFingerprint != 0L;
        }

        public static boolean accepted(SegmentReceipt candidate) {
            return candidate != null
                    && candidate.segmentId() != null
                    && !candidate.segmentId().isBlank()
                    && candidate.kind() != null
                    && candidate.fullyInspectedAndFinite();
        }

        private static boolean finiteOrdered(float min, float max) {
            return Float.isFinite(min)
                    && Float.isFinite(max)
                    && min <= max;
        }
    }

    public static List<VertexReceipt> exactBladeMeshVertices() {
        return EXACT_BLADE_MESH_VERTICES;
    }

    public static List<VertexReceipt> exactDropletVertices(
            DropletSpec droplet, float xRotation) {
        return createDropletVertices(droplet, xRotation);
    }

    private static List<SegmentBaseline> createExactSegmentBaseline() {
        List<SegmentBaseline> baseline = new ArrayList<>(16);
        for (DropletSpec droplet : RainBladeDonorGeometry.DROPLETS) {
            baseline.add(toBaseline(SegmentReceipt.fromVertices(
                    droplet.segmentId(),
                    SegmentKind.CUBOID,
                    createDropletVertices(
                            droplet,
                            RainBladeDonorGeometry.DROPS_X_ROTATION))));
        }
        baseline.add(toBaseline(SegmentReceipt.fromVertices(
                BLADE_MESH_SEGMENT_ID,
                SegmentKind.CUSTOM_MESH,
                EXACT_BLADE_MESH_VERTICES)));
        return List.copyOf(baseline);
    }

    private static SegmentBaseline toBaseline(SegmentReceipt receipt) {
        return new SegmentBaseline(
                receipt.segmentId(),
                receipt.kind(),
                receipt.vertexCount(),
                receipt.windingFaceCount(),
                receipt.minX(),
                receipt.maxX(),
                receipt.minY(),
                receipt.maxY(),
                receipt.minZ(),
                receipt.maxZ(),
                receipt.minU(),
                receipt.maxU(),
                receipt.minV(),
                receipt.maxV(),
                receipt.geometryFingerprint(),
                receipt.normalFingerprint(),
                receipt.windingFingerprint());
    }

    private static List<VertexReceipt> createBladeMeshVertices() {
        Vector3f[] corners = new Vector3f[8];
        for (BladeVertex source : RainBladeDonorGeometry.BLADE_VERTICES) {
            boolean xPositive = !source.xPositive();
            boolean yPositive = !source.yPositive();
            int index = (source.zPositive() ? 1 : 0)
                    | (yPositive ? 2 : 0)
                    | (xPositive ? 4 : 0);
            corners[index] = new Vector3f(
                    -source.x(), -source.y(), source.z());
        }

        PoseStack pose = new PoseStack();
        pose.translate(
                0.0F,
                RainBladeDonorGeometry.WATER_CUTTER_Y / 16.0F,
                0.0F);
        List<VertexReceipt> vertices = new ArrayList<>(24);
        appendFace(vertices, pose.last(), corners,
                new int[] {5, 4, 6, 7},
                0.0F, 6.0F, 16.0F, 12.0F,
                Direction.EAST);
        appendFace(vertices, pose.last(), corners,
                new int[] {0, 1, 3, 2},
                0.0F, 6.0F, 16.0F, 12.0F,
                Direction.WEST);
        appendFace(vertices, pose.last(), corners,
                new int[] {5, 1, 0, 4},
                0.0F, 6.0F, 16.0F, 12.0F,
                Direction.DOWN);
        appendFace(vertices, pose.last(), corners,
                new int[] {6, 2, 3, 7},
                0.0F, 12.0F, 16.0F, 6.0F,
                Direction.UP);
        appendFace(vertices, pose.last(), corners,
                new int[] {4, 0, 2, 6},
                0.0F, 6.0F, 16.0F, 12.0F,
                Direction.NORTH);
        appendFace(vertices, pose.last(), corners,
                new int[] {1, 5, 7, 3},
                0.0F, 6.0F, 16.0F, 12.0F,
                Direction.SOUTH);
        return List.copyOf(vertices);
    }

    private static List<VertexReceipt> createDropletVertices(
            DropletSpec droplet, float xRotation) {
        float x0 = droplet.x() - droplet.deformation();
        float y0 = droplet.y() - droplet.deformation();
        float z0 = droplet.z() - droplet.deformation();
        float x1 = droplet.x() + droplet.sizeX()
                + droplet.deformation();
        float y1 = droplet.y() + droplet.sizeY()
                + droplet.deformation();
        float z1 = droplet.z() + droplet.sizeZ()
                + droplet.deformation();
        Vector3f[] corners = new Vector3f[] {
            new Vector3f(x0, y0, z0),
            new Vector3f(x1, y0, z0),
            new Vector3f(x1, y1, z0),
            new Vector3f(x0, y1, z0),
            new Vector3f(x0, y0, z1),
            new Vector3f(x1, y0, z1),
            new Vector3f(x1, y1, z1),
            new Vector3f(x0, y1, z1)
        };

        float u0 = droplet.textureU();
        float u1 = u0 + droplet.sizeZ();
        float u2 = u1 + droplet.sizeX();
        float u3 = u2 + droplet.sizeX();
        float u4 = u2 + droplet.sizeZ();
        float u5 = u4 + droplet.sizeX();
        float v0 = droplet.textureV();
        float v1 = v0 + droplet.sizeZ();
        float v2 = v1 + droplet.sizeY();

        PoseStack pose = new PoseStack();
        pose.translate(
                0.0F,
                RainBladeDonorGeometry.WATER_CUTTER_Y / 16.0F,
                0.0F);
        pose.translate(
                RainBladeDonorGeometry.DROPS_X / 16.0F,
                RainBladeDonorGeometry.DROPS_Y / 16.0F,
                RainBladeDonorGeometry.DROPS_Z / 16.0F);
        pose.mulPose(new org.joml.Quaternionf().rotationZYX(
                0.0F, 0.0F, xRotation));

        List<VertexReceipt> vertices = new ArrayList<>(24);
        appendFace(vertices, pose.last(), corners,
                new int[] {5, 4, 0, 1},
                u1, v0, u2, v1, Direction.DOWN);
        appendFace(vertices, pose.last(), corners,
                new int[] {2, 3, 7, 6},
                u2, v1, u3, v0, Direction.UP);
        appendFace(vertices, pose.last(), corners,
                new int[] {0, 4, 7, 3},
                u0, v1, u1, v2, Direction.WEST);
        appendFace(vertices, pose.last(), corners,
                new int[] {1, 0, 3, 2},
                u1, v1, u2, v2, Direction.NORTH);
        appendFace(vertices, pose.last(), corners,
                new int[] {5, 1, 2, 6},
                u2, v1, u4, v2, Direction.EAST);
        appendFace(vertices, pose.last(), corners,
                new int[] {4, 5, 6, 7},
                u4, v1, u5, v2, Direction.SOUTH);
        return List.copyOf(vertices);
    }

    private static void appendFace(
            List<VertexReceipt> output,
            PoseStack.Pose pose,
            Vector3f[] corners,
            int[] indices,
            float u0,
            float v0,
            float u1,
            float v1,
            Direction direction) {
        float[] us = new float[] {u1, u0, u0, u1};
        float[] vs = new float[] {v0, v0, v1, v1};
        Vector3f normal = pose.transformNormal(
                new Vector3f(
                        direction.getStepX(),
                        direction.getStepY(),
                        direction.getStepZ()),
                new Vector3f());
        if (normal.isFinite() && normal.lengthSquared() > 0.0F) {
            normal.normalize();
        }
        for (int index = 0; index < indices.length; index++) {
            Vector3f corner = corners[indices[index]];
            Vector3f position = pose.pose().transformPosition(
                    corner.x() / 16.0F,
                    corner.y() / 16.0F,
                    corner.z() / 16.0F,
                    new Vector3f());
            output.add(new VertexReceipt(
                    canonical(position.x(), POSITION_GRID),
                    canonical(position.y(), POSITION_GRID),
                    canonical(position.z(), POSITION_GRID),
                    canonical(
                            us[index]
                                    / RainBladeDonorGeometry.TEXTURE_WIDTH,
                            UV_GRID),
                    canonical(
                            vs[index]
                                    / RainBladeDonorGeometry.TEXTURE_HEIGHT,
                            UV_GRID),
                    canonical(normal.x(), NORMAL_GRID),
                    canonical(normal.y(), NORMAL_GRID),
                    canonical(normal.z(), NORMAL_GRID)));
        }
    }

    private static long geometryFingerprint(
            List<VertexReceipt> vertices, int uninspected) {
        long fingerprint = FNV_OFFSET_BASIS;
        for (VertexReceipt vertex : vertices) {
            fingerprint = fingerprint(fingerprint, 0x50);
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.x()));
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.y()));
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.z()));
            fingerprint = fingerprint(fingerprint, 0x55);
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.u()));
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.v()));
            fingerprint = fingerprint(fingerprint, 0x4E);
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.normalX()));
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.normalY()));
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.normalZ()));
        }
        if (uninspected > 0) {
            fingerprint = fingerprint(fingerprint, 0x42);
            fingerprint = fingerprint(fingerprint, uninspected);
        }
        return fingerprint;
    }

    private static long normalFingerprint(List<VertexReceipt> vertices) {
        long fingerprint = FNV_OFFSET_BASIS;
        for (VertexReceipt vertex : vertices) {
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.normalX()));
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.normalY()));
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(vertex.normalZ()));
        }
        return fingerprint;
    }

    private static WindingSummary windingSummary(
            List<VertexReceipt> vertices) {
        long fingerprint = FNV_OFFSET_BASIS;
        boolean inspected = vertices.size() % 4 == 0;
        int faceCount = 0;
        for (int index = 0;
                inspected && index < vertices.size(); index += 4) {
            VertexReceipt first = vertices.get(index);
            VertexReceipt second = vertices.get(index + 1);
            VertexReceipt third = vertices.get(index + 2);
            Vector3f edgeA = new Vector3f(
                    second.x() - first.x(),
                    second.y() - first.y(),
                    second.z() - first.z());
            Vector3f edgeB = new Vector3f(
                    third.x() - first.x(),
                    third.y() - first.y(),
                    third.z() - first.z());
            float dot = canonical(
                    edgeA.cross(edgeB).dot(
                            first.normalX(),
                            first.normalY(),
                            first.normalZ()),
                    NORMAL_GRID);
            inspected &= Float.isFinite(dot)
                    && Math.abs(dot) > 0.0000001F;
            fingerprint = fingerprint(fingerprint,
                    Float.floatToRawIntBits(dot));
            faceCount++;
        }
        return new WindingSummary(
                inspected, faceCount, fingerprint);
    }

    private static long fingerprint(long current, int value) {
        current ^= value & 0xFFFFFFFFL;
        return current * FNV_PRIME;
    }

    private static float canonical(float value, double grid) {
        if (!Float.isFinite(value)) {
            return value;
        }
        float canonical = (float) (Math.rint(value * grid) / grid);
        return canonical == 0.0F ? 0.0F : canonical;
    }

    private record WindingSummary(
            boolean fullyInspected,
            int faceCount,
            long fingerprint) {}
}
