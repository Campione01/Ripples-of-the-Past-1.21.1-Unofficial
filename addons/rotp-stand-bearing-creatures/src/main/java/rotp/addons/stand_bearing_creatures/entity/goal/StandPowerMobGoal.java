package rotp.addons.stand_bearing_creatures.entity.goal;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Mob;
import rotp.addons.stand_bearing_creatures.mechanics.combo.ComboManager;
import rotp.addons.stand_bearing_creatures.mechanics.combo.PowerComboData;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

public final class StandPowerMobGoal
        extends AbstractPowerMobGoal<StandPower> {

    public StandPowerMobGoal(Mob mob) {
        super(mob, MobPowerUtil.STAND_HELD_KEY_TAG);
    }

    @Override
    @Nullable
    protected StandPower power() {
        return StandPower.get(mob);
    }

    @Override
    @Nullable
    protected PowerComboData comboData(StandPower power) {
        return power.getPowerType() != null
                ? ComboManager.stand(power.getPowerType().getId())
                : null;
    }

    @Override
    protected int resolveLevel(StandPower power) {
        return power.getResolveLevel();
    }

    @Override
    protected double maximumTargetRange(StandPower power) {
        return MobPowerUtil.isLongRange(power.getPowerType())
                ? 32.0D
                : 12.5D;
    }

    @Override
    protected double resourceAmount(StandPower power) {
        return power.getStamina();
    }

    // 1.16 050 StandAI.isActionAvailable: a step, pick or block action
    // whose cost the stamina cannot pay is not available. An infinite
    // stamina reads as the full bar there and here.
    @Override
    protected boolean paysStaminaCost(StandPower power, Ability ability) {
        return ability.getStaminaCost(power) <= power.getStamina();
    }

    @Override
    protected void preparePower(StandPower power) {
        if (!power.isSummoned()) {
            power.getPowerType().summon(mob, power);
        }
    }

    // 1.16 StandAI listed the unlocked actions for each pick, which drew
    // every time stop's training at random. The unlocked skills are the
    // core's Resolve unlocks, as 1.16's getAllUnlockedActions were.
    @Override
    protected void beforePick(StandPower power) {
        MobPowerUtil.randomizeTimeStopTraining(power, mob.getRandom());
    }

    // 1.16 StandAI's same-tick unsummon dropped the charge it had fired.
    @Override
    protected boolean firesHeldOnTargetLoss() {
        return false;
    }

    // 1.16 StandAI unsummoned with toggleSummon: the Stand played its
    // unsummon once the task it could not cancel (a recovery, a click
    // action) was over. forceUnsummon removed it at once and silently.
    // A mad Purple Haze refuses this unsummon and stays out, as in 1.16:
    // its tick stopped the unsummon task every tick (060 PurpleHazeEntity).
    @Override
    protected void onTargetLost(StandPower power) {
        if (!power.isSummoned()) {
            return;
        }
        StandEntity stand = power.getSummonedStandEntity();
        EntityActionInstance action = stand != null
                ? stand.getCurStandAction()
                : null;
        if (action == null || action.isOver()) {
            power.getPowerType().unsummon(mob, power);
        }
    }
}
