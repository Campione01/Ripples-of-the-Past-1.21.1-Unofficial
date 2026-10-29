package rotp.addons.tusk.diagnostic;

import java.util.List;

public final class TuskAcceptanceGate {
    private static final float VALUE_EPSILON = 1.0E-4F;
    private static final float POSITION_LIMIT = 128.0F;
    private static final float POSITION_SPAN_LIMIT = 8.0F;
    private static final float NORMAL_MIN_LENGTH = 0.9F;
    private static final float NORMAL_MAX_LENGTH = 1.1F;
    private static final float NORMAL_DIRECTION_DOT = 0.9F;

    private TuskAcceptanceGate() {}

    public static boolean wormholeViewsAccepted(
            int minimumFrames,
            int firstPersonOwnerFrames,
            int thirdPersonOwnerFrames,
            int thirdPersonObserverFrames,
            boolean ownerCameraRestored) {
        return minimumFrames > 0
                && firstPersonOwnerFrames >= minimumFrames
                && thirdPersonOwnerFrames >= minimumFrames
                && thirdPersonObserverFrames >= minimumFrames
                && ownerCameraRestored;
    }

    public static boolean wormholeMovementAccepted(
            boolean moveInputStarted,
            boolean moveInputReleased,
            int moveInputCount,
            int moveReceiptCount,
            boolean expectedMember,
            boolean expectedTargetMedium,
            boolean entryFixed,
            boolean rangeVerified,
            boolean axisFactorsVerified,
            boolean groundCompensationVerified,
            boolean transformedPositionVerified) {
        return moveInputStarted
                && moveInputReleased
                && moveInputCount == 1
                && moveReceiptCount == 1
                && expectedMember
                && expectedTargetMedium
                && entryFixed
                && rangeVerified
                && axisFactorsVerified
                && groundCompensationVerified
                && transformedPositionVerified;
    }

