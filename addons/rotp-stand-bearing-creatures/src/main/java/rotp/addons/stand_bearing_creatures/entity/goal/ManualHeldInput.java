package rotp.addons.stand_bearing_creatures.entity.goal;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.subsystems.target.ActionTarget;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

/**
 * A key pressed for a Stand user outside its AI goal: the Debug Stand's
 * Use Random Action and /rotp_mwp use_random. 1.16 050 UseRandomAction
 * called setHeldAction for a hold-to-fire action instead of clicking it, so
 * the key stays down and is let go by the goal's own rule. Server thread
 * only: every entry point ignores the client, which also runs a mob's
 * death; a press is lost with the entity, like the action it started.
 */
public final class ManualHeldInput {
    private static final Map<LivingEntity, Held> HELD =
            new WeakHashMap<>();

    // The action is held weakly: it leads back to its user, which would
    // otherwise never leave the weak map.
    private record Held(
            short keyId,
            PowerClass<?> powerClass,
            @Nullable WeakReference<EntityActionInstance> action,
            String abilityName,
            int sinceTick) {}

    // A range of its own: the core's pseudo keys for a mob's goal count up from 1.
    private static final AtomicInteger KEY_IDS = new AtomicInteger();

    private static short nextKeyId() {
        return (short) (0x5000 + (KEY_IDS.getAndIncrement() & 0x0FFF));
    }

    public static boolean press(Ability ability, LivingEntity user) {
        if (user.level().isClientSide()) {
            return false;
        }
        // 1.16 UseRandomAction's click was refused there, the hold kept.
        if (MobPowerUtil.resumesOwnTimeStop(user, ability)) {
            return false;
        }
        // 1.16 setHeldAction let go of the previous hold first.
        release(user);
        // 1.16 UseRandomAction pressed at AddonUtil.getActionTarget.
        ActionTarget aim = AbstractPowerMobGoal.aimAlong(
                user, AbstractPowerMobGoal.lookTarget(user));
        // There 1.16 clickAction's checkRequirements refused a move its
        // conditions or target refused, and the first held tick stopped a
        // setHeldAction one; the core asks nothing of a mob's press.
        PowerClass<?> powerClass = ability.abilityId.powerClass();
        Power<?> power = powerClass != null ? powerClass.get(user) : null;
        ConditionCheck check = power != null
                ? ability.checkConditions(power)
                : ConditionCheck.NEGATIVE;
        if (!check.isPositive() && !check.shouldContinueHold()) {
            return false;
        }
        // 1.16 canClickDuringTask :495-497 refused the running task's own
        // ability, silent and unqueued; the release above left its state, as
        // before the core's keyPressMob asks.
        if (power != null
                && AvailableAbilities.refusesRunningActionPressedAgain(
                        power, ability)) {
            return false;
        }
        // The core's keyPressMob drops the press's result, and a follow-up
        // the core takes without holding a key went off all the same.
        BufferingState pressed = BufferingState.clickOnly();
        HeldInputEntry held = AbilityInput.keyPress(
                nextKeyId(),
                ability,
                user,
                new FriendlyByteBuf(Unpooled.EMPTY_BUFFER),
                InputMethod.HOLD,
                0.0F,
                pressed,
                null);
        if (held == null) {
            return pressed.isActionSuccess;
        }
        EntityActionInstance action =
                held.action instanceof EntityActionInstance started
                        ? started
                        : null;
        HELD.put(user, new Held(
                held.keyId,
                held.powerClass,
                action != null ? new WeakReference<>(action) : null,
                ability.name(),
                user.tickCount));
        AbstractPowerMobGoal.keepManualPressAim(
                user, held.powerClass, aim, action);
        return true;
    }

    // 1.16 kept one held action per power.
    public static boolean isHolding(
            LivingEntity user, PowerClass<?> powerClass) {
        if (user.level().isClientSide()) {
            return false;
        }
        Held held = HELD.get(user);
        return held != null && held.powerClass() == powerClass;
    }

    public static void tick(LivingEntity user) {
        if (user.level().isClientSide()) {
            return;
        }
        Held held = HELD.get(user);
        if (held == null) {
            return;
        }
        int heldTicks = user.tickCount - held.sinceTick();
        boolean done;
        if (held.action() == null) {
            done = heldTicks >= AbstractPowerMobGoal.fallbackHoldTicks(
                    held.abilityName());
        }
        else {
            // A collected action ended long ago.
            EntityActionInstance action = held.action().get();
            done = action == null
                    || AbstractPowerMobGoal.shouldReleaseHeld(
                            action,
                            held.abilityName(),
                            heldTicks);
        }
        if (done) {
            release(user);
        }
    }

    public static void release(LivingEntity user) {
        if (user.level().isClientSide()) {
            return;
        }
        Held held = HELD.remove(user);
        if (held != null) {
            AbilityInput.keyRelease(held.keyId(), user);
        }
    }

    public static void releaseOnTargetLoss(
            LivingEntity user, PowerClass<?> powerClass, boolean fire) {
        if (user.level().isClientSide()) {
            return;
        }
        Held held = HELD.get(user);
        if (held == null || held.powerClass() != powerClass) {
            return;
        }
        if (fire && held.action() != null) {
            AbstractPowerMobGoal.fireHeldAction(held.action().get());
        }
        release(user);
    }

    private ManualHeldInput() {}
}
