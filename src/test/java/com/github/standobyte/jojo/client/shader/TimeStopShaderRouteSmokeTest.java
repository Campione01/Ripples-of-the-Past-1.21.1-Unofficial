package com.github.standobyte.jojo.client.shader;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public final class TimeStopShaderRouteSmokeTest {
    private TimeStopShaderRouteSmokeTest() {}

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
