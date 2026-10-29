package rotp.addons.tusk.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import rotp.core.api.client.render.EntityPostRenderExtensions;

import javax.annotation.Nullable;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class TuskClientRenderDiagnostics {
    private static final double MIN_FORWARD_DOT = 0.2D;
    private static final Map<ReceiptKey, ReceiptState> RECEIPTS =
            new LinkedHashMap<>();
    private static final Map<ReceiptKey, GeometryReceiptState>
            GEOMETRY_RECEIPTS = new LinkedHashMap<>();

    private TuskClientRenderDiagnostics() {}

    public static synchronized void reset() {
        RECEIPTS.clear();
        GEOMETRY_RECEIPTS.clear();
    }

    public static GeometryCapture beginGeometry(
            VisualRoute visualRoute,
            Entity entity,
            @Nullable UUID ownerUuid,
            ResourceLocation texture,
            VertexConsumer delegate) {
        return new GeometryCapture(
                visualRoute,
                entity.getUUID(),
                ownerUuid,
                texture,
                delegate);
    }

    @Nullable
    public static synchronized GeometrySnapshot geometry(
            UUID entityUuid,
            VisualRoute visualRoute,
            ViewRoute viewRoute) {
        GeometryReceiptState receipt = GEOMETRY_RECEIPTS.get(
                new ReceiptKey(entityUuid, visualRoute, viewRoute));
        return receipt != null ? receipt.latest : null;
    }

    @Nullable
    public static synchronized GeometrySnapshot geometryAnyView(
            UUID entityUuid, VisualRoute visualRoute) {
        GeometrySnapshot latest = null;
        for (Map.Entry<ReceiptKey, GeometryReceiptState> entry
                : GEOMETRY_RECEIPTS.entrySet()) {
            ReceiptKey key = entry.getKey();
            GeometrySnapshot candidate = entry.getValue().latest;
            if (key.entityUuid().equals(entityUuid)
                    && key.visualRoute() == visualRoute
                    && candidate != null
                    && (latest == null
                            || candidate.frameId() > latest.frameId())) {
                latest = candidate;
            }
        }
        return latest;
    }

    public static void recordEntityRender(
            VisualRoute visualRoute,
            Entity entity,
            @Nullable UUID ownerUuid) {
        if (ownerUuid != null) {
            record(visualRoute, entity.getUUID(), ownerUuid);
        }
    }

    public static void recordFixedScreenRender(
            VisualRoute visualRoute,
            Entity entity,
            @Nullable UUID ownerUuid) {
        if (ownerUuid != null) {
            record(visualRoute, entity.getUUID(), ownerUuid);
        }
    }

    public static synchronized int frames(
            UUID entityUuid,
            VisualRoute visualRoute,
            ViewRoute viewRoute) {
        ReceiptState receipt = RECEIPTS.get(
                new ReceiptKey(entityUuid, visualRoute, viewRoute));
        return receipt != null ? receipt.frames : 0;
    }

    public static boolean isOnScreen(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return false;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 toEntity = entity.getBoundingBox().getCenter()
                .subtract(camera.getPosition());
        if (toEntity.lengthSqr() <= 1.0E-6D) {
            return true;
        }
        Vector3f look = camera.getLookVector();
        double forwardDot = toEntity.normalize().dot(
                new Vec3(look.x(), look.y(), look.z()));
        return forwardDot >= MIN_FORWARD_DOT;
    }

    private static synchronized void record(
            VisualRoute visualRoute,
            UUID entityUuid,
            UUID ownerUuid) {
        ViewRoute viewRoute = currentView(ownerUuid);
        if (viewRoute == ViewRoute.UNCLASSIFIED) {
            return;
        }
        long frameId = EntityPostRenderExtensions.currentFrameId();
        ReceiptKey key = new ReceiptKey(
                entityUuid, visualRoute, viewRoute);
        ReceiptState receipt = RECEIPTS.computeIfAbsent(
                key, ignored -> new ReceiptState());
        if (receipt.lastFrameId != frameId) {
            receipt.lastFrameId = frameId;
            receipt.frames++;
        }
    }

    private static synchronized void recordGeometry(
            UUID entityUuid,
            VisualRoute visualRoute,
            ViewRoute viewRoute,
            GeometrySnapshot snapshot) {
        if (viewRoute == ViewRoute.UNCLASSIFIED) {
            return;
        }
        ReceiptKey key = new ReceiptKey(
                entityUuid, visualRoute, viewRoute);
        GeometryReceiptState receipt = GEOMETRY_RECEIPTS.computeIfAbsent(
                key, ignored -> new GeometryReceiptState());
        if (receipt.lastFrameId != snapshot.frameId()) {
            receipt.lastFrameId = snapshot.frameId();
            receipt.frames++;
        }
        receipt.latest = snapshot;
    }

    private static ViewRoute currentView(UUID ownerUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity cameraEntity = minecraft.getCameraEntity();
        if (minecraft.player == null || cameraEntity == null) {
            return ViewRoute.UNCLASSIFIED;
        }
        CameraType cameraType = minecraft.options.getCameraType();
        boolean localPlayerOwnsVisual = ownerUuid.equals(
                minecraft.player.getUUID());
        boolean cameraTracksOwner = ownerUuid.equals(
                cameraEntity.getUUID());
        if (cameraType == CameraType.FIRST_PERSON
                && localPlayerOwnsVisual
                && cameraTracksOwner) {
            return ViewRoute.FIRST_PERSON_OWNER;
        }
        if (cameraType != CameraType.FIRST_PERSON
                && localPlayerOwnsVisual
                && cameraTracksOwner) {
            return ViewRoute.THIRD_PERSON_OWNER;
        }
        if (cameraType != CameraType.FIRST_PERSON
                && localPlayerOwnsVisual
                && !cameraTracksOwner) {
            return ViewRoute.THIRD_PERSON_OBSERVER;
        }
        return ViewRoute.UNCLASSIFIED;
    }

    public enum VisualRoute {
        DAMAGING_WORMHOLE,
        PORTAL,
        REMOTE_ARM,
        REMOTE_ARM_MODEL,
        REMOTE_ARM_FIRST_PERSON_PORTAL,
        REMOTE_ARM_SHOULDER_PORTAL
    }

    public enum ViewRoute {
        FIRST_PERSON_OWNER,
        THIRD_PERSON_OWNER,
        THIRD_PERSON_OBSERVER,
        UNCLASSIFIED
    }

    private record ReceiptKey(
            UUID entityUuid,
            VisualRoute visualRoute,
            ViewRoute viewRoute) {}

    private static final class ReceiptState {
        private long lastFrameId = Long.MIN_VALUE;
        private int frames;
    }

    private static final class GeometryReceiptState {
        private long lastFrameId = Long.MIN_VALUE;
        private int frames;
        @Nullable private GeometrySnapshot latest;
    }

    public record GeometrySnapshot(
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
            float minNormalLength,
            float maxNormalLength,
            float minNormalZ,
            float maxNormalZ,
            int projectedVertexCount,
            double projectedWidthPixels,
            double projectedHeightPixels,
            List<VertexSample> vertices) {
        public GeometrySnapshot {
            vertices = List.copyOf(vertices);
        }
    }

    public record VertexSample(
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

    public static final class GeometryCapture implements AutoCloseable {
        private static final float PROJECTION_EPSILON = 1.0E-5F;
        private final VisualRoute visualRoute;
        private final UUID entityUuid;
        @Nullable private final UUID ownerUuid;
        private final ResourceLocation texture;
        private final ViewRoute viewRoute;
        private final long frameId;
        private final Matrix4f projection;
        private final int screenWidth;
        private final int screenHeight;
        private final boolean projectionFinite;
        private final float projectionDeterminant;
        private final CapturingVertexConsumer consumer;
        private int vertexCount;
        private int uvCount;
        private int normalCount;
        private int uninspectedVertexCount;
        private boolean finitePositions = true;
        private boolean finiteUvs = true;
        private boolean finiteNormals = true;
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private float minU = Float.POSITIVE_INFINITY;
        private float minV = Float.POSITIVE_INFINITY;
        private float maxU = Float.NEGATIVE_INFINITY;
        private float maxV = Float.NEGATIVE_INFINITY;
        private float minNormalLength = Float.POSITIVE_INFINITY;
        private float maxNormalLength = Float.NEGATIVE_INFINITY;
        private float minNormalZ = Float.POSITIVE_INFINITY;
        private float maxNormalZ = Float.NEGATIVE_INFINITY;
        private int projectedVertexCount;
        private double minProjectedX = Double.POSITIVE_INFINITY;
        private double minProjectedY = Double.POSITIVE_INFINITY;
        private double maxProjectedX = Double.NEGATIVE_INFINITY;
        private double maxProjectedY = Double.NEGATIVE_INFINITY;
        private final List<MutableVertex> vertices = new ArrayList<>();
        private boolean closed;

        private GeometryCapture(
                VisualRoute visualRoute,
                UUID entityUuid,
                @Nullable UUID ownerUuid,
                ResourceLocation texture,
                VertexConsumer delegate) {
            this.visualRoute = visualRoute;
            this.entityUuid = entityUuid;
            this.ownerUuid = ownerUuid;
            this.texture = texture;
            this.viewRoute = ownerUuid != null
                    ? currentView(ownerUuid)
                    : ViewRoute.UNCLASSIFIED;
            this.frameId = EntityPostRenderExtensions.currentFrameId();
            this.projection = new Matrix4f(
                    RenderSystem.getProjectionMatrix());
            float[] projectionValues = new float[16];
            projection.get(projectionValues);
            boolean finiteProjection = true;
            for (float value : projectionValues) {
                finiteProjection &= Float.isFinite(value);
            }
            this.projectionFinite = finiteProjection;
            this.projectionDeterminant = projection.determinant();
            Minecraft minecraft = Minecraft.getInstance();
            this.screenWidth = minecraft.getWindow().getWidth();
            this.screenHeight = minecraft.getWindow().getHeight();
            this.consumer = new CapturingVertexConsumer(delegate, this);
        }

        public VertexConsumer consumer() {
            return consumer;
        }

        private void addPosition(float x, float y, float z) {
            vertexCount++;
            vertices.add(new MutableVertex(x, y, z));
            boolean finite = Float.isFinite(x)
                    && Float.isFinite(y)
                    && Float.isFinite(z);
            finitePositions &= finite;
            if (!finite) {
                return;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            Vector4f clip = projection.transform(
                    new Vector4f(x, y, z, 1.0F));
            if (!Float.isFinite(clip.x())
                    || !Float.isFinite(clip.y())
                    || !Float.isFinite(clip.w())
                    || clip.w() <= PROJECTION_EPSILON) {
                return;
            }
            double ndcX = clip.x() / clip.w();
            double ndcY = clip.y() / clip.w();
            double pixelX = (ndcX * 0.5D + 0.5D) * screenWidth;
            double pixelY = (1.0D - (ndcY * 0.5D + 0.5D))
                    * screenHeight;
            if (!Double.isFinite(pixelX) || !Double.isFinite(pixelY)) {
                return;
            }
            projectedVertexCount++;
            MutableVertex vertex = vertices.getLast();
            vertex.projected = true;
            vertex.projectedX = pixelX;
            vertex.projectedY = pixelY;
            minProjectedX = Math.min(minProjectedX, pixelX);
            minProjectedY = Math.min(minProjectedY, pixelY);
            maxProjectedX = Math.max(maxProjectedX, pixelX);
            maxProjectedY = Math.max(maxProjectedY, pixelY);
        }

        private void addUv(float u, float v) {
            uvCount++;
            if (!vertices.isEmpty()) {
                MutableVertex vertex = vertices.getLast();
                vertex.u = u;
                vertex.v = v;
            }
            boolean finite = Float.isFinite(u) && Float.isFinite(v);
            finiteUvs &= finite;
            if (finite) {
                minU = Math.min(minU, u);
                minV = Math.min(minV, v);
                maxU = Math.max(maxU, u);
                maxV = Math.max(maxV, v);
            }
        }

        private void addNormal(float x, float y, float z) {
            normalCount++;
            if (!vertices.isEmpty()) {
                MutableVertex vertex = vertices.getLast();
                vertex.normalX = x;
                vertex.normalY = y;
                vertex.normalZ = z;
            }
            boolean finite = Float.isFinite(x)
                    && Float.isFinite(y)
                    && Float.isFinite(z);
            finiteNormals &= finite;
            if (finite) {
                float length = (float) Math.sqrt(x * x + y * y + z * z);
                minNormalLength = Math.min(minNormalLength, length);
                maxNormalLength = Math.max(maxNormalLength, length);
                minNormalZ = Math.min(minNormalZ, z);
                maxNormalZ = Math.max(maxNormalZ, z);
            }
        }

        private void addUninspectedVertices(int count) {
            vertexCount += count;
            uninspectedVertexCount += count;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            double projectedWidth = projectedVertexCount > 0
                    ? maxProjectedX - minProjectedX : Double.NaN;
            double projectedHeight = projectedVertexCount > 0
                    ? maxProjectedY - minProjectedY : Double.NaN;
            GeometrySnapshot snapshot = new GeometrySnapshot(
                    frameId,
                    entityUuid.toString(),
                    ownerUuid != null ? ownerUuid.toString() : "none",
                    visualRoute.name(),
                    viewRoute.name(),
                    texture.toString(),
                    screenWidth,
                    screenHeight,
                    projectionFinite,
                    projectionDeterminant,
                    vertexCount,
                    uvCount,
                    normalCount,
                    uninspectedVertexCount,
                    finitePositions,
                    finiteUvs,
                    finiteNormals,
                    finiteOrNaN(minX),
                    finiteOrNaN(minY),
                    finiteOrNaN(minZ),
                    finiteOrNaN(maxX),
                    finiteOrNaN(maxY),
                    finiteOrNaN(maxZ),
                    finiteOrNaN(minU),
                    finiteOrNaN(minV),
                    finiteOrNaN(maxU),
                    finiteOrNaN(maxV),
                    finiteOrNaN(minNormalLength),
                    finiteOrNaN(maxNormalLength),
                    finiteOrNaN(minNormalZ),
                    finiteOrNaN(maxNormalZ),
                    projectedVertexCount,
                    projectedWidth,
                    projectedHeight,
                    vertices.stream()
                            .map(MutableVertex::snapshot)
                            .toList());
            recordGeometry(
                    entityUuid, visualRoute, viewRoute, snapshot);
        }

        private static float finiteOrNaN(float value) {
            return Float.isFinite(value) ? value : Float.NaN;
        }

        private static final class MutableVertex {
            private final float x;
            private final float y;
            private final float z;
            private float u = Float.NaN;
            private float v = Float.NaN;
            private float normalX = Float.NaN;
            private float normalY = Float.NaN;
            private float normalZ = Float.NaN;
            private boolean projected;
            private double projectedX = Double.NaN;
            private double projectedY = Double.NaN;

            private MutableVertex(float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }

            private VertexSample snapshot() {
                return new VertexSample(
                        x,
                        y,
                        z,
                        u,
                        v,
                        normalX,
                        normalY,
                        normalZ,
                        projected,
                        projectedX,
                        projectedY);
            }
        }
    }

    private static final class CapturingVertexConsumer
            implements VertexConsumer {
        private final VertexConsumer delegate;
        private final GeometryCapture capture;

        private CapturingVertexConsumer(
                VertexConsumer delegate, GeometryCapture capture) {
            this.delegate = delegate;
            this.capture = capture;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            capture.addPosition(x, y, z);
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(
                int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(
                float red, float green, float blue, float alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setWhiteAlpha(int alpha) {
            delegate.setWhiteAlpha(alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            capture.addUv(u, v);
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setLight(int packedLight) {
            delegate.setLight(packedLight);
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int packedOverlay) {
            delegate.setOverlay(packedOverlay);
            return this;
        }

        @Override
        public VertexConsumer setNormal(
                float normalX, float normalY, float normalZ) {
            capture.addNormal(normalX, normalY, normalZ);
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }

        @Override
        public void addVertex(
                float x,
                float y,
                float z,
                int color,
                float u,
                float v,
                int packedOverlay,
                int packedLight,
                float normalX,
                float normalY,
                float normalZ) {
            capture.addPosition(x, y, z);
            capture.addUv(u, v);
            capture.addNormal(normalX, normalY, normalZ);
            delegate.addVertex(
                    x,
                    y,
                    z,
                    color,
                    u,
                    v,
                    packedOverlay,
                    packedLight,
                    normalX,
                    normalY,
                    normalZ);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float red,
                float green,
                float blue,
                float alpha,
                int packedLight,
                int packedOverlay) {
            capture.addUninspectedVertices(4);
            delegate.putBulkData(
                    pose,
                    quad,
                    red,
                    green,
                    blue,
                    alpha,
                    packedLight,
                    packedOverlay);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float[] brightness,
                float red,
                float green,
                float blue,
                float alpha,
                int[] lightmap,
                int packedOverlay,
                boolean readAlpha) {
            capture.addUninspectedVertices(4);
            delegate.putBulkData(
                    pose,
                    quad,
                    brightness,
                    red,
                    green,
                    blue,
                    alpha,
                    lightmap,
                    packedOverlay,
                    readAlpha);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float red,
                float green,
                float blue,
                float alpha,
                int packedLight,
                int packedOverlay,
                boolean readExistingColor) {
            capture.addUninspectedVertices(4);
            delegate.putBulkData(
                    pose,
                    quad,
                    red,
                    green,
                    blue,
                    alpha,
                    packedLight,
                    packedOverlay,
                    readExistingColor);
        }

        @Override
        public VertexConsumer misc(
                VertexFormatElement element, int... rawData) {
            delegate.misc(element, rawData);
            return this;
        }

        @Override
        public int applyBakedLighting(
                int packedLight, ByteBuffer data) {
            return delegate.applyBakedLighting(packedLight, data);
        }

        @Override
        public void applyBakedNormals(
                Vector3f generated,
                ByteBuffer data,
                Matrix3f normalTransform) {
            delegate.applyBakedNormals(
                    generated, data, normalTransform);
        }
    }
}
