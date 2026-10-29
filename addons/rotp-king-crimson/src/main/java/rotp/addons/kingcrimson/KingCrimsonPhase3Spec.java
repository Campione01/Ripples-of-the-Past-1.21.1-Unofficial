package rotp.addons.kingcrimson;

public final class KingCrimsonPhase3Spec {
    // 1.16 InitStands staminaCost(175), never charged: the hold-only Epitaph never ran StandAction.onPerform.
    // Kept as the skill's unlock exp, which the other King Crimson skills also take from their stamina cost.
    public static final float EPITAPH_ACTIVATION_STAMINA = 175.0F;
    public static final int EPITAPH_WINDOW_TICKS = 60;
    public static final int EPITAPH_COOLDOWN_TICKS = 35;
    public static final int DODGE_FEEDBACK_TICKS = 6;
    public static final double DODGE_DISTANCE = 2.0D;
    public static final int HIGH_RESOLVE_LEVEL = 3;

    public static final String EPITAPH_POST_CHAIN_PATH =
            "shaders/post/epitaph.json";

    private KingCrimsonPhase3Spec() {}
}
