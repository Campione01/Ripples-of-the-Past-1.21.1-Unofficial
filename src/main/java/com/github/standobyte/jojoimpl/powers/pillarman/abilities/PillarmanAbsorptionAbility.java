package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
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
import com.github.standobyte.jojo.util.functions.UtilFunctions;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonProtectionAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;

import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class PillarmanAbsorptionAbility extends PillarmanActionAbility {

	public PillarmanAbsorptionAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 2, PillarmanMode.NONE, true, 0.0F, AbsorptionInstance::new);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		setIgnoresPerformerStun();
	}

	@Override
	public ConditionCheck checkSpecificConditions(com.github.standobyte.jojo.powersystem.Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!UtilFunctions.isHandFree(
				user, InteractionHand.MAIN_HAND)) {
			return ConditionCheck.createNegative("hand");
		}
		Level level = user.level();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return ConditionCheck.createNegative("peaceful");
		}
		if (findAbsorptionTarget(user, level) == null) {
			return ConditionCheck.NEGATIVE;
		}
		return ConditionCheck.POSITIVE;
	}

	public static boolean absorb(LivingEntity attacker, LivingEntity target, float absorbDamage) {
		Level level = attacker.level();
		if (level.isClientSide()) {
			return false;
		}
		DamageSource damageSource = DamageUtil.make(level, ModDamageTypes.PILLAR_MAN_ABSORPTION, attacker, attacker);
		if (HamonProtectionAbility.preventBlockDamage(target, damageSource, absorbDamage)) {
			return false;
		}
		return dealAbsorptionDamage(target, absorbDamage, damageSource);
	}

	public static boolean dealAbsorptionDamage(LivingEntity target, float absorbDamage, DamageSource damageSource) {
		boolean hurt = target.hurt(damageSource, absorbDamage);
		if (hurt) {
			int duration = Mth.floor(20.0F * absorbDamage);
			addOrExtendEffect(target, MobEffects.MOVEMENT_SLOWDOWN, duration, 1);
			addOrExtendEffect(target, MobEffects.DIG_SLOWDOWN, duration, 1);
			addOrExtendEffect(target, MobEffects.WEAKNESS, duration, 1);
			addOrExtendEffect(target, MobEffects.CONFUSION, duration, 1);
		}
		return hurt;
	}

	private static void addOrExtendEffect(LivingEntity target, Holder<MobEffect> effect, int duration, int amplifier) {
		MobEffectInstance old = target.getEffect(effect);
		int newDuration = duration + (old != null ? old.getDuration() : 0);
		target.addEffect(new MobEffectInstance(effect, newDuration, amplifier));
	}

	private static float bloodDrainMultiplier(Level level) {
		var values = JojoModConfig.getCommonConfigInstance(false).bloodDrainMultiplier.get();
		if (values.isEmpty()) {
			return 1.0F;
		}
		int index = Mth.clamp(level.getDifficulty().getId(), 0, values.size() - 1);
		return values.get(index).floatValue();
	}

	private static LivingEntity findAbsorptionTarget(LivingEntity user, Level level) {
		var aim = LivingComponentAction.getAim(user);
		if (aim == null) {
			return null;
		}
		ActionTarget target = aim.getTarget().resolveEntityId(level);
		if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget
				&& livingTarget != user && livingTarget.isAlive() && user.distanceToSqr(livingTarget) <= 4.0D) {
			return livingTarget;
		}
		return null;
	}

	public static class AbsorptionInstance extends EntityActionInstance {
		public AbsorptionInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.PERFORM && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					ClientsideSoundsHelper.playLoopingActionSound(ModSoundEvents.PILLAR_MAN_ABSORPTION.get(), user, this,
							ActionPhase.PERFORM, 1.25F, 0.8F);
				}
			}
		}

		@Override
		public void actionTick() {
			Level level = level();
			if (level.isClientSide() || getPhase() != ActionPhase.PERFORM) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			LivingEntity target = findAbsorptionTarget(user, level);
			if (target != null && absorb(user, target, 2.0F)) {
				PlayerPower.getPowerData(user, PillarmanPowerType.PILLAR_MAN).ifPresent(data -> {
					data.addEnergy(user, bloodDrainMultiplier(level) * 35.0F);
					data.syncOnUpdate(user);
				});
			}
		}

		@Override
		public void onButtonStopHold() {
			forceStop();
			syncPhaseChanges();
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}
	}
}
