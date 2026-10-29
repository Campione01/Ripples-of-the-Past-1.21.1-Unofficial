package rotp.addons.nextalbum;

import net.minecraft.resources.ResourceLocation;

public final class NextAlbumIds {
    public static final String MOD_ID = "rotp_nextalbum";

    public static final String COLLAPSE_PATH = "collapse";
    public static final String LUCK_PLUCK_PATH = "luck_pluck";
    public static final String MISTA_SUIT_CLOTH_PATH =
            "mista_suit_cloth";
    public static final String MISTA_SUIT_PANTS_PATH =
            "mista_suit_pants";
    public static final String MISTA_SUIT_BOOTS_PATH =
            "mista_suit_boots";
    public static final String AJA_STONE_NECKLACE_PATH =
            "aja_stone_necklace";
    public static final String PLANT_AGING_DUST_PATH = "plant_aging_dust";

    private NextAlbumIds() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
