package rotp.addons.whitesnake.ability;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.ItemUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disc.DiscPayload;
import rotp.addons.whitesnake.standdisc.StandDiscService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class WhitesnakeRemoveStandDiscAbility
        extends StandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 30;
    public static final int COOLDOWN_TICKS = 30;
    public static final float EXTRACTION_STAMINA_COST = 300.0F;
    public static final double TARGET_RANGE = 20.0D;
    public static final String MEMORY_REMOVAL_ABILITY =
            "removing_the_memory_disk";

    public WhitesnakeRemoveStandDiscAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, RemoveStandDiscAction::new);
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

    // The hold charges in the windup, and a full charge stays held there
    // until the key is let go, as 1.16 held it before it fired.
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

    // 1.16 stopHeldAction(false): the stopped hold never fires, even when
    // it is fully charged (a key release would fire it).
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

    // 1.16 getTargetRequirement ENTITY: an entity target was always kept (standKeepsTarget only
    // decided for the other kinds); the core keeps only living ones the Stand may attack
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == TargetType.ENTITY;
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

    // 1.16 tickHeldAction and stopHeldAction(true) re-ran checkRequirements
    // with the current target, so losing it stops the hold; the disc the
    // press asks for is rechecked with it.
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

    // The aimed target and, for an insertion, the held disc; 1.16 had no
    // stamina condition here.
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
        // 1.16 ENTITY requirement: checkRangeAndTarget answered a target
        // past the Stand's reach with its message
        if (!isInStandRange(user, aimed)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        // then StandEntityAction.checkStandTarget, without a message
        if (!WhitesnakeRemoveMemoryDiscAbility.standMayTarget(
                user, aimed.getEntity())) {
            return ConditionCheck.NEGATIVE;
        }

        // 1.16 asked for no disc: a Stand-less target with none in hand
        // charged, moved nothing and set both timers (the perform says so)
        return ConditionCheck.POSITIVE;
    }

    @Override
    public Component getName(Power<?> context) {
        return isInsertionContext(context)
                ? Component.translatable(
                        "rotp_whitesnake.ability."
                                + "whitesnake_insert_stand_disc")
                : super.getName(context);
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return isInsertionContext(context)
                ? "whitesnake_insert_stand_disc"
                : super.getSpriteName(context);
    }

    private boolean isInsertionContext(Power<?> context) {
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null
                || DiscPayload.captureStandDisc(user).isEmpty()) {
            return false;
        }
        LivingEntity target = normalizeTarget(
                findCurrentTarget(user, user.level()), user);
        if (target == null) {
            return false;
        }
        StandPower targetPower = StandPower.get(target);
        return targetPower == null || !targetPower.hasPower();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (action instanceof RemoveStandDiscAction removeAction) {
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

    private static LivingEntity normalizeTarget(
            ActionTarget target, LivingEntity user) {
        return target.getEntity() instanceof LivingEntity living
                ? normalizeTarget(living, user)
                : null;
    }

    private static LivingEntity normalizeTarget(
            LivingEntity target, LivingEntity user) {
        LivingEntity normalized = StandUtil.getStandUser(target);
        return normalized != null
                && normalized.isAlive()
                && normalized != user
                ? normalized
                : null;
    }

    public static final class RemoveStandDiscAction
            extends EntityActionInstance {
        private ActionTarget target = ActionTarget.EMPTY;

        public RemoveStandDiscAction(EntityActionType ability) {
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

        // 1.16 noAdheringToUserOffset gave the task no offset at all (its
        // builder's standOffsetFromUser(0.667, 0.2, 0.0) never applied), so a
        // retraction during it heads for the default offset
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (getPerformer() instanceof StandEntity stand) {
                WhitesnakeRemoveMemoryDiscAbility.standBeforeTarget(
                        stand, getPowerUser(), getTarget(level()));
            }
            LivingEntity user = getPowerUser();
            if (!level().isClientSide() && user != null) {
                sayShoutOf(
                        ability, user, WhitesnakeAddon.PUCCI_REMOVE_DISK);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() == ActionPhase.WINDUP) {
                keepStandAimedAtTarget(getTarget(level()));
            }
        }

        // 1.16 holdType() + holdToFire(30, false): a full charge waits for
        // the key to be let go and fires then; letting go earlier cancels.
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.WINDUP;
        }

        // 1.16 stopHeldAction(true) checked the requirements once more
        // before the released charge fired.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                if (getPhaseTick() >= getCurPhaseLength()
                        && ability instanceof WhitesnakeRemoveStandDiscAbility held
                        && held.canFireReleasedHold(this)) {
                    setPhaseStart(ActionPhase.PERFORM);
                }
                else {
                    forceStop();
                }
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }

            // 1.16 004 InitStands :76 standSound(PERFORM, WHITESNAKE_REMOVE_DISC):
            // the phase sound played at every perform, whatever standPerform did.
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

            StandPower targetPower = StandPower.get(targetUser);
            if (targetPower != null && targetPower.hasPower()) {
                extract(user, userPower, targetPower);
            }
            else {
                insert(user, targetUser, userPower, targetPower);
            }
            // 1.16 standPerform (004 WhitesnakeRemovingStandDisc :65, :79-80)
            // set both timers for any living target user, whether or not a
            // disc moved.
            if (ability instanceof WhitesnakeRemoveStandDiscAbility remove) {
                userPower.setAbilityCooldown(remove.name(), COOLDOWN_TICKS);
            }
            userPower.setAbilityCooldown(
                    MEMORY_REMOVAL_ABILITY, COOLDOWN_TICKS);
        }

        private boolean extract(
                LivingEntity user,
                StandPower userPower,
                StandPower targetPower) {
            StandDiscService.Result result =
                    StandDiscService.extractTo(
                            targetPower.getUser(), user);
            if (!result.applied()) {
                fail(user, standDiscReason(result.status()));
                return false;
            }

            StandAbilityStamina.consume(
                    ability,
                    userPower,
                    EXTRACTION_STAMINA_COST,
                    false);
            return true;
        }

        private boolean insert(
                LivingEntity user,
                LivingEntity targetUser,
                StandPower userPower,
                StandPower targetPower) {
            DiscPayload held =
                    DiscPayload.captureStandDisc(user).orElse(null);
            if (held == null || !held.isStillHeldBy(user)) {
                fail(user, "whitesnake_stand_disc");
                return false;
            }
            StandInstance replacement =
                    StandDiscItem.getStandInstance(held.carrier());
            if (replacement == null || !replacement.standExists()) {
                fail(user, "whitesnake_stand_disc");
                return false;
            }

            boolean carrierConsumed = false;
            if (StandDiscService.isAuthoritative(held.carrier())) {
                if (!held.consumeFrom(user)) {
                    fail(user, "whitesnake_stand_disc");
                    return false;
                }
                carrierConsumed = true;
                StandDiscService.Result result =
                        StandDiscService.restore(
                                held.carrier(), targetUser);
                if (!result.applied()) {
                    ItemUtil.giveItemTo(
                            user, held.carrier(), true);
                    fail(user, standDiscReason(result.status()));
                    return false;
                }
                targetPower = StandPower.get(targetUser);
            } else {
                if (targetPower == null) {
                    targetPower =
                            PowerClass.STAND.attachGet(targetUser);
                }
                StandPowerTransitions.Result result =
                        StandPowerTransitions.insert(
                                targetPower, replacement);
                if (!result.applied()) {
                    fail(user, transitionReason(result.status()));
                    return false;
                }
                // 1.16 giveStandFromInstance(standExistedInWorld): not counted twice
                StandDiscItem.takePutOut(targetUser, held.carrier());
            }
            if (!carrierConsumed && !held.consumeFrom(user)) {
                fail(user, "whitesnake_stand_disc");
                return false;
            }

            if (targetPower.getPowerType()
                    instanceof EntityStandType entityStandType
                    && targetPower.getSummonedStandEntity() == null) {
                entityStandType.summon(targetUser, targetPower);
            }
            return true;
        }

        private static String standDiscReason(
                StandDiscService.Status status) {
            return switch (status) {
                case NO_STAND -> "whitesnake_no_stand";
                case ALREADY_EXTRACTED ->
                    "whitesnake_stand_disc_already_extracted";
                case OWNER_MISMATCH ->
                    "whitesnake_stand_disc_target";
                case TOKEN_MISMATCH, NO_EXTRACTION ->
                    "whitesnake_stand_disc_stale";
                case STAND_ALREADY_PRESENT ->
                    "whitesnake_stand_already_present";
                case STAND_MISMATCH ->
                    "whitesnake_stand_changed";
                case INVALID_DISC, INVALID_STAND_STATE ->
                    "whitesnake_stand_disc";
                default -> "whitesnake_transition_failed";
            };
        }

        private static String transitionReason(
                StandPowerTransitions.Status status) {
            return switch (status) {
                case NO_STAND -> "whitesnake_no_stand";
                case STAND_ALREADY_PRESENT ->
                    "whitesnake_stand_already_present";
                case SOURCE_MISMATCH ->
                    "whitesnake_stand_changed";
                default -> "whitesnake_transition_failed";
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
