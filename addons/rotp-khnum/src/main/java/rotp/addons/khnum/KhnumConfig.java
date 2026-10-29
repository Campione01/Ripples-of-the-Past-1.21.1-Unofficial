package rotp.addons.khnum;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class KhnumConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MIN_ENTITY_HEIGHT;
    public static final ModConfigSpec.DoubleValue MAX_ENTITY_HEIGHT;
    public static final ModConfigSpec.DoubleValue MIN_ENTITY_WIDTH;
    public static final ModConfigSpec.DoubleValue MAX_ENTITY_WIDTH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            ENTITY_DISGUISE_BLACKLIST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("disguise");
        MIN_ENTITY_HEIGHT = builder.defineInRange(
                "minimumEntityHeight", 1.0D, 0.1D, 10.0D);
        MAX_ENTITY_HEIGHT = builder.defineInRange(
                "maximumEntityHeight", 3.0D, 0.5D, 20.0D);
        MIN_ENTITY_WIDTH = builder.defineInRange(
                "minimumEntityWidth", 0.5D, 0.1D, 5.0D);
        MAX_ENTITY_WIDTH = builder.defineInRange(
                "maximumEntityWidth", 2.0D, 0.3D, 10.0D);
        ENTITY_DISGUISE_BLACKLIST = builder.defineList(
                "entityBlacklist",
                List.of(
                        "minecraft:fireball",
                        "minecraft:small_fireball",
                        "minecraft:dragon_fireball",
                        "minecraft:wither_skull",
                        "minecraft:shulker_bullet",
                        "minecraft:arrow",
                        "minecraft:spectral_arrow",
                        "minecraft:trident",
                        "minecraft:snowball",
                        "minecraft:egg",
                        "minecraft:ender_pearl",
                        "minecraft:experience_bottle",
                        "minecraft:potion",
                        "minecraft:llama_spit",
                        "minecraft:end_crystal",
                        "minecraft:boat",
                        "minecraft:minecart"),
                // Only the config screen uses it: without one NeoForge's list editor has no "+" button.
                () -> "",
                value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    private KhnumConfig() {}
}
