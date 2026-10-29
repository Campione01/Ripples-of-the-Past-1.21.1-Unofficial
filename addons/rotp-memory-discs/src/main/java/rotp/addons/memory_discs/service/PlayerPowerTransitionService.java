package rotp.addons.memory_discs.service;

import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.addons.memory_discs.item.MemoryDiscItem;
import rotp.addons.memory_discs.item.PowerDiscData;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class PlayerPowerTransitionService {
    private PlayerPowerTransitionService() {}

    public static Result applyDisc(
            LivingEntity rawTarget, ItemStack disc) {
        return applyDisc(rawTarget, disc, true);
    }

    public static Result applyDisc(
            LivingEntity rawTarget, ItemStack disc,
            boolean grantFullVampirism) {
        if (disc == null
                || disc.isEmpty()
                || !(disc.getItem() instanceof MemoryDiscItem)) {
            return Result.rejected(Status.INVALID_DISC, null, null);
        }

        boolean hasStoredId = PowerDiscData.hasStoredPowerId(disc);
        PlayerPowerType<?> requestedType =
                PowerDiscData.getPowerType(disc).orElse(null);
        if (hasStoredId && requestedType == null) {
            return Result.rejected(
                    Status.INVALID_POWER_ID,
                    rawTarget != null && rawTarget.isAlive()
                            ? rawTarget
                            : null,
                    null);
        }
        return transition(rawTarget, requestedType, grantFullVampirism);
    }

    public static Result clearPower(LivingEntity rawTarget) {
        return transition(rawTarget, null, false);
    }

    private static Result transition(
            LivingEntity rawTarget,
            PlayerPowerType<?> requestedType,
            boolean grantFullVampirism) {
        if (rawTarget == null || !rawTarget.isAlive()) {
            return Result.rejected(
                    Status.INVALID_TARGET, null, null);
        }
        LivingEntity target = rawTarget;
        if (target.level().isClientSide()) {
            return Result.rejected(
                    Status.NOT_SERVER_SIDE, target, null);
        }
        if (requestedType != null
                && PlayerPowerType.fromId(requestedType.getId())
                        != requestedType) {
            return Result.rejected(
                    Status.INVALID_POWER_ID, target, null);
        }

        PlayerPower power =
                PlayerPower.getOptional(target).orElse(null);
        if (power == null) {
            return Result.rejected(
                    Status.POWER_UNAVAILABLE, target, null);
        }

        synchronized (power) {
            PlayerPowerType<?> previousType =
                    power.getPowerType();
            if (previousType == null && requestedType == null) {
                return Result.rejected(
                        Status.NO_ACTIVE_POWER,
                        target,
                        null);
            }

            PowerData previousData = previousType != null
                    ? power.getPowerTypeData(previousType)
                    : null;
            PowerData requestedData = requestedType != null
                    ? power.getPowerTypeData(requestedType)
                    : null;
            CompoundTag previousFreshState = previousType != null
                    ? freshState(power, previousType)
                    : null;
            CompoundTag requestedFreshState =
                    requestedType != null
                            && requestedType != previousType
                    ? freshState(power, requestedType)
                    : null;

            if (previousType != null) {
                power.setPowerType(null);
                resetStoredData(
                        power, previousData, previousFreshState);
            }
            if (requestedType != null
                    && requestedType != previousType) {
                resetStoredData(
                        power, requestedData, requestedFreshState);
            }
            // The data reset clears its mirror; the authoritative blood pool
            // is separate and must reset before the new 300-blood grant.
            if (previousType == ModPlayerPowers.VAMPIRISM.get()
                    || requestedType == ModPlayerPowers.VAMPIRISM.get()) {
                VampirismState.get(target).blood().setCurrent(0.0F);
            }
            power.setLeapCooldown(0);
            if (requestedType != null) {
                power.setPowerType(requestedType);
            }
            if (power.getPowerType() != requestedType) {
                if (power.getPowerType() != previousType) {
                    power.setPowerType(previousType);
                }
                return Result.rejected(
                        Status.TRANSITION_REJECTED,
                        target,
                        previousType);
            }

            if (previousType == ModPlayerPowers.HAMON.get()
                    && requestedType != previousType
                    && target instanceof ServerPlayer player) {
                ModCriteriaTriggers.triggerAbandonHamon(player);
            }
            if (grantFullVampirism && requestedType
                    == ModPlayerPowers.VAMPIRISM.get()) {
                PlayerPower.getPowerData(
                                target,
                                ModPlayerPowers.VAMPIRISM)
                        .filter(VampirismData.class::isInstance)
                        .map(VampirismData.class::cast)
                        .ifPresent(data ->
                                data.setVampireFullPower(
                                        true, target));
            }

            return Result.applied(
                    target, previousType, requestedType);
        }
    }

    private static CompoundTag freshState(
            PlayerPower power, PlayerPowerType<?> type) {
        PowerData freshData = type.newDataInstance();
        freshData.onInit(power);
        return freshData.serializeNBT(
                power.getUser().registryAccess());
    }

    private static void resetStoredData(
            PlayerPower power,
            PowerData storedData,
            CompoundTag freshState) {
        RegistryAccess registryAccess =
                power.getUser().registryAccess();
        storedData.deserializeNBT(registryAccess, freshState);
    }

    public enum Status {
        APPLIED(""),
        NOT_SERVER_SIDE(
                "item.rotp_mds.transition.not_server"),
        INVALID_TARGET(
                "item.rotp_mds.transition.invalid_target"),
        POWER_UNAVAILABLE(
                "item.rotp_mds.transition.power_unavailable"),
        INVALID_DISC(
                "item.rotp_mds.transition.invalid_disc"),
        INVALID_POWER_ID(
                "item.rotp_mds.transition.invalid_power_id"),
        NO_ACTIVE_POWER(
                "item.rotp_mds.transition.no_active_power"),
        TRANSITION_REJECTED(
                "item.rotp_mds.transition.rejected");

        private final String translationKey;

        Status(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record Result(
            Status status,
            LivingEntity target,
            PlayerPowerType<?> previousType,
            PlayerPowerType<?> currentType) {
        public Result {
            if (status == null) {
                throw new IllegalArgumentException(
                        "Transition status must not be null");
            }
        }

        public boolean applied() {
            return status == Status.APPLIED;
        }

        private static Result applied(
                LivingEntity target,
                PlayerPowerType<?> previousType,
                PlayerPowerType<?> currentType) {
            return new Result(
                    Status.APPLIED,
                    target,
                    previousType,
                    currentType);
        }

        private static Result rejected(
                Status status,
                LivingEntity target,
                PlayerPowerType<?> previousType) {
            return new Result(
                    status,
                    target,
                    previousType,
                    target != null
                            ? PlayerPower.getOptional(target)
                                    .map(PlayerPower::getPowerType)
                                    .orElse(null)
                            : null);
        }
    }
}
