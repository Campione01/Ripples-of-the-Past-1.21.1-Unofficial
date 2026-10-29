package rotp.addons.weatherreport.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

public final class WeatherReportColdWindAbility
        extends WeatherReportWindAbility {
    public static final double RANGE = 16.0D;
    public static final int MAX_HOLD_TICKS = 80;
    public static final float STAMINA_COST_TICK = 3.0F;
    public static final int COOLDOWN_TICKS = 120;
    public static final int RESOLVE_LEVEL = 1;
    public static final int FREEZE_DURATION_TICKS = 200;
    public static final int REMAINING_FIRE_TICKS = 2;
    public static final float BLAZE_DAMAGE = 1.0F;

    public WeatherReportColdWindAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                RANGE,
                MAX_HOLD_TICKS,
                STAMINA_COST_TICK,
                COOLDOWN_TICKS,
                true);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }
}
