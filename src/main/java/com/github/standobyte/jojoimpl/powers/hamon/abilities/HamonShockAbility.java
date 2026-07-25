package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.EntityActionAbility;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class HamonShockAbility extends EntityActionAbility {
	private static final int SHOCK_TICK = 9;
	private static final int STOP_TICK = 15;
	private static final float WALK_SPEED = 0.25F;

	public HamonShockAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, ShockInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, SHOCK_TICK);
		setDefaultPhaseLength(ActionPhase.PERFORM, STOP_TICK - SHOCK_TICK);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 1);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context != null ? context.getUser() : null;
		HamonData hamon = getHamonData(context);
		if (user == null || hamon == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (hamon.getEnergy() <= 0.0F) {
			return ConditionCheck.createNegative("some_energy");
		}
		return isValidShockTarget(getAimTarget(user, user.level())) ? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative("living_mob_shock");
	}

	private static boolean isValidShockTarget(ActionTarget target) {
		if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)) {
			return false;
		}
		return livingTarget.isAlive()
				&& !JojoDefinitions.isUndeadOrVampiric(livingTarget)
				&& !ModStatusEffects.isStunned(livingTarget);
	}

	private static ActionTarget getAimTarget(LivingEntity user, Level level) {
		var aim = LivingComponentAction.getAim(user);
		ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
		return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
	}

	private static HamonData getHamonData(Power<?> context) {
		PowerData data = context != null ? context.getCurTypeData() : null;
		return data instanceof HamonData hamon ? hamon : null;
	}

	public static class ShockInstance extends EntityActionInstance {
		private LivingEntity shockedTarget;

		public ShockInstance(EntityActionType ability) { super(ability); }

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				captureActionTargetFromAim(user);
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = newPhase != null ? WALK_SPEED : 1.0F;
		}

		@Override
		public void actionTick() {
			if (level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					HamonSparksLoopSound.playSparkSound(user, user.position(), 1.0F, true);
				}
				return;
			}
			if (shockedTarget != null && getPhase() == ActionPhase.PERFORM) {
				HamonUtil.emitHamonSparkParticles(level(), null, shockedTarget.getBoundingBox().getCenter(), 1.0F);
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null) return;
			ActionTarget target = getActionTargetSnapshot(level);
			if (!isValidShockTarget(target) || !(target.getMainEntity() instanceof LivingEntity targetEntity)) {
				return;
			}
			Power<?> context = ability instanceof HamonShockAbility shockAbility
					? shockAbility.getUserPower(user) : null;
			HamonData hamon = getHamonData(context);
			if (hamon == null) {
				return;
			}
			user.swing(InteractionHand.MAIN_HAND, true);
			float strengthRatio = hamon.getHamonStrengthLevel() / (float) HamonData.MAX_STAT_LEVEL;
			float controlRatio = hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL;
			float energy = hamon.getEnergy();
			float energyRatio = hamon.getMaxEnergy() > 0.0F ? energy / hamon.getMaxEnergy() : 0.0F;
			float efficiency = hamon.getActionEfficiency(0.0F, false, ModHamonSkills.HAMON_SHOCK.get(), user);
			int duration = (int) (20 + (80 * controlRatio + 60 * energyRatio) * efficiency);
			int amplifier = (int) (strengthRatio * 0.05F * efficiency);
			hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, energy * efficiency);
			hamon.setEnergy(0.0F);
			hamon.syncOnUpdate(user);
			shockedTarget = targetEntity;
			targetEntity.addEffect(new MobEffectInstance(ModStatusEffects.HAMON_SHOCK,
					duration, amplifier, false, false, true));
			HamonUtil.emitHamonSparkParticles(level, user instanceof Player player ? player : null,
					targetEntity.getBoundingBox().getCenter(), 1.0F);
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			userWalkSpeed = 1.0F;
		}
	}
}
