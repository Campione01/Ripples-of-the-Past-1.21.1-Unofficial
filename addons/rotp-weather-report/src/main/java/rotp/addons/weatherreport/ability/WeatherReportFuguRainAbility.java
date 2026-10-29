package rotp.addons.weatherreport.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.entity.WeatherReportFugaSummonerEntity;
import rotp.addons.weatherreport.entity.WeatherReportStandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class WeatherReportFuguRainAbility
        extends StandEntityAbility {
    public static final int CHARGE_TICKS = 30;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 0;
    public static final int COOLDOWN_TICKS = 720;
    public static final int RESOLVE_LEVEL = 3;
    public static final double RANGE = 25.0D;
    public static final double SUMMON_HEIGHT = 45.0D;
    private static final double HIT_SURFACE_OFFSET = 1.0E-4D;

    private static final ActionAnimIdentifier CHANGE_WEATHER_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    WeatherReportAddon.CHANGE_WEATHER_NAME, false);

    public WeatherReportFuguRainAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, FuguRainAction::new);
        // 1.16 SHIFT variation of lightning: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, CHARGE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context.getUser();
        StandEntity stand = context instanceof StandPower standPower
                ? standPower.getSummonedStandEntity()
                : null;
        return user == null ? ConditionCheck.NEGATIVE
                : checkWeatherAndSky(user.level(), targetPosition(user, stand, false));
    }

    // 1.16 PowerBaseImpl.tickHeldAction reran checkStandConditions on every
    // held tick: an aim that loses the sky, or the rain stopping, ends the
    // charge with its message.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        LivingEntity user = context.getUser();
        StandEntity stand = context instanceof StandPower standPower
                ? standPower.getSummonedStandEntity()
                : null;
        return user == null ? ConditionCheck.NEGATIVE
                : checkWeatherAndSky(user.level(), targetPosition(user, stand, false));
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return CHANGE_WEATHER_ANIM;
    }

    // The weather flag, as 1.16 getLevelData().isRaining(): Level.isRaining() is the rain level, which fades for
    // about 80 ticks after the rain has stopped and rises for 20 ticks after it starts.
    private static ConditionCheck checkWeatherAndSky(
            Level level, Vec3 target) {
        if (!level.canSeeSky(BlockPos.containing(target))) {
            return ConditionCheck.createNegative(
                    "fugurain-warningneedtoseesky");
        }
        if (!level.getLevelData().isRaining()) {
            return ConditionCheck.createNegative(
                    "fugurain-warningrain");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 stand.aimWithStandOrUser(25, target).getLocation(), the target
    // being always empty for this action: a Stand that follows its user
    // turns toward what the user's precision ray meets, then its own
    // precision ray from its eyes rules (entities before blocks, blocks by
    // their outline). With no Stand out, the user's ray.
    private static Vec3 targetPosition(
            LivingEntity user, StandEntity ownStand, boolean turnStand) {
        WeatherReportAim.Hit hit = ownStand != null
                ? WeatherReportAim.standAim(
                        ownStand, user, RANGE, turnStand)
                : WeatherReportAim.precisionTrace(
                        user, null, user, RANGE);
        BlockHitResult block = hit.block();
        if (block == null) {
            return hit.location();
        }
        // Nudged out of the surface so that rounding cannot place it in the block.
        return block.getLocation().add(
                block.getDirection().getStepX() * HIT_SURFACE_OFFSET,
                block.getDirection().getStepY() * HIT_SURFACE_OFFSET,
                block.getDirection().getStepZ() * HIT_SURFACE_OFFSET);
    }

    public static final class FuguRainAction
            extends EntityActionInstance {
        public FuguRainAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!level().isClientSide()
                    && getPerformer() instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        WeatherReportAddon
                                .WEATHER_REPORT_FUGU_CAST,
                        1.0F,
                        1.0F);
                LivingEntity user = getPowerUser();
                if (user != null) {
                    sayShoutOf(
                            ability,
                            user,
                            WeatherReportAddon
                                    .WEATHER_REPORT_USER_FUGU_RAIN);
                }
            }
        }

        // 1.16 checkStandConditions turned Weather Report toward the aim on every held tick on the server and on the
        // caster's client, after the Stand's tick had set it back to its user's rotation; the port turns it on every
        // side (owner question A6d-Q1). The perform tick aims once on its own.
        @Override
        public void actionTick() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE
                    && getPerformer() instanceof WeatherReportStandEntity stand) {
                LivingEntity user = getPowerUser();
                if (user != null) {
                    WeatherReportAim.standAim(stand, user, RANGE, true);
                }
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPerformer()
                            instanceof WeatherReportStandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            // One aim, as 1.16 standPerform: a second one would turn the
            // Stand again from its turned rotation.
            Vec3 target = user != null
                    ? targetPosition(user, stand, true) : null;
            ConditionCheck check = target != null
                    ? checkWeatherAndSky(serverLevel, target)
                    : ConditionCheck.NEGATIVE;
            if (!check.isPositive()) {
                ConditionCheck.sendActionFailedMessage(
                        null, check, user);
                stopAction();
                return;
            }

            WeatherReportFugaSummonerEntity summoner =
                    new WeatherReportFugaSummonerEntity(
                            user, stand, serverLevel);
            summoner.moveTo(
                    target.x,
                    target.y + SUMMON_HEIGHT,
                    target.z);
            if (serverLevel.addFreshEntity(summoner)) {
                stand.activateFugaSummoner(summoner);
                StandPower standPower = StandPower.get(user);
                if (ability
                        instanceof WeatherReportFuguRainAbility fuguRain) {
                    fuguRain.setCooldownOnUse(standPower);
                }
            }
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
