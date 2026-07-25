package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.mechanics.KnockbackCollisionImpact;
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

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class HamonSunlightYellowOverdriveAbility extends HamonActionRuntimeAbility {
	private final int minChargeTicks;
	private final int maxChargeTicks;
	private final int stopTick;
	private final Map<UUID, Float> spentEnergy = new HashMap<>();

	public HamonSunlightYellowOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		this(abilityType, abilityId, SYOverdrive::new, 10, 40, 11);
	}

	protected HamonSunlightYellowOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId,
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj,
			int minChargeTicks, int maxChargeTicks, int stopTick) {
		super(abilityType, abilityId, createActionObj);
		this.minChargeTicks = minChargeTicks;
		this.maxChargeTicks = maxChargeTicks;
		this.stopTick = stopTick;
		setDefaultPhaseLength(ActionPhase.WINDUP, maxChargeTicks);
		setDefaultPhaseLength(ActionPhase.PERFORM, stopTick);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
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
		if (!isRequiredHandFree(user)) {
			return ConditionCheck.createNegative("hand");
		}
		HamonData hamon = getHamonData(context);
		return hamon != null && (isCreative(context) || hamon.getEnergy() > 0.0F)
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("some_energy");
	}

	@Override
	protected float getHeldTickEnergyCost(Power<?> context, int ticksHeld) {
		HamonData hamon = getHamonData(context);
		return hamon != null ? getActualMaxEnergy(hamon) / Math.max(maxChargeTicks, 1) : 0.0F;
	}

	@Override
	protected boolean consumeRuntimeOnPerform(LivingEntity user) {
		Power<?> context = getUserPower(user);
		HamonData hamon = getHamonData(context);
		if (hamon == null) {
			return false;
		}
		playHamonShout(user, hamon);
		return true;
	}

	protected boolean isRequiredHandFree(LivingEntity user) {
		return user.getItemInHand(getRequiredFreeHand()).isEmpty();
	}

	protected InteractionHand getRequiredFreeHand() {
		return InteractionHand.MAIN_HAND;
	}

	protected InteractionHand getSwingHand() {
		return InteractionHand.MAIN_HAND;
	}

	protected int getMinChargeTicks() {
		return minChargeTicks;
	}

	protected int getStopTick() {
		return stopTick;
	}

	protected ParticleOptions getPunchParticles() {
		return ModParticles.HAMON_SPARK_YELLOW.get();
	}

	protected static float getActualMaxEnergy(HamonData hamon) {
		return hamon.getMaxBreathStability();
	}

	private void resetSpentEnergy(LivingEntity user) {
		spentEnergy.remove(user.getUUID());
	}

	private float takeSpentEnergy(LivingEntity user) {
		Float spent = spentEnergy.remove(user.getUUID());
		return spent != null ? spent : 0.0F;
	}

	protected boolean consumeChargeTick(LivingEntity user, int ticksHeld) {
		Power<?> context = getUserPower(user);
		HamonData hamon = getHamonData(context);
		if (hamon == null || hamon.isMeditating()) {
			return false;
		}
		float tickCost = getHeldTickEnergyCost(context, ticksHeld);
		if (tickCost <= 0.0F) {
			return true;
		}
		if (isCreative(context)) {
			addSpentEnergy(user, tickCost);
			return true;
		}
		float energyBefore = hamon.getEnergy();
		float consumed = Math.min(tickCost, energyBefore);
		if (consumed <= 0.0F) {
			return true;
		}
		hamon.setEnergy(energyBefore - consumed);
		addSpentEnergy(user, consumed);
		if (ticksHeld % 5 == 0) {
			hamon.syncOnUpdate(user);
		}
		return true;
	}

	private void addSpentEnergy(LivingEntity user, float tickCost) {
		spentEnergy.merge(user.getUUID(), tickCost, Float::sum);
	}

	protected void refundSpentEnergy(LivingEntity user) {
		Power<?> context = getUserPower(user);
		HamonData hamon = getHamonData(context);
		if (hamon == null || isCreative(context)) {
			resetSpentEnergy(user);
			return;
		}
		float spent = takeSpentEnergy(user);
		if (spent > 0.0F) {
			hamon.setEnergy(Math.min(hamon.getMaxEnergy(), hamon.getEnergy() + spent));
			hamon.syncOnUpdate(user);
		}
	}

	public static class SYOverdrive extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private int ticksHeld;
		private boolean fired;
		private float auraChargeStartEnergy;
		protected float energySpentRatio;
		@Nullable protected HamonData userHamon;

		public SYOverdrive(EntityActionType ability) { super(ability); }

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.WINDUP && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					ClientsideSoundsHelper.playLoopingActionSound(ModSoundEvents.HAMON_SYO_CHARGE.get(), user, this,
							ActionPhase.WINDUP, 1.0F, 1.0F);
				}
			}
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			HamonSunlightYellowOverdriveAbility ability = syoAbility();
			if (user != null && ability != null) {
				ability.resetSpentEnergy(user);
				HamonData hamon = ability.getHamonData(ability.getUserPower(user));
				auraChargeStartEnergy = hamon != null ? hamon.getEnergy() : 0.0F;
			}
		}

		public float getSpentEnergyForAura(HamonData hamon) {
			HamonSunlightYellowOverdriveAbility ability = syoAbility();
			if (ability == null || hamon == null || ticksHeld <= 0) {
				return 0.0F;
			}
			float tickCost = getActualMaxEnergy(hamon) / Math.max(ability.maxChargeTicks, 1);
			float estimatedSpent = tickCost * Math.min(ticksHeld, ability.maxChargeTicks);
			float missingFromClientEnergy = Math.max(auraChargeStartEnergy - hamon.getEnergy(), 0.0F);
			return Math.min(missingFromClientEnergy, estimatedSpent);
		}

		@Override
		public void actionTick() {
			LivingEntity user = getPowerUser();
			HamonSunlightYellowOverdriveAbility ability = syoAbility();
			if (user == null || ability == null) {
				return;
			}
			if (getPhase() == ActionPhase.WINDUP) {
				if (!level().isClientSide() && !ability.consumeChargeTick(user, ticksHeld)) {
					forceStop();
					syncPhaseChanges();
					return;
				}
				ticksHeld++;
				return;
			}
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			int tick = (int) getPhaseTick();
			if (tick == 1) {
				user.swing(ability.getSwingHand(), true);
				level().playSound(null, user.getX(), user.getEyeY(), user.getZ(),
						ModSoundEvents.HAMON_SYO_SWING.get(), user.getSoundSource(), 1.0F, 1.0F);
			}
			else if (tick == 4 && !level().isClientSide()) {
				performPunch(user);
			}
			else if (tick >= ability.getStopTick()) {
				forceStop();
				syncPhaseChanges();
			}
		}

		@Override
		public void actionPerformStart() {
			LivingEntity user = getPowerUser();
			HamonSunlightYellowOverdriveAbility ability = syoAbility();
			if (user == null || ability == null) {
				return;
			}
			fired = true;
			Power<?> context = ability.getUserPower(user);
			userHamon = ability.getHamonData(context);
			float spent = !level().isClientSide() ? ability.takeSpentEnergy(user) : 0.0F;
			float maxEnergy = userHamon != null ? getActualMaxEnergy(userHamon) : 0.0F;
			energySpentRatio = maxEnergy > 0.0F ? Math.min(spent / maxEnergy, 1.0F) : 0.0F;
		}

		@Override
		public void onButtonStopHold() {
			HamonSunlightYellowOverdriveAbility ability = syoAbility();
			if (ability != null && getPhase() == ActionPhase.WINDUP) {
				if (getPhaseTick() >= ability.getMinChargeTicks()) {
					setPhaseStart(ActionPhase.PERFORM);
				}
				else {
					forceStop();
				}
				syncPhaseChanges();
				return;
			}
			super.onButtonStopHold();
		}

		private void performPunch(LivingEntity user) {
			HamonSunlightYellowOverdriveAbility ability = syoAbility();
			if (ability == null || userHamon == null || !ability.isRequiredHandFree(user)) {
				return;
			}
			ActionTarget target = HamonAbilityHelpers.getAimTarget(user, level());
			if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget) {
				doHamonAttack(user, livingTarget, ability);
				HamonAbilityHelpers.doMeleeAttack(user, livingTarget);
				if (user instanceof Player player) {
					player.resetAttackStrengthTicker();
				}
			}
		}

		protected void doHamonAttack(LivingEntity user, LivingEntity target, HamonSunlightYellowOverdriveAbility ability) {
			float efficiency = userHamon.getActionEfficiency(0.0F, true, ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get(), user);
			float damage = (3.25F + 6.75F * energySpentRatio) * efficiency;
			if (HamonAbilityHelpers.hamonHurtWithParticles(target, user, damage, ability.getPunchParticles(), 12)) {
				target.level().playSound(null, target, ModSoundEvents.HAMON_SYO_PUNCH.get(),
						target.getSoundSource(), energySpentRatio, 1.0F);
				userHamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH,
						getActualMaxEnergy(userHamon) * energySpentRatio * efficiency);
				target.knockback(2.5F, user.getX() - target.getX(), user.getZ() - target.getZ());
				if (userHamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get())) {
					KnockbackCollisionImpact.getHandler(target)
							.onPunchSetKnockbackImpact(target.getDeltaMovement(), user)
							.hamonDamage(damage, 0, ability.getPunchParticles());
				}
				userHamon.syncOnUpdate(user);
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			LivingEntity user = getPowerUser();
			HamonSunlightYellowOverdriveAbility ability = syoAbility();
			if (!fired && user != null && ability != null && !level().isClientSide()) {
				ability.refundSpentEnergy(user);
			}
			userWalkSpeed = 1.0F;
		}

		@Nullable
		protected HamonSunlightYellowOverdriveAbility syoAbility() {
			return ability instanceof HamonSunlightYellowOverdriveAbility syo ? syo : null;
		}
	}
}
