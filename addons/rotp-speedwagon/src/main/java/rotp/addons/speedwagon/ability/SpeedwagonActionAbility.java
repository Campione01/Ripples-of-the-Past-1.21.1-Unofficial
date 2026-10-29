package rotp.addons.speedwagon.ability;

import java.util.function.Function;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;

import rotp.addons.speedwagon.power.SpeedwagonData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class SpeedwagonActionAbility extends EntityActionAbility {
    private final float energyCost;
    private final float heldEnergyCost;
    private final int speedwagonCooldown;

    protected SpeedwagonActionAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            float energyCost,
            float heldEnergyCost,
            int cooldown,
            Function<
                    EntityActionType,
                    ? extends EntityActionInstance> actionFactory) {
        super(abilityType, abilityId, actionFactory);
        this.energyCost = Math.max(energyCost, 0.0F);
        this.heldEnergyCost =
                Math.max(heldEnergyCost, 0.0F);
        this.speedwagonCooldown = Math.max(cooldown, 0);
        this.spriteName = abilityId.nameInMoveset();
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        SpeedwagonData data = getData(context);
        if (data == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (!(this instanceof RunAbility)
                && LivingComponentAction.getCurEntityAction(context.getUser())
                        instanceof RunAbility.RunAction running
                && running.getPhase() == ActionPhase.PERFORM) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 PowerBaseImpl.checkRequirements refused an action on
        // cooldown without a message.
        if (data.isAbilityOnCooldown(name())) {
            return ConditionCheck.NEGATIVE;
        }
        if (!isCreative(context)
                && !data.hasEnergy(
                        energyCost + heldEnergyCost)) {
            return ConditionCheck.createNegative(
                    "no_energy_speedwagon");
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public boolean canContinueAction(
            EntityActionInstance action) {
        if (heldEnergyCost <= 0.0F
                || action.getPhase() != ActionPhase.PERFORM) {
            return true;
        }
        LivingEntity user = action.getPowerUser();
        Power<?> context =
                user != null ? getUserPower(user) : null;
        SpeedwagonData data = getData(context);
        return context != null
                && data != null
                && (isCreative(context)
                        || data.hasEnergy(heldEnergyCost));
    }

    protected boolean consumeInitialEnergy(
            LivingEntity user) {
        Power<?> context = getUserPower(user);
        SpeedwagonData data = getData(context);
        if (context == null || data == null) {
            return false;
        }
        if (!data.consumeEnergy(user, energyCost)) {
            return false;
        }
        if (!isCreative(context) && speedwagonCooldown > 0) {
            data.setAbilityCooldown(
                    name(), speedwagonCooldown);
        }
        data.syncOnUpdate(user);
        return true;
    }

    protected boolean consumeHeldEnergy(
            LivingEntity user) {
        Power<?> context = getUserPower(user);
        SpeedwagonData data = getData(context);
        return context != null
                && data != null
                && data.consumeEnergy(user, heldEnergyCost);
    }

    protected void syncData(LivingEntity user) {
        Power<?> context = getUserPower(user);
        SpeedwagonData data = getData(context);
        if (data != null) {
            data.syncOnUpdate(user);
        }
    }

    protected static SpeedwagonData getData(
            Power<?> context) {
        return context != null
                && context.getCurTypeData()
                        instanceof SpeedwagonData data
                ? data
                : null;
    }

    protected static SpeedwagonData getData(
            LivingEntity user,
            EntityActionInstance action) {
        if (user == null
                || !(action.ability
                        instanceof SpeedwagonActionAbility ability)) {
            return null;
        }
        return getData(ability.getUserPower(user));
    }

    protected static boolean isCreative(Power<?> context) {
        return context != null
                && context.getUser()
                        instanceof Player player
                && player.getAbilities().instabuild;
    }
}
