package rotp.addons.stand_bearing_creatures.entity.goal;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.PowerData;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.zombie.ZombieData;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import rotp.addons.stand_bearing_creatures.mechanics.combo.ComboManager;
import rotp.addons.stand_bearing_creatures.mechanics.combo.MaintenanceStep;
import rotp.addons.stand_bearing_creatures.mechanics.combo.PowerComboData;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

public final class PlayerPowerMobGoal
        extends AbstractPowerMobGoal<PlayerPower> {

    public PlayerPowerMobGoal(Mob mob) {
        super(mob, MobPowerUtil.PLAYER_POWER_HELD_KEY_TAG);
    }

    @Override
    @Nullable
    protected PlayerPower power() {
        return PlayerPower.get(mob);
    }

    @Override
    @Nullable
    protected PowerComboData comboData(PlayerPower power) {
        return power.getPowerType() != null
                ? ComboManager.playerPower(
                        power.getPowerType().getId())
                : null;
    }

    @Override
    protected int resolveLevel(PlayerPower power) {
        return 0;
    }

    // 1.16 050 NonStandAI.tryToStartNewCombo drew from every combo name
    // (NonStandComboData.getComboNames: no Resolve table)
    @Override
    protected List<String> comboNames(PlayerPower power, PowerComboData data) {
        return data.combos().keySet().stream().sorted().toList();
    }

    // 1.16 050 NonStandAI.tryToContinueCombo only reset the combo when the
    // next step was not available; the next pick started over
    @Override
    protected boolean randomPickWhenComboBreaks() {
        return false;
    }

    // 1.16 050 NonStandAI.runAI (:101-110) ran its combos whenever the
    // power had combo data, without StandAI's useWIPComboSystem check
    @Override
    protected boolean combosFollowConfig() {
        return false;
    }

    // and never blocked: NonStandComboData read every key but maintenance
    // as a combo, and its random pick left out no block action
    @Override
    @Nullable
    protected String blockAction(PowerComboData data) {
        return null;
    }

    @Override
    protected double maximumTargetRange(PlayerPower power) {
        return 32.0D;
    }

    @Override
    protected double resourceAmount(PlayerPower power) {
        PowerData data = power.getCurTypeData();
        if (data instanceof HamonData hamon) {
            return hamon.getEnergy();
        }
        if (data instanceof VampirismData vampirism) {
            return vampirism.getBloodLevel();
        }
        if (data instanceof PillarmanData pillarman) {
            return pillarman.getEnergy();
        }
        if (data instanceof ZombieData zombie) {
            return zombie.getEnergy();
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override
    protected boolean runMaintenance(
            PlayerPower power,
            PowerComboData data,
            Map<String, Ability> abilities,
            LivingEntity target) {
        MaintenanceStep maintenance = data.maintenance();
        if (maintenance == null
                || !maintenanceRequired(
                        power,
                        maintenance.condition())) {
            return false;
        }
        return performNamed(
                power,
                abilities,
                maintenance.action(),
                null,
                target);
    }

    private boolean maintenanceRequired(
            PlayerPower power,
            String condition) {
        double current;
        double maximum;
        PowerData data = power.getCurTypeData();
        if (data instanceof HamonData hamon) {
            current = hamon.getEnergy();
            maximum = hamon.getMaxEnergy();
        }
        else if (data instanceof VampirismData vampirism) {
            current = vampirism.getBloodLevel();
            maximum = vampirism.getMaxBlood(mob);
        }
        else if (data instanceof PillarmanData pillarman) {
            current = pillarman.getEnergy();
            maximum = pillarman.getMaxEnergy(mob);
        }
        else if (data instanceof ZombieData zombie) {
            current = zombie.getEnergy();
            maximum = zombie.getMaxEnergy(mob);
        }
        else {
            return false;
        }
        if (maximum <= 0.0D) {
            return false;
        }

        String compact = condition.replace(" ", "");
        int operator = Math.max(
                compact.indexOf('<'),
                compact.indexOf('>'));
        if (operator < 0) {
            return false;
        }
        try {
            double threshold = Double.parseDouble(
                    compact.substring(operator + 1)
                            .replace("%", ""));
            double percentage = current / maximum * 100.0D;
            return compact.charAt(operator) == '<'
                    ? percentage < threshold
                    : percentage > threshold;
        }
        catch (NumberFormatException ignored) {
            return false;
        }
    }
}
