package rotp.addons.speedwagon;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpeedwagonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue KEEP_SP_ON_DEATH;
    public static final ModConfigSpec.BooleanValue
            INSTRUCTORS_HOUSE_SPAWN;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("Keep Powers After Death");
        KEEP_SP_ON_DEATH = builder
                .translation("rotp_sp.config.keepSpOnDeath")
                .comment(
                        "Keep the Speedwagon non-Stand power after death.")
                .define("keepSpOnDeath", true);
        builder.pop();
        builder.push("Structures Spawn");
        INSTRUCTORS_HOUSE_SPAWN = builder
                .translation(
                        "rotp_sp.config.instructorsHouseSpawn")
                .comment(
                        "Allow new instructor houses to generate.")
                .define("instructorsHouseSpawn", true);
        builder.pop();
        SPEC = builder.build();
    }

    private SpeedwagonConfig() {}
}
