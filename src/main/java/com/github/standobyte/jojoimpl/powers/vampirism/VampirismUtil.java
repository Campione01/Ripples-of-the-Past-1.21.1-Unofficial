package com.github.standobyte.jojoimpl.powers.vampirism;

import java.util.List;
import java.util.function.Predicate;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.mixin.vampirism.NearestAttackableTargetGoalAccessor;
import com.github.standobyte.jojo.mixin.vampirism.TargetingConditionsAccessor;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

public final class VampirismUtil {
	private VampirismUtil() {
	}

	public static float maxBloodMultiplier(LivingEntity user) {
		JojoModConfig.Common config = JojoModConfig.getCommonConfigInstance(user.level().isClientSide());
		return difficultyValue(user.level(), config.maxBloodMultiplier.get(), 1.0F);
	}

	public static float bloodTickDown(LivingEntity user) {
		JojoModConfig.Common config = JojoModConfig.getCommonConfigInstance(user.level().isClientSide());
		return difficultyValue(user.level(), config.bloodTickDown.get(), 0.0F);
	}

	public static float healCost(Level level) {
		JojoModConfig.Common config = JojoModConfig.getCommonConfigInstance(level.isClientSide());
		return difficultyValue(level, config.bloodHealCost.get(), 0.0F);
	}

	public static void editMobAiGoals(Mob mob) {
		if (mob.getClassification(false) == MobCategory.MONSTER) {
			makeMobNeutralToVampirePlayers(mob);
		}
		else if (mob instanceof IronGolem) {
			mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, Player.class, 5, false, false,
					VampirismUtil::isPlayerJojoVampiric));
		}
	}

	private static void makeMobNeutralToVampirePlayers(Mob mob) {
		if (JojoModConfig.getCommonConfigInstance(false).vampiresAggroMobs.get()) {
			return;
		}
		Predicate<LivingEntity> nonVampireTarget = VampirismUtil::canMonsterTargetPlayer;
		for (var wrappedGoal : mob.targetSelector.getAvailableGoals()) {
			Goal goal = wrappedGoal.getGoal();
			if (goal instanceof NearestAttackableTargetGoal<?> targetGoal) {
				NearestAttackableTargetGoalAccessor accessor =
						(NearestAttackableTargetGoalAccessor) (Object) targetGoal;
				if (accessor.jojo_ripples$getTargetType() == Player.class) {
					TargetingConditions conditions = accessor.jojo_ripples$getTargetConditions();
					if (conditions != null) {
						Predicate<LivingEntity> oldSelector =
								((TargetingConditionsAccessor) (Object) conditions).jojo_ripples$getSelector();
						TargetingConditions updatedConditions = conditions.copy();
						updatedConditions.selector(
								oldSelector != null ? oldSelector.and(nonVampireTarget) : nonVampireTarget);
						accessor.jojo_ripples$setTargetConditions(updatedConditions);
					}
				}
			}
		}
	}

	private static boolean canMonsterTargetPlayer(LivingEntity target) {
		if (!(target instanceof Player)) {
			return false;
		}
		boolean vampireProtected = PlayerPower.getPowerData(target, ModPlayerPowers.VAMPIRISM)
				.map(data -> data.getCuringStage(target) < 3)
				.orElse(false);
		boolean zombieProtected = PlayerPower.getPowerData(target, ModPlayerPowers.ZOMBIE).isPresent();
		boolean pillarmanProtected = PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN)
				.map(data -> data.isStoneFormEnabled() || data.getEvolutionStage() > 1)
				.orElse(false);
		return !vampireProtected && !zombieProtected && !pillarmanProtected;
	}

	private static boolean isPlayerJojoVampiric(LivingEntity target) {
		return target instanceof Player
				&& (PlayerPower.getPowerData(target, ModPlayerPowers.VAMPIRISM).isPresent()
						|| PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN).isPresent());
	}

	public static void consumeEnergyOnHeal(LivingHealEvent event) {
		LivingEntity entity = event.getEntity();
		if (!entity.isAlive() || event.getAmount() <= 0.0F) {
			return;
		}
		PlayerPower power = PlayerPower.get(entity);
		if (power == null || !power.hasPower()) {
			return;
		}

		VampirismData vampirism = power.getCurTypeData(ModPlayerPowers.VAMPIRISM).orElse(null);
		PillarmanData pillarman = power.getCurTypeData(ModPlayerPowers.PILLAR_MAN).orElse(null);
		BloodEconomy blood = null;
		float available;
		if (vampirism != null) {
			blood = VampirismState.get(entity).blood();
			available = blood.current();
		}
		else if (pillarman != null && pillarman.getEvolutionStage() > 1) {
			available = pillarman.getEnergy();
		}
		else {
			return;
		}

		float healCost = healCost(entity.level());
		if (healCost <= 0.0F) {
			return;
		}
		float actualHeal = Math.min(event.getAmount(), available / healCost);
		actualHeal = Math.min(actualHeal, entity.getMaxHealth() - entity.getHealth());
		if (actualHeal <= 0.0F) {
			event.setCanceled(true);
			return;
		}

		float consumedEnergy = actualHeal * healCost;
		if (blood != null) {
			blood.consume(consumedEnergy);
			vampirism.setBloodLevel(blood.current());
			vampirism.syncOnUpdate(entity);
		}
		else {
			pillarman.consumeEnergy(entity, consumedEnergy);
			pillarman.syncOnUpdate(entity);
		}
		event.setAmount(actualHeal);
	}

	private static float difficultyValue(Level level, List<? extends Double> values, float fallback) {
		if (values.isEmpty()) {
			return fallback;
		}
		int difficultyId = Mth.clamp(level.getDifficulty().getId(), 0, values.size() - 1);
		double value = values.get(difficultyId);
		return Double.isFinite(value) ? (float) value : fallback;
	}
}
