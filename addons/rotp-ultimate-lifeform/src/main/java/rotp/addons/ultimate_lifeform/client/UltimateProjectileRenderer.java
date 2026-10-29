package rotp.addons.ultimate_lifeform.client;

import java.util.EnumSet;
import java.util.Set;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.GeometryEvidence;
import rotp.addons.ultimate_lifeform.client.UltimateLifeformDownstreamSubmissions.RegisteredSubmission;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class UltimateProjectileRenderer
        extends EntityRenderer<UltimateProjectileEntity> {
    private static final ResourceLocation SCALE =
            UltimateLifeformAddon.id(
                    "textures/entity/projectile/scale_projectile.png");
    private static final ResourceLocation TENTACLE =
            UltimateLifeformAddon.id(
                    "textures/entity/projectile/tentacle.png");
    private static final ResourceLocation TENTACLE_MIRRORED =
            UltimateLifeformAddon.id(
                    "textures/entity/projectile/tentacle_mirrored.png");
    private static final ResourceLocation LASER_END =
            UltimateLifeformAddon.id(
                    "textures/entity/laser_end.png");
    private static final ResourceLocation GUARDIAN_BEAM =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/guardian_beam.png");
    private static final Set<Direction> ALL_CUBE_FACES =
            EnumSet.allOf(Direction.class);
    private static final ModelPart.Cube TENTACLE_BODY =
            new ModelPart.Cube(
                    0, 14,
                    -0.5F, -1.0F, -5.0F,
                    1.0F, 2.0F, 10.0F,
                    0.0F, 0.0F, 0.0F,
                    false, 32.0F, 32.0F, ALL_CUBE_FACES);
    private static final ModelPart.Cube TENTACLE_TIP_BASE =
            new ModelPart.Cube(
                    12, 0,
                    -0.5F, -2.0F, -9.0F,
                    1.0F, 4.0F, 4.0F,
                    0.0F, 0.0F, 0.0F,
                    false, 32.0F, 32.0F, ALL_CUBE_FACES);
    private static final ModelPart.Cube TENTACLE_TIP_END =
            new ModelPart.Cube(
                    12, 8,
                    -0.5F, -1.0F, -12.0F,
                    1.0F, 2.0F, 3.0F,
                    0.0F, 0.0F, 0.0F,
                    false, 32.0F, 32.0F, ALL_CUBE_FACES);

    public UltimateProjectileRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(
            UltimateProjectileEntity entity) {
        if (entity.isTentacle()) {
            return entity.isTentacleRight()
                    ? TENTACLE_MIRRORED
                    : TENTACLE;
        }
        if (entity.isGuardian()) {
            return LASER_END;
        }
        return SCALE;
    }

    @Override
    public boolean shouldRender(
            UltimateProjectileEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        if (super.shouldRender(
                entity, frustum, cameraX, cameraY, cameraZ)) {
            return true;
        }
        AABB extendedGeometry = null;
        if (entity.isTentacle()) {
            extendedGeometry = new AABB(
                    entity.getTentacleOrigin(1.0F),
                    entity.getTentacleEnd(1.0F));
        }
        else if (entity.isGuardian()) {
            extendedGeometry = new AABB(
                    entity.position(), entity.getBeamEnd());
        }
        return extendedGeometry != null
                && frustum.isVisible(extendedGeometry.inflate(0.5D));
    }

    @Override
    public void render(
            UltimateProjectileEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
        int packedLight) {
        if (entity.isTentacle()) {
            GeometryCapture capture = new GeometryCapture();
            renderTentacle(
                    entity,
                    partialTick,
                    poseStack,
                    counting(buffer, capture),
                    packedLight);
            recordTentacle(entity, partialTick, capture);
        }
        else if (entity.isGuardian()) {
            GeometryCapture capture = new GeometryCapture();
            MultiBufferSource counted = counting(buffer, capture);
            renderGuardianBeam(entity, partialTick, poseStack, counted);
            renderBillboard(
                    entity,
                    poseStack,
                    counted,
                    LASER_END,
                    0.35F,
                    0x00F000F0);
            recordGuardian(entity, partialTick, capture);
        }
        else {
            renderBillboard(
                    entity,
                    poseStack,
                    buffer,
                    SCALE,
                    0.45F,
                    packedLight);
            UltimateLifeformClientAcceptance.recordGlobalVisual(
                    entity,
                    ClientReceipt.SCALE_PROJECTILE,
                    "scale_projectile");
        }
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                buffer,
                packedLight);
    }

    private void renderTentacle(
            UltimateProjectileEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        Vec3 renderOrigin = entity.getPosition(partialTick);
        Vec3 ownerOrigin = entity.getTentacleOrigin(partialTick);
        Vec3 endpoint = entity.getTentacleEnd(partialTick);
        Vec3 chain = endpoint.subtract(ownerOrigin);
        Vec3 bendAxis = new Vec3(-chain.z, 0.0D, chain.x);
        if (bendAxis.lengthSqr() < 1.0E-7D) {
            bendAxis = new Vec3(1.0D, 0.0D, 0.0D);
        }
        bendAxis = bendAxis.normalize();
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityCutoutNoCull(
                        getTextureLocation(entity)));
        Vec3 previous = ownerOrigin;
        Vec3 tipDirection = chain;
        for (int segment = 0; segment < 8; segment++) {
            double progress = (segment + 1.0D) / 8.0D;
            double articulation = Math.sin(
                    progress * Math.PI * 2.0D
                            + (entity.tickCount + partialTick) * 0.18D)
                    * 0.08D * Math.sin(progress * Math.PI);
            Vec3 current = ownerOrigin.add(chain.scale(progress))
                    .add(bendAxis.scale(articulation))
                    .add(0.0D,
                            -Math.sin(progress * Math.PI) * 0.12D,
                            0.0D);
            tipDirection = current.subtract(previous);
            renderOrientedSegment(
                    previous.subtract(renderOrigin),
                    current.subtract(renderOrigin),
                    entity.isTentacleRight(),
                    poseStack,
                    vertices,
                    packedLight);
            previous = current;
        }
        renderTentacleTip(
                endpoint.subtract(renderOrigin),
                tipDirection,
                entity.isTentacleRight(),
                poseStack,
                vertices,
                packedLight);
    }

    private static void renderOrientedSegment(
            Vec3 from,
            Vec3 to,
            boolean right,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight) {
        Vec3 direction = to.subtract(from);
        float length = (float) direction.length();
        if (length <= 1.0E-4F) {
            return;
        }
        Vec3 midpoint = from.add(to).scale(0.5D);
        Vec3 normal = direction.scale(1.0D / length);
        float yaw = (float) Mth.atan2(normal.x, normal.z)
                * Mth.RAD_TO_DEG;
        float pitch = -(float) Math.asin(
                Mth.clamp(normal.y, -1.0D, 1.0D))
                * Mth.RAD_TO_DEG;
        poseStack.pushPose();
        poseStack.translate(midpoint.x, midpoint.y, midpoint.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        if (!right) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        }
        float renderedLength = length + 2.0F / 16.0F;
        poseStack.scale(
                1.0F,
                1.0F,
                renderedLength / (10.0F / 16.0F));
        PoseStack.Pose pose = poseStack.last();
        TENTACLE_BODY.compile(
                pose,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
        poseStack.popPose();
    }

    private static void renderTentacleTip(
            Vec3 endpoint,
            Vec3 direction,
            boolean right,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight) {
        double length = direction.length();
        if (length <= 1.0E-4D) {
            return;
        }
        Vec3 normal = direction.scale(1.0D / length);
        float yaw = (float) Mth.atan2(normal.x, normal.z)
                * Mth.RAD_TO_DEG;
        float pitch = -(float) Math.asin(
                Mth.clamp(normal.y, -1.0D, 1.0D))
                * Mth.RAD_TO_DEG;
        poseStack.pushPose();
        poseStack.translate(endpoint.x, endpoint.y, endpoint.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        if (!right) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        }
        PoseStack.Pose pose = poseStack.last();
        TENTACLE_TIP_BASE.compile(
                pose,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
        TENTACLE_TIP_END.compile(
                pose,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
        poseStack.popPose();
    }

    private void renderGuardianBeam(
            UltimateProjectileEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer) {
        Vec3 start = entity.getPosition(partialTick);
        Vec3 beam = entity.getBeamEnd().subtract(start);
        double length = beam.length();
        if (length <= 1.0E-4D) {
            return;
        }
        Vec3 direction = beam.scale(1.0D / length);
        Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-7D) {
            side = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 up = direction.cross(side).normalize();
        float age = entity.tickCount + partialTick;
        float spin = age * -0.075F;
        float colorProgress = Mth.clamp(age / 20.0F, 0.0F, 1.0F);
        float colorProgressSquared = colorProgress * colorProgress;
        int red = 64 + (int) (colorProgressSquared * 191.0F);
        int green = 32 + (int) (colorProgressSquared * 191.0F);
        int blue = 128 - (int) (colorProgressSquared * 64.0F);
        int color = 0xFF000000 | red << 16 | green << 8 | blue;
        float scroll = -1.0F + age / 20.0F;
        float maxV = scroll + (float) length * 2.5F;
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityCutoutNoCull(GUARDIAN_BEAM));
        PoseStack.Pose pose = poseStack.last();
        beamRibbon(
                pose,
                vertices,
                radialOffset(side, up, spin + Mth.PI, 0.2D),
                radialOffset(side, up, spin, 0.2D),
                beam,
                scroll,
                maxV,
                color);
        beamRibbon(
                pose,
                vertices,
                radialOffset(side, up, spin + Mth.HALF_PI, 0.2D),
                radialOffset(
                        side,
                        up,
                        spin + Mth.PI + Mth.HALF_PI,
                        0.2D),
                beam,
                scroll,
                maxV,
                color);
        beamEndCap(
                pose,
                vertices,
                beam,
                radialOffset(
                        side, up, spin + Mth.PI * 0.75F, 0.282D),
                radialOffset(
                        side, up, spin + Mth.PI * 0.25F, 0.282D),
                radialOffset(
                        side, up, spin + Mth.PI * 1.75F, 0.282D),
                radialOffset(
                        side, up, spin + Mth.PI * 1.25F, 0.282D),
                color);

        poseStack.pushPose();
        poseStack.translate(beam.x, beam.y, beam.z);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.35F, 0.35F, 0.35F);
        PoseStack.Pose endpointPose = poseStack.last();
        VertexConsumer endpointVertices = buffer.getBuffer(
                RenderType.entityCutoutNoCull(LASER_END));
        billboardQuad(endpointPose, endpointVertices, 0x00F000F0);
        poseStack.popPose();
    }

    private static Vec3 radialOffset(
            Vec3 side,
            Vec3 up,
            float angle,
            double radius) {
        return side.scale(Math.cos(angle) * radius)
                .add(up.scale(Math.sin(angle) * radius));
    }

    private static void beamRibbon(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            Vec3 firstOffset,
            Vec3 secondOffset,
            Vec3 beam,
            float minV,
            float maxV,
            int color) {
        vertex(
                pose,
                vertices,
                beam.add(firstOffset),
                0.4999F,
                maxV,
                0x00F000F0,
                color);
        vertex(
                pose,
                vertices,
                firstOffset,
                0.4999F,
                minV,
                0x00F000F0,
                color);
        vertex(
                pose,
                vertices,
                secondOffset,
                0.0F,
                minV,
                0x00F000F0,
                color);
        vertex(
                pose,
                vertices,
                beam.add(secondOffset),
                0.0F,
                maxV,
                0x00F000F0,
                color);
    }

    private static void beamEndCap(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            Vec3 beam,
            Vec3 first,
            Vec3 second,
            Vec3 third,
            Vec3 fourth,
            int color) {
        vertex(
                pose, vertices, beam.add(first),
                0.5F, 0.5F, 0x00F000F0, color);
        vertex(
                pose, vertices, beam.add(second),
                1.0F, 0.5F, 0x00F000F0, color);
        vertex(
                pose, vertices, beam.add(third),
                1.0F, 0.0F, 0x00F000F0, color);
        vertex(
                pose, vertices, beam.add(fourth),
                0.5F, 0.0F, 0x00F000F0, color);
    }

    private void renderBillboard(
            UltimateProjectileEntity entity,
            PoseStack poseStack,
            MultiBufferSource buffer,
            ResourceLocation texture,
            float scale,
            int packedLight) {
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityCutoutNoCull(texture));
        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(scale, scale, scale);
        billboardQuad(poseStack.last(), vertices, packedLight);
        poseStack.popPose();
    }

    private static void billboardQuad(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int packedLight) {
        vertex(
                pose, vertices,
                new Vec3(-0.5D, -0.5D, 0.0D),
                0.0F, 1.0F, packedLight);
        vertex(
                pose, vertices,
                new Vec3(0.5D, -0.5D, 0.0D),
                1.0F, 1.0F, packedLight);
        vertex(
                pose, vertices,
                new Vec3(0.5D, 0.5D, 0.0D),
                1.0F, 0.0F, packedLight);
        vertex(
                pose, vertices,
                new Vec3(-0.5D, 0.5D, 0.0D),
                0.0F, 0.0F, packedLight);
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            Vec3 position,
            float u,
            float v,
            int packedLight) {
        vertex(
                pose,
                vertices,
                position,
                u,
                v,
                packedLight,
                0xFFFFFFFF);
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            Vec3 position,
            float u,
            float v,
            int packedLight,
            int color) {
        vertices.addVertex(
                        pose.pose(),
                        (float) position.x,
                        (float) position.y,
                        (float) position.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void recordTentacle(
            UltimateProjectileEntity entity,
            float partialTick,
            GeometryCapture capture) {
        Vec3 start = entity.getTentacleOrigin(partialTick);
        Vec3 end = entity.getTentacleEnd(partialTick);
        UltimateLifeformClientAcceptance.recordGlobalGeometrySubmission(
                capture.proof(
                        entity,
                        ClientReceipt.TENTACLE_PROJECTILE,
                        ClientReceipt.TENTACLE_CHAIN,
                        "tentacles",
                        "tentacle_mesh",
                        (entity.isTentacleRight()
                                ? TENTACLE_MIRRORED
                                : TENTACLE).toString(),
                        start,
                        end));
    }

    private static void recordGuardian(
            UltimateProjectileEntity entity,
            float partialTick,
            GeometryCapture capture) {
        Vec3 start = entity.getPosition(partialTick);
        Vec3 end = entity.getBeamEnd();
        UltimateLifeformClientAcceptance.recordGlobalGeometrySubmission(
                capture.proof(
                        entity,
                        ClientReceipt.LASER_PROJECTILE,
                        ClientReceipt.GUARDIAN_ENDPOINT_BEAM,
                        "guardian_laser",
                        "guardian_mesh",
                        GUARDIAN_BEAM + "|" + LASER_END,
                        start,
                        end));
    }

    private static MultiBufferSource counting(
            MultiBufferSource delegate, GeometryCapture capture) {
        return renderType -> new CountingVertexConsumer(
                delegate.getBuffer(renderType), capture);
    }

    private static final class GeometryCapture {
        private int vertexCount;
        private double minX = Double.NaN;
        private double minY = Double.NaN;
        private double minZ = Double.NaN;
        private double maxX = Double.NaN;
        private double maxY = Double.NaN;
        private double maxZ = Double.NaN;

        private GeometrySubmissionProof proof(
                UltimateProjectileEntity entity,
                ClientReceipt primaryReceipt,
                ClientReceipt spatialReceipt,
                String evidenceToken,
                String submissionKind,
                String resource,
                Vec3 start,
                Vec3 end) {
            GeometryEvidence evidence = new GeometryEvidence(
                    vertexCount,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    start.x,
                    start.y,
                    start.z,
                    end.x,
                    end.y,
                    end.z);
            long frameNanos = Util.getNanos();
            RegisteredSubmission primary =
                    UltimateLifeformDownstreamSubmissions.registerGeometry(
                            entity,
                            primaryReceipt,
                            resource,
                            evidence,
                            frameNanos);
            RegisteredSubmission spatial =
                    UltimateLifeformDownstreamSubmissions.registerGeometry(
                            entity,
                            spatialReceipt,
                            resource,
                            evidence,
                            frameNanos);
            if (primary == null || spatial == null) {
                UltimateLifeformDownstreamSubmissions
                        .discardRegistration(primary);
                UltimateLifeformDownstreamSubmissions
                        .discardRegistration(spatial);
            }
            return new GeometrySubmissionProof(
                    entity,
                    primaryReceipt,
                    spatialReceipt,
                    evidenceToken,
                    submissionKind,
                    vertexCount,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    start,
                    end,
                    primary,
                    spatial);
        }

        private void add(float x, float y, float z) {
            vertexCount++;
            if (!Float.isFinite(x)
                    || !Float.isFinite(y)
                    || !Float.isFinite(z)) {
                minX = Double.NaN;
                minY = Double.NaN;
                minZ = Double.NaN;
                maxX = Double.NaN;
                maxY = Double.NaN;
                maxZ = Double.NaN;
                return;
            }
            if (vertexCount == 1) {
                minX = maxX = x;
                minY = maxY = y;
                minZ = maxZ = z;
                return;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
    }

    private static final class CountingVertexConsumer
            implements VertexConsumer {
        private final VertexConsumer delegate;
        private final GeometryCapture capture;

        private CountingVertexConsumer(
                VertexConsumer delegate, GeometryCapture capture) {
            this.delegate = delegate;
            this.capture = capture;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            capture.add(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
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
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    static final class GeometrySubmissionProof {
        private final UltimateProjectileEntity entity;
        private final ClientReceipt primaryReceipt;
        private final ClientReceipt spatialReceipt;
        private final String evidenceToken;
        private final String submissionKind;
        private final int vertexCount;
        private final double submittedMinX;
        private final double submittedMinY;
        private final double submittedMinZ;
        private final double submittedMaxX;
        private final double submittedMaxY;
        private final double submittedMaxZ;
        private final Vec3 start;
        private final Vec3 end;
        @javax.annotation.Nullable
        private final RegisteredSubmission primaryRegistration;
        @javax.annotation.Nullable
        private final RegisteredSubmission spatialRegistration;
        private boolean claimed;

        private GeometrySubmissionProof(
                UltimateProjectileEntity entity,
                ClientReceipt primaryReceipt,
                ClientReceipt spatialReceipt,
                String evidenceToken,
                String submissionKind,
                int vertexCount,
                double submittedMinX,
                double submittedMinY,
                double submittedMinZ,
                double submittedMaxX,
                double submittedMaxY,
                double submittedMaxZ,
                Vec3 start,
                Vec3 end,
                @javax.annotation.Nullable
                RegisteredSubmission primaryRegistration,
                @javax.annotation.Nullable
                RegisteredSubmission spatialRegistration) {
            this.entity = entity;
            this.primaryReceipt = primaryReceipt;
            this.spatialReceipt = spatialReceipt;
            this.evidenceToken = evidenceToken;
            this.submissionKind = submissionKind;
            this.vertexCount = vertexCount;
            this.submittedMinX = submittedMinX;
            this.submittedMinY = submittedMinY;
            this.submittedMinZ = submittedMinZ;
            this.submittedMaxX = submittedMaxX;
            this.submittedMaxY = submittedMaxY;
            this.submittedMaxZ = submittedMaxZ;
            this.start = start;
            this.end = end;
            this.primaryRegistration = primaryRegistration;
            this.spatialRegistration = spatialRegistration;
        }

        synchronized boolean claim() {
            if (claimed) {
                return false;
            }
            claimed = true;
            return true;
        }

        UltimateProjectileEntity entity() {
            return entity;
        }

        ClientReceipt primaryReceipt() {
            return primaryReceipt;
        }

        ClientReceipt spatialReceipt() {
            return spatialReceipt;
        }

        String evidenceToken() {
            return evidenceToken;
        }

        String submissionKind() {
            return submissionKind;
        }

        int vertexCount() {
            return vertexCount;
        }

        double submittedMinX() {
            return submittedMinX;
        }

        double submittedMinY() {
            return submittedMinY;
        }

        double submittedMinZ() {
            return submittedMinZ;
        }

        double submittedMaxX() {
            return submittedMaxX;
        }

        double submittedMaxY() {
            return submittedMaxY;
        }

        double submittedMaxZ() {
            return submittedMaxZ;
        }

        Vec3 start() {
            return start;
        }

        Vec3 end() {
            return end;
        }

        @javax.annotation.Nullable
        RegisteredSubmission registration(ClientReceipt receipt) {
            return receipt == primaryReceipt
                    ? primaryRegistration
                    : receipt == spatialReceipt
                            ? spatialRegistration
                            : null;
        }
    }
}
