package rotp.addons.badcompany.entity;

import net.minecraft.util.Mth;

public enum BadCompanyUnitType {
    ALL(0, 0, 0),
    SOLDIER(1, 10, 0),
    TANK(2, 2, 2),
    HELICOPTER(4, 2, 3);

    private final int mask;
    private final int cap;
    private final int minimumResolve;

    BadCompanyUnitType(
            int mask, int cap, int minimumResolve) {
        this.mask = mask;
        this.cap = cap;
        this.minimumResolve = minimumResolve;
    }

    public int mask() {
        return mask;
    }

    public int cap() {
        return cap;
    }

    public int minimumResolve() {
        return minimumResolve;
    }

    public boolean includes(BadCompanyUnitType type) {
        return this == ALL || this == type;
    }

    public BadCompanyUnitType next(boolean backwards) {
        BadCompanyUnitType[] values = values();
        return values[Math.floorMod(
                ordinal() + (backwards ? -1 : 1),
                values.length)];
    }

    public static BadCompanyUnitType byOrdinal(int ordinal) {
        BadCompanyUnitType[] values = values();
        return values[Mth.clamp(
                ordinal, 0, values.length - 1)];
    }
}
