package rotp.addons.d4c.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CLoveTrainPresentationSpec;
import rotp.addons.d4c.D4CLoveTrainPresentationSpec.Phase;
import rotp.addons.d4c.network.LoveTrainPresentationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class D4CLoveTrainClientPresentation {
    private static final String GOLDEN_PILLAR =
            D4CAddon.id("golden_light_pillar").toString();
    private static final String SHORT_PILLAR =
            D4CAddon.id("short_golden_light_pillar").toString();
    private static final String LIGHT_BALL =
            D4CAddon.id("light_ball").toString();
    private static final String WIDE_PILLAR =
            D4CAddon.id("wide_golden_light_pillar").toString();
    private static final String GOLD_LIGHT =
            D4CAddon.id("gold_light").toString();

    private static final Map<UUID, Presentation> PRESENTATIONS =
            new HashMap<>();
    private static final Map<UUID, LifecycleState> LIFECYCLES =
            new HashMap<>();
    private static final Map<UUID, EmissionState> LAST_EMISSIONS =
            new HashMap<>();
    private static final Set<UUID> ACTIVE_LIGHT_WALLS =
            new HashSet<>();
    private static long emissionSequence;
    private static long cleanupGeneration;

    private D4CLoveTrainClientPresentation() {}

    public static void accept(
            LoveTrainPresentationPayload payload) {
        switch (payload.signal()) {
            case START -> start(payload.owner());
            case COMMIT -> commit(payload.owner());
            case CANCEL -> cancel(payload.owner());
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clearAll();
            return;
        }

        Iterator<Map.Entry<UUID, Presentation>> iterator =
                PRESENTATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Presentation> entry =
                    iterator.next();
            UUID ownerId = entry.getKey();
            Player owner = level.getPlayerByUUID(ownerId);
            if (owner == null
                    || !owner.isAlive()
                    || owner.isRemoved()) {
                iterator.remove();
                markCleaned(ownerId);
                continue;
            }

            Presentation presentation = entry.getValue();
            presentation.tick++;
            if (presentation.tick
                    >= D4CLoveTrainPresentationSpec.TOTAL_TICKS) {
                LIFECYCLES.put(
                        ownerId,
                        new LifecycleState(
                                D4CLoveTrainPresentationSpec.TOTAL_TICKS,
                                Phase.COMPLETE,
                                presentation.committed));
                iterator.remove();
                cleanupGeneration++;
                continue;
            }

            Phase phase =
                    D4CLoveTrainPresentationSpec.phaseAt(
                            presentation.tick);
            switch (phase) {
                case GATHER ->
                        spawnGather(level, owner, presentation.tick);
                case CONVERGENCE ->
                        spawnConvergence(
                                level, owner, presentation.tick);
                case PILLARS ->
                        spawnPillars(
                                level, owner, presentation.tick);
                case FINALE ->
                        spawnFinale(
                                level, owner, presentation.tick);
                default -> {
                }
            }
            LIFECYCLES.put(
                    ownerId,
                    new LifecycleState(
                            presentation.tick,
                            phase,
                            presentation.committed));
        }

        tickLightWalls(level);
    }

    public static void removeOwner(UUID owner) {
        if (owner == null) {
            return;
        }
        boolean changed = PRESENTATIONS.remove(owner) != null;
        changed |= ACTIVE_LIGHT_WALLS.remove(owner);
        LIFECYCLES.put(
                owner,
                new LifecycleState(-1, Phase.IDLE, false));
        if (changed) {
            cleanupGeneration++;
        }
    }

    public static void clearAll() {
        if (!PRESENTATIONS.isEmpty()
                || !ACTIVE_LIGHT_WALLS.isEmpty()
                || !LIFECYCLES.isEmpty()
                || !LAST_EMISSIONS.isEmpty()) {
            cleanupGeneration++;
        }
        PRESENTATIONS.clear();
        ACTIVE_LIGHT_WALLS.clear();
        LIFECYCLES.clear();
        LAST_EMISSIONS.clear();
    }

    public static DiagnosticSnapshot localSnapshot() {
        Player player = Minecraft.getInstance().player;
        return player != null
                ? snapshot(player.getUUID())
                : new DiagnosticSnapshot(
                        null,
                        false,
                        -1,
                        Phase.IDLE,
                        false,
                        false,
                        "",
                        0,
                        emissionSequence,
                        cleanupGeneration,
                        PRESENTATIONS.size(),
                        ACTIVE_LIGHT_WALLS.size());
    }

    public static DiagnosticSnapshot snapshot(UUID owner) {
        Presentation presentation = PRESENTATIONS.get(owner);
        LifecycleState lifecycle = presentation != null
                ? new LifecycleState(
                        presentation.tick,
                        D4CLoveTrainPresentationSpec.phaseAt(
                                presentation.tick),
                        presentation.committed)
                : LIFECYCLES.getOrDefault(
                        owner,
                        new LifecycleState(
                                -1, Phase.IDLE, false));
        EmissionState emission =
                LAST_EMISSIONS.getOrDefault(
                        owner, EmissionState.NONE);
        return new DiagnosticSnapshot(
                owner,
                presentation != null,
                lifecycle.tick,
                lifecycle.phase,
                lifecycle.committed,
                ACTIVE_LIGHT_WALLS.contains(owner),
                emission.type,
                emission.count,
                emission.sequence,
                cleanupGeneration,
                PRESENTATIONS.size(),
                ACTIVE_LIGHT_WALLS.size());
    }

    private static void start(UUID owner) {
        if (PRESENTATIONS.put(
                owner, new Presentation()) != null) {
            cleanupGeneration++;
        }
        LAST_EMISSIONS.remove(owner);
        LIFECYCLES.put(
                owner,
                new LifecycleState(
                        0, Phase.GATHER, false));
    }

    private static void commit(UUID owner) {
        Presentation presentation = PRESENTATIONS.get(owner);
        if (presentation == null) {
            presentation = new Presentation();
            presentation.tick =
                    D4CLoveTrainPresentationSpec.COMMIT_TICK;
            PRESENTATIONS.put(owner, presentation);
        }
        presentation.committed = true;
        LIFECYCLES.put(
                owner,
                new LifecycleState(
                        presentation.tick,
                        D4CLoveTrainPresentationSpec.phaseAt(
                                presentation.tick),
                        true));
    }

    private static void cancel(UUID owner) {
        PRESENTATIONS.remove(owner);
        ACTIVE_LIGHT_WALLS.remove(owner);
        LIFECYCLES.put(
                owner,
                new LifecycleState(-1, Phase.IDLE, false));
        cleanupGeneration++;
    }

    private static void markCleaned(UUID owner) {
        ACTIVE_LIGHT_WALLS.remove(owner);
        LIFECYCLES.put(
                owner,
                new LifecycleState(-1, Phase.IDLE, false));
        cleanupGeneration++;
    }

    private static void tickLightWalls(ClientLevel level) {
        Set<UUID> activeNow = new HashSet<>();
        for (Player player : level.players()) {
            if (!player.isAlive()
                    || player.isRemoved()
                    || !player.hasEffect(
                            D4CAddon.LOVE_TRAIN_LIGHT_WALL)) {
                continue;
            }
            activeNow.add(player.getUUID());
            spawnLightWall(level, player);
        }
        if (!ACTIVE_LIGHT_WALLS.equals(activeNow)
                && ACTIVE_LIGHT_WALLS.stream()
                        .anyMatch(
                                owner -> !activeNow.contains(owner))) {
            cleanupGeneration++;
        }
        ACTIVE_LIGHT_WALLS.clear();
        ACTIVE_LIGHT_WALLS.addAll(activeNow);
    }

    private static void spawnGather(
            ClientLevel level, Player owner, int tick) {
        Vec3 center = owner.position();
        float progress = (float) tick
                / D4CLoveTrainPresentationSpec.GATHER_END_TICK;
        int count = 3 + (int) (progress * 7.0F);
        for (int index = 0; index < count; index++) {
            double radius = 4.0D + Math.random() * 6.0D;
            double angle = Math.random() * Math.PI * 2.0D;
            double vertical = Math.random() * 3.0D - 1.0D;
            double x = center.x + Math.cos(angle) * radius;
            double y = center.y + 1.0D + vertical;
            double z = center.z + Math.sin(angle) * radius;
            add(
                    level,
                    D4CAddon.GOLD_LIGHT.get(),
                    x,
                    y,
                    z,
                    (center.x - x) * 0.01D,
                    0.005D + Math.random() * 0.01D,
                    (center.z - z) * 0.01D);
        }
        recordEmission(owner.getUUID(), GOLD_LIGHT, count);
    }

    private static void spawnConvergence(
            ClientLevel level, Player owner, int tick) {
        Vec3 center = owner.position();
        float progress = (float) (
                tick
                        - D4CLoveTrainPresentationSpec
                                .GATHER_END_TICK)
                / (D4CLoveTrainPresentationSpec
                        .CONVERGENCE_END_TICK
                        - D4CLoveTrainPresentationSpec
                                .GATHER_END_TICK);
        int lightBalls = 20
                + (int) (progress * 35.0F);
        for (int index = 0; index < lightBalls; index++) {
            double azimuth =
                    Math.random() * Math.PI * 2.0D;
            double polar = Math.acos(
                    1.0D - 2.0D * Math.random());
            double radius =
                    (2.0D + Math.random() * 5.0D)
                            * (1.2D - progress * 0.4D);
            double scale = 0.6D + Math.random() * 0.8D;
            double distance = radius * scale;
            double offsetX = distance
                    * Math.sin(polar) * Math.cos(azimuth);
            double offsetY = distance * Math.cos(polar);
            double offsetZ = distance
                    * Math.sin(polar) * Math.sin(azimuth);
            add(
                    level,
                    D4CAddon.LIGHT_BALL.get(),
                    center.x + offsetX,
                    center.y + 1.0D + offsetY,
                    center.z + offsetZ,
                    center.x,
                    center.y + 1.0D,
                    center.z);
        }
        recordEmission(
                owner.getUUID(), LIGHT_BALL, lightBalls);

        int spirals = 8 + (int) (progress * 12.0F);
        int goldLights = spirals;
        for (int index = 0; index < spirals; index++) {
            double angle = (
                    tick * 0.15D
                            + index * Math.PI * 2.0D
                                    / spirals)
                    % (Math.PI * 2.0D);
            double radius = 1.5D
                    + Math.sin(tick * 0.1D) * 0.5D;
            double vertical =
                    Math.sin(tick * 0.08D + index)
                            * 1.5D;
            double x = center.x + Math.cos(angle) * radius;
            double y = center.y + 1.0D + vertical;
            double z = center.z + Math.sin(angle) * radius;
            add(
                    level,
                    D4CAddon.GOLD_LIGHT.get(),
                    x,
                    y,
                    z,
                    (center.x - x) * 0.05D,
                    0.02D,
                    (center.z - z) * 0.05D);
        }
        if (progress > 0.3F) {
            goldLights += 6;
            for (int index = 0; index < 6; index++) {
                double angle = index * Math.PI / 3.0D
                        + tick * 0.1D;
                double radius = 0.8D
                        + Math.sin(tick * 0.2D) * 0.2D;
                add(
                        level,
                        D4CAddon.GOLD_LIGHT.get(),
                        center.x + Math.cos(angle) * radius,
                        center.y + 1.0D,
                        center.z + Math.sin(angle) * radius,
                        0.0D,
                        0.0D,
                        0.0D);
            }
        }
        recordEmission(
                owner.getUUID(), GOLD_LIGHT, goldLights);
    }

    private static void spawnPillars(
            ClientLevel level, Player owner, int tick) {
        Vec3 center = owner.position();
        float progress = (float) (
                tick
                        - D4CLoveTrainPresentationSpec
                                .CONVERGENCE_END_TICK)
                / (D4CLoveTrainPresentationSpec
                        .PILLARS_END_TICK
                        - D4CLoveTrainPresentationSpec
                                .CONVERGENCE_END_TICK);
        float rotationSpeed = 3.0F + progress * 8.0F;
        double outerRadius = 3.0D + progress * 1.5D;
        int outerPillars = 6 + (int) (progress * 2.0F);
        float outerRotation =
                tick * rotationSpeed * 0.05F;
        int wideCount = 0;
        int shortCount = 0;
        for (int outer = 0;
                outer < outerPillars;
                outer++) {
            double angle = outer * Math.PI * 2.0D
                    / outerPillars + outerRotation;
            double x = center.x
                    + Math.cos(angle) * outerRadius;
            double z = center.z
                    + Math.sin(angle) * outerRadius;
            for (int vertical = 0;
                    vertical < 10;
                    vertical++) {
                double y = center.y + 8.0D
                        - vertical * 0.8D;
                add(
                        level,
                        D4CAddon.WIDE_GOLDEN_LIGHT_PILLAR.get(),
                        x,
                        y,
                        z,
                        0.05D,
                        -0.08D,
                        0.0D);
                wideCount++;
                if (Math.random() < 0.3D) {
                    add(
                            level,
                            D4CAddon
                                    .SHORT_GOLDEN_LIGHT_PILLAR
                                    .get(),
                            x + (Math.random() - 0.5D) * 0.3D,
                            y,
                            z + (Math.random() - 0.5D) * 0.3D,
                            0.0D,
                            -0.05D,
                            0.0D);
                    shortCount++;
                }
            }
        }

        double innerRadius = 1.5D + progress * 0.8D;
        int innerPillars = 4 + (int) (progress * 2.0F);
        float innerRotation =
                -tick * rotationSpeed * 0.08F;
        for (int inner = 0;
                inner < innerPillars;
                inner++) {
            double angle = inner * Math.PI * 2.0D
                    / innerPillars + innerRotation;
            double x = center.x
                    + Math.cos(angle) * innerRadius;
            double z = center.z
                    + Math.sin(angle) * innerRadius;
            for (int vertical = 0;
                    vertical < 8;
                    vertical++) {
                add(
                        level,
                        D4CAddon.WIDE_GOLDEN_LIGHT_PILLAR.get(),
                        x,
                        center.y + 6.0D - vertical * 0.7D,
                        z,
                        0.03D,
                        -0.06D,
                        0.0D);
                wideCount++;
            }
        }
        recordEmission(
                owner.getUUID(), WIDE_PILLAR, wideCount);
        if (shortCount > 0) {
            recordEmission(
                    owner.getUUID(),
                    SHORT_PILLAR,
                    shortCount);
        }

        int goldLights = outerPillars + innerPillars + 8;
        for (int outer = 0;
                outer < outerPillars;
                outer++) {
            double angle = outer * Math.PI * 2.0D
                    / outerPillars + outerRotation;
            double radius = outerRadius + 0.3D;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            add(
                    level,
                    D4CAddon.GOLD_LIGHT.get(),
                    x,
                    center.y + 2.0D,
                    z,
                    Math.cos(angle + Math.PI / 2.0D)
                            * 0.12D,
                    -0.03D,
                    Math.sin(angle + Math.PI / 2.0D)
                            * 0.12D);
        }
        for (int inner = 0;
                inner < innerPillars;
                inner++) {
            double angle = inner * Math.PI * 2.0D
                    / innerPillars + innerRotation;
            double radius = innerRadius + 0.2D;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            add(
                    level,
                    D4CAddon.GOLD_LIGHT.get(),
                    x,
                    center.y + 1.5D,
                    z,
                    Math.cos(angle - Math.PI / 2.0D)
                            * 0.08D,
                    -0.02D,
                    Math.sin(angle - Math.PI / 2.0D)
                            * 0.08D);
        }
        for (int index = 0; index < 8; index++) {
            add(
                    level,
                    D4CAddon.GOLD_LIGHT.get(),
                    center.x + (Math.random() - 0.5D) * 0.6D,
                    center.y + 4.0D + Math.random() * 2.0D,
                    center.z + (Math.random() - 0.5D) * 0.6D,
                    0.0D,
                    -0.08D,
                    0.0D);
        }
        recordEmission(
                owner.getUUID(), GOLD_LIGHT, goldLights);
    }

    private static void spawnFinale(
            ClientLevel level, Player owner, int tick) {
        float progress = (float) (
                tick
                        - D4CLoveTrainPresentationSpec
                                .PILLARS_END_TICK)
                / (D4CLoveTrainPresentationSpec.TOTAL_TICKS
                        - D4CLoveTrainPresentationSpec
                                .PILLARS_END_TICK);
        if (progress > 0.75F) {
            return;
        }

        Vec3 center = owner.position();
        int wideCount = 0;
        for (int vertical = 0; vertical < 30; vertical++) {
            double y;
            if (progress <= 0.25F) {
                y = center.y + 25.0D
                        - vertical * (progress * 4.0F);
            } else {
                y = center.y + 25.0D - vertical;
                if (y <= center.y + 2.0D) {
                    y -= Math.sin(tick * 0.2D) * 0.5D;
                }
            }
            if (y < center.y - 5.0D
                    || y > center.y + 25.0D) {
                continue;
            }
            double width = 0.15D
                    + Math.sin(tick * 0.1D) * 0.05D;
            add(
                    level,
                    D4CAddon.WIDE_GOLDEN_LIGHT_PILLAR.get(),
                    center.x,
                    y,
                    center.z,
                    width,
                    y <= center.y + 1.0D ? -0.1D : 0.0D,
                    0.0D);
            wideCount++;
        }
        for (int vertical = 0; vertical < 35; vertical++) {
            double y = center.y + 30.0D - vertical;
            if (y < center.y - 5.0D
                    || y > center.y + 30.0D) {
                continue;
            }
            double verticalOffset = y <= center.y + 2.0D
                    ? -Math.sin(tick * 0.2D) * 0.3D
                    : 0.0D;
            add(
                    level,
                    D4CAddon.WIDE_GOLDEN_LIGHT_PILLAR.get(),
                    center.x,
                    y + verticalOffset,
                    center.z,
                    0.6D + Math.sin(tick * 0.15D) * 0.15D,
                    y <= center.y + 1.0D ? -0.15D : 0.0D,
                    0.0D);
            wideCount++;
        }
        recordEmission(
                owner.getUUID(), WIDE_PILLAR, wideCount);

        int goldLights = 15 + 48 + 80;
        for (int index = 0; index < 15; index++) {
            add(
                    level,
                    D4CAddon.GOLD_LIGHT.get(),
                    center.x + (Math.random() - 0.5D) * 0.8D,
                    center.y + 1.0D + Math.random() * 3.0D,
                    center.z + (Math.random() - 0.5D) * 0.8D,
                    0.0D,
                    0.15D,
                    0.0D);
        }
        for (int ring = 0; ring < 3; ring++) {
            double radius = 2.0D + ring * 1.5D;
            int points = 12 + ring * 4;
            for (int point = 0; point < points; point++) {
                double angle = point * Math.PI * 2.0D
                        / points
                        + tick * 0.1D * (ring + 1);
                add(
                        level,
                        D4CAddon.GOLD_LIGHT.get(),
                        center.x + Math.cos(angle) * radius,
                        center.y + 1.0D
                                + Math.sin(
                                        tick * 0.08D + ring)
                                        * 2.0D,
                        center.z + Math.sin(angle) * radius,
                        0.0D,
                        0.05D,
                        0.0D);
            }
        }
        for (int spoke = 0; spoke < 8; spoke++) {
            double angle = spoke * Math.PI / 4.0D
                    + tick * 0.05D;
            double radius = 3.0D
                    + Math.sin(tick * 0.1D) * 0.5D;
            for (int vertical = 0;
                    vertical < 10;
                    vertical++) {
                double x = center.x
                        + Math.cos(angle) * radius;
                double y = center.y + vertical * 0.5D;
                double z = center.z
                        + Math.sin(angle) * radius;
                add(
                        level,
                        D4CAddon.GOLD_LIGHT.get(),
                        x,
                        y,
                        z,
                        (center.x - x) * 0.02D,
                        0.1D,
                        (center.z - z) * 0.02D);
            }
        }
        if (progress > 0.3F) {
            goldLights += 20;
            for (int index = 0; index < 20; index++) {
                double angle =
                        Math.random() * Math.PI * 2.0D;
                double radius =
                        1.0D + Math.random() * 4.0D;
                add(
                        level,
                        D4CAddon.GOLD_LIGHT.get(),
                        center.x + Math.cos(angle) * radius,
                        center.y + 0.1D,
                        center.z + Math.sin(angle) * radius,
                        0.0D,
                        0.02D,
                        0.0D);
            }
        }
        recordEmission(
                owner.getUUID(), GOLD_LIGHT, goldLights);
    }

    private static void spawnLightWall(
            ClientLevel level, Player owner) {
        Vec3 look = owner.getLookAngle();
        Vec3 horizontal = new Vec3(
                look.x, 0.0D, look.z).normalize();
        Vec3 center = owner.position();
        int count = 0;
        for (Vec3 direction : new Vec3[] {
                horizontal, horizontal.scale(-1.0D)}) {
            for (double distance = 0.5D;
                    distance <= 40.0D;
                    distance += 2.0D) {
                Vec3 base = center.add(
                        direction.scale(distance));
                for (double yOffset = -0.5D;
                        yOffset <= 3.0D;
                        yOffset += 0.1D) {
                    add(
                            level,
                            D4CAddon.GOLDEN_LIGHT_PILLAR.get(),
                            base.x,
                            base.y + yOffset,
                            base.z,
                            0.0D,
                            0.0D,
                            0.0D);
                    count++;
                }
            }
        }
        recordEmission(
                owner.getUUID(), GOLDEN_PILLAR, count);
    }

    private static void add(
            ClientLevel level,
            SimpleParticleType type,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed) {
        level.addParticle(
                type,
                x,
                y,
                z,
                xSpeed,
                ySpeed,
                zSpeed);
    }

    private static void recordEmission(
            UUID owner, String type, int count) {
        if (count <= 0) {
            return;
        }
        emissionSequence++;
        LAST_EMISSIONS.put(
                owner,
                new EmissionState(
                        type, count, emissionSequence));
    }

    private static final class Presentation {
        private int tick;
        private boolean committed;
    }

    private record LifecycleState(
            int tick, Phase phase, boolean committed) {}

    private record EmissionState(
            String type, int count, long sequence) {
        private static final EmissionState NONE =
                new EmissionState("", 0, 0L);
    }

    public record DiagnosticSnapshot(
            UUID owner,
            boolean presentationActive,
            int presentationTick,
            Phase presentationPhase,
            boolean committed,
            boolean lightWallActive,
            String lastEmissionType,
            int lastEmissionCount,
            long emissionSequence,
            long cleanupGeneration,
            int trackedPresentations,
            int trackedLightWalls) {}
}
