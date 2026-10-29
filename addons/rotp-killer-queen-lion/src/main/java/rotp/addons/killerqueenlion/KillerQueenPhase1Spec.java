package rotp.addons.killerqueenlion;

public final class KillerQueenPhase1Spec {
    public static final String SOURCE_SHA256 =
            "AB4298324E68B6FAF18236B3256C17F53F3CD82504C3E58327F2959024F66FC2";
    public static final String SOURCE_MODEL_SHA256 =
            "B41895EA8A4CE263FC41413E845CD2149ECC7F2BF431EB1CEEDEBB1B96B5413B";
    public static final String DECOMPILE_TREE_SHA256 =
            "CA7A08CC08375D45565C3730E60A88388AAB4CC232B45A63C6EEFD466498987A";

    public static final double POWER = 15.0D;
    public static final double SPEED = 14.0D;
    public static final double EFFECTIVE_RANGE = 3.0D;
    public static final double MAX_RANGE = 8.0D;
    public static final double DURABILITY = 12.0D;
    public static final double PRECISION = 12.0D;
    public static final float ENTITY_WIDTH = 0.65F;
    public static final float ENTITY_HEIGHT = 1.95F;
    public static final int STAND_COLOR = 0xEA60D5;

    public static final int PRIMARY_BOMB_RESOLVE_LEVEL = 2;
    public static final double PRIMARY_BOMB_RANGE = 16.0D;
    public static final float PRIMARY_BOMB_MARK_STAMINA = 125.0F;
    public static final float PRIMARY_BOMB_DETONATE_STAMINA = 250.0F;
    public static final float PRIMARY_BOMB_CLEAR_STAMINA = 50.0F;
    public static final float ITEM_BOMB_DETONATE_STAMINA = 20.0F;
    public static final double BLOCK_BOMB_RANGE = 50.0D;
    public static final float BLOCK_BOMB_MARK_STAMINA = 75.0F;
    public static final float BLOCK_BOMB_DETONATE_STAMINA = 125.0F;
    public static final float BLOCK_BOMB_CLEAR_STAMINA = 5.0F;
    public static final int SHEER_HEART_RESOLVE_LEVEL = 3;
    public static final float SHEER_HEART_STAMINA = 200.0F;
    public static final int SNOW_BOMB_RESOLVE_LEVEL = 4;
    public static final float PROJECTILE_BOMB_STAMINA = 100.0F;
    public static final float PROJECTILE_DETONATE_STAMINA = 100.0F;
    public static final float BUBBLE_MARK_STAMINA = 50.0F;
    public static final float BUBBLE_BLOCK_DETONATE_STAMINA = 125.0F;
    public static final int BUBBLE_MARK_LIFETIME_TICKS = 200;
    public static final long BUBBLE_BLOCK_MARK_LIFETIME_TICKS = 6000L;
    public static final float BTD_MARK_STAMINA = 50.0F;
    public static final float BTD_RESET_STAMINA = 500.0F;
    // 1.16 standPerform acts only on more stamina than these, read before
    // the Stand task pays the cost
    public static final float BLOCK_BOMB_MARK_STAMINA_FLOOR = 124.0F;
    public static final float PRIMARY_BOMB_DETONATE_STAMINA_FLOOR = 249.0F;
    public static final float ITEM_BOMB_DETONATE_STAMINA_FLOOR = 20.0F;

    private KillerQueenPhase1Spec() {}
}
