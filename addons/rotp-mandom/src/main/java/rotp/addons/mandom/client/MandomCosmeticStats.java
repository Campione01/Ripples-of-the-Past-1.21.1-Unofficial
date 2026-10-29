package rotp.addons.mandom.client;

import rotp.core.client.ui.StandStatsRenderer.CosmeticStandStats;
import rotp.core.client.ui.StandStatsRenderer.HexagonStandStat;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;

public final class MandomCosmeticStats
        extends CosmeticStandStats {
    @Override
    public float statConvertedValue(
            HexagonStandStat stat,
            StandPower standData,
            StandStats stats,
            float statLeveling) {
        return switch (stat) {
            case RANGE -> 0.0F;
            case DEV_POTENTIAL -> 3.0F;
            default -> super.statConvertedValue(
                    stat, standData, stats, statLeveling);
        };
    }
}
