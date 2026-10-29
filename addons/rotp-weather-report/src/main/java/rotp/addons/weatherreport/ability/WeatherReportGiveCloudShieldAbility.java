package rotp.addons.weatherreport.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WeatherReportGiveCloudShieldAbility
        extends EntityActionAbility {
    public static final int EFFECT_DURATION_TICKS = 1200;
    public static final float STAMINA_COST = 400.0F;
    public static final int COOLDOWN_TICKS = 1300;
    public static final double TARGET_RANGE = 8.0D;

    public WeatherReportGiveCloudShieldAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, GiveCloudShieldAction::new);
        // 1.16 SHIFT variation of cloud shield: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        cooldown(COOLDOWN_TICKS);
    }

    // 1.16 weather_report_give_cloud_shield (042 InitStands :79) was a
    // StandAction its user's power performed, the Stand out or not
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 StandAction.getPerformer :191-193: the summoned Stand, else the
    // user, whose stun PowerBaseImpl.checkRequirements :264 checked
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        return stand != null ? stand : user;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (level.isClientSide()) {
            return null;
        }
        return setOrBufferAction(level, user, getPerformer(user),
                inputMethod, extraClientInput, clickHoldResolveTime,
                bufferingState);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return null;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        LookTarget look = findLookTarget(user, user.level());
        if (look.tooFar()) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (look.entity() == null) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    record LookTarget(@Nullable LivingEntity entity, boolean tooFar) {
        static final LookTarget NONE = new LookTarget(null, false);
        static final LookTarget TOO_FAR = new LookTarget(null, true);
    }

    static LookTarget findLookTarget(
            LivingEntity user, Level level) {
        ActionTarget syncedTarget = getSyncedLookTarget(user);
        if (!syncedTarget.isEmpty(level)) {
            if (syncedTarget.getType()
                    == ActionTarget.TargetType.ENTITY) {
                return admit(user, syncedTarget.getMainEntity());
            }
            if (HitResultUtil.isTargetWithinRange(
                    syncedTarget,
                    user,
                    level,
                    TARGET_RANGE,
                    TARGET_RANGE)) {
                return LookTarget.NONE;
            }
        }

        ActionTarget clipped = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                TARGET_RANGE,
                TARGET_RANGE,
                level,
                entity -> entity instanceof LivingEntity
                        && entity != user,
                user,
                0.0D,
                ClipContext.Block.OUTLINE);
        return admit(user, clipped.getMainEntity());
    }

    private static LookTarget admit(
            LivingEntity user, @Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)
                || living == user) {
            return LookTarget.NONE;
        }
        return isWithinDonorReach(reachPerformer(user), living)
                ? new LookTarget(living, false)
                : LookTarget.TOO_FAR;
    }

    // 1.16 StandAction.getPerformer: the summoned Stand, else the user.
    private static LivingEntity reachPerformer(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        return stand != null && stand.isAlive() ? stand : user;
    }

    // 1.16 Action.checkRangeAndTarget with JojoModUtil.getDistance:
    // 8 blocks, 4 when the performer cannot see the target.
    private static boolean isWithinDonorReach(
            LivingEntity performer, Entity target) {
        Vec3 eye = performer.getEyePosition();
        AABB box = target.getBoundingBox();
        double distance = 0.0D;
        if (!box.contains(eye)) {
            double eyeFraction = performer.getBbHeight() == 0.0F
                    ? 0.0D
                    : performer.getEyeHeight() / performer.getBbHeight();
            Vec3 point = new Vec3(
                    Mth.lerp(0.5D, box.minX, box.maxX),
                    Mth.lerp(eyeFraction, box.minY, box.maxY),
                    Mth.lerp(0.5D, box.minZ, box.maxZ));
            distance = box.clip(eye, point)
                    .map(hit -> eye.distanceTo(hit)
                            - performer.getBbWidth() / 2.0D)
                    .orElse(-1.0D);
        }
        double rangeSq = TARGET_RANGE * TARGET_RANGE;
        if (!performer.hasLineOfSight(target)) {
            rangeSq /= 4.0D;
        }
        return distance * distance <= rangeSq;
    }

    // The press check also runs on the user's client (the HUD, the client's
    // own press check), whose copy of the user holds no stored aim (1.16:
    // the mouse target).
    private static ActionTarget getSyncedLookTarget(LivingEntity user) {
        return LivingComponentAction.getAimTarget(user);
    }

    public static final class GiveCloudShieldAction
            extends EntityActionInstance {
        public GiveCloudShieldAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            LivingEntity user = getPowerUser();
            if (user != null) {
                if (!level().isClientSide()) {
                    // 1.16 performed at once on the target the press admitted.
                    LivingEntity admitted =
                            findLookTarget(user, level()).entity();
                    setActionTargetSnapshot(admitted != null
                            ? new ActionTarget(admitted)
                            : null);
                    // 1.16 give_cloud_shield .shout(WEATHER_REPORT)
                    sayShoutOf(ability, user,
                            WeatherReportAddon.WEATHER_REPORT_CALL);
                }
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }

            LivingEntity target = targetFromSnapshot(user);
            if (target == null) {
                target = findLookTarget(user, level()).entity();
            }
            if (target == null) {
                return;
            }
            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored; a short bar just drops to 0.
            StandPower standPower = StandPower.get(user);
            StandAbilityStamina.consume(ability, standPower, STAMINA_COST, false);

            target.addEffect(new MobEffectInstance(
                    WeatherReportAddon.CLOUD_SHIELD_EFFECT,
                    EFFECT_DURATION_TICKS,
                    0,
                    false,
                    false,
                    true));
            // The call line is the onActionSet shout only (1.16 plays it once).
        }

        private LivingEntity targetFromSnapshot(
                LivingEntity user) {
            ActionTarget target = getActionTargetSnapshot(level());
            if (target.isEmpty(level())) {
                return null;
            }
            Entity entity = target.getMainEntity();
            return entity instanceof LivingEntity living
                    && living != user
                    ? living
                    : null;
        }
    }
}
