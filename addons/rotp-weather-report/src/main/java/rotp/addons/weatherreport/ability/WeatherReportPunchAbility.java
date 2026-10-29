package rotp.addons.weatherreport.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class WeatherReportPunchAbility
        extends StandEntityPunchAbility {
    private Holder<SoundEvent> userShoutSound;

    public WeatherReportPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, WeatherReportPunch::new);
    }

    public WeatherReportPunchAbility userShoutSound(
            Holder<SoundEvent> sound) {
        userShoutSound = sound;
        return this;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide()
                && standUser != null
                && userShoutSound != null) {
            sayShout(standUser, userShoutSound);
        }
    }

    public static final class WeatherReportPunch
            extends StandEntityPunch {
        public WeatherReportPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected SoundEvent getPunchSwingSound(StandEntity stand) {
            return WeatherReportAddon.WEATHER_REPORT_SWING.get();
        }
    }
}
