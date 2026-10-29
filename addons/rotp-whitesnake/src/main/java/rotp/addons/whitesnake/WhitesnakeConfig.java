package rotp.addons.whitesnake;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WhitesnakeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue
            SHOW_GLINT_ON_WHITESNAKE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("Whitesnake glint effect");
        SHOW_GLINT_ON_WHITESNAKE = builder
                .translation("rotp_whitesnake.glint_config")
                .define("showGlintOnWhitesnake", true);
        builder.pop();
        SPEC = builder.build();
    }

    private WhitesnakeConfig() {}
}
