package rotp.addons.sexpistols;

import net.minecraft.resources.ResourceLocation;

public final class SexPistolsIds {
    public static final String MOD_ID = "rotp_sex_pistols";

    public static final String FOUR_ATTACK_ICON_PATH = "four_attack_icon";
    public static final String REVOLVER_PATH = "revolver";
    public static final String REVOLVER_AMMO_PATH = "revolver_ammo";
    public static final String REVOLVER_CASING_PATH = "revolver_casing";
    public static final String REVOLVER_BULLET_PATH = "revolver_bullet";
    public static final String BULLET_HOLE_PATH = "bullet_hole";
    public static final String REVOLVER_MUZZLE_FLASH_PATH =
            "revolver_muzzle_flash";
    public static final String REVOLVER_CASING_EJECT_PATH =
            "revolver_casing_eject";
    public static final String KICK_MUZZLE_FLASH_PATH =
            "kick_muzzle_flash";
    public static final String KICK_MUZZLE_FLASH_CRITICAL_PATH =
            "kick_muzzle_flash_critical";
    public static final String MISTA_SUIT_HAT_PATH = "mista_suit_hat";
    public static final String MISTA_HAT_ARMOR_MATERIAL_PATH =
            "mista_hat";
    public static final String MISTA_HAT_ARMOR_LAYER_PATH =
            MISTA_SUIT_HAT_PATH;
    public static final String JOYFUL_PATH = "joyful";

    public static final String SEX_PISTOLS_PATH = "sex_pistols";
    public static final String SEX_PISTOLS_SELECT_PATH =
            "sex_pistols_select";
    public static final String SEX_PISTOLS_TARGET_MODE_PATH =
            "sex_pistols_target_mode";
    public static final String SEX_PISTOLS_TRANSFER_ORDER_PATH =
            "sex_pistols_transfer_order";
    public static final String SEX_PISTOLS_MOVE_SELECTED_PATH =
            "sex_pistols_move_selected";
    public static final String SEX_PISTOLS_STAND_RELOAD_PATH =
            "sex_pistols_stand_reload";
    public static final String SEX_PISTOLS_QUICK_RELOAD_PATH =
            "sex_pistols_quick_reload";
    public static final String SEX_PISTOLS_RECALL_PATH =
            "sex_pistols_recall";
    public static final String SEX_PISTOLS_TRAJECTORY_VISION_PATH =
            "sex_pistols_trajectory_vision";
    public static final String SEX_PISTOLS_ENCIRCLEMENT_PATH =
            "sex_pistols_encirclement";
    public static final String SEX_PISTOLS_PIERCING_SHOT_PATH =
            "sex_pistols_piercing_shot";
    public static final String SEX_PISTOLS_SPLITTING_SHOT_PATH =
            "sex_pistols_splitting_shot";
    public static final int[] SEX_PISTOLS_NUMBERS = {1, 2, 3, 5, 6, 7};

    public static final ResourceLocation FOUR_ATTACK_ICON =
            id(FOUR_ATTACK_ICON_PATH);
    public static final ResourceLocation SEX_PISTOLS =
            id(SEX_PISTOLS_PATH);

    private SexPistolsIds() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
