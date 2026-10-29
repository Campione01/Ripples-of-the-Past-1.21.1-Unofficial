package rotp.addons.reworked;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ReworkedConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_TS_RESOLVE;
    public static final ModConfigSpec.BooleanValue ENABLE_NEW_TS_ICONS;

    static {
        ModConfigSpec.Builder commonBuilder =
                new ModConfigSpec.Builder();
        commonBuilder.push("Ripples of the Past Reworked");
        ENABLE_TS_RESOLVE = commonBuilder
                .translation("rejojo.config.enableTSResolve")
                .comment(
                        "Enables The World's per-tick and endpoint time-stop resolve rewards. "
                                + "Road-roller and multi-knife rewards are independent.")
                .define("enableTSResolve", true);
        commonBuilder.pop();
        SPEC = commonBuilder.build();

        ModConfigSpec.Builder clientBuilder =
                new ModConfigSpec.Builder();
        clientBuilder.push("Ripples of the Past Reworked");
        ENABLE_NEW_TS_ICONS = clientBuilder
                .translation("rejojo.config.enableNewTSIcons")
                .comment(
                        "Uses the donor-specific time-stop ability icons "
                                + "instead of their current-core fallbacks.")
                .define("enableNewTSIcons", true);
        clientBuilder.pop();
        CLIENT_SPEC = clientBuilder.build();
    }

    private ReworkedConfig() {}
}
