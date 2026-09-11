package com.github.standobyte.jojo.client.shader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public final class TimeStopShaderRouteSmokeTest {
    private TimeStopShaderRouteSmokeTest() {}

    public static void main(String[] args) {
        run();
        System.out.println("Time Stop shader route and timeline smoke test passed");
    }

    public static void run() {
        Set<ResourceLocation> checked = new HashSet<>();
        ResourceLocation route =
                TimeStopShaderManager.findNamespacedShaderRoute(
                        "test_addon:custom_time_stop",
                        path -> {
                            checked.add(path);
                            return path.equals(id(
                                    "test_addon",
                                    "shaders/post/"
                                            + "custom_time_stop.json"));
                        });
        check(id("test_addon", "custom_time_stop").equals(route),
                "existing namespaced route was not selected");
        check(checked.equals(Set.of(id(
                        "test_addon",
                        "shaders/post/custom_time_stop.json"))),
                "namespaced route checked the wrong PostChain path");

        check(TimeStopShaderManager.findNamespacedShaderRoute(
                        "test_addon:missing",
                        path -> false)
                        == null,
                "missing namespaced route did not fall back");
        check(TimeStopShaderManager.findNamespacedShaderRoute(
                        "the_world_time_stop",
                        path -> {
                            throw new AssertionError(
                                    "built-in route queried resources");
                        })
                        == null,
                "built-in route entered addon lookup");
        check(TimeStopShaderManager.findNamespacedShaderRoute(
                        "not a valid:route",
                        path -> {
                            throw new AssertionError(
                                    "invalid route queried resources");
                        })
                        == null,
                "invalid namespaced route did not fall back");
        check(TimeStopShaderManager.findNamespacedShaderRoute(
                        null,
                        path -> {
                            throw new AssertionError(
                                    "null route queried resources");
                        })
                        == null,
                "null route did not fall back");

        verifyTimeline();
        verifyStartupUniformWiring();
    }

    private static void verifyTimeline() {
        int[] durations = {1, 3, 5, 64, 65, 99, 100, 101, 200};
        int samples = 0;
        for (int startup : new int[] {0, 35}) {
            for (int duration : durations) {
                TimeStopState.Instance instance = new TimeStopState.Instance(
                        1, duration, duration, new ChunkPos(0, 0),
                        1, 1, "the_world_time_stop").withStartupDelay(startup);
                TimeStopShaderManager manager = new TimeStopShaderManager();
                float previousRemaining = Float.POSITIVE_INFINITY;
                float previousSaturation = 0.0F;
                for (int elapsed = 0; elapsed < startup + duration; elapsed++) {
                    check(instance.isActive(), "visual timing shortened gameplay lifetime");
                    check(instance.ticksLeft() == duration - Math.max(elapsed - startup, 0),
                            "startup consumed gameplay duration");
                    for (int frame = 0; frame <= 100; frame++) {
                        float partial = frame / 100.0F;
                        int legacyLength = elapsed + instance.ticksLeft();
                        float legacyTicks = Math.min(legacyLength, elapsed + partial);
                        manager.updateTimeline(instance, legacyLength, elapsed, partial);
                        near(manager.length(), legacyLength, "opening-wave length changed");
                        near(manager.ticks(), legacyTicks, "opening-wave elapsed time changed");
                        near(manager.startupTicksLeft(), Math.max(startup - elapsed, 0),
                                "startup uniform did not count down");
                        check(openingWave(manager.length(), manager.ticks())
                                        == openingWave(legacyLength, legacyTicks),
                                "100-tick opening-wave threshold changed");

                        float remaining = manager.length() - manager.ticks()
                                + manager.startupTicksLeft();
                        near(remaining, startup + duration - elapsed - partial,
                                "end fade omitted remaining startup");
                        check(remaining <= previousRemaining + 0.0001F,
                                "end-fade time increased across frames or a tick boundary");
                        float saturation = saturation(remaining);
                        check(saturation + 0.0001F >= previousSaturation,
                                "saturation pulsed backward across frames or a tick boundary");
                        if (frame == 0 && elapsed > 0) {
                            near(remaining, previousRemaining, "end fade jumped at a tick boundary");
                            near(saturation, previousSaturation, "saturation jumped at a tick boundary");
                        }
                        if (startup == 0) {
                            check(Float.floatToIntBits(saturation)
                                            == Float.floatToIntBits(saturation(legacyLength - legacyTicks)),
                                    "no-startup fade changed");
                        }
                        previousRemaining = remaining;
                        previousSaturation = saturation;
                        samples++;
                    }
                    instance = instance.tickDown();
                }
                check(!instance.isActive(), "visual timing extended gameplay lifetime");
                near(previousRemaining, 0.0F, "fade did not finish at expiry");
                manager.updateTimeline(instance.withStartupDelay(35), 1, 0, 0);
                manager.reset();
                near(manager.startupTicksLeft(), 0.0F, "reset retained a stale startup uniform");
            }
        }
        System.out.println("Time Stop timeline samples checked: " + samples);
    }

    private static boolean openingWave(float length, float ticks) {
        return length >= 100.0F && ticks > 0.0F && ticks / 35.0F < 1.0F;
    }

    private static float saturation(float remaining) {
        return remaining < 5.0F
                ? 1.0F - Math.max(remaining, 0.0F) / 5.0F * (1.0F - 0.2F)
                : 0.2F;
    }

    private static void verifyStartupUniformWiring() {
        Path root = Path.of(System.getProperty("user.dir"));
        Path shaders = root.resolve("src/main/resources/assets/jojo_ripples/shaders/program");
        JsonObject program = JsonParser.parseString(read(shaders.resolve("time_stop.json")))
                .getAsJsonObject();
        int startupUniforms = 0;
        for (var element : program.getAsJsonArray("uniforms")) {
            JsonObject uniform = element.getAsJsonObject();
            if ("TSStartupTicksLeft".equals(uniform.get("name").getAsString())) {
                startupUniforms++;
                check("float".equals(uniform.get("type").getAsString())
                                && uniform.get("count").getAsInt() == 1
                                && uniform.getAsJsonArray("values").size() == 1,
                        "startup uniform shape changed");
                near(uniform.getAsJsonArray("values").get(0).getAsFloat(), 0,
                        "startup uniform needs a backward-compatible zero default");
            }
        }
        check(startupUniforms == 1, "startup uniform missing or duplicated");
        String fragment = read(shaders.resolve("time_stop.fsh"));
        check(fragment.contains("uniform float TSStartupTicksLeft;")
                        && fragment.contains("float timeLeft = TSLength - TSTicks + TSStartupTicksLeft;")
                        && fragment.contains("FadeInLength > 0.0 && timeLeft < FadeInLength"),
                "fragment fade is not wired to remaining startup");
        check(fragment.contains("TSLength >= 100.0 && tsEffectTiming < 1.0"),
                "opening-wave threshold must remain based on unchanged TSLength");
        Path javaRoot = root.resolve("src/main/java/com/github/standobyte/jojo/client/shader");
        String postShader = read(javaRoot.resolve("TimeStopPostShader.java"));
        check(postShader.contains("safeGetUniform(\"TSStartupTicksLeft\").set(manager.startupTicksLeft())"),
                "post-chain uniform upload is missing");
        String manager = read(javaRoot.resolve("TimeStopShaderManager.java"));
        check(manager.contains("updateTimeline(instance, timelineLength, ClientTimeStopHandler.getTimeStopTicks(), partialTick)"),
                "live shader manager bypasses the tested timeline calculation");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        }
        catch (IOException error) {
            throw new AssertionError("failed to read " + path, error);
        }
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) <= 0.0001F,
                message + ": expected=" + expected + ", actual=" + actual);
    }

    private static ResourceLocation id(
            String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(
                namespace, path);
    }

    private static void check(
            boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
