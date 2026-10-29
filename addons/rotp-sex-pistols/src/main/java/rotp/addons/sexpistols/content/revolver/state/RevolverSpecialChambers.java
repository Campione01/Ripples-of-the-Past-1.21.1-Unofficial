package rotp.addons.sexpistols.content.revolver.state;

import rotp.addons.sexpistols.registry.SexPistolsDataComponents;
import net.minecraft.world.item.ItemStack;

public final class RevolverSpecialChambers {
    public static final int CHAMBER_MASK = 0x3F;
    public static final int ENCIRCLEMENT_SHIFT = 0;
    public static final int PIERCING_SHIFT = 6;
    public static final int SPLITTING_SHIFT = 12;
    public static final int MAX_ENCODED = (1 << 18) - 1;

    private RevolverSpecialChambers() {}

    public static int encoded(ItemStack stack) {
        return stack.getOrDefault(
                        SexPistolsDataComponents
                                .REVOLVER_SPECIAL_CHAMBERS.get(),
                        0)
                & MAX_ENCODED;
    }

    public static boolean has(
            ItemStack stack, int chamber, ShotType type) {
        if (type == ShotType.NONE) {
            return modeForFiring(stack, chamber) == ShotType.NONE;
        }
        return (mask(stack, type)
                & 1 << Math.floorMod(
                        chamber, RevolverState.CHAMBER_COUNT)) != 0;
    }

    public static ShotType modeForFiring(
            ItemStack stack, int chamber) {
        if (has(stack, chamber, ShotType.SPLITTING)) {
            return ShotType.SPLITTING;
        }
        if (has(stack, chamber, ShotType.PIERCING)) {
            return ShotType.PIERCING;
        }
        if (has(stack, chamber, ShotType.ENCIRCLEMENT)) {
            return ShotType.ENCIRCLEMENT;
        }
        return ShotType.NONE;
    }

    public static void prime(
            ItemStack stack, int chamber, ShotType type) {
        int bit = 1 << Math.floorMod(
                chamber, RevolverState.CHAMBER_COUNT);
        int encoded = encoded(stack);
        switch (type) {
            case PIERCING -> {
                encoded = replaceMask(
                        encoded, ShotType.ENCIRCLEMENT, 0);
                encoded = replaceMask(
                        encoded, ShotType.PIERCING, bit);
            }
            case SPLITTING -> {
                encoded = replaceMask(
                        encoded, ShotType.ENCIRCLEMENT, 0);
                encoded = replaceMask(
                        encoded, ShotType.PIERCING, 0);
                encoded = replaceMask(
                        encoded, ShotType.SPLITTING, bit);
            }
            case ENCIRCLEMENT -> encoded = replaceMask(
                    encoded, ShotType.ENCIRCLEMENT, bit);
            case NONE -> {
                return;
            }
        }
        setEncoded(stack, encoded);
    }

    public static void clearFired(
            ItemStack stack, int chamber, ShotType type) {
        if (type == ShotType.NONE) {
            return;
        }
        int bit = 1 << Math.floorMod(
                chamber, RevolverState.CHAMBER_COUNT);
        setEncoded(
                stack,
                replaceMask(
                        encoded(stack),
                        type,
                        mask(stack, type) & ~bit));
    }

    public static void clearChamber(
            ItemStack stack, int chamber) {
        int bit = 1 << Math.floorMod(
                chamber, RevolverState.CHAMBER_COUNT);
        int encoded = encoded(stack);
        for (ShotType type : ShotType.values()) {
            if (type != ShotType.NONE) {
                encoded = replaceMask(
                        encoded,
                        type,
                        mask(encoded, type) & ~bit);
            }
        }
        setEncoded(stack, encoded);
    }

    private static int mask(
            ItemStack stack, ShotType type) {
        return mask(encoded(stack), type);
    }

    private static int mask(int encoded, ShotType type) {
        return encoded >> type.shift() & CHAMBER_MASK;
    }

    private static int replaceMask(
            int encoded, ShotType type, int mask) {
        int shiftedMask = CHAMBER_MASK << type.shift();
        return encoded & ~shiftedMask
                | (mask & CHAMBER_MASK) << type.shift();
    }

    private static void setEncoded(
            ItemStack stack, int encoded) {
        int normalized = encoded & MAX_ENCODED;
        if (normalized == 0) {
            stack.remove(
                    SexPistolsDataComponents
                            .REVOLVER_SPECIAL_CHAMBERS.get());
        }
        else {
            stack.set(
                    SexPistolsDataComponents
                            .REVOLVER_SPECIAL_CHAMBERS.get(),
                    normalized);
        }
    }

    public enum ShotType {
        NONE(-1, "none"),
        ENCIRCLEMENT(
                ENCIRCLEMENT_SHIFT,
                "sex_pistols_encirclement"),
        PIERCING(
                PIERCING_SHIFT,
                "sex_pistols_piercing_shot"),
        SPLITTING(
                SPLITTING_SHIFT,
                "sex_pistols_splitting_shot");

        private final int shift;
        private final String spriteName;

        ShotType(int shift, String spriteName) {
            this.shift = shift;
            this.spriteName = spriteName;
        }

        public int shift() {
            return shift;
        }

        public String spriteName() {
            return spriteName;
        }

        public static ShotType byId(int id) {
            return switch (id) {
                case 1 -> ENCIRCLEMENT;
                case 2 -> PIERCING;
                case 3 -> SPLITTING;
                default -> NONE;
            };
        }
    }
}
