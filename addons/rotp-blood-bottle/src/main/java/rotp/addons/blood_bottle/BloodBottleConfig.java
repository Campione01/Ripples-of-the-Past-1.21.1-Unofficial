package rotp.addons.blood_bottle;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BloodBottleConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BOTTLE_DRAIN_PENALTY;
    public static final ModConfigSpec.IntValue FILL_BOTTLE_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_BOTTLE_HUNGER_VALUE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("Blood Bottle Configuration");
        BOTTLE_DRAIN_PENALTY = builder
                .translation("jojo.config.bottleDrinkPenalty")
                .comment(
                        "Percentage difference between refill cost and "
                                + "the blood bottle resource value.")
                .defineInRange("bottleDrainPenalty", 0, 0, 100);
        FILL_BOTTLE_MULTIPLIER = builder
                .translation("jojo.config.fillBottleMultiplier")
                .comment("Stored blood value as a percentage.")
                .defineInRange("fillBottleMultiplier", 100, 1, 1000);
        MAX_BOTTLE_HUNGER_VALUE = builder
                .translation("jojo.config.maxBottleHungerValue")
                .comment(
                        "Maximum active resource percentage at which a "
                                + "blood bottle can be drunk.")
                .defineInRange("maxBottleHungerValue", 65, 1, 100);
        builder.pop();
        SPEC = builder.build();
    }

    private BloodBottleConfig() {}
}
