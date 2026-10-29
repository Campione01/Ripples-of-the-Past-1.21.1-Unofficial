package rotp.addons.harvest.entity;

import net.minecraft.util.Mth;

public enum HarvestMemberMode {
    FOLLOW,
    ATTACK,
    GO_TO,
    STAY_WITH,
    SEARCH,
    RETURN_ITEM;

    public static HarvestMemberMode byId(int id) {
        return values()[Mth.clamp(id, 0, values().length - 1)];
    }
}
