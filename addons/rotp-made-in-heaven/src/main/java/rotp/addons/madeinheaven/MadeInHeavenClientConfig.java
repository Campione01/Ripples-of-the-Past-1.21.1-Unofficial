package rotp.addons.madeinheaven;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MadeInHeavenClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue
            ENABLE_NEW_TIME_SYSTEM_ICONS;

    static {
        ModConfigSpec.Builder builder =
                new ModConfigSpec.Builder();
        builder.push("Client config");
        ENABLE_NEW_TIME_SYSTEM_ICONS = builder
                .translation(
                        "rotp_mih.config.client.enableNewTSIcons")
                .comment(
                        "Use the donor's new clock icons for "
                                + "time acceleration and clear.")
                .define("enableNewTSIcons", true);
        builder.pop();
        SPEC = builder.build();
    }

    private MadeInHeavenClientConfig() {}
}
