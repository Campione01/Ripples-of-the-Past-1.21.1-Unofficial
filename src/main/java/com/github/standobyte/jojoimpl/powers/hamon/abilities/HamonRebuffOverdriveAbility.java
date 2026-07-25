package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.client.sound.ClientVoiceLineManager;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.HeldInput;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class HamonRebuffOverdriveAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 1200.0F;
	private static final int COOLDOWN_TICKS = 100;
	private static final float NORMAL_DAMAGE = 6.0F;
	private static final float COUNTER_DAMAGE = 10.0F;
	private static final double NORMAL_KNOCKBACK = 1.0D;
	private static final double COUNTER_KNOCKBACK = 2.0D;
	private static final AttributeModifier NO_KNOCKBACK = new AttributeModifier(
			JojoMod.resLoc("hamon_rebuff_overdrive_no_knockback"),
			1.0D,
			AttributeModifier.Operation.ADD_VALUE);

	public HamonRebuffOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, HamonRebuffOverdrive::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, HamonRebuffOverdrive.WINDUP_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, HamonRebuffOverdrive.PERFORM_TICKS);
		setDefaultPhaseLength(ActionPhase.RECOVERY, HamonRebuffOverdrive.RECOVERY_TICKS);
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		if (!level.isClientSide()) {
			HamonRebuffOverdrive activeRebuff = getActiveRebuff(user);
			if (activeRebuff != null && activeRebuff.canCancel()) {
				activeRebuff.cancel();
				bufferingState.setActionSuccess();
				return null;
			}
		}
		else if (user == Minecraft.getInstance().player && getActiveRebuff(user) == null
				&& user.getRandom().nextInt(5) == 0) {
			ClientVoiceLineManager.playVoiceLine(user, ModSoundEvents.JOSEPH_GIGGLE.get(),
					user.getSoundSource(), 1.0F, 1.0F, false);
		}
		return super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		LivingEntity user = context != null ? context.getUser() : null;
		HamonRebuffOverdrive activeRebuff = user != null ? getActiveRebuff(user) : null;
		if (activeRebuff != null && activeRebuff.canCancel()) {
			return ConditionCheck.POSITIVE;
		}
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		return user != null && hasFreeHands(user) ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("hands");
	}

	public static boolean onUserIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !target.isAlive()) {
			return false;
		}
		HamonRebuffOverdrive rebuff = getActiveRebuff(target);
		if (rebuff == null) {
			return false;
		}
		if (rebuff.getPhase() == ActionPhase.PERFORM) {
			event.setCanceled(true);
			return true;
		}
		if (!rebuff.isInWindup()) {
			return false;
		}
		if (!hasFreeHands(target)) {
			rebuff.stopWithCooldown(COOLDOWN_TICKS);
			return false;
		}
		Entity direct = event.getSource().getDirectEntity();
		if (!(direct instanceof LivingEntity attacker)) {
			return false;
		}
		if (!rebuff.isCounterTiming()) {
			rebuff.sayMistimedCounter();
			rebuff.stopWithCooldown(COOLDOWN_TICKS);
			return false;
		}
		if (!rebuff.canCounterDamage(event.getAmount())) {
			rebuff.playSwingSound(target);
			rebuff.stopWithCooldown(COOLDOWN_TICKS);
			return false;
		}
		if (rebuff.counterAttack(attacker)) {
			event.setCanceled(true);
			return true;
		}
		return false;
	}

	private static HamonRebuffOverdrive getActiveRebuff(LivingEntity user) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(user);
		return action instanceof HamonRebuffOverdrive rebuff ? rebuff : null;
	}

	private static boolean hasFreeHands(LivingEntity user) {
		return user.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
				&& user.getItemInHand(InteractionHand.OFF_HAND).isEmpty();
	}

	public static class HamonRebuffOverdrive extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		public static final int WINDUP_TICKS = 14;
		public static final int COUNTER_TIMING_WINDOW = 7;
		public static final int PERFORM_TICKS = 10;
		public static final int RECOVERY_TICKS = 16;
		private boolean didAttack;
		private boolean didSwing;
		private boolean saidMistimedCounter;

		public HamonRebuffOverdrive(EntityActionType ability) { super(ability); }

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
			if (user != null && !level().isClientSide()) {
				applyKnockbackResistance(user);
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			LivingEntity user = getPowerUser();
			if (user != null && !level().isClientSide()) {
				removeKnockbackResistance(user);
			}
		}

		@Override
		public void actionTick() {
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			if (level().isClientSide()) {
				if (isCounterTiming()) {
					HamonSparksLoopSound.playSparkSound(user, user.position(), 1.0F);
				}
				return;
			}
			if (isInWindup()) {
				if (!hasFreeHands(user)) {
					stopWithCooldown(COOLDOWN_TICKS);
					return;
				}
				if (!didSwing && getPhaseTick() >= WINDUP_TICKS - 3) {
					playSwingSound(user);
					user.swing(InteractionHand.MAIN_HAND, true);
					didSwing = true;
				}
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null || didAttack) {
				return;
			}
			ActionTarget target = getActionTargetSnapshot(level);
			LivingEntity livingTarget = null;
			if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity firstTarget) {
				livingTarget = firstTarget;
			}
			else {
				captureActionTargetFromAim(user);
				target = getActionTargetSnapshot(level);
				if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity)) {
					return;
				}
				livingTarget = (LivingEntity) target.getMainEntity();
			}
			punch(livingTarget, false);
		}

		public boolean isInWindup() {
			return getPhase() == ActionPhase.WINDUP;
		}

		public boolean isCounterTiming() {
			return isInWindup() && getPhaseTick() >= WINDUP_TICKS - COUNTER_TIMING_WINDOW;
		}

		public boolean canCancel() {
			return isInWindup();
		}

		public void cancel() {
			if (!canCancel()) {
				return;
			}
			float progress = WINDUP_TICKS > 0 ? getPhaseTick() / (float) WINDUP_TICKS : 1.0F;
			stopWithCooldown(Math.round(COOLDOWN_TICKS * progress * progress));
		}

		public void sayMistimedCounter() {
			if (!saidMistimedCounter) {
				LivingEntity user = getPowerUser();
				if (user != null && !level().isClientSide()) {
					JojoModUtil.sayVoiceLine(user, ModSoundEvents.JOSEPH_OH_NO);
				}
				saidMistimedCounter = true;
			}
		}

		public boolean canCounterDamage(float damageAmount) {
			LivingEntity user = getPowerUser();
			HamonData hamon = getHamonData(user);
			if (user == null || hamon == null) {
				return false;
			}
			float efficiency = hamon.getActionEfficiency(ENERGY_COST, true, ModHamonSkills.REBUFF_OVERDRIVE.get(), user);
			return efficiency >= 1.0F || efficiency >= damageAmount / user.getMaxHealth();
		}

		public boolean counterAttack(LivingEntity target) {
			LivingEntity user = getPowerUser();
			if (user == null || didAttack || level().isClientSide()) {
				return false;
			}
			JojoModUtil.sayVoiceLine(user, ModSoundEvents.JOSEPH_REBUFF_OVERDRIVE);
			boolean attacked = punch(target, true);
			if (attacked) {
				setPhaseStart(ActionPhase.PERFORM);
				syncPhaseChanges();
			}
			return attacked;
		}

		private boolean punch(LivingEntity target, boolean properCounter) {
			LivingEntity user = getPowerUser();
			HamonData hamon = getHamonData(user);
			if (user == null || hamon == null || target == null || !hasFreeHands(user)) {
				return false;
			}
			float preEnergy = hamon.getEnergy();
			float efficiency = hamon.getActionEfficiency(ENERGY_COST, true, ModHamonSkills.REBUFF_OVERDRIVE.get(), user);
			if (efficiency <= 0.0F) {
				return false;
			}
			float damage = (properCounter ? COUNTER_DAMAGE : NORMAL_DAMAGE) * efficiency;
			boolean hurt = HamonAbilityHelpers.hamonHurt(target, user, damage);
			if (!hurt) {
				return false;
			}
			if (properCounter) {
				level().playSound(null, target, ModSoundEvents.HAMON_REBUFF_PUNCH.get(),
						target.getSoundSource(), 1.0F, 1.0F);
				if (hamon.isSkillLearned(ModHamonSkills.HAMON_SHOCK.get())) {
					target.addEffect(new MobEffectInstance(ModStatusEffects.HAMON_SHOCK, 50, 0, false, false, true));
				}
			}
			target.knockback(properCounter ? COUNTER_KNOCKBACK : NORMAL_KNOCKBACK,
					user.getX() - target.getX(), user.getZ() - target.getZ());
			user.doHurtTarget(target);
			user.swing(InteractionHand.MAIN_HAND, true);
			hamon.consumeEnergy(ENERGY_COST, user);
			hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, Math.min(ENERGY_COST, preEnergy) * efficiency);
			hamon.setAbilityCooldown(hamonAbility().name(), properCounter ? 0 : COOLDOWN_TICKS / 2, COOLDOWN_TICKS);
			hamon.syncOnUpdate(user);
			didAttack = true;
			return true;
		}

		private void stopWithCooldown(int cooldown) {
			setCooldown(cooldown);
			forceStop();
			syncPhaseChanges();
		}

		private void setCooldown(int cooldown) {
			LivingEntity user = getPowerUser();
			HamonData hamon = getHamonData(user);
			HamonActionRuntimeAbility ability = hamonAbility();
			if (hamon != null && ability != null) {
				hamon.setAbilityCooldown(ability.name(), cooldown, COOLDOWN_TICKS);
				hamon.syncOnUpdate(user);
			}
		}

		private HamonData getHamonData(LivingEntity user) {
			HamonActionRuntimeAbility ability = hamonAbility();
			Power<?> context = ability != null && user != null ? ability.getUserPower(user) : null;
			return ability != null ? ability.getHamonData(context) : null;
		}

		private void playSwingSound(LivingEntity user) {
			level().playSound(null, user, ModSoundEvents.HAMON_SYO_SWING.get(), user.getSoundSource(), 1.0F, 0.5F);
		}

		private void applyKnockbackResistance(LivingEntity user) {
			AttributeInstance attribute = user.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (attribute != null) {
				attribute.removeModifier(NO_KNOCKBACK.id());
				attribute.addTransientModifier(NO_KNOCKBACK);
			}
		}

		private void removeKnockbackResistance(LivingEntity user) {
			AttributeInstance attribute = user.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (attribute != null) {
				attribute.removeModifier(NO_KNOCKBACK.id());
			}
		}
	}
}
