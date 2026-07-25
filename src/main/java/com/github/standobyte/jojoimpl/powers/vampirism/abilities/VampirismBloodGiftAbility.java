package com.github.standobyte.jojoimpl.powers.vampirism.abilities;

import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismData;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class VampirismBloodGiftAbility extends VampirismActionAbility {
	private static final int HOLD_TO_FIRE_TICKS = 60;
	private static final float HOLD_BLOOD_COST_PER_TICK = 5.0F;
	private static final double MAX_RANGE_SQ_ENTITY_TARGET = 4.0D;

	public VampirismBloodGiftAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 3, HOLD_BLOOD_COST_PER_TICK, BloodGiftInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	protected boolean requiresVampireFullPower() {
		return false;
	}

	@Override
	protected float getWindupHoldToFireIndicatorLength() {
		return HOLD_TO_FIRE_TICKS;
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
		if (!user.getMainHandItem().isEmpty()) {
			return ConditionCheck.createNegative("hand");
		}
		if (user.getHealth() <= 10.0F) {
			return ConditionCheck.createNegative("user_too_low_health");
		}
		Player target = getGiftTarget(user);
		if (target == null) {
			return ConditionCheck.createNegative("player_target");
		}
		PlayerPower targetPower = PlayerPower.get(target);
		if (targetPower == null) {
			return ConditionCheck.createNegative("cant_become_vampire");
		}
		if (targetPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get()) {
			return ConditionCheck.createNegative("already_vampire");
		}
		if (targetPower.hasPower()) {
			return ConditionCheck.createNegative("cant_become_vampire");
		}
		if (target.getHealth() > 6.0F) {
			return ConditionCheck.createNegative("target_too_many_health");
		}
		return ConditionCheck.POSITIVE;
	}

	public static class BloodGiftInstance extends EntityActionInstance {
		public BloodGiftInstance(EntityActionType ability) {
			super(ability);
			userWalkSpeed = 0.3F;
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.WINDUP || level().isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !canContinueWindup(user)
					|| getPhaseTick() < HOLD_TO_FIRE_TICKS && !consumeBlood(user, HOLD_BLOOD_COST_PER_TICK)) {
				forceStop();
				syncPhaseChanges();
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !canContinueWindup(user)) {
				return;
			}
			Player target = getGiftTarget(user);
			if (target == null) {
				return;
			}
			PlayerPower targetPower = PlayerPower.get(target);
			if (targetPower == null || targetPower.hasPower() || target.getHealth() > 6.0F) {
				return;
			}
			targetPower.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			VampirismData targetData = PlayerPower.getPowerData(target, ModPlayerPowers.VAMPIRISM).orElse(null);
			if (targetData != null) {
				targetData.setVampireFullPower(false, target);
			}
			user.hurt(DamageUtil.make(level, ModDamageTypes.BLOOD_GIFT), 10.0F);
			boolean wasDead = target.getHealth() <= 0.0F;
			target.heal(target.getMaxHealth());
			if (wasDead) {
				JojoModUtil.onLivingResurrect(target);
			}
			target.deathTime = 0;
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.WINDUP) {
				forceStop();
			}
		}
	}

	private static boolean canContinueWindup(LivingEntity user) {
		if (user.level().getDifficulty() == Difficulty.PEACEFUL
				|| !user.getMainHandItem().isEmpty()
				|| user.getHealth() <= 10.0F) {
			return false;
		}
		Player target = getGiftTarget(user);
		if (target == null || target.getHealth() > 6.0F) {
			return false;
		}
		PlayerPower targetPower = PlayerPower.get(target);
		return targetPower != null && !targetPower.hasPower();
	}

	private static Player getGiftTarget(LivingEntity user) {
		ActionTarget target = getAimTarget(user.level(), user);
		if (target.getType() == TargetType.ENTITY) {
			Entity entity = target.getMainEntity();
			if (entity instanceof Player player && user.distanceToSqr(player) <= MAX_RANGE_SQ_ENTITY_TARGET) {
				return player;
			}
		}
		return null;
	}

	private static ActionTarget getAimTarget(Level level, LivingEntity user) {
		var aim = LivingComponentAction.getAim(user);
		return aim != null ? aim.getTarget().resolveEntityId(level) : ActionTarget.EMPTY;
	}
}
