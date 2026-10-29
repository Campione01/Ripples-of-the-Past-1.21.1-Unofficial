package rotp.addons.weatherreport.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.entity.WeatherReportIcicleEntity;
import rotp.addons.weatherreport.entity.WeatherReportStandEntity;
import net.minecraft.world.entity.LivingEntity;

public final class WeatherReportIcicleStrikeAbility
        extends StandEntityAbility {
    public static final int PERFORM_TICKS = 20;
    public static final float STAMINA_COST = 375.0F;
    public static final int TECHNICAL_COOLDOWN_TICKS = 10;
    public static final int ADDITIONAL_COOLDOWN_TICKS = 100;
    public static final int RESOLVE_LEVEL = 2;

    private static final ActionAnimIdentifier RANGED_ATTACK_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "ranged_attack", false);

    public WeatherReportIcicleStrikeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, IcicleStrikeAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.ARMS);
        cooldown(
                TECHNICAL_COOLDOWN_TICKS,
                ADDITIONAL_COOLDOWN_TICKS);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return RANGED_ATTACK_ANIM;
    }

    public static final class IcicleStrikeAction
            extends EntityActionInstance {
        public IcicleStrikeAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            // 1.16 standOffsetFromUser(0.2, 0.5) after standOffsetFront(),
            // turned by the view yaw
            setStandOffset(
                    0.2D,
                    0.5D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof WeatherReportStandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        WeatherReportAddon.WEATHER_REPORT_SWING,
                        1.0F,
                        1.0F);
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof WeatherReportStandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }

            WeatherReportIcicleEntity icicle =
                    new WeatherReportIcicleEntity(stand, level());
            icicle.setShootingPosOf(stand);
            addProjectileWithStandStats(icicle);
            if (icicle.isAddedToLevel()) {
                stand.activateIcicle(icicle);
            }
            // 1.16 StandEntityTask: the cost is paid after the perform with
            // the result ignored; a short bar just drops to 0.
            StandPower standPower = StandPower.get(user);
            StandAbilityStamina.consume(ability, standPower, STAMINA_COST, false);
        }

        @Override
        public void actionPerformEnd() {
            beginRetraction();
        }

        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            beginRetraction();
        }

        private void beginRetraction() {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof WeatherReportStandEntity stand) {
                WeatherReportIcicleEntity icicle =
                        stand.getActiveIcicle();
                if (icicle != null) {
                    icicle.beginRetracting();
                }
            }
        }
    }
}
