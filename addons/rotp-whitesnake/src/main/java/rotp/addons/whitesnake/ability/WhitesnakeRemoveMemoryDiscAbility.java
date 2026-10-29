package rotp.addons.whitesnake.ability;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disc.MemoryDiscItem;
import rotp.addons.whitesnake.disc.MemoryDiscItem.HeldDisc;
import rotp.addons.whitesnake.memory.MemoryDiscService;
import rotp.addons.whitesnake.memory.MemoryDiscService.Result;
import rotp.addons.whitesnake.memory.MemoryDiscService.Status;
import rotp.addons.whitesnake.memory.MemoryLossState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WhitesnakeRemoveMemoryDiscAbility
        extends StandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 30;
    public static final int COOLDOWN_TICKS = 30;
    public static final float STAMINA_COST = 200.0F;
    public static final double TARGET_RANGE = 20.0D;
    public static final String STAND_DISC_REMOVAL_ABILITY =
            "whitesnake_remove_stand_disc";

    public WhitesnakeRemoveMemoryDiscAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, RemoveMemoryDiscAction::new);
        partsRequired(StandPart.ARMS);
        // 1.16 holdToFire(30) with no standRecoveryTicks: the task ended
        // with its perform
        setDefaultPhaseLength(
                ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
        // 1.16 registered no cooldown for either disc removal (its technical part was 0): a success set both
        // 30-tick timers directly with setCooldownTimer, so neither Resolve nor Creative shortened them.
        // 1.16 cancelHeldOnGettingAttacked: a hit during the hold stops it.
        setCancelHeldOnGettingAttacked();
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return HOLD_TO_FIRE_TICKS;
    }

    // The hold charges in the windup, as 1.16 held it before it fired.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until the held guard or barrage is let go.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    // 1.16 stopHeldAction(false): the stopped hold never fires.
    @Override
    public void stopHeldActionOnGettingAttacked(
            EntityActionInstance action) {
        action.forceStop();
        action.syncPhaseChanges();
    }

    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return true;
    }

    // 1.16 standKeepsTarget: any target; the core keeps only living ones the Stand may attack
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    @Override
    public boolean canTargetEntityForAiming(
            StandEntity standEntity, Entity target) {
        return canUseAsTarget(target, standEntity.getUser());
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        return checkTargetAndDisc(context.getUser());
    }

    // 1.16 tickHeldAction re-ran checkRequirements with the current target
    // (checkStandConditions asked about the disc for it), so losing either
    // stops the hold.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return checkTargetAndDisc(context.getUser());
    }

    // The aimed target and its memory disc state; 1.16 had no stamina
    // condition here.
    private ConditionCheck checkTargetAndDisc(LivingEntity user) {
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        ActionTarget aimed = findCurrentTarget(user, user.level());
        LivingEntity target = normalizeTarget(aimed, user);
        if (target == null) {
            return ConditionCheck.createNegative(
                    "whitesnake_disc_target");
        }
        // 1.16: no ENTITY requirement, so the "target_too_far" of
        // checkRangeAndTarget dropped the target and checkStandConditions,
        // finding none, refused without a message
        if (!isInStandRange(user, aimed)) {
            return ConditionCheck.NEGATIVE;
        }
        // StandEntityAction.checkStandTarget dropped it the same way
        if (!standMayTarget(user, aimed.getEntity())) {
            return ConditionCheck.NEGATIVE;
        }

        HeldDisc held = MemoryDiscItem.findHeldForTarget(
                user, target.getUUID()).orElse(null);
        if (!user.level().isClientSide()) {
            MemoryLossState state =
                    WhitesnakeAddon.memoryState(target);
            if (held != null
                    && !state.matches(held.data().extractionId())) {
                return ConditionCheck.createNegative(
                        "whitesnake_memory_disc_stale");
            }
            if (held == null && state.isExtracted()) {
                return ConditionCheck.createNegative(
                        "whitesnake_memory_already_extracted");
            }
            if (held == null
                    && MemoryDiscItem.hasDiscForTarget(
                            user, target.getUUID())) {
                return ConditionCheck.createNegative(
                        "whitesnake_memory_duplicate_disc");
            }
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (action instanceof RemoveMemoryDiscAction removeAction) {
            ActionTarget target = findCurrentTarget(powerUser, level);
            if (!isInStandRange(powerUser, target)) {
                target = ActionTarget.EMPTY;
            }
            removeAction.setTarget(target);
            if (!target.isEmpty(level)) {
                action.standRotationTarget = target.copy();
                action.aimAs = AimingEntity.STAND;
            }
        }
    }

    private ActionTarget findCurrentTarget(
            LivingEntity user, Level level) {
        if (user == null) {
            return ActionTarget.EMPTY;
        }
        // The aims only go from the user's client to the server, so that
        // client reads the aim it sends
        ActionTargetAim aim = level.isClientSide()
                        && user == ClientProxy.getClientPlayer()
                ? ClientsideAim.playerAim
                : LivingComponentAction.getAim(user);
        ActionTarget target =
                aim != null ? aim.getTarget() : ActionTarget.EMPTY;
        target = target != null
                ? target.resolveEntityId(level)
                : ActionTarget.EMPTY;
        // 1.16 overrideVanillaMouseTarget: a pressed torch or grass gave way
        // to what the Stand's collision ray met, and a pressed block had no
        // other target
        boolean pressedBlock = target.getType() == TargetType.BLOCK;
        target = overridePressedTarget(user, target);
        if (isValidTarget(user, target, level)) {
            return target;
        }
        if (pressedBlock) {
            return ActionTarget.EMPTY;
        }
        target = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                TARGET_RANGE,
                TARGET_RANGE,
                level,
                entity -> canUseAsTarget(entity, user),
                user,
                0);
        return isValidTarget(user, target, level)
                ? target
                : ActionTarget.EMPTY;
    }

    private static boolean canUseAsTarget(
            Entity entity, LivingEntity user) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && normalizeTarget(living, user) != null;
    }

    private static boolean isValidTarget(
            LivingEntity user, ActionTarget target, Level level) {
        if (target.getType() != TargetType.ENTITY
                || target.isEmpty(level)
                || !(target.getEntity() instanceof LivingEntity living)
                || normalizeTarget(living, user) == null) {
            return false;
        }
        return living.getBoundingBox().distanceToSqr(
                user.getEyePosition()) <= TARGET_RANGE * TARGET_RANGE;
    }

    // 1.16 Action.checkRangeAndTarget: 8 blocks from the performer (the
    // Stand once it is out) to the entity targeted, 4 without a line of sight
    private static boolean isInStandRange(
            LivingEntity user, ActionTarget target) {
        return user != null
                && target.getType() == TargetType.ENTITY
                && target.getEntity() != null
                && ActionTargetRange.isEntityWithinRange(
                        ActionTargetRange.standPerformer(user),
                        target.getEntity(),
                        ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ);
    }

    /**
     * 1.16 StandEntityAction.checkStandTarget: only an entity the Stand may attack (StandEntity.canAttack), silently.
     * The press summoned the Stand first, so with it away the same rules are asked of its user: no friendly fire, not
     * an animal riding with the user, not the user's own tamed animal.
     */
    static boolean standMayTarget(LivingEntity user, Entity target) {
        StandPower power = StandPower.get(user);
        StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
        if (stand != null) {
            return stand.canAttackEntity(target);
        }
        if (!(target instanceof LivingEntity living) || living == user
                || !DamageUtil.isNotFriendlyFire(user, StandUtil.getStandUser(living))) {
            return false;
        }
        return !(living instanceof Animal)
                || !living.isPassengerOfSameVehicle(user)
                        && !(living instanceof TamableAnimal tamed && tamed.getOwner() == user);
    }

    private static LivingEntity normalizeTarget(
            ActionTarget target, LivingEntity user) {
        return target.getEntity() instanceof LivingEntity living
                ? normalizeTarget(living, user)
                : null;
    }

    // 1.16 WhitesnakeRemovingMemoryDisk/StandDisc.onTaskSet: as the hold
    // starts, the Stand, which keeps no offset in these actions, stands one
    // block before its living target on its user's side
    static void standBeforeTarget(
            StandEntity stand, LivingEntity user, ActionTarget target) {
        if (user == null
                || stand.isManuallyControlled()
                || !(target.getEntity() instanceof LivingEntity living)
                || !living.isAlive()) {
            return;
        }
        Vec3 toTarget =
                living.position().subtract(user.position()).normalize();
        stand.setPos(living.position().subtract(toTarget));
    }

    private static LivingEntity normalizeTarget(
            LivingEntity target, LivingEntity user) {
        LivingEntity normalized =
                MemoryDiscService.normalizeTarget(target);
        return normalized != null && normalized != user
                ? normalized
                : null;
    }

    public static final class RemoveMemoryDiscAction
            extends EntityActionInstance {
        private ActionTarget target = ActionTarget.EMPTY;

        public RemoveMemoryDiscAction(EntityActionType ability) {
            super(ability);
        }

        private void setTarget(ActionTarget target) {
            this.target = target != null
                    ? target.copy()
                    : ActionTarget.EMPTY;
        }

        private ActionTarget getTarget(Level level) {
            target = target.resolveEntityId(level);
            return target;
        }

        // 1.16 noAdheringToUserOffset gave the task no offset at all, so a
        // retraction during it heads for the default offset
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (getPerformer() instanceof StandEntity stand) {
                standBeforeTarget(stand, getPowerUser(), getTarget(level()));
            }
            // 1.16 standSound(WINDUP, PUCCI_REMOVE_DISK): a Stand sound.
            if (!level().isClientSide()
                    && getPerformer() instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        WhitesnakeAddon.PUCCI_REMOVE_DISK.get(),
                        1.0F,
                        1.0F);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() == ActionPhase.WINDUP) {
                keepStandAimedAtTarget(getTarget(level()));
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                forceStop();
            }
        }

        private void transferDisc(
                LivingEntity user, StandPower userPower) {
            ActionTarget currentTarget = getTarget(level());
            LivingEntity targetUser =
                    user != null
                            ? normalizeTarget(currentTarget, user)
                            : null;
            if (user == null
                    || userPower == null
                    || targetUser == null
                    || !isValidTarget(user, currentTarget, level())
                    || !isInStandRange(user, currentTarget)) {
                fail(user, "whitesnake_disc_target");
                return;
            }
            // D4C cancels the donor action body; held-disc restoration and both
            // cooldown writes stay cancelled, the perform cost is still paid.
            if (MemoryDiscService.rejectLoveTrainProtectedTarget(targetUser, user)) {
                return;
            }

            HeldDisc held = MemoryDiscItem.findHeldForTarget(
                    user, targetUser.getUUID()).orElse(null);
            if (held != null && !held.isStillHeldBy(user)) {
                setDiscTimers(userPower);
                fail(user, "whitesnake_memory_disc");
                return;
            }
            Result result = held != null
                    ? MemoryDiscService.restore(
                            held.snapshot(), targetUser)
                    : MemoryDiscService.extractTo(
                            targetUser, user);
            if (!result.applied()) {
                if (result.status() != Status.PROTECTED_BY_LOVE_TRAIN) {
                    setDiscTimers(userPower);
                    fail(user, failureReason(result.status()));
                }
                return;
            }
            setDiscTimers(userPower);
            if (held != null && !held.consumeFrom(user)) {
                fail(user, "whitesnake_memory_disc");
            }
        }

        // 1.16 standPerform (004 WhitesnakeRemovingMemoryDisk :67, :94-95)
        // set both timers for every living victim, whether or not a disc
        // moved; only D4C's Love Train cancelled that body.
        private void setDiscTimers(StandPower userPower) {
            if (ability
                    instanceof WhitesnakeRemoveMemoryDiscAbility remove) {
                userPower.setAbilityCooldown(
                        remove.name(), COOLDOWN_TICKS);
            }
            userPower.setAbilityCooldown(
                    STAND_DISC_REMOVAL_ABILITY, COOLDOWN_TICKS);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            // 1.16 004 InitStands :75 standSound(PERFORM, WHITESNAKE_REMOVE_DISC): the phase sound played at every
            // perform, whatever standPerform did (D4C's Love Train cancelled only its body)
            if (getPerformer() instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        WhitesnakeAddon.WHITESNAKE_REMOVE_DISC.get(),
                        1.0F,
                        1.0F);
            }
            LivingEntity user = getPowerUser();
            StandPower userPower =
                    user != null ? StandPower.get(user) : null;
            transferDisc(user, userPower);
            // 1.16 StandEntityTask paid the cost after every standPerform,
            // whatever it did, and ignored the result.
            if (userPower != null) {
                StandAbilityStamina.consume(
                        ability, userPower, STAMINA_COST, false);
            }
        }

        private static String failureReason(Status status) {
            return switch (status) {
                case ALREADY_EXTRACTED ->
                    "whitesnake_memory_already_extracted";
                case DUPLICATE_DISC ->
                    "whitesnake_memory_duplicate_disc";
                case INVALID_DISC ->
                    "whitesnake_memory_disc";
                case INVALID_PAYLOAD, TARGET_MISMATCH ->
                    "whitesnake_memory_disc_target";
                case NO_MEMORY_LOSS, TOKEN_MISMATCH ->
                    "whitesnake_memory_disc_stale";
                case INVALID_TARGET ->
                    "whitesnake_disc_target";
                default -> "whitesnake_memory_transition_failed";
            };
        }

        private void fail(LivingEntity user, String reason) {
            if (user == null) {
                return;
            }
            ConditionCheck.sendActionFailedMessage(
                    ability instanceof Ability abilityObject
                            ? abilityObject
                            : null,
                    ConditionCheck.createNegative(reason),
                    user);
        }
    }
}
