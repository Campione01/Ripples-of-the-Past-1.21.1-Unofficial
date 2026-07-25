package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.mechanics.HypnosisEffect;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonHypnosisState;
import com.github.standobyte.jojoimpl.powers.hamon.HamonHypnosisState.HypnosisTargetCheck;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HamonHypnosisAbility extends HamonActionRuntimeAbility {

	public HamonHypnosisAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, HypnosisInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 8);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context != null ? context.getUser() : null;
		return user != null ? checkHypnosisTarget(HamonAbilityHelpers.getAimTarget(user, user.level()), user)
				: ConditionCheck.NEGATIVE;
	}

	@Override
	protected void onHeldTick(HamonHeldActionInstance action, LivingEntity user, Power<?> context, HamonData hamon, int ticksHeld) {
		if (!(action instanceof HypnosisInstance hypnosis)) {
			return;
		}
		Level level = user.level();
		ActionTarget target = hypnosis.getHypnosisTarget(level);
		if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)
				|| !checkHypnosisTarget(target, user).isPositive()) {
			return;
		}
		if (level.isClientSide()) {
			hypnosisClientFeedback(user, livingTarget);
		}
		else {
			HamonHypnosisState.get(livingTarget).startedHypnosisProcess(user);
		}
	}

	private static ConditionCheck checkHypnosisTarget(ActionTarget target, LivingEntity user) {
		if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)) {
			return ConditionCheck.createNegative("hypnosis");
		}
		return switch (HamonHypnosisState.canBeHypnotized(livingTarget, user)) {
			case CORRECT -> ConditionCheck.POSITIVE;
			case ALREADY_TAMED_BY_USER -> ConditionCheck.createNegative("already_tamed");
			case INVALID -> ConditionCheck.createNegative("hypnosis");
		};
	}

	private static void hypnosisClientFeedback(LivingEntity user, LivingEntity livingTarget) {
		Vec3 userPos = user.getEyePosition();
		Vec3 targetPos = livingTarget.getBoundingBox().getCenter();
		Vec3 particlesPos = userPos.add(targetPos.subtract(userPos).scale(0.5D));
		HamonSparksLoopSound.playSparkSound(user, particlesPos, 1.0F, true);
		CustomParticlesHelper.createHamonSparkParticles(null, particlesPos, 1);
	}

	public static class HypnosisInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public HypnosisInstance(EntityActionType ability) { super(ability); }

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				captureActionTargetFromAim(user);
			}
		}

		ActionTarget getHypnosisTarget(Level level) {
			return getActionTargetSnapshot(level);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null) return;
			ActionTarget target = getActionTargetSnapshot(level);
			if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)
					|| !checkHypnosisTarget(target, user).isPositive()) {
				return;
			}
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			Power<?> context = hamonAbility != null ? hamonAbility.getUserPower(user) : null;
			HamonData hamon = hamonAbility != null ? hamonAbility.getHamonData(context) : null;
			if (hamon != null) {
				float controlLvl = hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL;
				int duration = (int) (controlLvl * controlLvl * 24000);
				if (duration > 0) {
					HypnosisEffect.hypnotizeEntity(livingTarget, user, duration);
				}
			}
		}
	}
}
