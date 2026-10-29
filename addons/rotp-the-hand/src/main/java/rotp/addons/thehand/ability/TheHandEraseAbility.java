package rotp.addons.thehand.ability;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.thehand.TheHandAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TheHandEraseAbility extends StandEntityAbility {
    public static final int CHARGE_TICKS = 15;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 8;
    public static final float STAMINA_COST = 150.0F;
    public static final float LARGE_TARGET_THRESHOLD = 1.5F;
    public static final float SMALL_TARGET_FACTOR = 0.8F;
    public static final float LARGE_TARGET_FACTOR = 0.5F;
    public static final float BLOCK_STRENGTH_THRESHOLD = 20.0F;
    // 1.16 TheHandEntity.preparation: kept by the Stand between Erases
    private static final float DEFAULT_PREPARATION = 0.5F;
    private static final String PREPARATION_KEY = "rotp_the_hand_preparation";
    private static final ActionAnimIdentifier CHARGE_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "the_hand_erase_charge", false);

    public TheHandEraseAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, EraseAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, CHARGE_TICKS);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        // 1.16 InitStands.java:130 builds it from a plain
        // StandEntityHeavyAttack.Builder: the whole Stand comes out
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
        noFinisherBarDecay = true;
        // 1.16 cancelHeldOnGettingAttacked: a hit during the charge
        // drops it without the swipe.
        setCancelHeldOnGettingAttacked();
    }

    // 1.16 StandEntityHeavyAttack.checkStandConditions: a Stand that cannot
    // attack in melee (no attack speed or reach) cannot erase.
    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        ConditionCheck check = super.checkStandEntityConditions(
                standPower, standEntity);
        return check.isPositive()
                ? ConditionCheck.noMessage(standEntity.canAttackMelee())
                : check;
    }

    // 1.16 tickHeldAction asked checkStandConditions again on every held
    // tick: the charge stops once the Stand can no longer attack in melee.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()
                || !(action.getPerformer() instanceof StandEntity stand)) {
            return base;
        }
        return ConditionCheck.noMessage(stand.canAttackMelee());
    }

    // 1.16 TheHandErase.standKeepsTarget: any block or entity
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    // 1.16 StandEntityHeavyAttack.lockOnTargetPosition: the front offset
    // stays along the user's view, measured to the task's target
    @Override
    public boolean lockOnTargetPosition(StandPower standPower,
            StandEntity standEntity, EntityActionInstance action) {
        return false;
    }

    static double minFrontOffset(StandEntity stand) {
        return Math.min(0.5D, stand.getEffectiveRange());
    }

    static double maxFrontOffset(StandEntity stand) {
        return Math.min(2.0D, stand.getMaxRange());
    }

    @Override
    public void stopHeldActionOnGettingAttacked(
            EntityActionInstance action) {
        if (action instanceof EraseAction eraseAction) {
            eraseAction.cancelFromDamage();
        } else {
            super.stopHeldActionOnGettingAttacked(action);
        }
    }

    // 1.16 ActionsOverlayGui heldReadyToFire: green once held for
    // getHoldDurationToFire, half of getHoldDurationMax (TheHandErase
    // :290-292), while the charge waits for the release or its maximum;
    // none when that half is 0. Same threshold as the release in
    // EraseAction.onButtonStopHold.
    @Override
    public boolean isActionHeldReadyToFire(EntityActionInstance action) {
        int ticksToFire = holdTicksToFire(action);
        return isActionHeld(action)
                && action.getPhase() == ActionPhase.BUTTON_CHARGE
                && ticksToFire > 0
                && action.getPhaseTick() >= ticksToFire;
    }

    // 1.16 TheHandErase.getHoldDurationToFire: getHoldDurationMax / 2 in
    // int arithmetic, which rounds an odd charge's half down.
    static int holdTicksToFire(EntityActionInstance action) {
        return Math.max(
                (int) action.phasesLength.getFloat(
                        ActionPhase.BUTTON_CHARGE),
                1) / 2;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        // The held charge keeps the raised-arm pose (the donor's
        // BUTTON_HOLD pose); the swipe itself is bound to WINDUP so
        // the erase lands as the swipe completes.
        if (action.getPhase() == ActionPhase.BUTTON_CHARGE) {
            return CHARGE_ANIM;
        }
        return super.getEntityAnim(action);
    }

    @Override
    public String getSpriteName(Power<?> context) {
        if (context instanceof StandPower standPower) {
            StandEntity stand = standPower.getSummonedStandEntity();
            if (stand != null
                    && stand.getCurStandAction()
                            instanceof EraseAction action
                    && action.isAttracting()) {
                return super.getSpriteName(context) + "_alt";
            }
            LivingEntity user = standPower.getUser();
            if (user != null && user.isShiftKeyDown()) {
                return super.getSpriteName(context) + "_alt";
            }
        }
        return super.getSpriteName(context);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (!level.isClientSide()
                && performer instanceof StandEntity stand) {
            action.phasesLength.put(
                    ActionPhase.BUTTON_CHARGE,
                    holdDurationMax(powerUser, stand));
            action.phasesLength.put(
                    ActionPhase.WINDUP,
                    StandStatFormulas.getHeavyAttackWindup(
                            stand.getAttackSpeed() * 2.5D,
                            stand.getFinisherMeter()));
            action.phasesLength.put(
                    ActionPhase.RECOVERY, RECOVERY_TICKS);
        }
    }

    // 1.16 TheHandErase.getHoldDurationMax (:294-300): 0 below Resolve 4 with
    // an empty finisher bar, which made the Erase no held action at all
    static int holdDurationMax(LivingEntity user, StandEntity stand) {
        StandPower power = user != null ? StandPower.get(user) : null;
        if (power == null) {
            return CHARGE_TICKS;
        }
        float finisher = stand.getFinisherMeter();
        return power.getResolveLevel() >= 4
                ? (int) (10.0F - 4.0F * finisher)
                : (int) (15.0F * (7.0F * finisher));
    }

    private static float lastPreparation(StandEntity stand) {
        CompoundTag kept = stand.getPersistentData();
        return kept.contains(PREPARATION_KEY)
                ? kept.getFloat(PREPARATION_KEY)
                : DEFAULT_PREPARATION;
    }

    public static final class EraseAction extends EntityActionInstance {
        private boolean attract;
        private boolean performed;
        private boolean targetSelected;
        private boolean cancelled;
        private boolean missTeleportResolved;
        private boolean missTeleportFromPerform;
        private boolean actionCleared;
        // 1.16 recoveryCount: server ticks spent in RECOVERY.
        private int recoveryTicks;
        private float preparation;
        private int erasedTargets;
        private long eraseCallbackGameTick = -1L;
        private ActionPhase eraseCallbackPhase;
        private float eraseCallbackPhaseTick = -1.0F;
        private long teleportCallbackGameTick = -1L;
        private ActionPhase teleportCallbackPhase;
        private float teleportCallbackPhaseTick = -1.0F;
        private Vec3 teleportPositionBefore;
        private Vec3 teleportPositionAfter;
        private int teleportCallbackCount;
        private long clearCallbackGameTick = -1L;
        private ActionPhase clearCallbackPhase;

        public EraseAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            LivingEntity user = getPowerUser();
            attract = user != null && user.isShiftKeyDown();
            if (!(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            preparation = lastPreparation(stand);
            ActionTarget target = captureActionTargetFromAim(stand);
            setStandFrontOffsetFromTarget(
                    stand, target, minFrontOffset(stand),
                    maxFrontOffset(stand));
            keepStandAimedAtTarget(target);
            aimAs = AimingEntity.STAND;
            if (!level().isClientSide()) {
                StandUtil.playStandEntitySound(
                        stand,
                        TheHandAddon.THE_HAND_ERASE_SOUND,
                        1.0F,
                        1.0F);
            }
        }

        // 1.16 front3dOffset measured the front offset to the task's
        // target, which an Erase pressed at nothing took over
        @Override
        protected void onTaskTargetInherited(ActionTarget target) {
            super.onTaskTargetInherited(target);
            if (getPerformer() instanceof StandEntity stand) {
                setStandFrontOffsetFromTarget(
                        stand, target, minFrontOffset(stand),
                        maxFrontOffset(stand));
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }

            eraseCallbackGameTick = level().getGameTime();
            eraseCallbackPhase = getPhase();
            eraseCallbackPhaseTick = getPhaseTick();

            performed = true;
            // 1.16 StandEntityHeavyAttack.standPerform: StandEntity.punch at
            // the task's target, one taken over from the previous task too
            ActionTarget target =
                    StandEntityPunchAbility.getFreshPunchTarget(
                            stand, getTaskTarget(level()));
            setActionTargetSnapshot(target);
            // 1.16 StandEntity.punch -> aimWithThisOrUser turned the Stand
            // toward what the strike aimed at before the hit
            stand.turnTowardsStrike(StandEntityPunchAbility.strikeAim(
                    stand, target,
                    StandEntityPunchAbility.CrosshairRule.ENTITY));
            // 1.16 StandEntity.punch made the strike's target the task's
            // (setTaskTarget), the one a next action pressed at nothing took
            // over
            setTaskTarget(target);
            targetSelected = target.getType()
                    != ActionTarget.TargetType.EMPTY;

            erasedTargets += TheHandCombat.eraseSweep(
                    stand, preparation, 0);
            switch (target.getType()) {
                case ENTITY -> eraseEntity(
                        target.getMainEntity(), stand);
                case BLOCK -> eraseBlock(
                        target.getBlockPos(), stand);
                default -> {
                    // Miss behavior runs when PERFORM exits.
                }
            }
            // 1.16 StandEntityTask: the cost is paid after the swipe; a
            // short bar just drops to 0.
            LivingEntity user = getPowerUser();
            StandAbilityStamina.consume(
                    ability,
                    user != null ? StandPower.get(user) : null,
                    STAMINA_COST,
                    false);
        }

        private void eraseEntity(Entity target, StandEntity stand) {
            if (target == null
                    || target.isRemoved()
                    || target == stand
                    || target == getPowerUser()) {
                return;
            }
            float damage = TheHandCombat.eraseDamage(
                    target, stand, preparation);
            TheHandCombat.attack(
                    stand, target, damage, preparation, false);
        }

        private void eraseBlock(BlockPos pos, StandEntity stand) {
            Level level = level();
            if (level.isClientSide()
                    || pos == null
                    || !JojoModUtil.breakingBlocksEnabled(level)) {
                return;
            }
            float hardness = stand.getBlockHardnessForStandBreak(
                    level.getBlockState(pos), level, pos);
            if (hardness >= 0.0F
                    && hardness <= BLOCK_STRENGTH_THRESHOLD) {
                level.removeBlock(pos, false);
            }
        }

        @Override
        public void actionPerformEnd() {
            teleportAfterMissIfEligible();
        }

        // 1.16 stopHeldAction(true): a charge let go too early, or one that
        // no longer passes the requirements, is dropped without firing.
        @Override
        public void onButtonStopHold() {
            if (getPhase() != ActionPhase.BUTTON_CHARGE) {
                return;
            }
            // 1.16 stoppedHolding: the ticks held over getHoldDurationMax,
            // for a charge dropped too early as well
            float held = getPhaseTick() / liveChargeMaximum();
            rememberPreparation(held);
            if (getPhaseTick() < holdTicksToFire(this)
                    || !(ability instanceof TheHandEraseAbility heldAbility
                            && heldAbility.canFireReleasedHold(this))) {
                cancelled = true;
                forceStop();
                syncPhaseChanges();
                return;
            }
            preparation = held;
            setPhaseStart(ActionPhase.WINDUP);
            syncPhaseChanges();
        }

        // 1.16 asked getHoldDurationMax on every held tick: a charge held
        // to the maximum it has now fires by itself.
        @Override
        public void actionTick() {
            if (!level().isClientSide()
                    && getPhase() == ActionPhase.RECOVERY) {
                recoveryTicks++;
            }
            if (level().isClientSide()
                    || getPhase() != ActionPhase.BUTTON_CHARGE) {
                return;
            }
            float maximum = liveChargeMaximum();
            if (getPhaseTick() + 1.0F < maximum) {
                return;
            }
            preparation = (getPhaseTick() + 1.0F) / maximum;
            rememberPreparation(preparation);
            setPhaseStart(ActionPhase.WINDUP);
            syncPhaseChanges();
        }

        // The finisher meter can change during the hold, and the charge's
        // length follows it. A charge never gets shorter than 1 tick once
        // it is held.
        private float liveChargeMaximum() {
            float stored = Math.max(
                    phasesLength.getFloat(ActionPhase.BUTTON_CHARGE), 1.0F);
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return stored;
            }
            float live = Math.max(
                    holdDurationMax(getPowerUser(), stand), 1);
            if (live != stored) {
                phasesLength.put(ActionPhase.BUTTON_CHARGE, live);
                curPhaseLength = live;
                syncPhaseChanges();
            }
            return live;
        }

        private void rememberPreparation(float value) {
            if (getPerformer() instanceof StandEntity stand) {
                stand.getPersistentData().putFloat(PREPARATION_KEY, value);
            }
        }

        // A maximum of 0 is no hold: the Erase swipes at once.
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.BUTTON_CHARGE
                    && curPhaseLength > 0.0F;
        }

        // 1.16 isCancelable: a punch or a heavy move cuts the recovery.
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return getPhase() == ActionPhase.RECOVERY
                    && TheHandCombat.followsUpErase(cancellingAbility);
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.RECOVERY
                    && getPhase() == ActionPhase.PERFORM) {
                teleportAfterMissIfEligible();
            }
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            if (!level().isClientSide()) {
                clearCallbackGameTick = level().getGameTime();
                clearCallbackPhase = getPhase();
            }
            actionCleared = true;
            teleportAfterMissIfEligible();
        }

        private void teleportAfterMissIfEligible() {
            if (missTeleportResolved
                    || level().isClientSide()
                    || !performed
                    || cancelled
                    || targetSelected
                    || erasedTargets > 0
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            // 1.16 onTaskStopped: the pull waits for the action to end,
            // so a full and a cut recovery pull differently.
            if (attract && !actionCleared) {
                return;
            }
            LivingEntity source = stand.isManuallyControlled()
                    ? stand
                    : getPowerUser();
            missTeleportResolved = true;
            missTeleportFromPerform =
                    getPhase() == ActionPhase.PERFORM;
            teleportCallbackGameTick = level().getGameTime();
            teleportCallbackPhase = getPhase();
            teleportCallbackPhaseTick = getPhaseTick();
            teleportPositionBefore = source != null
                    ? source.position()
                    : null;
            teleportCallbackCount++;
            TheHandCombat.teleportAfterMiss(
                    getPowerUser(), stand, attract,
                    recoveryTicks >= RECOVERY_TICKS,
                    StandSkillWalls.wallsHideEntities(level(), this));
            teleportPositionAfter = source != null
                    ? source.position()
                    : null;
        }

        boolean missTeleportOccurredDuringPerform() {
            return missTeleportResolved
                    && missTeleportFromPerform;
        }

        public EraseTimingSnapshot eraseTimingSnapshot() {
            return new EraseTimingSnapshot(
                    eraseCallbackGameTick,
                    eraseCallbackPhase,
                    eraseCallbackPhaseTick,
                    teleportCallbackGameTick,
                    teleportCallbackPhase,
                    teleportCallbackPhaseTick,
                    teleportPositionBefore,
                    teleportPositionAfter,
                    teleportCallbackCount,
                    clearCallbackGameTick,
                    clearCallbackPhase);
        }

        public void cancelFromDamage() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                rememberPreparation(getPhaseTick() / liveChargeMaximum());
                cancelled = true;
                forceStop();
                syncPhaseChanges();
            }
        }

        public boolean isAttracting() {
            return attract;
        }

        @Override
        public void toBuf(FriendlyByteBuf buffer) {
            buffer.writeBoolean(attract);
            buffer.writeFloat(preparation);
        }

        @Override
        public void fromBuf(FriendlyByteBuf buffer) {
            attract = buffer.readBoolean();
            preparation = buffer.readFloat();
        }

        public record EraseTimingSnapshot(
                long eraseCallbackGameTick,
                ActionPhase eraseCallbackPhase,
                float eraseCallbackPhaseTick,
                long teleportCallbackGameTick,
                ActionPhase teleportCallbackPhase,
                float teleportCallbackPhaseTick,
                Vec3 teleportPositionBefore,
                Vec3 teleportPositionAfter,
                int teleportCallbackCount,
                long clearCallbackGameTick,
                ActionPhase clearCallbackPhase) {}
    }
}
