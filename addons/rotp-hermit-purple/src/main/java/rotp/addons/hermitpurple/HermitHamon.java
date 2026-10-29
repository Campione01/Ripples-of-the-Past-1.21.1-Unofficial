package rotp.addons.hermitpurple;

import java.util.Optional;

import rotp.core.init.ModParticles;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.world.entity.LivingEntity;

public final class HermitHamon {
    private HermitHamon() {}

    public static Optional<HamonData> data(LivingEntity user) {
        return user == null
                ? Optional.empty()
                : PlayerPower.getPowerData(
                        user, ModPlayerPowers.HAMON);
    }

    public static boolean hasSkill(
            LivingEntity user, HamonSkill skill) {
        return data(user)
                .map(hamon -> hamon.isSkillLearned(skill))
                .orElse(false);
    }

    public static boolean hasChargedVines(LivingEntity user) {
        return data(user)
                .filter(hamon -> hamon.getEnergy() > 0.0F)
                .isPresent();
    }

    public static boolean fireLearnedAbility(
            LivingEntity user, String abilityName) {
        PlayerPower power = user != null
                ? PlayerPower.get(user)
                : null;
        if (power == null || !power.hasPower()) {
            return false;
        }
        Ability ability = power.getAbility(abilityName);
        if (ability == null
                || !ability.checkConditions(power).isPositive()) {
            return false;
        }
        BufferingState buffering = BufferingState.clickOnly();
        ability.onKeyPress(
                user.level(),
                user,
                null,
                InputMethod.CLICK,
                0.0F,
                buffering);
        return buffering.isActionSuccess;
    }

    public static boolean hurt(
            LivingEntity target,
            LivingEntity user,
            float damage,
            boolean red) {
        boolean hurt =
                HamonAbilityHelpers.hamonHurt(target, user, damage);
        if (hurt) {
            HamonAbilityHelpers.sendHamonParticles(
                    target,
                    red
                            ? ModParticles.HAMON_SPARK_RED.get()
                            : ModParticles.HAMON_SPARK.get(),
                    8);
        }
        return hurt;
    }
}
