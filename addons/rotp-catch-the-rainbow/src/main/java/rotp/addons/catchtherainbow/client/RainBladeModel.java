package rotp.addons.catchtherainbow.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import rotp.addons.catchtherainbow.entity.RainBladeEntity;
import rotp.addons.catchtherainbow.client.RainBladeDonorGeometry.DropletSpec;
import rotp.addons.catchtherainbow.client.RainBladeGeometryReceipt.SegmentKind;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.resources.ResourceLocation;

public final class RainBladeModel
        extends HierarchicalModel<RainBladeEntity> {
    public static final int DONOR_BLADE_MESH_COUNT =
            RainBladeGeometryReceipt.DONOR_BLADE_MESH_COUNT;
    public static final int DONOR_DROPLET_CUBE_COUNT =
            RainBladeGeometryReceipt.DONOR_DROPLET_CUBE_COUNT;
    public static final int VERTICES_PER_SEGMENT =
            RainBladeGeometryReceipt.VERTICES_PER_SEGMENT;
    public static final String BLADE_MESH_SEGMENT_ID =
            RainBladeGeometryReceipt.BLADE_MESH_SEGMENT_ID;
    public static final List<String> DROPLET_SEGMENT_IDS =
            RainBladeGeometryReceipt.DROPLET_SEGMENT_IDS;
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_ctr",
                            "catch_the_rainbow_rain_blade1"),
                    "main");

    private final ModelPart root;
    private final ModelPart waterCutter;
    private final ModelPart drops;
    private final List<ModelPart> dropletSegments;
    private final RainBladeDonorMesh bladeMesh;
    private UUID currentEntityUuid;

    public RainBladeModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
        this.waterCutter = root.getChild("water_cutter");
        this.drops = waterCutter.getChild("drops");
        this.dropletSegments = children(
                drops, DROPLET_SEGMENT_IDS);
        this.bladeMesh = RainBladeDonorMesh.create();
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition waterCutter = root.addOrReplaceChild(
                "water_cutter",
                CubeListBuilder.create(),
                PartPose.offset(
                        0.0F,
                        RainBladeDonorGeometry.WATER_CUTTER_Y,
                        0.0F));
        PartDefinition drops = waterCutter.addOrReplaceChild(
                "drops",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        RainBladeDonorGeometry.DROPS_X,
                        RainBladeDonorGeometry.DROPS_Y,
                        RainBladeDonorGeometry.DROPS_Z,
                        RainBladeDonorGeometry.DROPS_X_ROTATION,
                        0.0F,
                        0.0F));
        addDroplets(drops);
        return LayerDefinition.create(mesh, 16, 16);
    }

    private static void addDroplets(PartDefinition drops) {
        for (DropletSpec droplet
                : RainBladeDonorGeometry.DROPLETS) {
            drops.addOrReplaceChild(
                    droplet.segmentId(),
                    CubeListBuilder.create()
                            .texOffs(
                                    droplet.textureU(),
                                    droplet.textureV())
                            .addBox(
                                    droplet.x(),
                                    droplet.y(),
                                    droplet.z(),
                                    droplet.sizeX(),
                                    droplet.sizeY(),
                                    droplet.sizeZ(),
                                    new CubeDeformation(
                                            droplet.deformation())),
                    PartPose.ZERO);
        }
    }

    private static List<ModelPart> children(
            ModelPart parent, List<String> ids) {
        List<ModelPart> children = new ArrayList<>(ids.size());
        for (String id : ids) {
            children.add(parent.getChild(id));
        }
        return List.copyOf(children);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int color) {
        RainBladeRenderer.recordModelGeometry(
                currentEntityUuid,
                captureGeometry(
                        poseStack,
                        vertices,
                        packedLight,
                        packedOverlay,
                        color));
    }

    public static RainBladeGeometryReceipt.ModelGeometryReceipt
            captureCanonicalGeometry() {
        return captureCanonicalGeometry(0.0F, 0.0F);
    }

    public static RainBladeGeometryReceipt.ModelGeometryReceipt
            captureCanonicalGeometry(float xRotation, float yRotation) {
        RainBladeModel model = new RainBladeModel(
                createBodyLayer().bakeRoot());
        model.waterCutter.xRot = xRotation;
        model.waterCutter.yRot = yRotation;
        return model.captureGeometry(
                new PoseStack(), null, 0, 0, 0xFFFFFFFF);
    }

    private RainBladeGeometryReceipt.ModelGeometryReceipt captureGeometry(
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int color) {
        List<RainBladeGeometryReceipt.SegmentReceipt> receipts =
                new ArrayList<>(
                        DONOR_BLADE_MESH_COUNT
                                + DONOR_DROPLET_CUBE_COUNT);
        PoseStack canonicalPose = new PoseStack();
        poseStack.pushPose();
        canonicalPose.pushPose();
        try {
            root.translateAndRotate(poseStack);
            root.translateAndRotate(canonicalPose);
            waterCutter.translateAndRotate(poseStack);
            translateCanonicalWaterCutter(canonicalPose);
            // Donor child order is rotated droplets, then custom blade.
            poseStack.pushPose();
            canonicalPose.pushPose();
            try {
                drops.translateAndRotate(poseStack);
                drops.translateAndRotate(canonicalPose);
                renderSegments(
                        DROPLET_SEGMENT_IDS,
                        dropletSegments,
                        poseStack,
                        canonicalPose.last(),
                        vertices,
                        packedLight,
                        packedOverlay,
                        color,
                        receipts);
            }
            finally {
                canonicalPose.popPose();
                poseStack.popPose();
            }
            renderBladeMesh(
                    poseStack,
                    canonicalPose.last(),
                    vertices,
                    packedLight,
                    packedOverlay,
                    color,
                    receipts);
        }
        finally {
            canonicalPose.popPose();
            poseStack.popPose();
        }
        return RainBladeGeometryReceipt.ModelGeometryReceipt
                .from(receipts);
    }

    private void renderBladeMesh(
            PoseStack poseStack,
            PoseStack.Pose canonicalPose,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int color,
            List<RainBladeGeometryReceipt.SegmentReceipt> receipts) {
        CountingVertexConsumer counting = new CountingVertexConsumer(
                vertices,
                poseStack.last(),
                canonicalPose);
        bladeMesh.render(
                poseStack.last(),
                counting,
                packedLight,
                packedOverlay,
                color);
        receipts.add(counting.snapshot(
                BLADE_MESH_SEGMENT_ID,
                SegmentKind.CUSTOM_MESH));
    }

    private void translateCanonicalWaterCutter(PoseStack poseStack) {
        poseStack.translate(
                waterCutter.x / 16.0F,
                waterCutter.y / 16.0F,
                waterCutter.z / 16.0F);
        if (waterCutter.zRot != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationZYX(
                    waterCutter.zRot, 0.0F, 0.0F));
        }
        if (waterCutter.xScale != 1.0F
                || waterCutter.yScale != 1.0F
                || waterCutter.zScale != 1.0F) {
            poseStack.scale(
                    waterCutter.xScale,
                    waterCutter.yScale,
                    waterCutter.zScale);
        }
    }

    private static void renderSegments(
            List<String> ids,
            List<ModelPart> segments,
            PoseStack poseStack,
            PoseStack.Pose canonicalPose,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int color,
            List<RainBladeGeometryReceipt.SegmentReceipt> receipts) {
        for (int index = 0; index < ids.size(); index++) {
            CountingVertexConsumer counting =
                    new CountingVertexConsumer(
                            vertices,
                            poseStack.last(),
                            canonicalPose);
            segments.get(index).render(
                    poseStack,
                    counting,
                    packedLight,
                    packedOverlay,
                    color);
            receipts.add(counting.snapshot(
                    ids.get(index), SegmentKind.CUBOID));
        }
    }

    @Override
    public void setupAnim(
            RainBladeEntity entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float ticks,
            float yRotationOffset,
            float xRotation) {
        currentEntityUuid = entity.getUUID();
        waterCutter.yRot =
                yRotationOffset * ((float) Math.PI / 180.0F);
        waterCutter.xRot =
                xRotation * ((float) Math.PI / 180.0F);
    }

    private static final class CountingVertexConsumer
            implements VertexConsumer {
        private static final long FNV_OFFSET_BASIS =
                0xCBF29CE484222325L;
        private static final long FNV_PRIME = 0x100000001B3L;
        private static final double POSITION_GRID = 640.0D;
        private static final double UV_GRID = 1_600.0D;
        private static final double NORMAL_GRID = 100_000.0D;
        private final VertexConsumer delegate;
        private final Matrix4f inversePose;
        private final Matrix3f inverseNormal;
        private final Matrix4f canonicalPose;
        private final Matrix3f canonicalNormal;
        private final List<VertexSample> samples = new ArrayList<>(
                VERTICES_PER_SEGMENT);
        private int vertexCount;
        private int positionCount;
        private int uvCount;
        private int normalCount;
        private int uninspectedVertexCount;
        private boolean allPositionsFinite = true;
        private boolean allUvsFinite = true;
        private boolean allNormalsFinite = true;
        private float minX = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private float minU = Float.POSITIVE_INFINITY;
        private float maxU = Float.NEGATIVE_INFINITY;
        private float minV = Float.POSITIVE_INFINITY;
        private float maxV = Float.NEGATIVE_INFINITY;
        private CountingVertexConsumer(
                VertexConsumer delegate,
                PoseStack.Pose pose,
                PoseStack.Pose canonicalPose) {
            this.delegate = delegate;
            inversePose = new Matrix4f(pose.pose()).invert();
            inverseNormal = new Matrix3f(pose.normal()).invert();
            this.canonicalPose = new Matrix4f(
                    canonicalPose.pose());
            canonicalNormal = new Matrix3f(
                    canonicalPose.normal());
        }

        private RainBladeGeometryReceipt.SegmentReceipt snapshot(
                String segmentId, SegmentKind kind) {
            WindingSummary winding = windingSummary();
            return new RainBladeGeometryReceipt.SegmentReceipt(
                    segmentId,
                    kind,
                    vertexCount,
                    positionCount,
                    uvCount,
                    normalCount,
                    uninspectedVertexCount,
                    allPositionsFinite,
                    allUvsFinite,
                    allNormalsFinite,
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
                    geometryFingerprint(),
                    normalFingerprint(),
                    winding.fingerprint());
        }

        private void recordPosition(float x, float y, float z) {
            Vector3f local = inversePose.transformPosition(
                    new Vector3f(x, y, z));
            canonicalPose.transformPosition(local);
            float localX = canonical(local.x(), POSITION_GRID);
            float localY = canonical(local.y(), POSITION_GRID);
            float localZ = canonical(local.z(), POSITION_GRID);
            vertexCount++;
            positionCount++;
            allPositionsFinite &= Float.isFinite(localX)
                    && Float.isFinite(localY)
                    && Float.isFinite(localZ);
            minX = Math.min(minX, localX);
            maxX = Math.max(maxX, localX);
            minY = Math.min(minY, localY);
            maxY = Math.max(maxY, localY);
            minZ = Math.min(minZ, localZ);
            maxZ = Math.max(maxZ, localZ);
            samples.add(new VertexSample(
                    localX, localY, localZ));
        }

        private void recordUv(float u, float v) {
            float localU = canonical(u, UV_GRID);
            float localV = canonical(v, UV_GRID);
            uvCount++;
            allUvsFinite &= Float.isFinite(localU)
                    && Float.isFinite(localV);
            minU = Math.min(minU, localU);
            maxU = Math.max(maxU, localU);
            minV = Math.min(minV, localV);
            maxV = Math.max(maxV, localV);
            VertexSample sample = currentSample();
            if (sample == null) {
                uninspectedVertexCount++;
                return;
            }
            sample.u = localU;
            sample.v = localV;
            sample.uvPresent = true;
        }

        private void recordNormal(float x, float y, float z) {
            Vector3f local = inverseNormal.transform(
                    new Vector3f(x, y, z));
            canonicalNormal.transform(local);
            if (local.isFinite() && local.lengthSquared() > 0.0F) {
                local.normalize();
            }
            float localX = canonical(local.x(), NORMAL_GRID);
            float localY = canonical(local.y(), NORMAL_GRID);
            float localZ = canonical(local.z(), NORMAL_GRID);
            normalCount++;
            allNormalsFinite &= Float.isFinite(localX)
                    && Float.isFinite(localY)
                    && Float.isFinite(localZ);
            VertexSample sample = currentSample();
            if (sample == null) {
                uninspectedVertexCount++;
                return;
            }
            sample.normalX = localX;
            sample.normalY = localY;
            sample.normalZ = localZ;
            sample.normalPresent = true;
        }

        private void recordUninspectedVertices(int count) {
            vertexCount += count;
            uninspectedVertexCount += count;
        }

        private VertexSample currentSample() {
            return samples.isEmpty()
                    ? null : samples.get(samples.size() - 1);
        }

        private long geometryFingerprint() {
            long fingerprint = FNV_OFFSET_BASIS;
            for (VertexSample sample : samples) {
                fingerprint = fingerprint(fingerprint, 0x50);
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.x));
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.y));
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.z));
                fingerprint = fingerprint(fingerprint, 0x55);
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.u));
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.v));
                fingerprint = fingerprint(fingerprint, 0x4E);
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.normalX));
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.normalY));
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.normalZ));
            }
            if (uninspectedVertexCount > 0) {
                fingerprint = fingerprint(fingerprint, 0x42);
                fingerprint = fingerprint(
                        fingerprint, uninspectedVertexCount);
            }
            return fingerprint;
        }

        private long normalFingerprint() {
            long fingerprint = FNV_OFFSET_BASIS;
            for (VertexSample sample : samples) {
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.normalX));
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.normalY));
                fingerprint = fingerprint(fingerprint,
                        Float.floatToRawIntBits(sample.normalZ));
            }
            return fingerprint;
        }

        private WindingSummary windingSummary() {
            long fingerprint = FNV_OFFSET_BASIS;
            boolean inspected = samples.size() == vertexCount
                    && vertexCount % 4 == 0;
            int faceCount = 0;
            for (int index = 0;
                    inspected && index < samples.size(); index += 4) {
                VertexSample first = samples.get(index);
                VertexSample second = samples.get(index + 1);
                VertexSample third = samples.get(index + 2);
                inspected &= first.complete()
                        && second.complete() && third.complete()
                        && samples.get(index + 3).complete();
                if (!inspected) {
                    break;
                }
                Vector3f edgeA = new Vector3f(
                        second.x - first.x,
                        second.y - first.y,
                        second.z - first.z);
                Vector3f edgeB = new Vector3f(
                        third.x - first.x,
                        third.y - first.y,
                        third.z - first.z);
                float dot = canonical(
                        edgeA.cross(edgeB).dot(
                                first.normalX,
                                first.normalY,
                                first.normalZ),
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
            float canonical = (float) (Math.rint(
                    value * grid) / grid);
            return canonical == 0.0F ? 0.0F : canonical;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            recordPosition(x, y, z);
            if (delegate != null) {
                delegate.addVertex(x, y, z);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(
                int red, int green, int blue, int alpha) {
            if (delegate != null) {
                delegate.setColor(red, green, blue, alpha);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(
                float red, float green, float blue, float alpha) {
            if (delegate != null) {
                delegate.setColor(red, green, blue, alpha);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            if (delegate != null) {
                delegate.setColor(color);
            }
            return this;
        }

        @Override
        public VertexConsumer setWhiteAlpha(int alpha) {
            if (delegate != null) {
                delegate.setWhiteAlpha(alpha);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            recordUv(u, v);
            if (delegate != null) {
                delegate.setUv(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (delegate != null) {
                delegate.setUv1(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (delegate != null) {
                delegate.setUv2(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setLight(int packedLight) {
            if (delegate != null) {
                delegate.setLight(packedLight);
            }
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int packedOverlay) {
            if (delegate != null) {
                delegate.setOverlay(packedOverlay);
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(
                float normalX, float normalY, float normalZ) {
            recordNormal(normalX, normalY, normalZ);
            if (delegate != null) {
                delegate.setNormal(normalX, normalY, normalZ);
            }
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
            recordPosition(x, y, z);
            recordUv(u, v);
            recordNormal(normalX, normalY, normalZ);
            if (delegate != null) {
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
            recordUninspectedVertices(4);
            if (delegate != null) {
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
            recordUninspectedVertices(4);
            if (delegate != null) {
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
            recordUninspectedVertices(4);
            if (delegate != null) {
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
        }

        @Override
        public VertexConsumer misc(
                VertexFormatElement element, int... rawData) {
            if (delegate != null) {
                delegate.misc(element, rawData);
            }
            return this;
        }

        @Override
        public int applyBakedLighting(
                int packedLight, ByteBuffer data) {
            return delegate != null
                    ? delegate.applyBakedLighting(packedLight, data)
                    : packedLight;
        }

        @Override
        public void applyBakedNormals(
                Vector3f generated,
                ByteBuffer data,
                Matrix3f normalTransform) {
            if (delegate != null) {
                delegate.applyBakedNormals(
                        generated, data, normalTransform);
            }
        }

        private static final class VertexSample {
            private final float x;
            private final float y;
            private final float z;
            private float u;
            private float v;
            private float normalX;
            private float normalY;
            private float normalZ;
            private boolean uvPresent;
            private boolean normalPresent;

            private VertexSample(float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }

            private boolean complete() {
                return uvPresent && normalPresent;
            }
        }

        private record WindingSummary(
                boolean fullyInspected,
                int faceCount,
                long fingerprint) {}
    }
}
