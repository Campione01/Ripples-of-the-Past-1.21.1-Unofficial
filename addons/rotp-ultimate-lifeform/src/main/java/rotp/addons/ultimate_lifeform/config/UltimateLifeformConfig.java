package rotp.addons.ultimate_lifeform.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class UltimateLifeformConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue SKY_LAUNCH_ENERGY_PERCENT;
    public static final ModConfigSpec.DoubleValue ESSENCE_DROP_CHANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("global");
        SKY_LAUNCH_ENERGY_PERCENT = builder
                .comment("Maximum energy percentage for explosion sky launch.")
                .defineInRange("skyLaunchEnergyPercent", 25, 1, 100);
        ESSENCE_DROP_CHANCE = builder
                .comment("Independent chance for each essence on a non-kept Ultimate Lifeform death.")
                .defineInRange("ulfEssenceDropPercent", 0.3D, 0.0D, 1.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private UltimateLifeformConfig() {}
}