    public static boolean wormholeGeometryAccepted(
            GeometryReceipt receipt,
            String expectedEntityUuid,
            String expectedOwnerUuid,
            String expectedTexture,
            String expectedVisualRoute,
            String expectedViewRoute,
            GeometryKind kind) {
        if (receipt == null
                || expectedEntityUuid == null
                || expectedOwnerUuid == null
                || expectedTexture == null
                || expectedVisualRoute == null
                || kind == null
                || !expectedEntityUuid.equals(receipt.entityUuid())
                || !expectedOwnerUuid.equals(receipt.ownerUuid())
                || !expectedTexture.equals(receipt.texture())
                || !expectedVisualRoute.equals(receipt.visualRoute())
                || !(expectedViewRoute != null
                        ? expectedViewRoute.equals(receipt.viewRoute())
                        : acceptedViewRoute(receipt.viewRoute()))
                || receipt.frameId() < 0L
                || receipt.screenWidth() <= 0
                || receipt.screenHeight() <= 0
                || !receipt.projectionFinite()
                || !Float.isFinite(receipt.projectionDeterminant())
                || Math.abs(receipt.projectionDeterminant())
                        <= 1.0E-8F
                || receipt.vertexCount() != kind.expectedVertexCount
                || receipt.uvCount() != receipt.vertexCount()
                || receipt.normalCount() != receipt.vertexCount()
                || receipt.uninspectedVertexCount() != 0
                || !receipt.finitePositions()
                || !receipt.finiteUvs()
                || !receipt.finiteNormals()
                || receipt.vertices() == null
                || receipt.vertices().size() != receipt.vertexCount()
                || !orderedFinite(receipt.minX(), receipt.maxX())
                || !orderedFinite(receipt.minY(), receipt.maxY())
                || !orderedFinite(receipt.minZ(), receipt.maxZ())
                || !orderedFinite(receipt.minU(), receipt.maxU())
                || !orderedFinite(receipt.minV(), receipt.maxV())) {
            return false;
        }

        float xSpan = receipt.maxX() - receipt.minX();
        float ySpan = receipt.maxY() - receipt.minY();
        float zSpan = receipt.maxZ() - receipt.minZ();
        int transformedAxes = 0;
        transformedAxes += xSpan > VALUE_EPSILON ? 1 : 0;
        transformedAxes += ySpan > VALUE_EPSILON ? 1 : 0;
        transformedAxes += zSpan > VALUE_EPSILON ? 1 : 0;
        if (transformedAxes < kind.minimumTransformedAxes
                || xSpan > POSITION_SPAN_LIMIT
                || ySpan > POSITION_SPAN_LIMIT
                || zSpan > POSITION_SPAN_LIMIT
                || !withinPositionLimit(receipt.minX())
                || !withinPositionLimit(receipt.minY())
                || !withinPositionLimit(receipt.minZ())
                || !withinPositionLimit(receipt.maxX())
                || !withinPositionLimit(receipt.maxY())
                || !withinPositionLimit(receipt.maxZ())
                || receipt.minU() < -0.51F
                || receipt.minV() < -0.51F
                || receipt.maxU() > 1.51F
                || receipt.maxV() > 1.51F
                || (receipt.maxU() - receipt.minU()
                        <= VALUE_EPSILON
                        && receipt.maxV() - receipt.minV()
                                <= VALUE_EPSILON)) {
            return false;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        double minProjectedX = Double.POSITIVE_INFINITY;
        double minProjectedY = Double.POSITIVE_INFINITY;
        double maxProjectedX = Double.NEGATIVE_INFINITY;
        double maxProjectedY = Double.NEGATIVE_INFINITY;
        for (GeometryVertex vertex : receipt.vertices()) {
            if (vertex == null
                    || !finite(vertex.x(), vertex.y(), vertex.z())
                    || !finite(vertex.u(), vertex.v())
                    || !finite(
                            vertex.normalX(),
                            vertex.normalY(),
                            vertex.normalZ())
                    || !vertex.projected()
                    || !Double.isFinite(vertex.projectedX())
                    || !Double.isFinite(vertex.projectedY())) {
                return false;
            }
            float normalLength = length(
                    vertex.normalX(),
                    vertex.normalY(),
                    vertex.normalZ());
            if (normalLength < NORMAL_MIN_LENGTH
                    || normalLength > NORMAL_MAX_LENGTH) {
                return false;
            }
            if (kind == GeometryKind.REMOTE_ARM_MODEL
                    && (vertex.u() < -VALUE_EPSILON
                            || vertex.v() < -VALUE_EPSILON
                            || vertex.u() > 1.0F + VALUE_EPSILON
                            || vertex.v() > 1.0F + VALUE_EPSILON)) {
                return false;
            }
            minX = Math.min(minX, vertex.x());
            minY = Math.min(minY, vertex.y());
            minZ = Math.min(minZ, vertex.z());
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
            maxZ = Math.max(maxZ, vertex.z());
            minU = Math.min(minU, vertex.u());
            minV = Math.min(minV, vertex.v());
            maxU = Math.max(maxU, vertex.u());
            maxV = Math.max(maxV, vertex.v());
            minProjectedX = Math.min(
                    minProjectedX, vertex.projectedX());
            minProjectedY = Math.min(
                    minProjectedY, vertex.projectedY());
            maxProjectedX = Math.max(
                    maxProjectedX, vertex.projectedX());
            maxProjectedY = Math.max(
                    maxProjectedY, vertex.projectedY());
        }
        double projectedWidth = maxProjectedX - minProjectedX;
        double projectedHeight = maxProjectedY - minProjectedY;
        if (!close(minX, receipt.minX())
                || !close(minY, receipt.minY())
                || !close(minZ, receipt.minZ())
                || !close(maxX, receipt.maxX())
                || !close(maxY, receipt.maxY())
                || !close(maxZ, receipt.maxZ())
                || !close(minU, receipt.minU())
                || !close(minV, receipt.minV())
                || !close(maxU, receipt.maxU())
                || !close(maxV, receipt.maxV())
                || receipt.projectedVertexCount()
                        != receipt.vertexCount()
                || !Double.isFinite(receipt.projectedWidthPixels())
                || !Double.isFinite(receipt.projectedHeightPixels())
                || !close(
                        projectedWidth,
                        receipt.projectedWidthPixels())
                || !close(
                        projectedHeight,
                        receipt.projectedHeightPixels())
                || projectedWidth > receipt.screenWidth() * 4.0D
                || projectedHeight > receipt.screenHeight() * 4.0D
                || (projectedWidth <= 0.25D
                        && projectedHeight <= 0.25D)
                || !windingNormalsAccepted(
                        receipt.vertices(), kind)) {
            return false;
        }
        return kind != GeometryKind.FIRST_PERSON_PORTAL
                || firstPersonUvAccepted(receipt.vertices());
    }

    private static boolean acceptedViewRoute(String viewRoute) {
        return "FIRST_PERSON_OWNER".equals(viewRoute)
                || "THIRD_PERSON_OWNER".equals(viewRoute)
                || "THIRD_PERSON_OBSERVER".equals(viewRoute);
    }

    private static boolean orderedFinite(float minimum, float maximum) {
        return Float.isFinite(minimum)
                && Float.isFinite(maximum)
                && minimum <= maximum;
    }

    private static boolean withinPositionLimit(float value) {
        return value >= -POSITION_LIMIT && value <= POSITION_LIMIT;
    }

    private static boolean finite(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static boolean close(float first, float second) {
        float scale = Math.max(
                1.0F, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= VALUE_EPSILON * scale;
    }

    private static boolean close(double first, double second) {
        double scale = Math.max(
                1.0D, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= 1.0E-6D * scale;
    }

    private static boolean windingNormalsAccepted(
            List<GeometryVertex> vertices, GeometryKind kind) {
        float maximumArea = 0.0F;
        for (int index = 0; index < vertices.size(); index += 4) {
            maximumArea = Math.max(
                    maximumArea,
                    quadCrossLength(vertices, index));
        }
        int orientedQuads = 0;
        for (int index = 0; index < vertices.size(); index += 4) {
            GeometryVertex first = vertices.get(index);
            GeometryVertex second = vertices.get(index + 1);
            GeometryVertex third = vertices.get(index + 2);
            float abX = second.x() - first.x();
            float abY = second.y() - first.y();
            float abZ = second.z() - first.z();
            float acX = third.x() - first.x();
            float acY = third.y() - first.y();
            float acZ = third.z() - first.z();
            float crossX = abY * acZ - abZ * acY;
            float crossY = abZ * acX - abX * acZ;
            float crossZ = abX * acY - abY * acX;
            float crossLength = length(crossX, crossY, crossZ);
            if (crossLength <= 1.0E-5F) {
                continue;
            }
            orientedQuads++;
            boolean directionRequired =
                    kind != GeometryKind.MODEL_PART_PORTAL
                    || crossLength >= maximumArea * 0.5F;
            if (!directionRequired) {
                continue;
            }
            crossX /= crossLength;
            crossY /= crossLength;
            crossZ /= crossLength;
            for (int vertexIndex = index;
                    vertexIndex < index + 4;
                    vertexIndex++) {
                GeometryVertex vertex = vertices.get(vertexIndex);
                float dot = crossX * vertex.normalX()
                        + crossY * vertex.normalY()
                        + crossZ * vertex.normalZ();
                if (dot < NORMAL_DIRECTION_DOT) {
                    return false;
                }
            }
        }
        return orientedQuads >= kind.minimumOrientedQuads
                && orientedQuads <= kind.maximumOrientedQuads;
    }

    private static float quadCrossLength(
            List<GeometryVertex> vertices, int index) {
        GeometryVertex first = vertices.get(index);
        GeometryVertex second = vertices.get(index + 1);
        GeometryVertex third = vertices.get(index + 2);
        float abX = second.x() - first.x();
        float abY = second.y() - first.y();
        float abZ = second.z() - first.z();
        float acX = third.x() - first.x();
        float acY = third.y() - first.y();
        float acZ = third.z() - first.z();
        return length(
                abY * acZ - abZ * acY,
                abZ * acX - abX * acZ,
                abX * acY - abY * acX);
    }

    private static boolean firstPersonUvAccepted(
            List<GeometryVertex> vertices) {
        float[][] expected = {
                {0.0F, 0.25F},
                {0.25F, 0.25F},
                {0.25F, 0.0F},
                {0.0F, 0.0F}
        };
        for (int index = 0; index < expected.length; index++) {
            if (!close(vertices.get(index).u(), expected[index][0])
                    || !close(
                            vertices.get(index).v(),
                            expected[index][1])) {
                return false;
            }
        }
        return true;
    }

    public enum GeometryKind {
        MODEL_PART_PORTAL(24, 2, 2, 6),
        FIRST_PERSON_PORTAL(4, 2, 1, 1),
        REMOTE_ARM_MODEL(48, 3, 12, 12);

        private final int expectedVertexCount;
        private final int minimumTransformedAxes;
        private final int minimumOrientedQuads;
        private final int maximumOrientedQuads;

        GeometryKind(
                int expectedVertexCount,
                int minimumTransformedAxes,
                int minimumOrientedQuads,
                int maximumOrientedQuads) {
            this.expectedVertexCount = expectedVertexCount;
            this.minimumTransformedAxes = minimumTransformedAxes;
            this.minimumOrientedQuads = minimumOrientedQuads;
            this.maximumOrientedQuads = maximumOrientedQuads;
        }
    }

    public record GeometryVertex(
            float x,
            float y,
            float z,
            float u,
            float v,
            float normalX,
            float normalY,
            float normalZ,
            boolean projected,
            double projectedX,
            double projectedY) {}

    public record GeometryReceipt(
            long frameId,
            String entityUuid,
            String ownerUuid,
            String visualRoute,
            String viewRoute,
            String texture,
            int screenWidth,
            int screenHeight,
            boolean projectionFinite,
            float projectionDeterminant,
            int vertexCount,
            int uvCount,
            int normalCount,
            int uninspectedVertexCount,
            boolean finitePositions,
            boolean finiteUvs,
            boolean finiteNormals,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int projectedVertexCount,
            double projectedWidthPixels,
            double projectedHeightPixels,
            List<GeometryVertex> vertices) {
        public GeometryReceipt {
            if (vertices != null) {
                vertices = List.copyOf(vertices);
            }
        }
    }

    public static boolean goldenSpinVisualAccepted(
            int minimumFrames,
            boolean effectSynchronized,
            int targetVisibleFrames,
            int activeAbilityRendererFrames,
            boolean rendererObservedDuringPerform,
            boolean clientPerformReceiptVerified,
            boolean serverImpactReceiptVerified,
            boolean captureWhileAbilityActive) {
        return minimumFrames > 0
                && effectSynchronized
                && targetVisibleFrames >= minimumFrames
                && activeAbilityRendererFrames >= minimumFrames
                && rendererObservedDuringPerform
                && clientPerformReceiptVerified
                && serverImpactReceiptVerified
                && captureWhileAbilityActive;
    }
}
