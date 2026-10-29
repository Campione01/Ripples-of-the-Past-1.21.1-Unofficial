package rotp.addons.ger.client;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import rotp.core.api.client.render.StandAuraFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.ger.client.GerRtzVfxSettings.Mode;
import rotp.addons.ger.network.GerRtzPayloads.GhostTrail;
import rotp.addons.ger.network.GerRtzPayloads.StartPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@OnlyIn(Dist.CLIENT)
public final class GerRtzNormalRenderer {
    public static final int RTZ_AURA_COLOR = 0xF9F7EE;
    public static final int STORM_PRIMITIVES_PER_FRAME = 230;
    private static final float[] STORM_RADII = {
            25.0F, 20.0F, 15.0F, 10.0F, 5.0F
    };
    public static final int OUTER_PARTICLE_DENSITY = 28;
    public static final int INNER_PARTICLE_DENSITY = 18;
    public static final float OUTER_PARTICLE_SIZE = 0.125F;
    public static final float INNER_PARTICLE_SIZE = 0.09F;
    public static final float HORIZONTAL_RADIUS_OFFSET = 2.4F;
    public static final float VERTICAL_OFFSET = 4.0F;
    public static final double CENTER_FORWARD_OFFSET = 0.3D;
    public static final double STORM_CENTER_Y = 2.5D;

    private static final long INNER_PARTICLE_SEED_OFFSET =
            0x5DEECE66DL;
    private static final long RADIUS_SEED_STEP =
            7046029254386353131L;
    private static final long PARTICLE_SEED_STEP =
            132897987541L;
    private static final float FIRST_COLOR_WEIGHT = 0.7F;
    private static final int OUTER_FACE_ALPHA = 100;
    private static final int OUTER_EDGE_ALPHA = 130;
    private static final int INNER_FACE_ALPHA = 100;
    private static final int INNER_EDGE_ALPHA = 110;
    private static final Color GOLD =
            new Color(255, 230, 80, 180);
    private static final Color LIGHT_GOLD =
            new Color(255, 255, 210, 140);
    private static final int[][] CUBE_FACES = {
            {0, 1, 2, 3},
            {4, 7, 6, 5},
            {0, 4, 5, 1},
            {1, 5, 6, 2},
            {2, 6, 7, 3},
            {3, 7, 4, 0}
    };
    private static final int[][] CUBE_EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int[][] PYRAMID_FACES = {
            {0, 1, 2},
            {0, 2, 3},
            {0, 3, 1},
            {1, 3, 2}
    };
    private static final int[][] PYRAMID_EDGES = {
            {0, 1}, {0, 2}, {0, 3},
            {1, 2}, {2, 3}, {3, 1}
    };

