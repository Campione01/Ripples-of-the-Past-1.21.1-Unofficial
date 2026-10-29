package rotp.addons.neo_stand.client;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rotp.core.impl.stands.hierophant.HGEmeraldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import rotp.addons.neo_stand.NeoStandAddon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(
        modid = NeoStandAddon.MOD_ID,
        value = Dist.CLIENT)
public final class EmeraldTrailRenderer {
    private static final ResourceLocation TEXTURE =
            NeoStandAddon.id("textures/entity/emeraldatck.png");
    private static final double SEARCH_RADIUS = 256.0D;
    private static final double MIN_SAMPLE_DISTANCE = 0.08D;
    private static final double MAX_TRAIL_LENGTH = 30.0D;
    private static final float HALF_WIDTH = 0.08F;
    private static final Map<Integer, Trail> TRAILS =
            new HashMap<>();
    private static ClientLevel trackedLevel;
    private static int sampleClock;

    private EmeraldTrailRenderer() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity camera = minecraft.getCameraEntity();
        if (trackedLevel != level) {
            TRAILS.clear();
            trackedLevel = level;
            sampleClock = 0;
        }
        if (level == null || camera == null) {
            return;
        }
        AABB bounds = camera.getBoundingBox()
                .inflate(SEARCH_RADIUS);
        List<HGEmeraldEntity> emeralds =
                level.getEntitiesOfClass(
                        HGEmeraldEntity.class, bounds);
        int stride = emeralds.size() >= 32
                ? 3
                : emeralds.size() >= 16 ? 2 : 1;
        boolean sample = ++sampleClock % stride == 0;
        Set<Integer> present = new HashSet<>();
        for (HGEmeraldEntity emerald : emeralds) {
            present.add(emerald.getId());
            if (sample) {
                TRAILS.computeIfAbsent(
                                emerald.getId(),
                                ignored -> new Trail())
                        .sample(emerald.position());
            }
        }
        TRAILS.keySet().removeIf(id -> !present.contains(id));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || TRAILS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        PoseStack stack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();
        RenderType renderType =
                RenderType.entityTranslucent(TEXTURE);
        VertexConsumer vertices = buffers.getBuffer(renderType);
        for (Map.Entry<Integer, Trail> entry : TRAILS.entrySet()) {
            Entity entity =
                    minecraft.level.getEntity(entry.getKey());
            if (!(entity instanceof HGEmeraldEntity)
                    || entry.getValue().points.size() < 2) {
                continue;
            }
            renderTrail(
                    stack,
                    vertices,
                    camera,
                    entry.getValue());
        }
        buffers.endBatch(renderType);
    }

    private static void renderTrail(
            PoseStack stack,
            VertexConsumer vertices,
            Vec3 camera,
            Trail trail) {
        List<Vec3> points = List.copyOf(trail.points);
        double travelled = 0.0D;
        for (int index = 1; index < points.size(); index++) {
            Vec3 from = points.get(index - 1);
            Vec3 to = points.get(index);
            Vec3 segment = to.subtract(from);
            double length = segment.length();
            if (length < 1.0E-5D) {
                continue;
            }
            Vec3 midpoint = from.add(to).scale(0.5D);
            Vec3 view = camera.subtract(midpoint);
            Vec3 side = segment.cross(view);
            if (side.lengthSqr() < 1.0E-8D) {
                side = segment.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            if (side.lengthSqr() < 1.0E-8D) {
                continue;
            }
            side = side.normalize().scale(HALF_WIDTH);
            float u0 = (float) Math.min(
                    1.0D, travelled / MAX_TRAIL_LENGTH);
            travelled += length;
            float u1 = (float) Math.min(
                    1.0D, travelled / MAX_TRAIL_LENGTH);
            Vec3 fromLeft = from.add(side).subtract(camera);
            Vec3 fromRight = from.subtract(side).subtract(camera);
            Vec3 toRight = to.subtract(side).subtract(camera);
            Vec3 toLeft = to.add(side).subtract(camera);
            PoseStack.Pose pose = stack.last();
            vertex(vertices, pose, fromLeft, u0, 0.0F);
            vertex(vertices, pose, fromRight, u0, 0.015625F);
            vertex(vertices, pose, toRight, u1, 0.015625F);
            vertex(vertices, pose, toLeft, u1, 0.0F);
        }
    }

    private static void vertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 point,
            float u,
            float v) {
        vertices.addVertex(
                        pose,
                        (float) point.x,
                        (float) point.y,
                        (float) point.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static final class Trail {
        private final ArrayDeque<Vec3> points =
                new ArrayDeque<>();
        private final ArrayDeque<Double> distances =
                new ArrayDeque<>();
        private double length;

        private void sample(Vec3 point) {
            Vec3 previous = points.peekLast();
            if (previous != null) {
                double distance = previous.distanceTo(point);
                if (distance < MIN_SAMPLE_DISTANCE) {
                    return;
                }
                distances.addLast(distance);
                length += distance;
            }
            points.addLast(point);
            while (length > MAX_TRAIL_LENGTH
                    && points.size() > 2
                    && !distances.isEmpty()) {
                length -= distances.removeFirst();
                points.removeFirst();
            }
        }
    }
}
