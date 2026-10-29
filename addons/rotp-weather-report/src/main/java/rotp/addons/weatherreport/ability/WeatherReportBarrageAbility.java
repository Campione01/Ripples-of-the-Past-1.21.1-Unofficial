package rotp.addons.weatherreport.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.sounds.SoundEvent;

public final class WeatherReportBarrageAbility
        extends StandEntityBarrageAbility {
    public WeatherReportBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, WeatherReportBarrage::new);
    }

    public static final class WeatherReportBarrage
            extends StandEntityBarrage {
        public WeatherReportBarrage(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected SoundEvent getBarrageSwingSound() {
            return WeatherReportAddon.WEATHER_REPORT_SWING.get();
        }
    }
}
