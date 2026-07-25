package com.github.standobyte.jojoimpl.powers.vampirism.abilities;

import com.github.standobyte.jojoimpl.powers.vampirism.entity.HungryZombieEntity;

import com.github.standobyte.jojo.init.ModCustomStats;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class VampirismZombieSummonAbility extends VampirismActionAbility {

	public VampirismZombieSummonAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 3, 100.0F, ZombieSummonInstance::new);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	public void setCooldownOnUse(Power<?> context) {
		setVampirismCooldown(context, 100, 100);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (user.level().getDifficulty() == Difficulty.PEACEFUL) {
			return ConditionCheck.createNegative("peaceful");
		}
		if (zombieLimitReached(user)) {
			return ConditionCheck.createNegative("zombies_limit");
		}
		return ConditionCheck.POSITIVE;
	}

	public static class ZombieSummonInstance extends EntityActionInstance {
		public ZombieSummonInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || zombieLimitReached(user) || !consumeBlood(user, 100.0F)) {
				return;
			}
			int count = level.getDifficulty().getId();
			for (int i = 0; i < count; i++) {
				HungryZombieEntity zombie = new HungryZombieEntity(level);
				zombie.setSummonedFromAbility();
				zombie.copyPosition(user);
				zombie.setOwner(user);
				level.addFreshEntity(zombie);
			}
			if (user instanceof ServerPlayer player) {
				player.awardStat(Stats.CUSTOM.get(ModCustomStats.VAMPIRE_ZOMBIES_SUMMONED), count);
			}
		}
	}

	private static boolean zombieLimitReached(LivingEntity user) {
		Level level = user.level();
		int limit = level.getDifficulty().getId() * 10;
		if (limit <= 0) {
			return true;
		}
		AABB area = new AABB(user.getX(), level.getMinBuildHeight(), user.getZ(),
				user.getX(), level.getMaxBuildHeight(), user.getZ()).inflate(16.0D, 0.0D, 16.0D);
		return level.getEntitiesOfClass(HungryZombieEntity.class, area).size() > limit;
	}
}
