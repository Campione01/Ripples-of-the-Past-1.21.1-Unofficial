package rotp.core.compat.v1_21_4.missingmethods;

import net.minecraft.util.FastColor;

public class ARGB {

    public static int white(float alpha) {
        return FastColor.as8BitChannel(alpha) << 24 | 0xFFFFFF;
    }

    public static int color(float alpha, int color) {
        return FastColor.as8BitChannel(alpha) << 24 | (color & 0xFFFFFF);
    }
}
