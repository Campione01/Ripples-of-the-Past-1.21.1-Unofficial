package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.init.ModSoundEvents;
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
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class HamonOverdriveBeatAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 1500.0F;
	private static final float DAMAGE = 3.0F;

	public HamonOverdriveBeatAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, HamonOverdriveBeat::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 8);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		hamonHeldWalkSpeed(0.5F);
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
		if (!user.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
			return ConditionCheck.createNegative("hand");
		}
		HamonData hamon = getHamonData(context);
		return hamon != null && hamon.hasEnergy(ENERGY_COST) ? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative("no_energy_hamon");
	}

	@Override
	protected boolean consumeRuntimeOnPerform(LivingEntity user) {
		return true;
	}

	public static class HamonOverdriveBeat extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		public HamonOverdriveBeat(EntityActionType ability) { super(ability); }

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				captureActionTargetFromAim(user);
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = newPhase == ActionPhase.PERFORM ? 0.5F : 1.0F;
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			int tick = (int) getPhaseTick();
			if (tick == 2) {
				user.swing(InteractionHand.OFF_HAND, true);
				level().playSound(null, user.getX(), user.getEyeY(), user.getZ(),
						ModSoundEvents.HAMON_SYO_SWING.get(), user.getSoundSource(), 1.0F, 1.5F);
			}
			else if (tick == 5 && !level().isClientSide()) {
				punch(user);
			}
			else if (tick >= 8) {
				forceStop();
				syncPhaseChanges();
			}
		}

		private void punch(LivingEntity user) {
			HamonActionRuntimeAbility ability = hamonAbility();
			if (!(ability instanceof HamonOverdriveBeatAbility beatAbility)) {
				return;
			}
			Power<?> context = beatAbility.getUserPower(user);
			HamonData hamon = beatAbility.getHamonData(context);
			if (hamon == null || !user.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
				return;
			}
			Level level = level();
			captureActionTargetFromAim(user);
			ActionTarget target = getActionTargetSnapshot(level);
			if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)) {
				return;
			}
			float efficiency = hamon.getActionEfficiency(ENERGY_COST, true, ModHamonSkills.OVERDRIVE.get(), user);
			if (HamonAbilityHelpers.hamonHurt(livingTarget, user, DAMAGE * efficiency)) {
				level.playSound(null, livingTarget, ModSoundEvents.HAMON_SYO_PUNCH.get(),
						SoundSource.PLAYERS, 1.0F, 1.5F);
				livingTarget.knockback(1.25F, user.getX() - livingTarget.getX(), user.getZ() - livingTarget.getZ());
				float pointsEnergy = Math.min(ENERGY_COST, hamon.getEnergy());
				hamon.consumeEnergy(ENERGY_COST, user);
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, pointsEnergy * efficiency);
				hamon.syncOnUpdate(user);
			}
			HamonAbilityHelpers.doMeleeAttack(user, livingTarget);
			if (user instanceof Player player) {
				player.resetAttackStrengthTicker();
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			userWalkSpeed = 1.0F;
		}
	}
}
