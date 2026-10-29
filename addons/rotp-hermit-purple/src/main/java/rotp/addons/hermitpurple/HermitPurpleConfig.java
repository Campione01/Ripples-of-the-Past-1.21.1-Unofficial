package rotp.addons.hermitpurple;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HermitPurpleConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HAMON_TO_HERMIT;
    public static final ModConfigSpec.BooleanValue HERMIT_HAMON;
    public static final ModConfigSpec.IntValue CONTROL_THRESHOLD;
    public static final ModConfigSpec.IntValue STRENGTH_THRESHOLD;
    public static final ModConfigSpec.IntValue BREATHING_THRESHOLD;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("hamon");
        HAMON_TO_HERMIT = builder
                .comment(
                        "Attempt to grant Hermit Purple once a Hamon user "
                                + "meets all configured thresholds.")
                .define("hamonToHermit", false);
        HERMIT_HAMON = builder
                .comment(
                        "Allow high-resolve Hermit Purple users with Hamon "
                                + "to apply Hamon to Stand targets.")
                .define("hermitHamon", false);
        CONTROL_THRESHOLD = builder.defineInRange(
                "controlThreshold", 60, 0, 60);
        STRENGTH_THRESHOLD = builder.defineInRange(
                "strengthThreshold", 60, 0, 60);
        BREATHING_THRESHOLD = builder.defineInRange(
                "breathingThreshold", 100, 0, 100);
        builder.pop();
        SPEC = builder.build();
    }

    private HermitPurpleConfig() {}
}
