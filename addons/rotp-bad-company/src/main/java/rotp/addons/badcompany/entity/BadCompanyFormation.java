package rotp.addons.badcompany.entity;

import net.minecraft.util.Mth;

public enum BadCompanyFormation {
    RANDOM,
    CIRCLE,
    LINE;

    public BadCompanyFormation next(boolean backwards) {
        BadCompanyFormation[] values = values();
        return values[Math.floorMod(
                ordinal() + (backwards ? -1 : 1),
                values.length)];
    }

    public static BadCompanyFormation byOrdinal(int ordinal) {
        BadCompanyFormation[] values = values();
        return values[Mth.clamp(
                ordinal, 0, values.length - 1)];
    }
}
