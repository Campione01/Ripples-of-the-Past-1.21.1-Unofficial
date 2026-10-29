package rotp.addons.killerqueen.ability;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 StandEntityLightAttack, which most Killer Queen bomb moves were built on: getStandWindupTicks :99-105,
 * getStandActionTicks :107-111 and getStandRecoveryTicks :113-117 took StandStatFormulas' light-attack values from the
 * Stand whatever the builder set, and isCancelable :127-129 with isChainable and isFreeRecovery let any action, the
 * same one too, replace it in its recovery.
 */
public final class KillerQueenLightAttack {
    enum Windup {
        // the light attack's own
        LIGHT,
        // PunchBomb.getStandWindupTicks :99-102: the heavy attack's
        HEAVY,
        // EntityExplode after its hold: the task was already this action, so not a first punch
        AFTER_HOLD
    }

    private KillerQueenLightAttack() {}

    /** Server: the phase lengths a press sets, read from the Stand as 1.16 read them. */
    static void setPhases(EntityActionType ability, EntityActionInstance action, Level level,
            LivingEntity performer, Windup windup) {
        if (level.isClientSide() || !(performer instanceof StandEntity stand)) {
            return;
        }
        double speed = stand.getAttackSpeed();
        float finisher = stand.getFinisherMeter();
        EntityActionInstance previous = stand.getCurStandAction();
        boolean firstPunch = windup != Windup.AFTER_HOLD
                && (previous == null || previous.isOver() || previous.ability != ability);
        action.phasesLength.put(ActionPhase.WINDUP, windup == Windup.HEAVY
                ? StandStatFormulas.getHeavyAttackWindup(speed, finisher)
                : StandStatFormulas.getLightAttackWindup(speed, finisher, stand.guardCounter(), firstPunch));
        int perform = StandStatFormulas.getLightAttackRecovery(speed, finisher);
        action.phasesLength.put(ActionPhase.PERFORM, perform);
        action.phasesLength.put(ActionPhase.RECOVERY, perform * (stand.isArmsOnlyMode() ? 2 : 4));
    }

    static boolean givesWay(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.RECOVERY;
    }

    /**
     * Server: 1.16 StandEntityLightAttack.onTaskSet :131-140: each light-attack task alternated the Stand's punching
     * hand, and an arms-only Stand whose swing went to the off hand showed both arms. An action a failed input puts
     * back keeps its hand, as the core punch's does.
     */
    static void alternateHands(EntityActionInstance action, StandEntity stand) {
        if (stand.level().isClientSide() || action.isRestoringInputLifecycle()) {
            return;
        }
        if (stand.alternateHands() == InteractionHand.OFF_HAND && stand.isArmsOnlyMode()) {
            stand.setArmsOnlyMode(true, true);
        }
    }

    /**
     * Server: getOffsetFromUser :169-176, front3dOffset: the Stand stood before its user, 0.5 to 2 blocks toward the
     * task's target (the user's crosshair within 2 blocks without one), along the user's view (lockOnTargetPosition
     * false); none in arms-only mode.
     */
    static void standAtFront(EntityActionInstance action, StandEntity stand, ActionTarget target) {
        action.setStandFrontOffsetFromTarget(stand, target, Math.min(0.5D, stand.getEffectiveRange()),
                Math.min(2.0D, stand.getMaxRange()));
    }

    /**
     * Server, from the Stand's position update (EntityActionInstance#updateStandOffset): 1.16 StandEntity.updatePosition
     * asked the task's getOffsetFromUser on every tick, so front3dOffset followed a target or a user that moved, and
     * with no task target what the user's crosshair met, in that same tick.
     */
    static void refreshAtFront(EntityActionInstance action, StandEntity stand) {
        StandEntityBarrageAbility.refreshFrontOffset(action, stand, action.getTaskTarget(stand.level()),
                Math.min(0.5D, stand.getEffectiveRange()), Math.min(2.0D, stand.getMaxRange()));
    }

    /**
     * getSounds :120-124: the action's standSound played only with an entity as its task's target, outside arms-only
     * mode and with an empty finisher bar.
     */
    static boolean playsStandSound(EntityActionInstance action, StandEntity stand) {
        return action.getTaskTarget(stand.level()).getType() == ActionTarget.TargetType.ENTITY
                && !stand.isArmsOnlyMode()
                && stand.getFinisherMeter() <= 0;
    }

    /**
     * 1.16 StandEntityLightAttack.standTickPerform :89-91 (IHasStandPunch.playPunchSwingSound :56-64): each client
     * played the builder's swing sound (ModSounds.STAND_PUNCH_SWING, :187) once in the perform, on its first tick when
     * it ran under 5 ticks, else with 5 ticks left, at the Stand for the players who hear Stands
     * (clPlayPunchSwingSound :94-96, StandEntity.playSound), whatever the task held or the Stand showed.
     */
    public static final class SwingSound {
        private boolean played;

        public static boolean due(EntityActionInstance action) {
            return action.getPhase() == ActionPhase.PERFORM
                    && (action.getCurPhaseLength() < 5 || action.getPhaseTicksLeft() <= 5);
        }

        void tick(EntityActionInstance action) {
            if (played || !action.level().isClientSide()
                    || !(action.getPerformer() instanceof StandEntity stand) || !due(action)) {
                return;
            }
            played = true;
            if (ClientGlobals.canHearStand(stand)) {
                stand.level().playLocalSound(stand.getX(), stand.getY(), stand.getZ(),
                        ClientsideSoundsHelper.withStandSkin(ModSoundEvents.STAND_PUNCH_SWING.get(), stand),
                        stand.getSoundSource(), 1.0F, 0.9F + stand.getRandom().nextFloat() * 0.2F, false);
            }
        }
    }
}
