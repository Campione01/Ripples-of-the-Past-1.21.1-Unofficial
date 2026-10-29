package rotp.addons.stone_legacy_zombie_orders;

import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerData;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.zombie.ZombieData;

import net.minecraft.world.entity.LivingEntity;

public final class ZombiePowerEnergy {
    private ZombiePowerEnergy() {}

    public static Energy read(
            PlayerPower power,
            LivingEntity user) {
        PowerData data = power.getCurTypeData();
        if (data instanceof HamonData hamon) {
            return new Energy(
                    hamon.getEnergy(),
                    hamon.getMaxEnergy());
        }
        if (data instanceof PillarmanData pillarman) {
            return new Energy(
                    pillarman.getEnergy(),
                    pillarman.getMaxEnergy(user));
        }
        if (data instanceof VampirismData) {
            var blood = VampirismState.get(user).blood();
            return new Energy(blood.current(), blood.max());
        }
        if (data instanceof ZombieData zombie) {
            return new Energy(
                    zombie.getEnergy(),
                    zombie.getMaxEnergy(user));
        }
        if (data instanceof PlayerPowerData typed) {
            return new Energy(
                    typed.getCommandEnergy(),
                    typed.getCommandMaxEnergy(user));
        }
        return new Energy(0.0F, 1.0F);
    }

    public static boolean blocksZombieConversion(
            PlayerPower power) {
        return power.getCurTypeData()
                        instanceof HamonData hamon
                && hamon.getEnergy() >= 5.0F;
    }

    public static void setCurrent(
            PlayerPower power,
            LivingEntity user,
            float energy) {
        PowerData data = power.getCurTypeData();
        if (data instanceof HamonData hamon) {
            hamon.setEnergy(energy);
            hamon.syncOnUpdate(user);
        }
        else if (data instanceof PillarmanData pillarman) {
            pillarman.setEnergy(user, energy);
            pillarman.syncOnUpdate(user);
        }
        else if (data instanceof VampirismData vampirism) {
            var blood = VampirismState.get(user).blood();
            blood.setCurrent(energy);
            vampirism.setBloodLevel(blood.current());
            vampirism.syncOnUpdate(user);
        }
        else if (data instanceof ZombieData zombie) {
            zombie.setEnergy(user, energy);
            zombie.syncOnUpdate(user);
        }
        else if (data instanceof PlayerPowerData typed) {
            typed.setCommandEnergy(user, energy);
        }
    }

    public record Energy(float current, float max) {
        public Energy {
            current = Float.isFinite(current)
                    ? Math.max(current, 0.0F)
                    : 0.0F;
            max = Float.isFinite(max)
                    ? Math.max(max, 1.0F)
                    : 1.0F;
        }

        public float convertedZombieEnergy() {
            double converted =
                    Math.pow(1.6D * current, 1.8D)
                            / Math.pow(max, 2.0D);
            return Double.isFinite(converted)
                    ? (float) Math.max(converted, 0.0D)
                    : 0.0F;
        }
    }
}
