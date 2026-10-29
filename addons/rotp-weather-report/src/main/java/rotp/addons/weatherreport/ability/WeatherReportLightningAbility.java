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
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WeatherReportLightningAbility
        extends StandEntityAbility {
    public static final int CHARGE_TICKS = 40;
    public static final int PERFORM_TICKS = 1;
    public static final float STAMINA_COST = 75.0F;
    public static final int COOLDOWN_TICKS = 60;
    public static final int RESOLVE_LEVEL = 2;
    public static final double RANGE = 100.0D;
    public static final float CLEAR_DAMAGE = 7.0F;
    public static final float RAIN_DAMAGE = 10.0F;
    public static final float THUNDER_DAMAGE = 15.0F;

    private static final ActionAnimIdentifier RANGED_ATTACK_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "ranged_attack", false);

    public WeatherReportLightningAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, LightningAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, CHARGE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return RANGED_ATTACK_ANIM;
    }

    // 1.16 WeatherReportLightning.standKeepsTarget: a living entity
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.ENTITY
                && target.getEntity() instanceof LivingEntity;
    }

    public static final class LightningAction
            extends EntityActionInstance {
        public LightningAction(EntityActionType ability) {
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
                                .WEATHER_REPORT_LIGHTNING_CAST,
                        1.0F,
                        1.0F);
                LivingEntity user = getPowerUser();
                if (user != null) {
                    sayShoutOf(
                            ability,
                            user,
                            WeatherReportAddon
                                    .WEATHER_REPORT_USER_LIGHTNING);
                }
            }
        }

        // 1.16 StandEntityAction.overrideVanillaMouseTarget: a pressed block
        // without a collision shape gives way to what the Stand's ray meets
        @Override
        protected ActionTarget pressedTaskTarget() {
            ActionTarget pressed = WeatherReportAim.pressTarget(getPerformer());
            LivingEntity user = getPowerUser();
            return user != null
                    && ability instanceof StandEntityAbility lightning
                            ? lightning.overridePressedTarget(user, pressed)
                            : pressed;
        }

        @Override
        public void actionTick() {
            if (!level().isClientSide()
                    || getPhase() != ActionPhase.BUTTON_CHARGE) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandEntity stand = getPerformer()
                    instanceof StandEntity performerStand
                            ? performerStand
                            : null;
            if (user != null) {
                Vec3 target = targetPosition(user, stand);
                level().addParticle(
                        ParticleTypes.ELECTRIC_SPARK,
                        target.x,
                        target.y,
                        target.z,
                        0.0D,
                        0.0D,
                        0.0D);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower standPower =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                stopAction();
                return;
            }
            // 1.16 StandEntityTask: the cost is paid with the result ignored;
            // a short bar just drops to 0.
            StandAbilityStamina.consume(ability, standPower, STAMINA_COST, false);
            if (ability
                    instanceof WeatherReportLightningAbility lightning) {
                lightning.setCooldownOnUse(standPower);
            }

            StandEntity stand = getPerformer()
                    instanceof StandEntity performerStand
                            ? performerStand
                            : null;
            Vec3 target = targetPosition(user, stand);
            LightningBolt bolt =
                    EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt != null) {
                bolt.setPos(target.x, target.y, target.z);
                bolt.setDamage(weatherDamage(serverLevel));
                serverLevel.addFreshEntity(bolt);
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

    private static float weatherDamage(Level level) {
        if (level.isThundering()) {
            return THUNDER_DAMAGE;
        }
        return level.isRaining() ? RAIN_DAMAGE : CLEAR_DAMAGE;
    }

    // 1.16 JojoModUtil.rayTrace(user, 100, entity -> entity != standEntity)
    private static Vec3 targetPosition(
            LivingEntity user, StandEntity ownStand) {
        return WeatherReportAim.hitLocation(
                user, RANGE, entity -> entity != ownStand);
    }
}
