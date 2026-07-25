package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.entity_possessionv2.LivingComponentPossession;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class PillarmanHideInEntityAbility extends PillarmanActionAbility {
	private static final int HOLD_TO_FIRE_TICKS = 20;

	public PillarmanHideInEntityAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 2, PillarmanMode.NONE, false, 0.0F, 0.0F, 0.0F, 0,
				HideInEntityInstance::new);
		setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		return user != null && getValidTarget(user, user.level()) != null
				? ConditionCheck.POSITIVE
				: ConditionCheck.NEGATIVE;
	}

	private static LivingEntity getValidTarget(LivingEntity user, Level level) {
		var aim = LivingComponentAction.getAim(user);
		if (aim == null) {
			return null;
		}
		ActionTarget target = aim.getTarget().resolveEntityId(level);
		return target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget
				&& !livingTarget.is(user) ? livingTarget : null;
	}

	public static class HideInEntityInstance extends EntityActionInstance {
		public HideInEntityInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (newPhase == ActionPhase.BUTTON_CHARGE || newPhase == ActionPhase.PERFORM) {
				userWalkSpeed = 0.0F;
			}
			else {
				userWalkSpeed = 1.0F;
			}
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && getPhaseTick() < HOLD_TO_FIRE_TICKS) {
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
			if (user == null) {
				return;
			}
			LivingEntity target = getValidTarget(user, level);
			if (target != null) {
				LivingComponentPossession.setPossessionTarget(user, target, "pillarman_hide_in_entity");
			}
		}
	}
}
