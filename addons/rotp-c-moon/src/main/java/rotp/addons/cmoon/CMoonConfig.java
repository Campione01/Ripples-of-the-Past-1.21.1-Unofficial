package rotp.addons.cmoon;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CMoonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue DEFAULT_INVERSION_DAMAGE;
    public static final ModConfigSpec.IntValue
            DEFAULT_HEART_INVERSION_DAMAGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DEFAULT_INVERSION_DAMAGE = builder
                .comment(
                        "Amplifier used by ordinary C-Moon inversion hits.")
                .defineInRange("defaultInversionDamage", 1, 0, 100);
        DEFAULT_HEART_INVERSION_DAMAGE = builder
                .comment(
                        "Amplifier used by the Heart Inversion finisher.")
                .defineInRange(
                        "defaultHeartInversionDamage", 3, 0, 100);
        SPEC = builder.build();
    }

    private CMoonConfig() {}
}
