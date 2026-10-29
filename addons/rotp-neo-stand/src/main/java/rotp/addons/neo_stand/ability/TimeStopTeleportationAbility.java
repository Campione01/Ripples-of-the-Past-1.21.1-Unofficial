package rotp.addons.neo_stand.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.event.ModEventHooks;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TimeStopTeleportationAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 500.0F;
    public static final double HISTORY_DISTANCE = 20.0D;
    private static final int TRANSPORT_TICKS = 9;
    private static final Map<UUID, TeleportAction> ACTIVE = new HashMap<>();

    public TimeStopTeleportationAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, TeleportAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 9);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 15);
        cooldown(300, 600);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.MAIN_BODY);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || context.getUser() == null) {
            return base;
        }
        if (!context.getUser().level().isClientSide()) {
            LivingEntity user = context.getUser();
            ActionTarget target = targetInSight(user);
            if (!(target.getMainEntity() instanceof LivingEntity living)) {
                return ConditionCheck.NEGATIVE;
            }
            // 1.16 checkTarget after the override: Action.checkRangeAndTarget
            // (getMaxRangeSqEntityTarget 400 from the performer, a quarter
            // without a line of sight) and checkStandTarget (canAttack)
            if (!ActionTargetRange.isEntityWithinRange(
                    ActionTargetRange.standPerformer(user), living,
                    HISTORY_DISTANCE * HISTORY_DISTANCE)) {
                return ConditionCheck.createNegative("target_too_far");
            }
            StandPower power = StandPower.get(user);
            StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
            boolean standMayAttack = stand != null
                    ? stand.canAttack(living)
                    : userMayAttack(user, living);
            if (!standMayAttack
                    || MovementHistory.positionAgo(living, HISTORY_DISTANCE) == null) {
                return ConditionCheck.NEGATIVE;
            }
        }
        return base;
    }

    /*
     * StandEntity.canAttack asked of the user, for a press made while the
     * Stand is away: 1.16 StandEntityAction.onClick summoned it before
     * checkStandTarget asked its canAttack, which reads only its user.
     */
    private static boolean userMayAttack(
            LivingEntity user, LivingEntity target) {
        if (target == user || !DamageUtil.isNotFriendlyFire(
                user, StandUtil.getStandUser(target))) {
            return false;
        }
        return !(target instanceof Animal)
                || !target.isPassengerOfSameVehicle(user)
                        && !(target instanceof TamableAnimal tamed
                                && tamed.getOwner() == user);
    }

    // 1.16 ag.overrideVanillaMouseTarget (:109-116) replaced every press's
    // target, on both sides, with the first living entity its precision ray
    // from the user met within 20 blocks
    @Override
    public ActionTarget overridePressedTarget(LivingEntity user, ActionTarget pressed) {
        return targetInSight(user);
    }

    private static ActionTarget targetInSight(LivingEntity user) {
        StandPower power = StandPower.get(user);
        Entity ownStand = power != null ? power.getSummonedStandEntity() : null;
        var targets = HitResultUtil.clipMultipleEntities(user, HISTORY_DISTANCE,
                entity -> entity instanceof LivingEntity && entity != user && entity != ownStand,
                0.0D, 8.0D);
        return targets.isEmpty() ? ActionTarget.EMPTY : targets.getFirst();
    }

    // 1.16 ag.getTargetRequirement: ENTITY
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.ENTITY;
    }

    // StandEntityAbility.keepsTaskTarget with 1.16 ag.getMaxRangeSqEntityTarget
    // (400): kept to 20 blocks, 10 without a line of sight, instead of 8.
    @Override
    public boolean keepsTaskTarget(
            StandEntity standEntity, @Nullable ActionTarget target) {
        Level level = standEntity.level();
        ActionTarget kept = target != null
                ? target.resolveEntityId(level) : ActionTarget.EMPTY;
        return !kept.isEmpty(level)
                && keepsTaskTargetType(standEntity, kept)
                && kept.getEntity() instanceof LivingEntity living
                && living != standEntity
                && living.deathTime < 20
                && standEntity.canAttack(living)
                && ActionTargetRange.isEntityWithinRange(
                        standEntity, living,
                        HISTORY_DISTANCE * HISTORY_DISTANCE);
    }

    public static boolean protects(Entity entity) {
        return ACTIVE.values().stream().anyMatch(action ->
                !action.closed && (action.user == entity || action.stand == entity));
    }

    public static void cancelFor(Entity entity) {
        for (TeleportAction action : new ArrayList<>(ACTIVE.values())) {
            if (action.user == entity || action.stand == entity || action.target == entity) {
                action.finish(true);
            }
        }
    }

    public static void timeStopRemoved(TimeStopLifecycleEvent.Removed event) {
        for (TeleportAction action : new ArrayList<>(ACTIVE.values())) {
            if (action.world == event.getLevel()
                    && action.timeStopId == event.getInstance().id()) {
                if (action.userLost()) {
                    action.finish(false);
                } else {
                    // 1.16 ag.standTickPerform does not depend on the time stop:
                    // a stop ended early (no stamina) still lets the transport move.
                    action.detachTimeStop();
                }
            }
        }
    }

    public static final class TeleportAction
            extends EntityActionInstance {
        private Vec3 userStart;
        private Vec3 targetPast;
        private UUID targetId;
        private boolean moved;
        private ServerLevel world;
        private LivingEntity user;
        private LivingEntity stand;
        private LivingEntity target;
        private int timeStopId = -1;
        private boolean userWasInvulnerable;
        private boolean standWasInvulnerable;
        private boolean immunityInstalled;
        private boolean closed;
        private EffectLease userEffect;
        private EffectLease standEffect;
        private EffectLease targetEffect;

        public TeleportAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            LivingEntity user = getPowerUser();
            if (user != null) {
                // the server casts the donor's ray itself, with no synced aim
                // needed, and drops a target 1.16 checkStandTarget refused
                ActionTarget pressed = user.level().isClientSide()
                        ? captureActionTargetFromAim(user)
                        : setActionTargetSnapshot(takenOnPress(targetInSight(user)));
                // 1.16 turned the Stand to the target its task kept
                if (!user.level().isClientSide()
                        && performer instanceof StandEntity stand
                        && ability instanceof TimeStopTeleportationAbility
                                teleportation
                        && teleportation.keepsTaskTarget(stand, pressed)) {
                    keepStandAimedAtTarget(pressed);
                }
            }
        }

        private ActionTarget takenOnPress(ActionTarget target) {
            return performer instanceof StandEntity stand
                    && ability instanceof TimeStopTeleportationAbility teleportation
                    && !teleportation.takesPressedTarget(stand, target)
                    ? ActionTarget.EMPTY : target;
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            ActionTarget target =
                    getActionTargetSnapshot(serverLevel);
            LivingEntity livingTarget =
                    target.getMainEntity() instanceof LivingEntity living
                            ? living
                            : null;
            if (user == null || livingTarget == null || power == null) {
                startRecovery();
                return;
            }
            Vec3 historical = MovementHistory.positionAgo(
                    livingTarget, HISTORY_DISTANCE);
            if (historical == null) {
                startRecovery();
                return;
            }
            TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
            if (state.getInstance(user.getId()).isPresent()) {
                startRecovery();
                return;
            }
            TimeStopState.Instance instance = new TimeStopState.Instance(
                    user.getId(), TRANSPORT_TICKS, TRANSPORT_TICKS,
                    new ChunkPos(user.blockPosition()), 1, user.getId(),
                    "the_world_time_stop", -1, -1,
                    TimeStopLearning.getTimeStopStaminaCostTick(power)).withStandSkin(power);
            TimeStopLifecycleEvent.PreStart event =
                    ModEventHooks.onTimeStopPreStart(serverLevel, instance);
            if (event.isCanceled()) {
                startRecovery();
                return;
            }
            this.world = serverLevel;
            this.user = user;
            this.stand = performer instanceof LivingEntity living ? living : null;
            this.target = livingTarget;
            userStart = user.position();
            targetPast = historical;
            targetId = livingTarget.getUUID();
            timeStopId = event.getInstance().id();
            int effectTicks = event.getInstance().totalTicks() + 1;
            userEffect = EffectLease.install(user, effectTicks, true);
            if (stand != null) {
                standEffect = EffectLease.install(stand, effectTicks, true);
            }
            // The donor lets the selected mob update while its surroundings stop.
            if (livingTarget instanceof Mob) {
                targetEffect = EffectLease.install(livingTarget, effectTicks, false);
            }
            userWasInvulnerable = user.isInvulnerable();
            standWasInvulnerable = stand != null && stand.isInvulnerable();
            user.setInvulnerable(true);
            if (stand != null) {
                stand.setInvulnerable(true);
            }
            immunityInstalled = true;
            ACTIVE.put(user.getUUID(), this);
            // The donor's instance belongs to the core Time Stop, including its settlement.
            if (!state.commitPreStart(event)) {
                finish(false);
                startRecovery();
                return;
            }
            if (closed) {
                startRecovery();
                return;
            }
            teleport(user, livingTarget.position());
            playBlink(serverLevel, livingTarget.position());
            // 1.16 StandEntityTask: the cost is paid after the blink; a short
            // bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }

        @Override
        public void actionTick() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || userStart == null
                    || targetPast == null
                    || targetId == null || closed) {
                return;
            }
            LivingEntity user = getPowerUser();
            LivingEntity target = serverLevel.getEntity(targetId)
                    instanceof LivingEntity living ? living : null;
            if (user == null || target == null || !user.isAlive() || !target.isAlive()) {
                finish(true);
                startRecovery();
                return;
            }
            if (!moved && getPhaseTick() >= 4) {
                teleport(user, targetPast);
                teleport(target, targetPast);
                serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get())
                        .updateGrabbedEntityPosition(target);
                playBlink(serverLevel, targetPast);
                moved = true;
            }
            if (getPhaseTick() >= 8) {
                finish(true);
            }
        }

        @Override
        public void actionPerformEnd() {
            finish(true);
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            finish(true);
        }

        private boolean userLost() {
            return !user.isAlive() || user.isRemoved() || user.level() != world;
        }

        private void detachTimeStop() {
            timeStopId = -1;
            if (userEffect != null) userEffect.clear(true);
            if (standEffect != null) standEffect.clear(false);
            if (targetEffect != null) targetEffect.clear(false);
            userEffect = null;
            standEffect = null;
            targetEffect = null;
        }

        private void finish(boolean removeTimeStop) {
            if (closed || world == null) {
                return;
            }
            closed = true;
            ACTIVE.remove(user.getUUID(), this);
            if (userStart != null && user.isAlive() && !user.isRemoved() && user.level() == world) {
                teleport(user, userStart);
                playBlink(world, userStart);
            }
            if (immunityInstalled) {
                user.setInvulnerable(userWasInvulnerable);
                if (stand != null) {
                    stand.setInvulnerable(standWasInvulnerable);
                }
            }
            if (removeTimeStop && timeStopId >= 0) {
                world.getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(timeStopId);
            }
            if (userEffect != null) userEffect.clear(true);
            if (standEffect != null) standEffect.clear(false);
            if (targetEffect != null) targetEffect.clear(false);
            userStart = null;
        }

        private static void teleport(LivingEntity entity, Vec3 position) {
            entity.teleportTo(position.x, position.y, position.z);
            entity.setDeltaMovement(Vec3.ZERO);
        }

        private static void playBlink(
                ServerLevel level, Vec3 position) {
            level.playSound(
                    null,
                    position.x,
                    position.y,
                    position.z,
                    ModSoundEvents.THE_WORLD_TIME_STOP_BLINK.get(),
                    SoundSource.PLAYERS,
                    5.0F,
                    1.0F);
        }
    }

    private record EffectLease(LivingEntity entity, MobEffectInstance installed,
            MobEffectInstance displaced, long installedAt, int duration, boolean showIcon) {
        private static EffectLease install(LivingEntity entity, int ticks, boolean showIcon) {
            MobEffectInstance previous = entity.getEffect(ModStatusEffects.TIME_STOP);
            if (previous != null && (previous.getDuration() < 0 || previous.getDuration() >= ticks)) {
                return new EffectLease(entity, null, new MobEffectInstance(previous),
                        entity.level().getGameTime(), ticks, showIcon);
            }
            MobEffectInstance displaced = previous != null ? new MobEffectInstance(previous) : null;
            if (previous != null) entity.removeEffect(ModStatusEffects.TIME_STOP);
            entity.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP,
                    ticks, 0, showIcon, false, showIcon));
            return new EffectLease(entity, entity.getEffect(ModStatusEffects.TIME_STOP),
                    displaced, entity.level().getGameTime(), ticks, showIcon);
        }

        private void clear(boolean coreMayHaveRemovedEffect) {
            MobEffectInstance current = entity.getEffect(ModStatusEffects.TIME_STOP);
            if (current != null && (installed == null || current != installed
                    || current.getDuration() > duration || current.getAmplifier() != 0
                    || current.isAmbient() != showIcon || current.isVisible()
                    || current.showIcon() != showIcon)) {
                return;
            }
            if (current == null && !coreMayHaveRemovedEffect) {
                return;
            }
            if (current != null) entity.removeEffect(ModStatusEffects.TIME_STOP);
            if (displaced != null) {
                int left = displaced.getDuration() < 0 ? displaced.getDuration()
                        : displaced.getDuration() - (int) (entity.level().getGameTime() - installedAt);
                if (left != 0 && (left > 0 || displaced.getDuration() < 0)) {
                    entity.addEffect(new MobEffectInstance(displaced.getEffect(), left,
                            displaced.getAmplifier(), displaced.isAmbient(),
                            displaced.isVisible(), displaced.showIcon()));
                }
            }
        }
    }
}
