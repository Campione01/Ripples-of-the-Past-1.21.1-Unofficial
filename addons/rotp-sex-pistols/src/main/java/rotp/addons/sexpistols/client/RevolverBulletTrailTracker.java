package rotp.addons.sexpistols.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rotp.core.client.util.functions.ClientUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers.ShotType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT)
public final class RevolverBulletTrailTracker {
    public static final int ORDINARY_TRAIL_LIFETIME = 100;
    public static final int PIERCING_TRAIL_LIFETIME = 140;
    public static final int AFTERIMAGE_LIFETIME = 8;

    private static final Map<Integer, TrailState> ACTIVE =
            new HashMap<>();
    private static final List<TrailAfterimage> AFTERIMAGES =
            new ArrayList<>();
    private static ClientLevel trackedLevel;
    private static int clientTick;

    private RevolverBulletTrailTracker() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            clear();
            return;
        }
        if (trackedLevel != level) {
            clear();
            trackedLevel = level;
        }
        clientTick++;
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof RevolverBulletEntity bullet)
                    || !bullet.isAlive()) {
                continue;
            }
            TrailState state = ACTIVE.computeIfAbsent(
                    bullet.getId(), unused -> new TrailState());
            updateState(state, bullet);
            state.lastSeenTick = clientTick;
            state.texture = RevolverBulletRenderer.resolveTexture(bullet);
            seen.add(bullet.getId());
        }

        Iterator<Map.Entry<Integer, TrailState>> iterator =
                ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TrailState> entry = iterator.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            TrailState state = entry.getValue();
            if (clientTick - state.lastSeenTick <= 2
                    && state.points.size() > 1
                    && state.texture != null) {
                List<Vec3> points = state.points.stream()
                        .map(point -> point.position)
                        .toList();
                AFTERIMAGES.add(new TrailAfterimage(
                        points,
                        Math.max(state.maxSpeed, 1.2D),
                        state.piercing,
                        state.texture,
                        clientTick));
                SexPistolsPresentationDiagnostics
                        .recordAfterimageCreated(points.size());
            }
            iterator.remove();
        }
        AFTERIMAGES.removeIf(afterimage ->
                clientTick - afterimage.createdTick
                        > AFTERIMAGE_LIFETIME);
    }

    @SubscribeEvent
    public static void renderAfterimages(
            RenderLevelStageEvent event) {
        if (event.getStage()
                        != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || AFTERIMAGES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }
        float partialTick = event.getPartialTick()
                .getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Iterator<TrailAfterimage> iterator = AFTERIMAGES.iterator();
        while (iterator.hasNext()) {
            TrailAfterimage afterimage = iterator.next();
            float age = clientTick - afterimage.createdTick
                    + partialTick;
            float fade = 1.0F - Mth.clamp(
                    age / AFTERIMAGE_LIFETIME, 0.0F, 1.0F);
            if (fade <= 0.0F) {
                iterator.remove();
                continue;
            }
            List<RenderPoint> renderPoints = afterimage.points
                    .stream()
                    .map(point -> new RenderPoint(point, 1.0F))
                    .toList();
            TrailSnapshot snapshot = new TrailSnapshot(
                    renderPoints,
                    afterimage.speed,
                    afterimage.piercing,
                    afterimage.texture,
                    distance(renderPoints));
            RenderType renderType = RenderType.entityTranslucent(
                    afterimage.texture);
            renderRibbon(
                    poseStack,
                    buffers.getBuffer(renderType),
                    snapshot,
                    camera,
                    Vec3.ZERO,
                    fade * fade);
            buffers.endBatch(renderType);
        }
        poseStack.popPose();
        SexPistolsPresentationDiagnostics.recordAfterimageFrame(
                AFTERIMAGES.size());
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
        }
    }

    public static int activeTrailCount() {
        return ACTIVE.size();
    }

    public static int afterimageCount() {
        return AFTERIMAGES.size();
    }

    static TrailSnapshot activeSnapshot(
            RevolverBulletEntity bullet,
            float partialTick) {
        TrailState state = ACTIVE.get(bullet.getId());
        if (state == null) {
            state = new TrailState();
            updateState(state, bullet);
            state.lastSeenTick = clientTick;
            state.texture = RevolverBulletRenderer.resolveTexture(bullet);
            ACTIVE.put(bullet.getId(), state);
        }
        Vec3 current = bullet.tickCount <= 2
                ? bullet.position()
                : bullet.getPosition(partialTick);
        Vec3 motion = bullet.getDeltaMovement();
        double speed = motion.length();
        if (speed < 0.04D) {
            if (state.points.size() < 2) {
                return null;
            }
            motion = historicalMotion(current, state.points);
            speed = Math.max(1.2D, state.maxSpeed);
            if (motion.lengthSqr() < 1.0E-7D) {
                return null;
            }
        }
        Vec3 motionDirection = motion.normalize();
        double maxDistance = state.piercing
                ? Mth.clamp(speed * 34.0D, 72.0D, 220.0D)
                : Mth.clamp(speed * 28.0D, 48.0D, 160.0D);
        int lifetime = state.piercing
                ? PIERCING_TRAIL_LIFETIME
                : ORDINARY_TRAIL_LIFETIME;
        List<RenderPoint> points = new ArrayList<>();
        for (TrailPoint point : state.points) {
            Vec3 toCurrent = current.subtract(point.position);
            double distanceSqr = toCurrent.lengthSqr();
            if (distanceSqr <= 1.0E-8D
                    || distanceSqr > maxDistance * maxDistance
                    || toCurrent.normalize().dot(motionDirection)
                            < -0.08D) {
                continue;
            }
            float age = Mth.clamp(
                    (float) (clientTick - point.tick)
                            / Math.max(1, lifetime),
                    0.0F,
                    1.0F);
            float ageFade = 1.0F - age;
            points.add(new RenderPoint(
                    point.position, ageFade * ageFade));
        }
        points.add(new RenderPoint(current, 1.0F));
        if (points.size() < 2) {
            Vec3 fallback = motionDirection.scale(-Mth.clamp(
                    speed * 4.8D, 2.0D, 14.0D));
            points.add(0, new RenderPoint(
                    current.add(fallback), 1.0F));
        }
        return new TrailSnapshot(
                points,
                speed,
                state.piercing,
                state.texture != null
                        ? state.texture
                        : RevolverBulletRenderer.resolveTexture(bullet),
                distance(points));
    }

    static void renderRibbon(
            PoseStack poseStack,
            VertexConsumer vertices,
            TrailSnapshot snapshot,
            Vec3 cameraPosition,
            Vec3 vertexOrigin,
            float globalFade) {
        List<RenderPoint> points = snapshot.points;
        float speedFactor = Mth.clamp(
                (float) ((snapshot.speed - 0.15D) / 5.4D),
                0.0F,
                1.0F);
        float baseWidth = 0.14F
                * (0.95F + speedFactor * 2.1F)
                * (snapshot.piercing ? 1.55F : 1.0F);
        int count = points.size();
        for (int index = 1; index < count; index++) {
            RenderPoint fromPoint = points.get(index - 1);
            RenderPoint toPoint = points.get(index);
            Vec3 fromWorld = fromPoint.position;
            Vec3 toWorld = toPoint.position;
            Vec3 segment = toWorld.subtract(fromWorld);
            if (segment.lengthSqr() < 1.0E-7D) {
                continue;
            }
            Vec3 direction = segment.normalize();
            Vec3 toCamera = cameraPosition.subtract(toWorld);
            if (toCamera.lengthSqr() < 1.0E-10D) {
                continue;
            }
            Vec3 side = direction.cross(toCamera.normalize());
            if (side.lengthSqr() < 1.0E-10D) {
                side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            if (side.lengthSqr() < 1.0E-10D) {
                side = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
            }
            if (side.lengthSqr() < 1.0E-10D) {
                continue;
            }
            side = side.normalize();
            float fromProgress = (float) (index - 1) / (count - 1);
            float toProgress = (float) index / (count - 1);
            float fromFade = (0.18F + 0.82F * fromProgress)
                    * fromPoint.ageFade * globalFade;
            float toFade = (0.18F + 0.82F * toProgress)
                    * toPoint.ageFade * globalFade;
            renderSegment(
                    poseStack,
                    vertices,
                    fromWorld.subtract(vertexOrigin),
                    toWorld.subtract(vertexOrigin),
                    side,
                    baseWidth * fromFade,
                    baseWidth * toFade,
                    fromProgress,
                    toProgress,
                    (int) (235.0F * fromFade),
                    (int) (255.0F * toFade));
        }
    }

    private static void updateState(
            TrailState state, RevolverBulletEntity bullet) {
        Vec3 current = bullet.position();
        Vec3 motion = bullet.getDeltaMovement();
        double speed = motion.length();
        state.piercing = bullet.specialShotType() == ShotType.PIERCING;
        state.maxSpeed = Math.max(state.maxSpeed, speed);
        if (speed < 0.05D) {
            return;
        }
        double maxGap = Mth.clamp(
                speed * 2.6D, 1.25D, 8.0D);
        if (!state.points.isEmpty()) {
            Vec3 fromLast = current.subtract(
                    state.points.get(state.points.size() - 1).position);
            if (fromLast.lengthSqr() > maxGap * maxGap
                    || fromLast.lengthSqr() > 1.0E-8D
                            && fromLast.normalize().dot(
                                    motion.normalize()) < 0.15D) {
                state.points.clear();
            }
        }
        double minSpacing = Mth.clamp(
                0.42D - speed * 0.045D, 0.1D, 0.42D);
        if (state.points.isEmpty()) {
            Vec3 initial = current.subtract(motion.normalize().scale(
                    Mth.clamp(speed * 0.65D, 0.25D, 1.6D)));
            state.points.add(new TrailPoint(
                    initial, clientTick, (float) speed));
            state.points.add(new TrailPoint(
                    current, clientTick, (float) speed));
        }
        else if (state.points.get(state.points.size() - 1)
                .position.distanceToSqr(current)
                        >= minSpacing * minSpacing) {
            state.points.add(new TrailPoint(
                    current, clientTick, (float) speed));
        }
        int maxPoints = state.piercing
                ? Mth.clamp(
                        68 + (int) Math.ceil(speed * 13.0D),
                        96,
                        180)
                : Mth.clamp(
                        42 + (int) Math.ceil(speed * 10.0D),
                        64,
                        128);
        while (state.points.size() > maxPoints) {
            state.points.remove(0);
        }
        int lifetime = state.piercing
                ? PIERCING_TRAIL_LIFETIME
                : ORDINARY_TRAIL_LIFETIME;
        while (!state.points.isEmpty()
                && clientTick - state.points.get(0).tick > lifetime) {
            state.points.remove(0);
        }
    }

    private static Vec3 historicalMotion(
            Vec3 current, List<TrailPoint> points) {
        Vec3 motion = current.subtract(points.get(0).position);
        if (motion.lengthSqr() > 1.0E-7D) {
            return motion;
        }
        return points.get(points.size() - 1).position
                .subtract(points.get(0).position);
    }

    private static double distance(List<RenderPoint> points) {
        double distance = 0.0D;
        for (int index = 1; index < points.size(); index++) {
            distance += points.get(index).position.distanceTo(
                    points.get(index - 1).position);
        }
        return distance;
    }

    private static void renderSegment(
            PoseStack poseStack,
            VertexConsumer vertices,
            Vec3 from,
            Vec3 to,
            Vec3 side,
            float fromHalfWidth,
            float toHalfWidth,
            float fromU,
            float toU,
            int fromAlpha,
            int toAlpha) {
        if (fromAlpha <= 0 && toAlpha <= 0) {
            return;
        }
        Vec3 sideFrom = side.scale(fromHalfWidth);
        Vec3 sideTo = side.scale(toHalfWidth);
        Vec3 p0 = from.add(sideFrom);
        Vec3 p1 = from.subtract(sideFrom);
        Vec3 p2 = to.subtract(sideTo);
        Vec3 p3 = to.add(sideTo);
        vertex(poseStack, vertices, p0, fromU, 0.0F, fromAlpha);
        vertex(poseStack, vertices, p1, fromU, 1.0F, fromAlpha);
        vertex(poseStack, vertices, p2, toU, 1.0F, toAlpha);
        vertex(poseStack, vertices, p3, toU, 0.0F, toAlpha);
        vertex(poseStack, vertices, p3, toU, 0.0F, toAlpha);
        vertex(poseStack, vertices, p2, toU, 1.0F, toAlpha);
        vertex(poseStack, vertices, p1, fromU, 1.0F, fromAlpha);
        vertex(poseStack, vertices, p0, fromU, 0.0F, fromAlpha);
    }

    private static void vertex(
            PoseStack poseStack,
            VertexConsumer vertices,
            Vec3 point,
            float u,
            float v,
            int alpha) {
        PoseStack.Pose pose = poseStack.last();
        vertices.addVertex(
                        pose.pose(),
                        (float) point.x,
                        (float) point.y,
                        (float) point.z)
                .setColor(255, 255, 255, Mth.clamp(alpha, 0, 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(ClientUtil.MAX_LIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void clear() {
        if (!ACTIVE.isEmpty() || !AFTERIMAGES.isEmpty()
                || trackedLevel != null) {
            SexPistolsPresentationDiagnostics.recordTrailCleanup(
                    ACTIVE.size(), AFTERIMAGES.size());
        }
        ACTIVE.clear();
        AFTERIMAGES.clear();
        trackedLevel = null;
        clientTick = 0;
    }

    static record TrailSnapshot(
            List<RenderPoint> points,
            double speed,
            boolean piercing,
            ResourceLocation texture,
            double distance) {}

    static record RenderPoint(Vec3 position, float ageFade) {}

    private record TrailPoint(
            Vec3 position, int tick, float speed) {}

    private static final class TrailState {
        private final List<TrailPoint> points = new ArrayList<>();
        private double maxSpeed;
        private boolean piercing;
        private ResourceLocation texture;
        private int lastSeenTick;
    }

    private record TrailAfterimage(
            List<Vec3> points,
            double speed,
            boolean piercing,
            ResourceLocation texture,
            int createdTick) {}
}
