package rotp.addons.spawn_with_stand.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class StandSpawnConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue STAND_CHANCE;
    public static final ModConfigSpec.BooleanValue USE_STAND_ORDER;
    public static final ModConfigSpec.ConfigValue<
            List<? extends String>> STAND_GIVING_ORDER;

    private static final AtomicLong SNAPSHOT_EPOCH =
            new AtomicLong();
    private static volatile Snapshot activeSnapshot;

    static {
        ModConfigSpec.Builder builder =
                new ModConfigSpec.Builder();
        builder.push("Stand Spawn Settings");
        STAND_CHANCE = builder
                .comment(
                        "Chance (0-100%) to get a stand upon first "
                                + "joining a world (Default: 13.0)")
                .defineInRange(
                        "standChance",
                        13.0D,
                        0.0D,
                        100.0D);
        USE_STAND_ORDER = builder
                .comment(
                        "If true, stands are given in the order "
                                + "specified in standOrder. If false, "
                                + "eligible stands are selected randomly.")
                .define("useStandOrder", false);
        STAND_GIVING_ORDER = builder
                .comment(
                        "Ordered Stand IDs for successful first-login "
                                + "rolls, for example [\"jojo_ripples:the_world\", "
                                + "\"rotp_kingcrimson:kingcrimson\"]. "
                                + "After the list is exhausted, selection "
                                + "is random. An invalid entry consumes "
                                + "its ordered slot without a fallback.")
                .defineListAllowEmpty(
                        "standOrder",
                        List.<String>of(),
                        () -> "",
                        entry -> entry instanceof String);
        builder.pop();
        SPEC = builder.build();
        activeSnapshot = defaults();
    }

    private StandSpawnConfig() {}

    public static Snapshot snapshot() {
        return activeSnapshot;
    }

    public static void onConfigLoading(
            ModConfigEvent.Loading event) {
        refresh(event.getConfig());
    }

    public static void onConfigReloading(
            ModConfigEvent.Reloading event) {
        refresh(event.getConfig());
    }

    public static void onConfigUnloading(
            ModConfigEvent.Unloading event) {
        if (owns(event.getConfig())) {
            activeSnapshot = defaults();
        }
    }

    private static void refresh(ModConfig config) {
        if (!owns(config)) {
            return;
        }
        activeSnapshot = new Snapshot(
                STAND_CHANCE.get(),
                USE_STAND_ORDER.get(),
                List.copyOf(STAND_GIVING_ORDER.get()),
                SNAPSHOT_EPOCH.incrementAndGet());
    }

    private static boolean owns(ModConfig config) {
        return config != null && config.getSpec() == SPEC;
    }

    private static Snapshot defaults() {
        return new Snapshot(
                13.0D,
                false,
                List.of(),
                SNAPSHOT_EPOCH.incrementAndGet());
    }

    public record Snapshot(
            double standChance,
            boolean useStandOrder,
            List<String> standOrder,
            long epoch) {
        public Snapshot {
            standOrder = List.copyOf(standOrder);
        }
    }
}
