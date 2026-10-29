package rotp.addons.lovers;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class LoversConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue DAMAGE_MULTIPLIER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("Lovers Settings");
        DAMAGE_MULTIPLIER = builder
                .translation(
                        "rotp_lovers.config.multiple_damage")
                .comment(
                        "Multiple damage for all attacks sent to the "
                                + "entity containing Lovers.",
                        "Defaults to 1.")
                .defineInRange(
                        "DefaultMultipleDamage",
                        1,
                        1,
                        Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private LoversConfig() {}

    public static float scaleFeedback(float incomingAmount) {
        double scaled = Math.max(0.0F, incomingAmount)
                * (double) DAMAGE_MULTIPLIER.get();
        return (float) Math.min(Float.MAX_VALUE, scaled);
    }
}
