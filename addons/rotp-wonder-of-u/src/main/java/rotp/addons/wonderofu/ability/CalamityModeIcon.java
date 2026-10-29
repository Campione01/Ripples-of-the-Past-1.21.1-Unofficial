package rotp.addons.wonderofu.ability;

import java.util.function.Predicate;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import net.minecraft.util.FastColor;

public final class CalamityModeIcon {
    private static final int ACTIVE_ICON_TINT = 0xFF55FF55;

    private CalamityModeIcon() {}

    // Multiplied, not replaced: the colour passed in carries the HUD's
    // unusable tint and fade, which the core's own icons keep.
    public static int color(
            Power<?> context,
            int color,
            Predicate<WonderOfUStandEntity> modeEnabled) {
        if (context instanceof StandPower standPower
                && standPower.getSummonedStandEntity()
                        instanceof WonderOfUStandEntity stand
                && modeEnabled.test(stand)) {
            return FastColor.ARGB32.multiply(color, ACTIVE_ICON_TINT);
        }
        return color;
    }
}
