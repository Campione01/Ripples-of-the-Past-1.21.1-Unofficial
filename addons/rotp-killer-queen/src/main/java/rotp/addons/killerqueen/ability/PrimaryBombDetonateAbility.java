package rotp.addons.killerqueen.ability;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenPhase1Spec;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class PrimaryBombDetonateAbility
        extends StandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 20;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 10;

    public PrimaryBombDetonateAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DetonateAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        resolveLevelToUnlock(1);
        // 1.16 EntityExplode.checkConditions replaced StandAction's without
        // calling it, so no Stand part was asked for, at the press or held
        noFinisherBarDecay = true;
        // 1.16 kq_entity_explo cancelHeldOnGettingAttacked: a hit during
        // the charge drops it.
        setCancelHeldOnGettingAttacked();
        // 1.16 StandEntityLightAttack.Builder :193: pressed with Killer Queen
        // away, only its main arm comes out
        standAutoSummonMode(AutoSummonMode.MAIN_ARM);
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return HOLD_TO_FIRE_TICKS;
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        Player player = PrimaryBombAbility.activePlayer(context);
        return super.isAbilityAvailable(context)
                && player != null
                && KillerQueenStateService.primaryBombTarget(player)
                        != null;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        // 1.16 EntityExplode: refused while Bites the Dust is armed
        if (BitesTheDustKitLock.locked(context)) {
            return ConditionCheck.NEGATIVE;
        }
        Player player = PrimaryBombAbility.activePlayer(context);
        if (player == null
                || KillerQueenStateService.primaryBombTarget(player)
                        == null) {
            return ConditionCheck.createNegative(
                    "killer_queen_no_primary_bomb");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 tickHeldAction re-ran EntityExplode.checkConditions on every held
    // tick: only the armed Bites the Dust, not the bomb
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return BitesTheDustKitLock.locked(context)
                ? ConditionCheck.NEGATIVE
                : ConditionCheck.POSITIVE;
    }

    // 1.16 EntityExplode kept StandEntityLightAttack.standKeepsTarget: its
    // task held what it was pressed at, of any kind
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    // 1.16 EntityExplode.stopOnHeavyAttack: a heavy punch that hurt the Stand
    // sent the detonation into its recovery, so nothing went off
    @Override
    public boolean stopsOnHeavyAttack(EntityActionInstance action) {
        return true;
    }

    // 1.16 StandEntityLightAttack.lockOnTargetPosition
    @Override
    public boolean lockOnTargetPosition(StandPower standPower,
            StandEntity standEntity, EntityActionInstance action) {
        return false;
    }

    // 1.16 EntityExplode (kq_entity_explo): a StandEntityLightAttack (KillerQueenLightAttack)
    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        KillerQueenLightAttack.setPhases(this, action, level, performer,
                KillerQueenLightAttack.Windup.AFTER_HOLD);
    }

    public static final class DetonateAction
            extends EntityActionInstance {
        public DetonateAction(EntityActionType ability) {
            super(ability);
        }

        private final KillerQueenLightAttack.SwingSound swingSound =
                new KillerQueenLightAttack.SwingSound();

        @Override
        public void actionTick() {
            super.actionTick();
            swingSound.tick(this);
        }

        // 1.16 StandEntityAction.startedHolding set the task with the
        // pressed target, which the Stand turned to (rotateStand) and stood
        // before its user toward (StandEntityLightAttack.getOffsetFromUser)
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!level().isClientSide()
                    && getPerformer() instanceof StandEntity stand) {
                // 1.16 EntityExplode.onTaskSet: an arms-only Stand shows its
                // main arm alone
                if (stand.isArmsOnlyMode()) {
                    stand.setArmsOnlyMode(true, false);
                }
                ActionTarget target = captureActionTargetFromAim(stand);
                KillerQueenLightAttack.standAtFront(this, stand, target);
                keepStandAimedAtTarget(target);
            }
        }

        @Override
        protected void onTaskTargetInherited(ActionTarget target) {
            super.onTaskTargetInherited(target);
            if (getPerformer() instanceof StandEntity stand) {
                KillerQueenLightAttack.standAtFront(this, stand, target);
            }
        }

        @Override
        public void updateStandOffset(StandEntity stand) {
            KillerQueenLightAttack.refreshAtFront(this, stand);
        }

        /**
         * 1.16 kq_entity_explo standSound(Phase.RECOVERY, KQ_BOMB) (InitStands :85): killer_queen_boom at the Stand as
         * a recovery with ticks began (StandEntityTask.moveToPhase), filtered by StandEntityLightAttack.getSounds
         * :120-124: only with an entity as the task's target, outside arms-only mode and with an empty finisher bar.
         */
        public boolean boomsAsRecoveryBegins(StandEntity stand) {
            return phasesLength.getFloat(ActionPhase.RECOVERY) > 0
                    && getTaskTarget(level()).getType()
                            == ActionTarget.TargetType.ENTITY
                    && !stand.isArmsOnlyMode()
                    && stand.getFinisherMeter() <= 0;
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.RECOVERY
                    && level().isClientSide()
                    && getPerformer() instanceof StandEntity stand
                    && ClientGlobals.canHearStand(stand)
                    && boomsAsRecoveryBegins(stand)) {
                // 1.16 playSoundAtStand: a recovery that can be cancelled
                // cuts the sound off when another action replaces it
                ClientsideSoundsHelper.playNonVanillaClassSound(
                        new EntityStoppableSoundInstance(
                                ClientsideSoundsHelper.withStandSkin(
                                        KillerQueenAddon.KILLER_QUEEN_BOOM
                                                .get(),
                                        stand),
                                stand.getSoundSource(), 1.0F, 1.0F, stand,
                                stand.getRandom().nextLong(),
                                () -> isOver() || getPhase()
                                        != ActionPhase.RECOVERY));
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity)
                    || !(getPowerUser()
                            instanceof ServerPlayer user)) {
                return;
            }
            StandPower power = StandPower.get(user);
            // 1.16 EntityExplode.standPerform: silent below the floor
            if (power != null
                    && power.getStamina()
                            > KillerQueenPhase1Spec
                                    .PRIMARY_BOMB_DETONATE_STAMINA_FLOOR) {
                LivingEntity target =
                        KillerQueenStateService.primaryBombTarget(user);
                if (target != null) {
                    explode(user, target);
                }
            }
            // 1.16 StandEntityTask pays the cost after standPerform,
            // whatever came of it
            StandAbilityStamina.consume(
                    ability,
                    power,
                    KillerQueenPhase1Spec.PRIMARY_BOMB_DETONATE_STAMINA,
                    false);
        }

        // 1.16 EntityExplode.standPerform: the explosion's own sound only
        private void explode(ServerPlayer user, LivingEntity target) {
            float width = target.getBbWidth();
            float radius = (float) Math.sqrt(
                    14.0D
                            * target.getBbHeight()
                            * width
                            * width);
            level().explode(
                    user,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    radius,
                    Level.ExplosionInteraction.NONE);
            KillerQueenStateService.clearPrimaryBomb(user);
        }

        // 1.16 EntityExplode was a StandEntityLightAttack: its recovery gave way to
        // any Stand action, a new detonation too (isCancelable, isChainable,
        // isFreeRecovery), while its windup and perform queued them.
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return getPhase() == ActionPhase.RECOVERY;
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
            }
        }
    }
}
