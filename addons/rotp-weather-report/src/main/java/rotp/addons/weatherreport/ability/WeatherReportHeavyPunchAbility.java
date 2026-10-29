package rotp.addons.weatherreport.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class WeatherReportHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    private Holder<SoundEvent> userShoutSound;

    public WeatherReportHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 SHIFT variation of the punch: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
    }

    public WeatherReportHeavyPunchAbility userShoutSound(
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
}
