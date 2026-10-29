package rotp.addons.tournament;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TournamentConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue
            ENABLE_CORRECTED_RECORD_PLAYER_LIST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("Tournament compatibility");
        ENABLE_CORRECTED_RECORD_PLAYER_LIST = builder
                .comment(
                        "Opt-in private correction for the legacy music-disc "
                                + "drop list. False preserves the canonical "
                                + "single list element exactly.")
                .define("enableCorrectedRecordPlayerList", false);
        builder.pop();
        SPEC = builder.build();
    }

    private TournamentConfig() {}
}
