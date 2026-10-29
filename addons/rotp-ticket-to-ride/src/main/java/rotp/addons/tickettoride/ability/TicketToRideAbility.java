package rotp.addons.tickettoride.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModSoundEvents;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.client.TicketToRideClientSounds;
import rotp.addons.tickettoride.state.LoveTrainService;
import rotp.addons.tickettoride.state.TearsWoundService;
import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class TicketToRideAbility
        extends NoPoseStandEntityAbility {
    // firesOnRelease: 1.16 holdToFire(n, true), or holdType() with
    // holdToFire(n, false), waits for the key to be let go after a full
    // charge; the others fire as soon as the charge completes.
    public enum Mode {
        EXPLOD(20, 20, 75.0F, 120, 2, true),
        TRACE(20, 20, 750.0F, 1200, 3, true),
        CRASH(20, 20, 150.0F, 0, 3, false),
        RANDOM_MOB(20, 20, 75.0F, 0, 3, false),
        LIGHTNING_SINGLE(12, 0, 75.0F, 120, 0, true),
        LIGHTNING(30, 0, 75.0F, 1200, 0, true),
        METEOR(20, 20, 750.0F, 0, 3, false),
        LOVE_TRAIN(50, 0, 0.0F, 1200, 3, true),
        SHARE_LOVE_TRAIN(0, 0, 400.0F, 0, 3, false),
        REMOVE_SHARED(0, 0, 400.0F, 0, 3, false),
        REMOVE_LOVE_TRAIN(0, 0, 0.0F, 0, 0, false);

        private final int holdTicks;
        private final int recoveryTicks;
        private final float stamina;
        private final int cooldown;
        private final int resolveLevel;
        private final boolean firesOnRelease;

        Mode(
                int holdTicks,
                int recoveryTicks,
                float stamina,
                int cooldown,
                int resolveLevel,
                boolean firesOnRelease) {
            this.holdTicks = holdTicks;
            this.recoveryTicks = recoveryTicks;
            this.stamina = stamina;
            this.cooldown = cooldown;
            this.resolveLevel = resolveLevel;
            this.firesOnRelease = firesOnRelease;
        }

        // 1.16 InitStands partsRequired
        private StandPart[] partsRequired() {
            return switch (this) {
                case EXPLOD, TRACE ->
                        new StandPart[] {StandPart.ARMS};
                case CRASH, RANDOM_MOB, METEOR ->
                        new StandPart[] {StandPart.MAIN_BODY};
                default -> new StandPart[0];
            };
        }
    }

    private final Mode mode;

    public TicketToRideAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Mode mode) {
        super(
                abilityType,
                abilityId,
                type -> new TicketToRideAction(type, mode));
        this.mode = mode;
        setDefaultPhaseLength(
                ActionPhase.WINDUP, mode.holdTicks);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY, mode.recoveryTicks);
        if (mode.cooldown > 0) {
            cooldown(mode.cooldown);
        }
        if (mode.resolveLevel > 0) {
            resolveLevelToUnlock(mode.resolveLevel);
        }
        partsRequired(mode.partsRequired());
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return mode.holdTicks;
    }

    // The charging windup is the 1.16 hold, so a release from any bind
    // still cancels or fires it; the zero-hold modes were plain clicks.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return mode.holdTicks > 0
                        && action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick), and the Stand queued none
    // behind its task (canBeQueued). Share, remove and the dispel (a click,
    // the owner's call) have no hold.
    @Override
    public boolean isHoldAction() {
        return mode.holdTicks > 0;
    }

    // 1.16 GiveLF / RemoveLF (034 InitStands :69-70) were StandActions
    // their user's power performed at once, never the Stand's task: they went
    // off beside a running task, which neither queued nor held them back.
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return mode == Mode.SHARE_LOVE_TRAIN || mode == Mode.REMOVE_SHARED;
    }

    // 1.16 stopHeldAction(false): a hold stopped by anything but the
    // release never fires, a full charge included.
    @Override
    public void stopHeldActionOnGettingAttacked(
            EntityActionInstance action) {
        action.forceStop();
        action.syncPhaseChanges();
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base =
                super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        return TicketToRideAbilityService.canUse(
                mode, context.getUser());
    }

    // 1.16 tickHeldAction and stopHeldAction(true) re-ran checkConditions;
    // of the held modes only trace (its held item) and love train had
    // checks of their own.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return mode == Mode.TRACE || mode == Mode.LOVE_TRAIN
                ? TicketToRideAbilityService.canUse(
                        mode, context.getUser())
                : ConditionCheck.POSITIVE;
    }

    // 1.16 MobCrash.standKeepsTarget: a living entity the tears marked.
    // GiveLF and RemoveLF were plain StandActions, with no Stand task.
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return mode == Mode.CRASH
                && target.getType() == ActionTarget.TargetType.ENTITY
                && target.getEntity() instanceof LivingEntity living
                && TearsWoundService.isMarked(living);
    }

    // 1.16 InitStands standSound: explod and crash STAND_PUNCH_LIGHT,
    // randommob AJA_STONE_BEAM.
    @Nullable
    public static SoundEvent standPerformSound(Mode mode) {
        return switch (mode) {
            case EXPLOD, CRASH -> ModSoundEvents.STAND_PUNCH_LIGHT.get();
            case RANDOM_MOB -> ModSoundEvents.AJA_STONE_BEAM.get();
            default -> null;
        };
    }

    // Owner decision (wave 28): the donor registered meteor with
    // attackRecoveryFollowup(LIGHTING), which the 1.16 core never offered.
    // While meteor recovers, its slot gives lighting if lighting could be
    // used then (1.16 StandAction.canSetFollowUp).
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        if (mode == Mode.LIGHTNING
                && abilities != null
                && isMeteorRecovering(context)
                && abilities.getConditionCheck(name()).isPositive()) {
            abilities.replaceOtherAbilityWith(
                    context, TicketToRideAddon.METEOR, this);
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    public static boolean isMeteorRecovering(Power<?> context) {
        StandPower standPower = PowerClass.STAND.cast(context);
        StandEntity stand = standPower != null
                ? standPower.getSummonedStandEntity()
                : null;
        return stand != null
                && LivingComponentAction.getCurEntityAction(stand)
                        instanceof TicketToRideAction action
                && action.isMeteorRecovery();
    }

    public static final class TicketToRideAction
            extends EntityActionInstance {
        private final Mode mode;
        private int heldTicks;

        public TicketToRideAction(
                EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        // 1.16 MobCrash's task took what the user pointed at, a block without
        // a collision shape giving way to what the Stand's ray met behind it
        // (StandEntityAction.overrideVanillaMouseTarget), and its Stand
        // turned to a target the task kept.
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            LivingEntity user = getPowerUser();
            if (mode == Mode.CRASH
                    && !level().isClientSide()
                    && user != null
                    && getPerformer() instanceof StandEntity stand
                    && ability instanceof TicketToRideAbility crash) {
                ActionTargetAim aim = LivingComponentAction.getAim(user);
                ActionTarget pressed = aim != null && aim.getTarget() != null
                        ? crash.overridePressedTarget(user, aim.getTarget())
                                .resolveEntityId(level())
                        : ActionTarget.EMPTY;
                if (crash.keepsTaskTarget(stand, pressed)) {
                    keepStandAimedAtTarget(pressed);
                }
            }
            // 1.16 GiveLF / RemoveLF performed on the target their press was
            // checked with; a Stand this press summoned has no aim of its own.
            if ((mode == Mode.SHARE_LOVE_TRAIN || mode == Mode.REMOVE_SHARED)
                    && !level().isClientSide()
                    && user != null
                    && !isRestoringInputLifecycle()) {
                Entity pressed = TicketToRideAbilityService.pressedEntity(
                        mode, user);
                setActionTargetSnapshot(
                        pressed != null ? new ActionTarget(pressed) : null);
            }
        }

        // 0.2.2.2 took staminaCostTick only in PERFORM, so the lightning
        // charge costs nothing; the 75 is paid once when it fires.
        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.WINDUP) {
                return;
            }
            LivingEntity user = getPowerUser();
            ++heldTicks;
            if (level().isClientSide() && user != null
                    && mode == Mode.LOVE_TRAIN) {
                LoveTrainService.holdParticles(
                        user.position(), heldTicks, level().random,
                        (type, x, y, z, vx, vy, vz) -> level().addParticle(
                                type, x, y, z, vx, vy, vz));
            }
            if (level().isClientSide() && user != null) {
                Vec3 at = TicketToRideAbilityService.holdParticleAt(
                        mode, user, getPerformer());
                if (at != null) {
                    level().addParticle(
                            TicketToRideAddon.LOVE_TRAIN_LIGHT.get(),
                            at.x, at.y, at.z,
                            0.0D, 0.0D, 0.0D);
                }
            }
            if (!level().isClientSide()
                    && mode
                            == Mode.LIGHTNING_SINGLE
                    && user != null
                    && heldTicks % 10 == 0) {
                TicketToRideAbilityService.pullMarked(user);
            }
        }

        boolean isMeteorRecovery() {
            return mode == Mode.METEOR
                    && getPhase() == ActionPhase.RECOVERY;
        }

        // The meteor -> lighting follow-up: lighting, from either of its
        // slots, ends meteor's recovery; nothing else does.
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return isMeteorRecovery()
                    && cancellingAbility
                            instanceof TicketToRideAbility next
                    && next.mode == Mode.LIGHTNING;
        }

        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return mode.firesOnRelease
                    && getPhase() == ActionPhase.WINDUP;
        }

        // 1.16 standSound: each client played it when the task entered
        // PERFORM and stopped it when that phase ended.
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            SoundEvent sound = standPerformSound(mode);
            if (sound != null
                    && newPhase == ActionPhase.PERFORM
                    && getPerformer() instanceof StandEntity stand
                    && stand.level().isClientSide()) {
                TicketToRideClientSounds.playPhaseSound(
                        stand,
                        sound,
                        this,
                        newPhase);
            }
        }

        // 1.16 stopHeldAction(true) checked the requirements once more
        // before the released charge fired.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                if (mode.firesOnRelease
                        && getPhaseTick() >= getCurPhaseLength()
                        && ability instanceof TicketToRideAbility held
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
            if (!(level() instanceof ServerLevel level)
                    || !(ability
                            instanceof TicketToRideAbility current)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null) {
                return;
            }
            // 1.16 paid the cost with the result ignored (StandEntityTask,
            // StandAction.onPerform); a short bar just drops to 0
            if (mode.stamina > 0.0F) {
                StandAbilityStamina.consume(
                        (Ability) current, power, mode.stamina, false);
            }
            if (TicketToRideAbilityService.perform(
                    level, user, power, mode,
                    getActionTargetSnapshot(level).getMainEntity())) {
                current.setCooldownOnUse(power);
            }
        }
    }
}
