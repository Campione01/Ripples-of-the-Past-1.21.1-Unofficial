package rotp.addons.scarymonsters;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ScaryMonstersConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue
            DRAGON_FORM_DAMAGE_REDUCTION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("dragon_form");
        DRAGON_FORM_DAMAGE_REDUCTION = builder
                .comment(
                        "Damage reduction ratio in Dragon Form. "
                                + "0.0 gives no reduction and 1.0 gives immunity.")
                .defineInRange(
                        "dragonFormDamageReduction",
                        0.5D,
                        0.0D,
                        1.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private ScaryMonstersConfig() {}
}
