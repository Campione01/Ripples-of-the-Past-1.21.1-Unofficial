package rotp.addons.blood_bottle.service;

import rotp.addons.blood_bottle.BloodBottleConfig;
import rotp.addons.blood_bottle.BloodBottleEconomy;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.zombie.ZombieData;

import net.minecraft.world.entity.player.Player;

public final class BloodBottlePowerService {
    private BloodBottlePowerService() {}

    public static Gate canDrink(Player player) {
        if (player == null || !player.isAlive()) {
            return Gate.rejected(Status.INVALID_USER);
        }
        PlayerPower power =
                PlayerPower.getOptional(player).orElse(null);
        if (power == null) {
            return Gate.rejected(Status.POWER_UNAVAILABLE);
        }

        ResourceSnapshot resource = resource(power, player);
        if (resource.status() != Status.READY) {
            return Gate.rejected(resource.status());
        }
        boolean allowed = BloodBottleEconomy.canDrink(
                resource.current(),
                resource.maximum(),
                BloodBottleConfig.MAX_BOTTLE_HUNGER_VALUE.get());
        return allowed
                ? new Gate(
                        Status.READY,
                        resource.current(),
                        resource.maximum())
                : new Gate(
                        Status.ABOVE_DRINK_THRESHOLD,
                        resource.current(),
                        resource.maximum());
    }

    public static DrinkResult drink(Player player) {
        Gate gate = canDrink(player);
        if (!gate.allowed()) {
            return DrinkResult.rejected(gate.status());
        }
        if (player.level().isClientSide()) {
            return DrinkResult.rejected(Status.NOT_SERVER_SIDE);
        }

        PlayerPower power =
                PlayerPower.getOptional(player).orElse(null);
        if (power == null) {
            return DrinkResult.rejected(Status.POWER_UNAVAILABLE);
        }
        synchronized (power) {
            Gate currentGate = canDrink(player);
            if (!currentGate.allowed()) {
                return DrinkResult.rejected(currentGate.status());
            }

            PlayerPowerType<?> type = power.getPowerType();
            if (type == null
                    || type == ModPlayerPowers.HAMON.get()) {
                return transitionToVampirism(
                        player, power, type);
            }
            if (type == ModPlayerPowers.VAMPIRISM.get()) {
                VampirismData data = PlayerPower.getPowerData(
                                player, ModPlayerPowers.VAMPIRISM)
                        .orElse(null);
                if (data == null) {
                    return DrinkResult.rejected(
                            Status.POWER_DATA_UNAVAILABLE);
                }
                float amount = bottleValue(
                        data.getMaxBlood(player));
                data.addBlood(player, amount);
                return DrinkResult.applied(amount, false);
            }
            if (type == ModPlayerPowers.ZOMBIE.get()) {
                ZombieData data = PlayerPower.getPowerData(
                                player, ModPlayerPowers.ZOMBIE)
                        .orElse(null);
                if (data == null) {
                    return DrinkResult.rejected(
                            Status.POWER_DATA_UNAVAILABLE);
                }
                float amount = bottleValue(
                        data.getMaxEnergy(player));
                data.addEnergy(player, amount);
                data.syncOnUpdate(player);
                return DrinkResult.applied(amount, false);
            }
            if (type == ModPlayerPowers.PILLAR_MAN.get()) {
                PillarmanData data = PlayerPower.getPowerData(
                                player, ModPlayerPowers.PILLAR_MAN)
                        .orElse(null);
                if (data == null) {
                    return DrinkResult.rejected(
                            Status.POWER_DATA_UNAVAILABLE);
                }
                float amount = bottleValue(
                        data.getMaxEnergy(player));
                data.addEnergy(player, amount);
                data.syncOnUpdate(player);
                return DrinkResult.applied(amount, false);
            }
            return DrinkResult.rejected(
                    Status.UNSUPPORTED_PLAYER_POWER);
        }
    }

