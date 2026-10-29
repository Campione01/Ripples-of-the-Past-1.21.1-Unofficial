package rotp.addons.catchtherainbow;

import javax.annotation.Nullable;

import rotp.addons.catchtherainbow.ability.TempHealthAbility;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.item.CatchTheRainbowMaskItem;
import net.minecraft.world.entity.LivingEntity;

/** Unconditional, idempotent teardown for transient Catch state. */
public final class CatchTheRainbowCleanup {
    private CatchTheRainbowCleanup() {}

    public static void clearTransientState(
            @Nullable LivingEntity user,
            @Nullable CatchTheRainbowEntity controller) {
        runLifecycle(
                Lifecycle.GENERIC,
                controller != null,
                steps(user, controller));
    }

    public static void onControllerMissing(
            @Nullable LivingEntity user) {
        runLifecycle(
                Lifecycle.CONTROLLER_MISSING,
                false,
                steps(
                        user,
                        null,
                        () -> TempHealthAbility.clearIfStranded(user)));
    }

    // 1.16 saved a leaving player before its Stand noticed, so Temp
    // Health's Absorption stayed in the save and only the transient
    // max-health cut was lost.
    public static void onLogout(
            @Nullable LivingEntity user,
            @Nullable CatchTheRainbowEntity controller) {
        runLifecycle(
                Lifecycle.LOGOUT,
                controller != null,
                steps(
                        user,
                        controller,
                        () -> TempHealthAbility.endMaxHealthCut(user)));
    }

    public static void onUnsummon(
            @Nullable LivingEntity user,
            @Nullable CatchTheRainbowEntity controller) {
        runLifecycle(
                Lifecycle.UNSUMMON,
                controller != null,
                steps(user, controller));
    }

    public static void onControllerRemoved(
            @Nullable LivingEntity user,
            @Nullable CatchTheRainbowEntity controller) {
        runLifecycle(
                Lifecycle.CONTROLLER_REMOVE,
                controller != null,
                steps(user, controller));
    }

    public static void runUnconditional(Runnable... steps) {
        CatchTheRainbowCleanupSequence.runUnconditional(steps);
    }

    public static boolean isTransientStateCleared(
            @Nullable LivingEntity user,
            @Nullable CatchTheRainbowEntity controller) {
        return (controller == null
                        || !controller.isBladeRainActive()
                                && !controller.isRainWalkingActive()
                                && !controller.isMergeActive()
                                && !controller.isTempHealthActive())
                && !CatchTheRainbowEntity.hasRainWalkingSupport(user)
                && !CatchTheRainbowEntity.hasTransientReach(user)
                && !hasEffect(
                        user, CatchTheRainbowAddon.RAIN_MERGE_EFFECT)
                && !hasRedemptionResidue(user)
                && !TempHealthAbility.hasOwnedState(user)
                && !CatchTheRainbowMaskItem.containsAny(user);
    }

    public static boolean restorationPending(
            boolean sceneStarted, boolean alreadyCleaned) {
        return sceneStarted && !alreadyCleaned;
    }

    static void runLifecycle(
            Lifecycle lifecycle,
            boolean controllerPresent,
            CleanupSteps steps) {
        if (lifecycle == null || steps == null) {
            throw new IllegalArgumentException(
                    "Cleanup lifecycle and steps are required.");
        }
        CatchTheRainbowCleanupSequence.runUnconditional(
                () -> runControllerStep(
                        controllerPresent, steps.disableBladeRain()),
                () -> runControllerStep(
                        controllerPresent, steps.clearRainSupport()),
                () -> runControllerStep(
                        controllerPresent, steps.disableMerge()),
                () -> runControllerStep(
                        controllerPresent, steps.disableTempHealth()),
                steps.clearGravity(),
                steps.clearReach(),
                steps.clearMergeEffect(),
                steps.clearRedemptionEffect(),
                steps.clearTempHealth(),
                steps.purgeMask());
    }

    private static CleanupSteps steps(
            @Nullable LivingEntity user,
            @Nullable CatchTheRainbowEntity controller) {
        return steps(
                user, controller, () -> TempHealthAbility.clear(user));
    }

    private static CleanupSteps steps(
            @Nullable LivingEntity user,
            @Nullable CatchTheRainbowEntity controller,
            Runnable clearTempHealth) {
        return new CleanupSteps(
                () -> controller.setBladeRainActive(false),
                () -> controller.clearRainWalkingSupport(user),
                () -> controller.setMergeActive(false),
                () -> controller.setTempHealthActive(false),
                () -> CatchTheRainbowEntity.clearRainWalking(user),
                () -> CatchTheRainbowEntity.clearReach(user),
                () -> removeEffect(
                        user, CatchTheRainbowAddon.RAIN_MERGE_EFFECT),
                () -> clearRedemptionResidue(user),
                clearTempHealth,
                () -> CatchTheRainbowMaskItem.purgeAll(user));
    }

    private static void runControllerStep(
            boolean controllerPresent, Runnable step) {
        if (controllerPresent) {
            step.run();
        }
    }

    private static void removeEffect(
            @Nullable LivingEntity user,
            net.minecraft.core.Holder<
                    net.minecraft.world.effect.MobEffect> effect) {
        if (user != null) {
            user.removeEffect(effect);
        }
    }

    private static boolean hasEffect(
            @Nullable LivingEntity user,
            net.minecraft.core.Holder<
                    net.minecraft.world.effect.MobEffect> effect) {
        return user != null && user.hasEffect(effect);
    }

    // 1.16 kept Rain Redemption through unsummon, logout and Stand
    // loss, and ending it kills the user, so cleanup only strips it
    // from a dead body; dry weather and expiry still end it.
    private static void clearRedemptionResidue(
            @Nullable LivingEntity user) {
        if (hasRedemptionResidue(user)) {
            user.removeEffect(
                    CatchTheRainbowAddon.RAIN_REDEMPTION_EFFECT);
        }
    }

    private static boolean hasRedemptionResidue(
            @Nullable LivingEntity user) {
        return user != null
                && user.isDeadOrDying()
                && user.hasEffect(
                        CatchTheRainbowAddon.RAIN_REDEMPTION_EFFECT);
    }

    enum Lifecycle {
        GENERIC,
        CONTROLLER_MISSING,
        UNSUMMON,
        CONTROLLER_REMOVE,
        LOGOUT
    }

    public enum RestorationReceipt {
        PENDING,
        NATURAL_COMPLETION,
        ABORT;

        public boolean completed() {
            return this != PENDING;
        }

        public RestorationReceipt record(
                RestorationReceipt requested) {
            if (requested == null || requested == PENDING) {
                throw new IllegalArgumentException(
                        "A completed restoration cause is required.");
            }
            return completed() ? this : requested;
        }
    }

    record CleanupSteps(
            Runnable disableBladeRain,
            Runnable clearRainSupport,
            Runnable disableMerge,
            Runnable disableTempHealth,
            Runnable clearGravity,
            Runnable clearReach,
            Runnable clearMergeEffect,
            Runnable clearRedemptionEffect,
            Runnable clearTempHealth,
            Runnable purgeMask) {}
}
