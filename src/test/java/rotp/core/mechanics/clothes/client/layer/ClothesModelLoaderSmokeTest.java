package rotp.core.mechanics.clothes.client.layer;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;

public final class ClothesModelLoaderSmokeTest {
    private ClothesModelLoaderSmokeTest() {}

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        PackResources pack = (PackResources) Proxy.newProxyInstance(
                PackResources.class.getClassLoader(), new Class<?>[] {PackResources.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("packId")) return "bundled-clothes-test";
                    throw new UnsupportedOperationException(method.getName());
                });
        Resource resource = new Resource(pack, () -> {
            var stream = ClothesModelLoaderSmokeTest.class.getResourceAsStream(
                    "/assets/jojo_rotp_clothes/geo/clothes/jotaro_p3_anime.geo.json");
            if (stream == null) throw new IOException("Missing bundled Jotaro clothes model");
            return stream;
        });
        Map<ResourceLocation, Resource> resources = new LinkedHashMap<>();
        for (String namespace : new String[] {"jojo_rotp_clothes", "jojo_ripples", "resource_pack"}) {
            resources.put(ResourceLocation.fromNamespaceAndPath(namespace,
                    "geo/clothes/jotaro_p3_anime.geo.json"), resource);
        }
        resources.put(ResourceLocation.fromNamespaceAndPath("unrelated", "geo/other.geo.json"), resource);
        resources.put(ResourceLocation.fromNamespaceAndPath("unrelated", "geo/clothes/readme.txt"), resource);
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(
                ResourceManager.class.getClassLoader(), new Class<?>[] {ResourceManager.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("listResources")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    String prefix = arguments[0] + "/";
                    Predicate<ResourceLocation> filter = (Predicate<ResourceLocation>) arguments[1];
                    Map<ResourceLocation, Resource> found = new LinkedHashMap<>();
                    resources.forEach((path, entry) -> {
                        if (path.getPath().startsWith(prefix) && filter.test(path)) found.put(path, entry);
                    });
                    return found;
                });

        var models = new ClothesModelLoader().prepare(manager, InactiveProfiler.INSTANCE);
        if (models.size() != 3) {
            throw new AssertionError("Expected all three clothing namespaces, got " + models.keySet());
        }
        for (String namespace : new String[] {"jojo_rotp_clothes", "jojo_ripples", "resource_pack"}) {
            var key = ResourceLocation.fromNamespaceAndPath(namespace, "jotaro_p3_anime");
            var definition = models.get(key);
            if (definition == null || definition.bakeRoot().getAllParts().noneMatch(part -> !part.cubes.isEmpty())) {
                throw new AssertionError("Missing or empty parsed clothing model: " + key);
            }
        }
        System.out.println("Clothes model loader smoke test passed: bundled/core/custom namespaces, path filter, real geometry.");
    }
}