    private static DrinkResult transitionToVampirism(
            Player player,
            PlayerPower power,
            PlayerPowerType<?> previousType) {
        power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
        if (power.getPowerType()
                != ModPlayerPowers.VAMPIRISM.get()) {
            return DrinkResult.rejected(
                    Status.TRANSITION_REJECTED);
        }

        VampirismData data = PlayerPower.getPowerData(
                        player, ModPlayerPowers.VAMPIRISM)
                .orElse(null);
        if (data == null) {
            power.setPowerType(previousType);
            return DrinkResult.rejected(
                    Status.POWER_DATA_UNAVAILABLE);
        }

        // 1.16 DrinkableItem only called givePower(VAMPIRISM): the new
        // vampire keeps the default VampireFullPower=false, the same weak
        // vampire that Blood Gift makes. The granted data is fresh, so the
        // flag is left untouched here.
        float amount = bottleValue(data.getMaxBlood(player));
        data.addBlood(player, amount);
        return DrinkResult.applied(amount, true);
    }

    private static float bottleValue(float maxResource) {
        return BloodBottleEconomy.bottleValue(
                maxResource,
                BloodBottleConfig.BOTTLE_DRAIN_PENALTY.get(),
                BloodBottleConfig.FILL_BOTTLE_MULTIPLIER.get());
    }

    private static ResourceSnapshot resource(
            PlayerPower power, Player player) {
        PlayerPowerType<?> type = power.getPowerType();
        if (type == null) {
            return ResourceSnapshot.ready(0.0F, 0.0F);
        }
        if (type == ModPlayerPowers.VAMPIRISM.get()) {
            VampirismData data = PlayerPower.getPowerData(
                            player, ModPlayerPowers.VAMPIRISM)
                    .orElse(null);
            return data != null
                    ? ResourceSnapshot.ready(
                            data.getBloodLevel(),
                            data.getMaxBlood(player))
                    : ResourceSnapshot.rejected(
                            Status.POWER_DATA_UNAVAILABLE);
        }
        if (type == ModPlayerPowers.HAMON.get()) {
            HamonData data = PlayerPower.getPowerData(
                            player, ModPlayerPowers.HAMON)
                    .orElse(null);
            return data != null
                    ? ResourceSnapshot.ready(
                            data.getEnergy(),
                            data.getMaxEnergy())
                    : ResourceSnapshot.rejected(
                            Status.POWER_DATA_UNAVAILABLE);
        }
        if (type == ModPlayerPowers.ZOMBIE.get()) {
            ZombieData data = PlayerPower.getPowerData(
                            player, ModPlayerPowers.ZOMBIE)
                    .orElse(null);
            return data != null
                    ? ResourceSnapshot.ready(
                            data.getEnergy(),
                            data.getMaxEnergy(player))
                    : ResourceSnapshot.rejected(
                            Status.POWER_DATA_UNAVAILABLE);
        }
        if (type == ModPlayerPowers.PILLAR_MAN.get()) {
            PillarmanData data = PlayerPower.getPowerData(
                            player, ModPlayerPowers.PILLAR_MAN)
                    .orElse(null);
            return data != null
                    ? ResourceSnapshot.ready(
                            data.getEnergy(),
                            data.getMaxEnergy(player))
                    : ResourceSnapshot.rejected(
                            Status.POWER_DATA_UNAVAILABLE);
        }
        return ResourceSnapshot.rejected(
                Status.UNSUPPORTED_PLAYER_POWER);
    }

    public enum Status {
        READY,
        INVALID_USER,
        POWER_UNAVAILABLE,
        POWER_DATA_UNAVAILABLE,
        UNSUPPORTED_PLAYER_POWER,
        ABOVE_DRINK_THRESHOLD,
        NOT_SERVER_SIDE,
        TRANSITION_REJECTED
    }

    public record Gate(
            Status status, float current, float maximum) {
        public boolean allowed() {
            return status == Status.READY;
        }

        private static Gate rejected(Status status) {
            return new Gate(status, 0.0F, 0.0F);
        }
    }

    public record DrinkResult(
            Status status,
            float resourceAdded,
            boolean transitionedToVampirism) {
        public boolean applied() {
            return status == Status.READY;
        }

        private static DrinkResult applied(
                float amount, boolean transitioned) {
            return new DrinkResult(
                    Status.READY, amount, transitioned);
        }

        private static DrinkResult rejected(Status status) {
            return new DrinkResult(status, 0.0F, false);
        }
    }

    private record ResourceSnapshot(
            Status status, float current, float maximum) {
        private static ResourceSnapshot ready(
                float current, float maximum) {
            return new ResourceSnapshot(
                    Status.READY, current, maximum);
        }

        private static ResourceSnapshot rejected(Status status) {
            return new ResourceSnapshot(status, 0.0F, 0.0F);
        }
    }
}
