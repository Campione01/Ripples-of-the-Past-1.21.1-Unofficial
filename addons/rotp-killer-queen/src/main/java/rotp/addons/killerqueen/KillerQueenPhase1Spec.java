package rotp.addons.killerqueen;

public final class KillerQueenPhase1Spec {
    public static final String SOURCE_SHA256 =
            "D4C302283C9A5CF2134EB7A4AFB154464065C30366BB2DC6AEA3620B01EB539F";
    public static final String SOURCE_MODEL_SHA256 =
            "BBA0637C04F905052BA7E055AB942FB1557D54031EBB265E9F2B07262E999AB0";
    public static final String DECOMPILE_TREE_SHA256 =
            "F9EFD448B7CC914372ABE64D44AE6E6589AE221EA040D58FF64E831984737541";

    public static final double POWER = 15.0D;
    public static final double SPEED = 14.0D;
    public static final double EFFECTIVE_RANGE = 3.0D;
    public static final double MAX_RANGE = 8.0D;
    public static final double DURABILITY = 12.0D;
    public static final double PRECISION = 12.0D;
    public static final float ENTITY_WIDTH = 0.65F;
    public static final float ENTITY_HEIGHT = 1.95F;
    public static final int STAND_COLOR = 0xDA7BAF;

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
    public static final float BTD_MARK_STAMINA = 50.0F;
    public static final float BTD_RESET_STAMINA = 500.0F;
    // 1.16 standPerform acts only on more stamina than these, read before
    // the Stand task pays the cost
    public static final float BLOCK_BOMB_MARK_STAMINA_FLOOR = 124.0F;
    public static final float PRIMARY_BOMB_DETONATE_STAMINA_FLOOR = 249.0F;
    public static final float ITEM_BOMB_DETONATE_STAMINA_FLOOR = 20.0F;

    private KillerQueenPhase1Spec() {}
}
