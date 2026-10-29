package rotp.addons.weatherreport.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.network.WeatherSelectionSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class ChangeWeatherAbility extends StandEntityAbility {
    public static final int CHARGE_TICKS = 30;
    public static final float STAMINA_COST = 75.0F;
    public static final int COOLDOWN_TICKS = 1200;
    public static final int RESOLVE_LEVEL = 3;
    private static final ActionAnimIdentifier CHANGE_WEATHER_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    WeatherReportAddon.CHANGE_WEATHER_NAME, false);

    public ChangeWeatherAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ChangeWeatherAction::new);
        setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, CHARGE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return CHANGE_WEATHER_ANIM;
    }

    public static final class ChangeWeatherAction
            extends EntityActionInstance {
        public ChangeWeatherAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!level().isClientSide()
                    && getPerformer() instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        WeatherReportAddon
                                .WEATHER_REPORT_CHANGE_WEATHER_SOUND,
                        1.0F,
                        1.0F);
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower standPower =
                    user != null ? StandPower.get(user) : null;
            if (!(user instanceof ServerPlayer serverPlayer)) {
                stopAction();
                return;
            }
            // 1.16 StandEntityTask: the cost is paid with the result ignored;
            // a short bar just drops to 0.
            StandAbilityStamina.consume(ability, standPower, STAMINA_COST, false);
            if (ability instanceof ChangeWeatherAbility changeWeather) {
                changeWeather.setCooldownOnUse(standPower);
            }
            WeatherSelectionSessions.open(serverPlayer);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                stopAction();
            }
        }

        private void stopAction() {
            forceStop();
            if (!level().isClientSide()) {
                syncPhaseChanges();
            }
        }
    }
}
