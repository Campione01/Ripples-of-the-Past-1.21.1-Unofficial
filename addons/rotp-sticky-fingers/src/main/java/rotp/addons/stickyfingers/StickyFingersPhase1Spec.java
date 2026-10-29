package rotp.addons.stickyfingers;

public final class StickyFingersPhase1Spec {
    public static final String SOURCE_JAR =
            "RotP-\u94a2\u94fe\u624b\u6307-1.0.1.jar";
    public static final String SOURCE_SHA256 =
            "FBF5BB6C46C1F885BDD5D742E0772DA5522EF3875ECE9D90E6D5D0E4D26F97CF";
    public static final String STAND_ID = "rotp_stfn:sticky_fingers";
    public static final String EXTENDED_PUNCH_ABILITY_ID =
            "rotp_stfn:sticky_fingers_extended_punch";
    public static final String EXTENDED_PUNCH_ENTITY_ID =
            "rotp_stfn:stfn_extended_punch";

    public static final float EXTENDED_PUNCH_STAMINA = 375.0F;
    public static final int EXTENDED_PUNCH_PERFORM_TICKS = 25;
    public static final int EXTENDED_PUNCH_COOLDOWN_TECHNICAL = 20;
    public static final int EXTENDED_PUNCH_COOLDOWN_ADDITIONAL = 60;
    public static final float EXTENDED_PUNCH_DAMAGE = 2.5F;
    public static final float EXTENDED_PUNCH_MAX_HARDNESS = 5.0F;
    public static final int EXTENDED_PUNCH_RESOLVE_LEVEL = 1;
    public static final int EXTENDED_PUNCH_SKILL_POINTS = 100;

    public static final String ZIPPER_ABILITY_ID =
            "rotp_stfn:sticky_fingers_zipper";
    public static final String ZIPPER_BLOCK_ID =
            "rotp_stfn:sticky_fingers_zipper";
    public static final double ZIPPER_RANGE = 5.0D;
    // 1.16 zippers stayed until removed: a lease never times out.
    public static final long ZIPPER_NO_EXPIRY = Long.MAX_VALUE;
    // 1.16 had no cap (the 320 owner distance bounds it); this only
    // guards the saved lease data against hold-sweeping.
    public static final int MAX_ZIPPER_LEASES = 256;
    public static final double MAX_ZIPPER_OWNER_DISTANCE_SQR =
            320.0D;
    public static final int ZIPPER_RESOLVE_LEVEL = 2;
    public static final int ZIPPER_SKILL_POINTS = 150;
    public static final boolean ZIPPER_WORLD_MUTATION_ACTIVE = true;
    public static final String ZIPPER_OWNERSHIP =
            "addon block entity plus per-dimension SavedData lease";
    public static final String CLIMB_ZIPPER_ABILITY_ID =
            "rotp_stfn:sticky_fingers_climb_zipper";
    public static final float CLIMB_ZIPPER_STAMINA = 250.0F;
    public static final int CLIMB_ZIPPER_COOLDOWN_TICKS = 100;
    public static final int CLIMB_ZIPPER_MAX_TICKS = 200;
    public static final double CLIMB_ZIPPER_SPEED = 0.5D;
    public static final String HIDE_IN_MOB_ABILITY_ID =
            "rotp_stfn:sticky_fingers_hide_in_mob";
    public static final int HIDE_IN_MOB_RESOLVE_LEVEL = 3;
    public static final int HIDE_IN_MOB_SKILL_POINTS = 200;
    public static final int HIDE_IN_MOB_MAX_TICKS = 1200;
    public static final double HIDE_IN_MOB_RANGE = 5.0D;

    public static final String ZIP_USER_WOUNDS_ABILITY_ID =
            "rotp_stfn:sticky_fingers_zip_user_wounds";
    public static final String ZIP_TARGET_WOUNDS_ABILITY_ID =
            "rotp_stfn:sticky_fingers_zip_target_wounds";
    public static final String ZIP_WOUNDS_EFFECT_ID =
            "rotp_stfn:zip_wounds";
    public static final float ZIP_WOUNDS_STAMINA = 250.0F;
    public static final int ZIP_WOUNDS_COOLDOWN_TICKS = 100;
    public static final int ZIP_WOUNDS_DURATION_TICKS = 1200;
    public static final int ZIP_WOUNDS_HEAL_INTERVAL_TICKS = 50;
    public static final float ZIP_WOUNDS_HEAL_AMOUNT = 2.0F;
    public static final float ZIP_WOUNDS_BREAK_DAMAGE = 2.0F;
    public static final double ZIP_WOUNDS_TARGET_RANGE = 5.0D;

    public static final String UNZIP_PUNCH_ABILITY_ID =
            "rotp_stfn:sticky_fingers_unzip_punch";
    public static final String PLAYER_HEAD_ENTITY_ID =
            "rotp_stfn:player_head";
    public static final String PLAYER_ARM_ENTITY_ID =
            "rotp_stfn:player_arm";
    public static final String PLAYER_LEG_ENTITY_ID =
            "rotp_stfn:player_leg";
    public static final float UNZIP_PART_STAMINA = 50.0F;
    // 1.16 sticky_fingers_unzip_head / _arm / _leg resolveLevelToUnlock(1)
    public static final int UNZIP_PART_RESOLVE_LEVEL = 1;
    public static final int DETACHED_PART_LIFETIME_TICKS = 1200;
    public static final float UNZIP_BASE_DAMAGE_MULTIPLIER = 0.5F;
    public static final float UNZIP_DAMAGE_REFUND_MULTIPLIER = 0.5F;
    public static final int ZIPPER_EFFECT_DURATION_TICKS = 100;
    public static final float ZIPPER_EFFECT_DAMAGE_MULTIPLIER = 1.5F;
    public static final double DETACHED_PART_RETURN_DISTANCE_SQR =
            2.25D;
    public static final double DETACHED_PART_RETURN_SPEED = 0.45D;

    public static final String OPEN_STORAGE_ABILITY_ID =
            "rotp_stfn:sticky_fingers_open_storage";
    public static final String OPEN_INNER_STORAGE_ABILITY_ID =
            "rotp_stfn:sticky_fingers_open_inner_storage";
    public static final String ENTITY_STORAGE_MENU_ID =
            "rotp_stfn:stfn_entity_container";
    public static final String BLOCK_STORAGE_MENU_ID =
            "rotp_stfn:stfn_block_container";
    public static final int ENTITY_STORAGE_SLOTS = 9;
    public static final int BLOCK_STORAGE_SLOTS = 18;
    public static final double STORAGE_RANGE = 5.0D;
    public static final double STORAGE_RANGE_SQR =
            STORAGE_RANGE * STORAGE_RANGE;
    public static final int OPEN_STORAGE_RESOLVE_LEVEL = 3;
    public static final int OPEN_INNER_STORAGE_RESOLVE_LEVEL = 4;
    public static final int OPEN_STORAGE_SKILL_POINTS = 200;
    public static final int OPEN_INNER_STORAGE_SKILL_POINTS = 250;

    private StickyFingersPhase1Spec() {}
}
