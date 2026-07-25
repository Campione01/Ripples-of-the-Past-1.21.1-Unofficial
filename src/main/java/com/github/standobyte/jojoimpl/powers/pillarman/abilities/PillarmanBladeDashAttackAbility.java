package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class PillarmanBladeDashAttackAbility extends PillarmanActionAbility {
	protected static final int HOLD_TO_FIRE_TICKS = 10;

	public PillarmanBladeDashAttackAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.LIGHT, false, 50.0F, 0.0F, 0.0F, 0,
				BladeDashInstance::new);
		setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 10);
		setDefaultPhaseLength(ActionPhase.PERFORM, 40);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		return user != null && user.onGround() ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
	}

	public static boolean onUserIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !target.isAlive()) {
			return false;
		}
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(target);
		return action != null
				&& action.ability instanceof PillarmanBladeDashAttackAbility
				&& action.getPhase() == ActionPhase.PERFORM;
	}

	public static class BladeDashInstance extends EntityActionInstance {
		private final Set<UUID> damagedEntities = new HashSet<>();
		private boolean energySpent;

		public BladeDashInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (ability instanceof PillarmanBladeDashAttackAbility dashAbility
					&& (newPhase == ActionPhase.BUTTON_CHARGE || newPhase == ActionPhase.PERFORM)) {
				userWalkSpeed = dashAbility.heldWalkSpeed;
				LivingEntity user = getPowerUser();
				if (user != null) {
					setBladesVisible(user, true);
				}
			}
			else {
				userWalkSpeed = 1.0F;
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			LivingEntity user = getPowerUser();
			if (user == null || !(ability instanceof PillarmanBladeDashAttackAbility dashAbility)) {
				return;
			}
			damagedEntities.clear();
			if (!level.isClientSide()) {
				Power<?> context = dashAbility.getUserPower(user);
				if (!dashAbility.consumeEnergy(context)) {
					forceStop();
					syncPhaseChanges();
					return;
				}
				energySpent = true;
			}
			Vec3 leap = Vec3.directionFromRotation(Mth.clamp(user.getXRot(), -45.0F, -18.0F), user.getYRot())
					.scale(1.0D + user.getAttributeValue(Attributes.MOVEMENT_SPEED) * 20.0D);
			user.setDeltaMovement(leap.x, leap.y * 0.05D, leap.z);
			user.hurtMarked = true;
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
			int performTicks = (int) getPhaseTick();
			if (performTicks == 1) {
				user.swing(InteractionHand.MAIN_HAND, true);
				level().playSound(null, user, ModSoundEvents.HAMON_SYO_SWING.get(), user.getSoundSource(), 1.0F, 1.0F);
				setBladesVisible(user, true);
			}
			if (!level().isClientSide() && energySpent) {
				hitTargets(user, level());
			}
			if (performTicks == 15) {
				setBladesVisible(user, false);
				forceStop();
				syncPhaseChanges();
			}
		}

		private void hitTargets(LivingEntity user, Level level) {
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, slashHitbox(user),
					entity -> entity != user && entity.isAlive() && JojoModUtil.canHarm(user, entity))) {
				if (damagedEntities.add(target.getUUID()) && dealPhysicalDamage(level, user, target)) {
					knockbackSideways(user, target);
					sparkEffect(target, 60);
				}
			}
		}

		private static boolean dealPhysicalDamage(Level level, LivingEntity user, LivingEntity target) {
			DamageSource damageSource = meleeDamageSource(level, user);
			float damage = (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE) + 5.0F;
			return target.hurt(damageSource, DamageUtil.addArmorPiercing(damage, 15.0F, target, damageSource));
		}

		private static void knockbackSideways(LivingEntity user, LivingEntity target) {
			Vec3 vecToTarget = target.position().subtract(user.position());
			boolean left = Mth.wrapDegrees(user.yBodyRot - MathUtil.yRotDegFromVec(vecToTarget)) < 0.0F;
			float knockbackYRot = (60.0F + user.getRandom().nextFloat() * 30.0F) * (left ? 1.0F : -1.0F);
			knockbackYRot += (float) -Mth.atan2(vecToTarget.x, vecToTarget.z) * MathUtil.RAD_TO_DEG;
			target.knockback(0.75F,
					Mth.sin(knockbackYRot * MathUtil.DEG_TO_RAD),
					-Mth.cos(knockbackYRot * MathUtil.DEG_TO_RAD));
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE) {
				PillarmanBladeDashAttackAbility dashAbility = ability instanceof PillarmanBladeDashAttackAbility dash ? dash : null;
				if (getPhaseTick() < HOLD_TO_FIRE_TICKS
						|| !level().isClientSide() && (dashAbility == null || !dashAbility.canContinueAction(this))) {
					forceStop();
				}
				else {
					setPhaseStart(ActionPhase.PERFORM);
				}
				syncPhaseChanges();
			}
		}

		@Override
		protected boolean shouldHoldPhaseAtEnd() {
			return getPhase() == ActionPhase.BUTTON_CHARGE;
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				setBladesVisible(user, false);
			}
			userWalkSpeed = 1.0F;
		}
	}

	public static AABB slashHitbox(LivingEntity user) {
		float xzAngle = -user.getYRot() * Mth.DEG_TO_RAD;
		Vec3 lookVec = new Vec3(Math.sin(xzAngle), 0.0D, Math.cos(xzAngle));
		Vec3 hitboxXZCenter = user.position().add(lookVec.scale(user.getBbWidth() * 0.75F));
		return new AABB(hitboxXZCenter, hitboxXZCenter)
				.inflate(user.getBbWidth() * 1.5F, 0.125D, user.getBbWidth() * 1.5F)
				.expandTowards(0.0D, user.getBbHeight() / 2.0D, 0.0D);
	}
}
