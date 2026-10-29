package rotp.addons.d4c.ability;

import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModParticles;
import rotp.core.init.ModSoundEvents;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CPhase2BSpec;
import rotp.addons.d4c.D4CPhase2BService;
import rotp.addons.d4c.D4CRuntimeRules;
import rotp.addons.d4c.entity.D4CAnnihilationCloneEntity;
import rotp.addons.d4c.entity.D4CMinionEntity;
import rotp.addons.d4c.network.D4CLoveTrainSync;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class D4CPhase2BAbility extends StandEntityAbility {
    private final Mode mode;

    public D4CPhase2BAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Mode mode) {
        super(
                abilityType,
                abilityId,
                type -> new Phase2BAction(type, mode));
        this.mode = mode;
        usageGroup = AbilityUsageGroup.UTILITY;
        if (mode.chargeTicks > 0) {
            setDefaultPhaseLength(
                    ActionPhase.BUTTON_CHARGE, mode.chargeTicks);
        } else if (mode.windupTicks > 0) {
            setDefaultPhaseLength(
                    ActionPhase.WINDUP, mode.windupTicks);
        }
        setDefaultPhaseLength(ActionPhase.PERFORM, mode.performTicks);
        setDefaultPhaseLength(ActionPhase.RECOVERY, mode.recoveryTicks);
        if (mode.additionalCooldown > 0) {
            cooldown(
                    mode.technicalCooldown,
                    mode.additionalCooldown,
                    0.0F);
        } else if (mode.technicalCooldown > 0) {
            cooldown(mode.technicalCooldown);
        }
        if (mode.resolveLevel > 0) {
            resolveLevelToUnlock(mode.resolveLevel);
        }
        standAutoSummonMode(AutoSummonMode.FULL);
        // 1.16 aa/aj.java:67-73: pullin_parallel_world_travel replaced
        // checkConditions without calling it, so no Stand part was asked for
        if (mode != Mode.PULL_IN_TRAVEL) {
            partsRequired(StandPart.ARMS);
        }
        if (mode == Mode.HAND_CHOP) {
            initIsFinisher("d4c_heavy_punch");
        }
        // 1.16 StandEntityHeavyAttack.noFinisherBarDecay and
        // StandEntityLightAttack.noFinisherBarDecay (ac.java kept it)
        noFinisherBarDecay = mode == Mode.HAND_CHOP
                || mode == Mode.RAPID_PUNCH;
    }

    public Mode mode() {
        return mode;
    }

    // 1.16 Builder#shout, said once the press is accepted.
    public static Holder<SoundEvent> modeShout(Mode mode) {
        return switch (mode) {
            case SUMMON_MINION -> D4CAddon.VALENTINE_SUMMON_ME;
            case SWAP_WITH_MINION -> D4CAddon.VALENTINE_CHANGE;
            case PULL_IN_TRAVEL -> D4CAddon.VALENTINE_PULL_TRAVEL;
            default -> null;
        };
    }

    // 1.16 standSound(PERFORM), played by the Stand.
    public static Holder<SoundEvent> modePerformStandSound(Mode mode) {
        return switch (mode) {
            case RAPID_PUNCH -> D4CAddon.VALENTINE_RAIDPUNCH;
            case PULL_IN_TRAVEL -> D4CAddon.D4C_TRAVERSE_FANG;
            default -> null;
        };
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        String donorAnimation = switch (mode) {
            case RAPID_PUNCH -> "rapidpunch";
            case HAND_CHOP -> "heavyPunch";
            case SUMMON_MINION, ANNIHILATION, PULL_IN_TRAVEL ->
                    "flangatck";
            case FLAG_TRAVEL -> "flangte";
            case LOVE_TRAIN -> "loveup";
            default -> null;
        };
        return donorAnimation != null
                ? ActionAnimIdentifier.getOrCreate(
                        donorAnimation, false)
                : super.getEntityAnim(action);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 aa/ag.java:82 getCooldown replaced the registered
    // cooldown(200, 100, 0.0f) outright with 200 - ticksHeld * 30: a click's
    // -1 set 230 whatever Resolve or Creative said, while
    // getCooldownTechnical stayed the registered 200
    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        if (mode == Mode.LIGHT_WALL
                && !(context instanceof StandPower standPower
                        && PlayerClientBroadcastedSettings
                                .isNoStandAbilityCooldownEnabled(
                                        standPower))) {
            return 200 - ticksHeld * 30;
        }
        return super.getCooldown(context, ticksHeld);
    }

    // 1.16 StandEntityHeavyAttack.getStandWindupTicks, read at the press
    // before onTaskSet took 0.51 off the meter; the server's lengths come to
    // clients with the action
    @Override
    public void initActionFromConfig(EntityActionInstance action, Level level,
            LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (mode == Mode.HAND_CHOP
                && !level.isClientSide()
                && performer instanceof StandEntity stand) {
            action.phasesLength.put(ActionPhase.WINDUP,
                    StandStatFormulas.getHeavyAttackWindup(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
        }
    }

    // 1.16 aa/aa/ac.java:112 standKeepsTarget: d4c_rapid_punch kept any
    // target; aa/aa/aa.java extends StandEntityHeavyAttack, which kept an
    // entity (summon_kick, aa/aa/ae.java, is no Stand task: see
    // D4CSummonKickAbility)
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return mode == Mode.RAPID_PUNCH
                || mode == Mode.HAND_CHOP
                        && target.getType() == ActionTarget.TargetType.ENTITY;
    }

    // 1.16 PowerBaseImpl.checkTarget emptied a pressed target standKeepsTarget
    // refused: the hand chop (StandEntityHeavyAttack) took no block
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return (mode != Mode.HAND_CHOP
                        || keepsTaskTargetType(standEntity, target))
                && super.takesPressedTarget(standEntity, target);
    }

    // 1.16 ac.java:121: the rapid punch turned its offset only to an
    // entity it kept; StandEntityHeavyAttack.lockOnTargetPosition is false
    @Override
    public boolean lockOnTargetPosition(StandPower standPower,
            StandEntity standEntity, EntityActionInstance action) {
        return mode != Mode.HAND_CHOP
                && (mode != Mode.RAPID_PUNCH
                        || action.getTaskTarget(standEntity.level())
                                .getType()
                                == ActionTarget.TargetType.ENTITY);
    }

    // 1.16 ac.java:116 standMovesByItself: from the last 2 windup ticks
    // on, the Stand stays where its front offset put it
    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return rapidPunchMovesByItself(standEntity);
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return rapidPunchMovesByItself(standEntity);
    }

    @Override
    public boolean lockStandManualMovement(
            StandPower standPower, StandEntity standEntity) {
        return rapidPunchMovesByItself(standEntity);
    }

    private boolean rapidPunchMovesByItself(StandEntity standEntity) {
        EntityActionInstance action = standEntity.getCurStandAction();
        if (mode != Mode.RAPID_PUNCH
                || action == null
                || !action.isOfAbility(this)) {
            return false;
        }
        ActionPhase phase = action.getPhase();
        return phase == ActionPhase.WINDUP
                        && action.phasesLength.getFloat(ActionPhase.WINDUP)
                                - action.getPhaseTick() <= 2.0F
                || phase == ActionPhase.PERFORM
                || phase == ActionPhase.RECOVERY;
    }

    // 1.16 ac.java:137 standRetractsAfterTask
    @Override
    public boolean retractsStandAfterAction(StandPower standPower,
            StandEntity standEntity, EntityActionInstance action) {
        return mode != Mode.RAPID_PUNCH
                && super.retractsStandAfterAction(
                        standPower, standEntity, action);
    }

    // 1.16 love_train cancelHeldOnGettingAttacked: a hit during the
    // charge drops it; the other D4C actions kept the 1.16 default.
    @Override
    public boolean cancelHeldOnGettingAttacked(
            EntityActionInstance action,
            DamageSource dmgSource,
            float dmgAmount) {
        return mode == Mode.LOVE_TRAIN;
    }

    // 1.16 StandEntityLightAttack and StandEntityHeavyAttack
    // checkStandConditions: no melee without attack speed and reach
    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        ConditionCheck check = super.checkStandEntityConditions(
                standPower, standEntity);
        if (!check.isPositive()
                || mode != Mode.HAND_CHOP && mode != Mode.RAPID_PUNCH) {
            return check;
        }
        return ConditionCheck.noMessage(standEntity.canAttackMelee());
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck parent = super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        return checkModeConditions(context);
    }

    // 1.16 tickHeldAction re-ran these on every held tick: annihilation's
    // checkStandConditions (aa/aa.java:63-72), love_train's
    // checkSpecificConditions (aa/ae.java:78-89) and pullin's checkConditions
    // (aa/aj.java:67-73); summon_player_minion and flag travel had none
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return switch (mode) {
            case ANNIHILATION, LOVE_TRAIN, PULL_IN_TRAVEL ->
                    checkModeConditions(context);
            default -> ConditionCheck.POSITIVE;
        };
    }

    private ConditionCheck checkModeConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 annihilation and annihilation_mark checkStandConditions:
        // with the Stand out, a bar under 300 / 50 refuses silently,
        // before the target is looked at. No other D4C action reads it.
        float staminaFloor = switch (mode) {
            case ANNIHILATION -> 300.0F;
            case ANNIHILATION_MARK -> 50.0F;
            default -> 0.0F;
        };
        if (staminaFloor > 0.0F
                && context instanceof StandPower power
                && power.getSummonedStandEntity() != null
                && power.getStamina() < staminaFloor) {
            return ConditionCheck.NEGATIVE;
        }
        if (!D4CPhase2BService.canExecute(mode, user)) {
            // 1.16 condition messages
            String reason = D4CPhase2BService.refusalKey(mode, user);
            return reason != null
                    ? ConditionCheck.createNegative(
                            Component.translatable(reason))
                    : ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    public enum Mode {
        RAPID_PUNCH(
                D4CPhase2BSpec.RAPID_STAMINA,
                0, 3, 15, 10, 40, 80, 2),
        // 1.16 StandEntityHeavyAttack.DEFAULT_STAMINA_COST; the windup and
        // the recovery are set per press (initActionFromConfig, onActionSet)
        HAND_CHOP(50, 0, 5, 1, 12, 0, 0, 0),
        // 1.16 ai/ah.java:74-86: the builder's standRecoveryDuration 0
        // (minion_ai_control was a StandAction, no task at all), so nothing
        // held the next press back once they had performed
        SUMMON_MINION(400, 13, 0, 1, 0, 100, 200, 4),
        ANNIHILATION_MARK(50, 0, 0, 1, 0, 10, 20, 2),
        ANNIHILATION(300, 13, 0, 1, 0, 120, 360, 3),
        FLAG_TRAVEL(150, 18, 0, 1, 0, 80, 250, 3),
        SWAP_WITH_MINION(200, 0, 0, 1, 0, 80, 160, 3),
        LOVE_TRAIN(500, 540, 0, 1, 0, 200, 400, 4),
        LOVE_TRAIN_DEACTIVATE(50, 0, 0, 1, 0, 20, 40, 4),
        LIGHT_WALL(150, 0, 0, 1, 0, 200, 100, 4),
        LIGHT_WALL_DEACTIVATE(0, 0, 0, 1, 0, 0, 0, 4),
        MINION_AI_CONTROL(50, 0, 0, 1, 0, 20, 40, 2),
        PULL_IN_TRAVEL(300, 13, 0, 1, 0, 80, 160, 3),
        ITEM_THROW(100, 0, 0, 1, 0, 20, 40, 1);

        private final float stamina;
        private final int chargeTicks;
        private final int windupTicks;
        private final int performTicks;
        private final int recoveryTicks;
        private final int technicalCooldown;
        private final int additionalCooldown;
        private final int resolveLevel;

        Mode(
                float stamina,
                int chargeTicks,
                int windupTicks,
                int performTicks,
                int recoveryTicks,
                int technicalCooldown,
                int additionalCooldown,
                int resolveLevel) {
            this.stamina = stamina;
            this.chargeTicks = chargeTicks;
            this.windupTicks = windupTicks;
            this.performTicks = performTicks;
            this.recoveryTicks = recoveryTicks;
            this.technicalCooldown = technicalCooldown;
            this.additionalCooldown = additionalCooldown;
            this.resolveLevel = resolveLevel;
        }

        public float stamina() {
            return stamina;
        }
    }

    private static final class Phase2BAction
            extends EntityActionInstance {
        private final Mode mode;
        private boolean committed;
        private boolean presentationStarted;
        private boolean presentationCancelled;
        private boolean playedHeavySwing;
        private int rapidHits;

        private Phase2BAction(
                EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            if (mode == Mode.HAND_CHOP
                    && getPerformer() instanceof StandEntity stand) {
                // what pressedTaskTarget hands to the task; aa.java:74-78
                // measured front3dOffset to the task's target from the start
                ActionTarget chopTarget = captureActionTargetFromAim(stand);
                setStandFrontOffsetFromTarget(
                        stand,
                        chopTarget,
                        Math.min(0.5D, stand.getEffectiveRange()),
                        Math.min(3.0D, stand.getMaxRange()));
                // 1.16 StandEntityHeavyAttack.onTaskSet; a failed input that
                // puts the chop back already took its hand and paid it
                if (!isRestoringInputLifecycle()) {
                    stand.alternateHands();
                }
                if (!level().isClientSide()
                        && !isRestoringInputLifecycle()) {
                    // 1.16 StandEntityHeavyAttack.onClick, before the task
                    stand.setHeavyPunchFinisher();
                    // aa.java:69-72: 0.8 of the heavy attack's recovery
                    phasesLength.put(ActionPhase.RECOVERY, (float) Math.round(
                            StandStatFormulas.getHeavyAttackRecovery(
                                    stand.getAttackSpeed(),
                                    stand.getLastHeavyFinisherValue())
                                    * 0.8F));
                    stand.addFinisherMeter(-0.51F, 0);
                }
            }
            // 1.16 StandEntityLightAttack.getOffsetFromUser: front3dOffset,
            // 0.5 to 2 toward the target; the Stand turned to it
            if (mode == Mode.RAPID_PUNCH
                    && getPerformer() instanceof StandEntity stand) {
                // 1.16 StandEntityLightAttack.onTaskSet
                if (!isRestoringInputLifecycle()) {
                    stand.alternateHands();
                }
                ActionTarget target = captureActionTargetFromAim(stand);
                setStandFrontOffsetFromTarget(
                        stand,
                        target,
                        Math.min(0.5D, stand.getEffectiveRange()),
                        Math.min(2.0D, stand.getMaxRange()));
                keepStandAimedAtTarget(target);
            }
            setDonorStandOffset();
            // 1.16 shout at the accepted press (held ones at hold start)
            if (!level().isClientSide()) {
                LivingEntity user = getPowerUser();
                if (user != null) {
                    Ability.sayShoutOf(ability, user, modeShout(mode));
                }
            }
        }

        // 1.16 StandEntityHeavyAttack.isFreeRecovery: the hand chop, a
        // finisher variation, gave way to other actions in its recovery.
        // StandEntityLightAttack.isCancelable did so for the rapid punch.
        // Neither for itself (StandEntityAction.canClickDuringTask): the
        // heavy attack is not chainable, and ac.java:173-175 chained the rapid
        // punch only while fewer than 6 hits had landed, all in its perform.
        // isOfAbility, not identity: a client's action holds the base-moveset
        // instance of its ability, not the one its user's moveset presses
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return getPhase() == ActionPhase.RECOVERY
                    && (mode == Mode.HAND_CHOP || mode == Mode.RAPID_PUNCH)
                    && !isOfAbility(cancellingAbility);
        }

        // 1.16 StandEntityHeavyAttack kept the entity it was pressed at
        @Override
        protected ActionTarget pressedTaskTarget() {
            return mode == Mode.HAND_CHOP
                    ? getActionTargetSnapshot(level())
                    : super.pressedTaskTarget();
        }

        // 1.16 front3dOffset was measured to the task's target, one taken
        // over from the previous task too
        @Override
        protected void onTaskTargetInherited(ActionTarget target) {
            super.onTaskTargetInherited(target);
            if (mode == Mode.RAPID_PUNCH
                    && getPerformer() instanceof StandEntity stand) {
                setStandFrontOffsetFromTarget(
                        stand,
                        target,
                        Math.min(0.5D, stand.getEffectiveRange()),
                        Math.min(2.0D, stand.getMaxRange()));
            }
            // aa/aa/aa.java:74-78: front3dOffset(task.getTarget(), 0.5, 3)
            if (mode == Mode.HAND_CHOP
                    && getPerformer() instanceof StandEntity stand) {
                setStandFrontOffsetFromTarget(
                        stand,
                        target,
                        Math.min(0.5D, stand.getEffectiveRange()),
                        Math.min(3.0D, stand.getMaxRange()));
            }
        }

        // 1.16 ai/ah.java:74-81 standOffsetFromUser(left, forward[, y]) for
        // the whole action in full mode, turned by the view yaw; arms-only
        // D4C kept its arms-only default.
        private void setDonorStandOffset() {
            switch (mode) {
                case SUMMON_MINION, ANNIHILATION -> setStandOffset(
                        new Vec3(0.0D, 0.0D, 2.0D),
                        StandOffsetFromUser.Rotations.HEAD,
                        false);
                case FLAG_TRAVEL -> setStandOffset(
                        -0.1D,
                        -0.5D,
                        StandOffsetFromUser.Rotations.HEAD,
                        false);
                case LOVE_TRAIN -> setStandOffset(
                        new Vec3(0.0D, 0.3D, -0.4D),
                        StandOffsetFromUser.Rotations.HEAD,
                        false);
                default -> {
                }
            }
        }

        @Override
        public void setPhase(ActionPhase newPhase, int tick) {
            boolean entersRecovery = handChopEntersRecovery(newPhase);
            float before = phasesLength.getFloat(ActionPhase.RECOVERY);
            super.setPhase(newPhase, tick);
            if (entersRecovery && Float.compare(before,
                    phasesLength.getFloat(ActionPhase.RECOVERY)) != 0) {
                syncPhaseChanges();
            }
        }

        private boolean handChopEntersRecovery(ActionPhase newPhase) {
            return mode == Mode.HAND_CHOP
                    && newPhase == ActionPhase.RECOVERY
                    && getPhase() != null
                    && getPhase() != ActionPhase.RECOVERY
                    && getPerformer() instanceof StandEntity
                    && !level().isClientSide();
        }

        // 1.16 love_train standSound(BUTTON_HOLD, valentine_love)
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            // 1.16 StandEntityTask.moveToPhase read getStandRecoveryTicks
            // as the recovery began: the speed then and the finisher value
            // recorded last (a heavy press during the chop records it again)
            if (handChopEntersRecovery(newPhase)
                    && getPerformer() instanceof StandEntity stand) {
                phasesLength.put(ActionPhase.RECOVERY, (float) Math.round(
                        StandStatFormulas.getHeavyAttackRecovery(
                                stand.getAttackSpeed(),
                                stand.getLastHeavyFinisherValue())
                                * 0.8F));
            }
            if (mode == Mode.LOVE_TRAIN
                    && newPhase == ActionPhase.BUTTON_CHARGE
                    && !level().isClientSide()) {
                StandEntity stand = actionStand();
                if (stand != null) {
                    StandUtil.playStandEntitySound(
                            stand, D4CAddon.VALENTINE_LOVE, 1.0F, 1.0F);
                }
            }
        }

        private StandEntity actionStand() {
            if (getPerformer() instanceof StandEntity stand) {
                return stand;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            return power != null ? power.getSummonedStandEntity() : null;
        }

        // 1.16 PERFORM sounds; flag travel's fang is played at the user.
        private void playPerformSounds(
                ServerLevel serverLevel, LivingEntity user) {
            Holder<SoundEvent> standSound = modePerformStandSound(mode);
            StandEntity stand = actionStand();
            if (standSound != null && stand != null) {
                StandUtil.playStandEntitySound(
                        stand, standSound, 1.0F, 1.0F);
            }
            if (mode == Mode.FLAG_TRAVEL) {
                serverLevel.playSound(
                        null,
                        user.getX(),
                        user.getY(),
                        user.getZ(),
                        D4CAddon.D4C_TRAVERSE_FANG.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || committed
                    || !(ability
                            instanceof D4CPhase2BAbility d4cAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null
                    || !D4CPhase2BService.canExecute(mode, user)) {
                // state changed during the charge
                String reason = user == null ? null
                        : D4CPhase2BService.refusalKey(mode, user);
                if (reason != null) {
                    D4CPhase2BService.notify(user, reason);
                }
                cancelLoveTrainPresentation();
                forceStop();
                return;
            }
            StandPower power = StandPower.get(user);
            float stamina = D4CPhase2BService.staminaCost(mode, user);
            committed = D4CPhase2BService.execute(
                    mode, serverLevel, user);
            if (!committed) {
                cancelLoveTrainPresentation();
                forceStop();
                return;
            }
            // 1.16 StandEntityTask paid after the perform with the result
            // ignored; a short bar just drops to 0.
            if (stamina > 0.0F) {
                StandAbilityStamina.consume(
                        ability, power, stamina, false);
            }
            playPerformSounds(serverLevel, user);
            if (mode == Mode.HAND_CHOP) {
                performHandChop();
            }
            if (mode == Mode.RAPID_PUNCH) {
                performRapidPunchStart();
            }
            d4cAbility.setCooldownOnUse(power);
        }

        @Override
        public void actionTick() {
            startLoveTrainPresentation();
            tickHandChop();
            if (!committed
                    || mode != Mode.RAPID_PUNCH
                    || getPhase() != ActionPhase.PERFORM
                    || !(level() instanceof ServerLevel)) {
                return;
            }
            // tick 0 runs before actionPerformStart, which strikes it
            int tick = (int) getPhaseTick();
            if (tick > 0) {
                playRapidSwingSound();
                tickRapidPunch(tick);
            }
        }

        // 1.16 StandEntity.updatePosition asked the task's getOffsetFromUser on
        // every tick, summon lock included, right before it placed the Stand:
        // front3dOffset of the task's target as it stood then, 0.5 to 2 for
        // the rapid punch (StandEntityLightAttack.getOffsetFromUser) and 0.5
        // to 3 for the hand chop (aa.java:74-78)
        @Override
        public void updateStandOffset(StandEntity stand) {
            double maxFront = switch (mode) {
                case RAPID_PUNCH -> 2.0D;
                case HAND_CHOP -> 3.0D;
                default -> 0.0D;
            };
            if (maxFront > 0.0D && !takesTargetsFromServer()) {
                StandEntityBarrageAbility.refreshFrontOffset(
                        this,
                        stand,
                        getTaskTarget(stand.level()),
                        Math.min(0.5D, stand.getEffectiveRange()),
                        Math.min(maxFront, stand.getMaxRange()));
            }
        }

        private void tickHandChop() {
            if (mode != Mode.HAND_CHOP
                    || !(getPerformer() instanceof StandEntity stand)
                    || level().isClientSide()) {
                return;
            }
            // 1.16 StandEntityHeavyAttack.standTickWindup and
            // clPlayPunchSwingSound: the swing with 3 windup ticks left
            if (getPhase() == ActionPhase.WINDUP
                    && !playedHeavySwing
                    && getCurPhaseLength() - getPhaseTick() <= 3.0F) {
                playedHeavySwing = true;
                broadcastStandSound(
                        stand,
                        stand.position(),
                        ModSoundEvents.STAND_PUNCH_HEAVY_SWING,
                        1.0F,
                        0.65F + stand.getRandom().nextFloat() * 0.2F);
            }
        }

        // 1.16 StandEntityLightAttack.standPerform (ac.java:110): 0.1 to the
        // finisher meter and a punch, then standTickPerform's first hit on
        // the same tick 0
        private void performRapidPunchStart() {
            if (getPerformer() instanceof StandEntity stand) {
                stand.addFinisherMeter(
                        D4CPhase2BSpec.RAPID_PERFORM_FINISHER);
            }
            performRapidHit();
            playRapidSwingSound();
            tickRapidPunch(0);
        }

        // 1.16 ac.java:117-118: standTickPerform's swing on every perform
        // tick, StandEntity.playSound at the Stand
        private void playRapidSwingSound() {
            if (getPerformer() instanceof StandEntity stand
                    && !stand.isSilent()
                    && level() instanceof ServerLevel serverLevel) {
                StandUtil.broadcastSound(
                        serverLevel,
                        stand.position(),
                        ModSoundEvents.STAND_PUNCH_SWING,
                        true,
                        StandPower.get(getPowerUser()),
                        stand.getSoundSource(),
                        0.5F,
                        1.0F);
            }
        }

        // 1.16 IHasStandPunch.playPunchImpactSound: a punch's impact sound
        // at the middle of the entity's box
        private void playImpactSound(StandEntity stand, Entity target,
                Holder<SoundEvent> sound) {
            playImpactSound(stand, target.getBoundingBox().getCenter(), sound);
        }

        private void playImpactSound(StandEntity stand, Vec3 pos,
                Holder<SoundEvent> sound) {
            broadcastStandSound(stand, pos, sound, 1.0F, 1.0F);
        }

        // 1.16 StandEntity.playSound: only players who can hear Stands
        private void broadcastStandSound(StandEntity stand, Vec3 pos,
                Holder<SoundEvent> sound, float volume, float pitch) {
            if (!stand.isSilent()
                    && level() instanceof ServerLevel serverLevel) {
                StandUtil.broadcastSound(
                        serverLevel,
                        pos,
                        sound,
                        true,
                        StandPower.get(getPowerUser()),
                        stand.getSoundSource(),
                        volume,
                        pitch);
            }
        }

        // 1.16 ac.java:119-135: a hit on every even task tick until the
        // sixth. One whose task still held an entity that had died struck
        // nothing; its stopTask was refused in PERFORM (canBeCanceled).
        private void tickRapidPunch(int tick) {
            if (tick % 2 != 0
                    || rapidHits >= D4CPhase2BSpec.RAPID_HITS) {
                return;
            }
            rapidHits++;
            if (!holdsDeadEntity()) {
                performRapidHit();
            }
        }

        // 1.16 StandEntityTask.keepTarget kept a dead entity until it was
        // removed or had lain dead for 20 ticks
        private boolean holdsDeadEntity() {
            ActionTarget held = getTaskTarget(level());
            return held.getType() == ActionTarget.TargetType.ENTITY
                    && held.getEntity() instanceof LivingEntity living
                    && !living.isAlive()
                    && !living.isRemoved()
                    && living.deathTime < 20;
        }

        @Override
        public void onButtonStopHold() {
            if (mode.chargeTicks > 0
                    && getPhase() == ActionPhase.BUTTON_CHARGE) {
                cancelLoveTrainPresentation();
                forceStop();
            }
        }

        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            if (!committed) {
                cancelLoveTrainPresentation();
            }
            super.onActionCleared(newAction);
        }

        private void startLoveTrainPresentation() {
            if (mode != Mode.LOVE_TRAIN
                    || presentationStarted
                    || presentationCancelled
                    || getPhase() != ActionPhase.BUTTON_CHARGE
                    || getPhaseTick() >= 1.0F
                    || !(level() instanceof ServerLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user != null) {
                presentationStarted = true;
                D4CLoveTrainSync.start(user);
            }
        }

        private void cancelLoveTrainPresentation() {
            if (mode != Mode.LOVE_TRAIN
                    || !presentationStarted
                    || presentationCancelled
                    || committed
                    || !(level() instanceof ServerLevel)) {
                return;
            }
            presentationCancelled = true;
            LivingEntity user = getPowerUser();
            if (user != null) {
                D4CLoveTrainSync.cancel(user);
            }
        }

        private void performRapidHit() {
            LivingEntity user = getPowerUser();
            if (user == null
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            // 1.16 ac.java standTickPerform: StandEntity.punch at the task's
            // target while the Stand reaches it, else along the user's view;
            // what it struck became the task's target (setTaskTarget): a miss
            // emptied it, a target the task does not keep left it
            ActionTarget punched = StandEntityPunchAbility.getFreshPunchTarget(
                    stand, getTaskTarget(level()));
            // 1.16 aimWithThisOrUser turned the Stand toward the aim in the
            // hit's own tick (MCUtil.rotateTowards)
            stand.turnTowardsStrike(StandEntityPunchAbility.strikeAim(
                    stand, punched,
                    StandEntityPunchAbility.CrosshairRule.BLOCK_OR_ENTITY));
            setTaskTarget(punched);
            if (punched.getType() == ActionTarget.TargetType.BLOCK) {
                // 1.16 StandEntityLightAttack.punchBlock: STAND_PUNCH_LIGHT at
                // the block's centre after the hit, judged on the block as it
                // was struck (StandBlockPunch.playImpactSound: hardness not 0)
                boolean impact = StandEntityPunchAbility.playHitSound(
                        punched, level());
                StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(
                        punched, level(), stand, true);
                if (impact) {
                    playImpactSound(stand,
                            Vec3.atCenterOf(punched.getBlockPos()),
                            ModSoundEvents.STAND_PUNCH_LIGHT);
                }
                return;
            }
            // 1.16 ac.punchEntity went through StandEntity.attackTarget for any
            // entity, one that is not living too (a boat, a minecart)
            Entity struck = punched.getMainEntity();
            if (struck == null
                    || struck instanceof D4CMinionEntity
                    || struck instanceof D4CAnnihilationCloneEntity) {
                return;
            }
            float damage = StandStatFormulas.getLightAttackDamage(
                    stand.getAttackDamage())
                    * D4CPhase2BSpec.RAPID_DAMAGE_MULTIPLIER;
            // read before the hit, which may remove the entity
            boolean admitted = damage > 0.0F && stand.canAttackEntity(struck);
            boolean hurt = standEntityAttack(
                    stand,
                    struck,
                    makePunchDamageSource(),
                    damage);
            // 1.16 StandEntityPunch.doHit added the punch's finisher whether
            // or not the hit hurt
            stand.addFinisherMeter(
                    D4CPhase2BSpec.RAPID_FINISHER_PER_HIT);
            if (!(struck instanceof LivingEntity target)) {
                // 1.16 StandEntityPunch.knockbackTarget: half the punch's
                // additional knockback became the entity's motion, away from
                // the Stand, hurt or not; attackTarget always played the impact
                if (admitted) {
                    Vec3 toTarget = struck.position()
                            .subtract(stand.position());
                    float yaw = (float) -Mth.atan2(toTarget.x, toTarget.z)
                            * Mth.RAD_TO_DEG;
                    struck.setDeltaMovement(new Vec3(0.0D, 0.0D,
                            D4CRuntimeRules.rapidBaseKnockback(rapidHits)
                                    * 0.5D)
                            .yRot(-yaw * Mth.DEG_TO_RAD));
                }
                playImpactSound(stand, struck,
                        ModSoundEvents.STAND_PUNCH_LIGHT);
                return;
            }
            if (!hurt) {
                return;
            }
            target.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION,
                    100,
                    0,
                    false,
                    false,
                    true));
            Vec3 direction = target.position()
                    .subtract(stand.position());
            if (direction.horizontalDistanceSqr() > 0.0D) {
                direction = direction.normalize();
                double baseKnockback =
                        D4CRuntimeRules.rapidBaseKnockback(rapidHits);
                target.push(
                        direction.x * baseKnockback,
                        0.0D,
                        direction.z * baseKnockback);
                if (rapidHits >= D4CPhase2BSpec.RAPID_HITS) {
                    target.push(
                            direction.x
                                    * D4CPhase2BSpec
                                            .RAPID_FINAL_HORIZONTAL_KNOCKBACK,
                            D4CPhase2BSpec
                                    .RAPID_FINAL_UPWARD_KNOCKBACK,
                            direction.z
                                    * D4CPhase2BSpec
                                            .RAPID_FINAL_HORIZONTAL_KNOCKBACK);
                }
                target.hurtMarked = true;
            }
            // 1.16 ac.java:158 impactSound(STAND_PUNCH_LIGHT)
            playImpactSound(stand, target, ModSoundEvents.STAND_PUNCH_LIGHT);
        }

        private void performHandChop() {
            if (!(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            // 1.16 StandEntityHeavyAttack.standPerform (aa/aa/aa.java:61):
            // StandEntity.punch at the task's target while the Stand reaches
            // it, else aimed again
            ActionTarget punched = StandEntityPunchAbility.getFreshPunchTarget(
                    stand, getTaskTarget(level()));
            stand.turnTowardsStrike(StandEntityPunchAbility.strikeAim(
                    stand, punched, StandEntityPunchAbility.CrosshairRule.ENTITY));
            setTaskTarget(punched);
            // 1.16 StandEntityHeavyAttack.punchBlock: a HeavyPunchBlockInstance
            // with d4c_hand_chop's punchSound (ai/ah.java:72)
            if (punched.getType() == ActionTarget.TargetType.BLOCK) {
                StandEntityHeavyPunchAbility.StandEntityHeavyPunch
                        .hitBlockAsHeavyPunch(
                                punched,
                                level(),
                                stand,
                                !isUserCreative(),
                                ModSoundEvents.THE_WORLD_PUNCH_HEAVY);
                return;
            }
            // 1.16 aa/aa/aa.java:80-84: punchEntity is a plain
            // StandEntityPunch, not the heavy punch's HeavyPunchInstance, so
            // no extra knockback, wall impact or Stand invulnerability ticks;
            // it struck any entity, one that is not living too
            Entity struckEntity = punched.getMainEntity();
            if (struckEntity == null
                    || struckEntity instanceof D4CMinionEntity
                    || struckEntity instanceof D4CAnnihilationCloneEntity) {
                return;
            }
            float damage = StandStatFormulas.getHeavyAttackDamage(
                    stand.getAttackDamage())
                    * D4CPhase2BSpec
                            .HAND_CHOP_DAMAGE_MULTIPLIER;
            boolean hurt = standEntityAttack(
                    stand,
                    struckEntity,
                    makePunchDamageSource(),
                    damage);
            if (hurt && struckEntity instanceof LivingEntity target) {
                // 1.16 aa/aa/aa.java:95 (100, 0, false, false):
                // no particles and, like 1.16, no icon.
                target.addEffect(new MobEffectInstance(
                        D4CAddon.BLEEDING,
                        100,
                        0,
                        false,
                        false,
                        false));
                // 1.16 aa/aa/aa.java:100-111: 40 drops sent one by one, each
                // at its own spot around the feet with its own spread
                if (target.level()
                        instanceof ServerLevel serverLevel) {
                    Vec3 feet = target.position();
                    RandomSource random = serverLevel.random;
                    for (int i = 0; i < D4CPhase2BSpec
                            .HAND_CHOP_BLOOD_PARTICLES; i++) {
                        double x = feet.x + (random.nextDouble() - 0.5D) * 2.5D;
                        double y = feet.y + random.nextDouble() * 2.0D;
                        double z = feet.z + (random.nextDouble() - 0.5D) * 2.5D;
                        double spreadX = (random.nextDouble() - 0.5D) * 0.3D;
                        double spreadY = random.nextDouble() * 0.2D;
                        double spreadZ = (random.nextDouble() - 0.5D) * 0.3D;
                        serverLevel.sendParticles(
                                ModParticles.BLOOD.get(),
                                x, y, z,
                                1,
                                spreadX, spreadY, spreadZ,
                                0.15D);
                    }
                }
            }
            // 1.16 StandEntityPunch.doHit: the punch's finisher, none for the
            // hand chop, still held the meter from decaying for 40 ticks
            stand.addFinisherMeter(0.0F);
            // 1.16 StandEntity.attackTarget: the impact sound (aa.java:83)
            // once the hit landed, always on an entity that is not living
            if (hurt || !(struckEntity instanceof LivingEntity)) {
                playImpactSound(stand, struckEntity, D4CAddon.D4C_HANDCHOP);
            }
        }
    }
}