    private GerRtzNormalRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (GerRtzClientState.activeMode() != Mode.NORMAL) {
            return;
        }
        if (event.getStage()
                == RenderLevelStageEvent.Stage.AFTER_SKY) {
            queueAura();
        }
        else if (event.getStage()
                == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderStorm(event);
        }
    }

    private static void queueAura() {
        Minecraft minecraft = Minecraft.getInstance();
        StartPayload active = GerRtzClientState.active();
        if (minecraft.level == null || active == null) {
            return;
        }
        Set<Integer> targetIds = new LinkedHashSet<>();
        targetIds.add(active.sourceEntityId());
        for (GhostTrail trail : active.trails()) {
            targetIds.add(trail.entityId());
        }
        int queued = 0;
        for (int entityId : targetIds) {
            Entity entity = minecraft.level.getEntity(entityId);
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            StandAuraFx.renderAura(entity, RTZ_AURA_COLOR);
            queued++;
        }
        GerRtzDiagnostics.recordAuraDraw(queued);
    }

    private static void renderStorm(
            RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        StartPayload active = GerRtzClientState.active();
        Entity source = GerRtzClientState.sourceEntity();
        if (minecraft.level == null
                || active == null
                || source == null
                || !source.isAlive()) {
            return;
        }
        float partialTick = event.getPartialTick()
                .getGameTimeDeltaPartialTick(true);
        float alpha =
                GerRtzClientState.normalStormAlpha(partialTick);
        if (alpha <= 0.0F) {
            return;
        }

        Vec3 center = stormCenter(source, partialTick);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();
        RenderType renderType =
                GerRtzRenderTypes.particleStorm();
        VertexConsumer vertices = buffers.getBuffer(renderType);
        float time =
                GerRtzClientState.elapsedTicks() + partialTick;
        long sequence = GerRtzDiagnostics.snapshot()
                .presentationSequence();
        long baseSeed = active.owner().getMostSignificantBits()
                ^ active.owner().getLeastSignificantBits()
                ^ ((long) active.sourceEntityId() << 32)
                ^ sequence * 341873128712L;

        int primitives = 0;
        for (int radiusIndex = 0;
                radiusIndex < STORM_RADII.length;
                radiusIndex++) {
            long seed = baseSeed
                    ^ radiusIndex * RADIUS_SEED_STEP;
            primitives += drawParticleBand(
                    vertices,
                    pose,
                    center,
                    camera,
                    seed,
                    time,
                    OUTER_PARTICLE_DENSITY,
                    STORM_RADII[radiusIndex],
                    OUTER_PARTICLE_SIZE,
                    false,
                    OUTER_FACE_ALPHA,
                    OUTER_EDGE_ALPHA,
                    alpha);
            primitives += drawParticleBand(
                    vertices,
                    pose,
                    center,
                    camera,
                    seed ^ INNER_PARTICLE_SEED_OFFSET,
                    time,
                    INNER_PARTICLE_DENSITY,
                    STORM_RADII[radiusIndex],
                    INNER_PARTICLE_SIZE,
                    true,
                    INNER_FACE_ALPHA,
                    INNER_EDGE_ALPHA,
                    alpha);
        }
        buffers.endBatch(renderType);
        GerRtzDiagnostics.recordStormPrimitives(primitives);
    }

    private static int drawParticleBand(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 center,
            Vec3 camera,
            long seed,
            float time,
            int density,
            float radius,
            float particleSize,
            boolean clockwise,
            int faceAlphaScale,
            int edgeAlphaScale,
            float lifecycleAlpha) {
        double direction = clockwise ? -1.0D : 1.0D;
        for (int index = 0; index < density; index++) {
            Random random = new Random(
                    seed ^ index * PARTICLE_SEED_STEP);
            double baseAngle =
                    random.nextDouble() * Math.PI * 2.0D;
            double orbitAngle = baseAngle
                    + direction * time * 0.16D;
            double horizontalRadius = radius
                    + random.nextDouble()
                            * HORIZONTAL_RADIUS_OFFSET;
            double vertical = (random.nextDouble() * 2.0D
                    - 1.0D) * VERTICAL_OFFSET;
            Vec3 particle = center.add(
                    Math.cos(orbitAngle) * horizontalRadius,
                    vertical,
                    Math.sin(orbitAngle) * horizontalRadius);
            Vec3 rotation = new Vec3(
                    random.nextDouble() * 360.0D
                            + time * 5.0F,
                    random.nextDouble() * 360.0D
                            + direction * time * 7.0D,
                    random.nextDouble() * 360.0D
                            + time * 3.0F);
            float edgeWidth = Math.max(
                    0.003F, particleSize * 0.08F);
            Color color = random.nextFloat()
                            < FIRST_COLOR_WEIGHT
                    ? GOLD
                    : LIGHT_GOLD;
            int faceAlpha = scaleAlpha(
                    color.alpha(), faceAlphaScale, lifecycleAlpha);
            int edgeAlpha = scaleAlpha(
                    color.alpha(), edgeAlphaScale, lifecycleAlpha);
            Vec3 relative = particle.subtract(camera);
            if (random.nextBoolean()) {
                drawOutlinedCube(
                        vertices,
                        pose,
                        relative,
                        particleSize,
                        edgeWidth,
                        rotation,
                        color,
                        faceAlpha,
                        edgeAlpha);
            }
            else {
                drawOutlinedPyramid(
                        vertices,
                        pose,
                        relative,
                        particleSize,
                        edgeWidth,
                        rotation,
                        color,
                        faceAlpha,
                        edgeAlpha);
            }
        }
        return density;
    }

    private static void drawOutlinedCube(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 center,
            float size,
            float edgeWidth,
            Vec3 rotation,
            Color color,
            int faceAlpha,
            int edgeAlpha) {
        double half = size * 0.5D;
        Vec3[] points = {
                rotate(new Vec3(-half, -half, -half), rotation),
                rotate(new Vec3(half, -half, -half), rotation),
                rotate(new Vec3(half, -half, half), rotation),
                rotate(new Vec3(-half, -half, half), rotation),
                rotate(new Vec3(-half, half, -half), rotation),
                rotate(new Vec3(half, half, -half), rotation),
                rotate(new Vec3(half, half, half), rotation),
                rotate(new Vec3(-half, half, half), rotation)
        };
        for (int[] face : CUBE_FACES) {
            drawQuad(
                    vertices,
                    pose,
                    center.add(points[face[0]]),
                    center.add(points[face[1]]),
                    center.add(points[face[2]]),
                    center.add(points[face[3]]),
                    color,
                    faceAlpha);
        }
        drawEdges(
                vertices,
                pose,
                center,
                points,
                CUBE_EDGES,
                edgeWidth,
                color,
                edgeAlpha);
    }

    private static void drawOutlinedPyramid(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 center,
            float edgeLength,
            float edgeWidth,
            Vec3 rotation,
            Color color,
            int faceAlpha,
            int edgeAlpha) {
        double scale = edgeLength / (2.0D * Math.sqrt(2.0D));
        Vec3[] points = {
                rotate(new Vec3(scale, scale, scale), rotation),
                rotate(new Vec3(scale, -scale, -scale), rotation),
                rotate(new Vec3(-scale, scale, -scale), rotation),
                rotate(new Vec3(-scale, -scale, scale), rotation)
        };
        for (int[] face : PYRAMID_FACES) {
            drawTriangle(
                    vertices,
                    pose,
                    center.add(points[face[0]]),
                    center.add(points[face[1]]),
                    center.add(points[face[2]]),
                    color,
                    faceAlpha);
        }
        drawEdges(
                vertices,
                pose,
                center,
                points,
                PYRAMID_EDGES,
                edgeWidth,
                color,
                edgeAlpha);
    }

    private static void drawEdges(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 center,
            Vec3[] points,
            int[][] edges,
            float width,
            Color color,
            int alpha) {
        for (int[] edgeIndices : edges) {
            Vec3 start = center.add(points[edgeIndices[0]]);
            Vec3 end = center.add(points[edgeIndices[1]]);
            Vec3 edge = end.subtract(start);
            if (edge.lengthSqr() < 1.0E-8D) {
                continue;
            }
            Vec3 midpoint = start.add(end).scale(0.5D);
            Vec3 view = midpoint.scale(-1.0D);
            if (view.lengthSqr() < 1.0E-8D) {
                view = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 normalizedEdge = edge.normalize();
            Vec3 side = normalizedEdge.cross(view.normalize());
            if (side.lengthSqr() < 1.0E-8D) {
                Vec3 fallback =
                        Math.abs(normalizedEdge.y) > 0.999D
                                ? new Vec3(1.0D, 0.0D, 0.0D)
                                : new Vec3(0.0D, 1.0D, 0.0D);
                side = normalizedEdge.cross(fallback);
            }
            Vec3 offset = side.normalize().scale(width * 0.5D);
            drawQuad(
                    vertices,
                    pose,
                    start.add(offset),
                    start.subtract(offset),
                    end.subtract(offset),
                    end.add(offset),
                    color,
                    alpha);
        }
    }

    private static void drawQuad(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            Color color,
            int alpha) {
        drawTriangle(vertices, pose, a, b, c, color, alpha);
        drawTriangle(vertices, pose, a, c, d, color, alpha);
    }

    private static void drawTriangle(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Color color,
            int alpha) {
        vertex(vertices, pose, a, color, alpha);
        vertex(vertices, pose, b, color, alpha);
        vertex(vertices, pose, c, color, alpha);
    }

    private static void vertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 point,
            Color color,
            int alpha) {
        vertices.addVertex(
                        pose,
                        (float) point.x,
                        (float) point.y,
                        (float) point.z)
                .setColor(
                        color.red(),
                        color.green(),
                        color.blue(),
                        alpha);
    }

    private static Vec3 stormCenter(
            Entity source, float partialTick) {
        float yaw = Mth.rotLerp(
                partialTick, source.yRotO, source.getYRot());
        double yawRadians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(
                -Math.sin(yawRadians),
                0.0D,
                Math.cos(yawRadians));
        return source.getPosition(partialTick)
                .add(forward.scale(CENTER_FORWARD_OFFSET))
                .add(0.0D, STORM_CENTER_Y, 0.0D);
    }

    private static Vec3 rotate(
            Vec3 point, Vec3 rotationDegrees) {
        double x = point.x;
        double y = point.y;
        double z = point.z;
        double angleX = Math.toRadians(rotationDegrees.x);
        double cosX = Math.cos(angleX);
        double sinX = Math.sin(angleX);
        double yAfterX = y * cosX - z * sinX;
        double zAfterX = y * sinX + z * cosX;
        y = yAfterX;
        z = zAfterX;
        double angleY = Math.toRadians(rotationDegrees.y);
        double cosY = Math.cos(angleY);
        double sinY = Math.sin(angleY);
        double xAfterY = x * cosY + z * sinY;
        double zAfterY = -x * sinY + z * cosY;
        x = xAfterY;
        z = zAfterY;
        double angleZ = Math.toRadians(rotationDegrees.z);
        double cosZ = Math.cos(angleZ);
        double sinZ = Math.sin(angleZ);
        return new Vec3(
                x * cosZ - y * sinZ,
                x * sinZ + y * cosZ,
                z);
    }

    private static int scaleAlpha(
            int alpha, int scale, float lifecycleAlpha) {
        return Mth.clamp(
                Math.round(alpha * scale / 255.0F
                        * lifecycleAlpha),
                0,
                255);
    }

    private record Color(
            int red, int green, int blue, int alpha) {}
}
