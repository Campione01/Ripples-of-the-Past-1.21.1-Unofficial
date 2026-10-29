package rotp.addons.thehand;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TheHandConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PERCENT_DAMAGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PERCENT_DAMAGE = builder
                .comment(
                        "Use the legacy target-size-scaled max-health damage "
                                + "for Erase and Erasure Barrage.")
                .translation("rotp_the_hand.config.percent_damage")
                .define("PercentDamage", true);
        SPEC = builder.build();
    }

    private TheHandConfig() {}
}
