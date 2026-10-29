package rotp.addons.d4c.travel;

import java.util.List;
import java.util.Map;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class D4CDimensionTargets {
    public static final ResourceLocation OVERWORLD =
            ResourceLocation.withDefaultNamespace("overworld");
    public static final ResourceLocation NETHER =
            ResourceLocation.withDefaultNamespace("the_nether");
    public static final ResourceLocation END =
            ResourceLocation.withDefaultNamespace("the_end");
    public static final ResourceLocation PARALLEL_WORLD =
            D4CAddon.id("parallel_world");

    public static final List<ResourceLocation> LEGACY_ORDER = List.of(
            OVERWORLD,
            NETHER,
            END,
            PARALLEL_WORLD,
            ResourceLocation.fromNamespaceAndPath("atum", "atum"),
            ResourceLocation.fromNamespaceAndPath(
                    "dead_guys_untitled_deep_dark_",
                    "deep_darky"),
            ResourceLocation.fromNamespaceAndPath(
                    "lostcities",
                    "lostcity"),
            ResourceLocation.fromNamespaceAndPath(
                    "twilightforest",
                    "skylight_forest"),
            ResourceLocation.fromNamespaceAndPath(
                    "twilightforest",
                    "twilightforest"));

    private static final Map<ResourceLocation, String> LABEL_KEYS = Map.of(
            OVERWORLD, "rotp_d4c.dimension.overworld",
            NETHER, "rotp_d4c.dimension.nether",
            END, "rotp_d4c.dimension.end",
            PARALLEL_WORLD, "rotp_d4c.dimension.parallel_world",
            LEGACY_ORDER.get(4), "rotp_d4c.dimension.atum",
            LEGACY_ORDER.get(5), "rotp_d4c.dimension.deep_dark",
            LEGACY_ORDER.get(6), "rotp_d4c.dimension.lost_city",
            LEGACY_ORDER.get(7), "rotp_d4c.dimension.skylight_forest",
            LEGACY_ORDER.get(8), "rotp_d4c.dimension.twilight_forest");

    private D4CDimensionTargets() {}

    public static boolean isKnown(ResourceLocation id) {
        return LEGACY_ORDER.contains(id);
    }

    public static ResourceKey<Level> levelKey(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static Component label(ResourceLocation id) {
        String key = LABEL_KEYS.get(id);
        return key != null
                ? Component.translatable(key)
                : Component.literal(id.toString());
    }
}
