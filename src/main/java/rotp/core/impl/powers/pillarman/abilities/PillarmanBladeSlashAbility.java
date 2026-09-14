package rotp.core.impl.powers.pillarman.abilities;

import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.UtilFunctions;
import rotp.core.impl.powers.pillarman.PillarmanMode;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class PillarmanBladeSlashAbility extends PillarmanActionAbility {

	public PillarmanBladeSlashAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.LIGHT, false, 10.0F, BladeSlashInstance::new);
		setDefaultPhaseLength(ActionPhase.PERFORM, 20);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		return user != null && UtilFunctions.isHandFree(
				user, InteractionHand.MAIN_HAND)
				? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative("hand");
	}

	public static class BladeSlashInstance extends EntityActionInstance {
		private boolean energySpent;

		public BladeSlashInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = newPhase == ActionPhase.PERFORM ? 0.5F : 1.0F;
		}

		@Override
		public void actionPerformStart() {
			if (level().isClientSide() || !(ability instanceof PillarmanBladeSlashAbility slashAbility)) {
				return;
			}
			LivingEntity user = getPowerUser();
			Power<?> context = user != null ? slashAbility.getUserPower(user) : null;
			if (user == null || !slashAbility.consumeEnergy(context)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			energySpent = true;
		}

		@Override
		public void actionTick() {
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			int fullTicks = (int) getFullTicksPassed();
			if (fullTicks == 2) {
				setBladesVisible(user, true);
			}
			if (fullTicks == 6) {
				user.swing(InteractionHand.MAIN_HAND, true);
				level().playSound(null, user, ModSoundEvents.HAMON_SYO_SWING.get(), user.getSoundSource(), 1.0F, 1.25F);
			}
			if (!level().isClientSide() && energySpent && fullTicks == 8) {
				hitTarget(user, level());
			}
			if (fullTicks >= 20) {
				forceStop();
				syncPhaseChanges();
			}
		}

		private static void hitTarget(LivingEntity user, Level level) {
			ActionTarget target = LivingComponentAction.getAim(user).getTarget();
			if (target == null) {
				return;
			}
			target = target.resolveEntityId(level);
			if (target.getType() != TargetType.ENTITY) {
				return;
			}
			Entity entity = target.getEntity();
			if (!(entity instanceof LivingEntity targetLiving) || !targetLiving.isAlive() || !user.canAttack(targetLiving)) {
				return;
			}
			DamageSource damageSource = meleeDamageSource(level, user);
			float damage = (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE) + 5.0F;
			if (targetLiving.hurt(damageSource, DamageUtil.addArmorPiercing(damage, 15.0F, targetLiving, damageSource))) {
				sparkEffect(targetLiving, 9);
				level.playSound(null, targetLiving, ModSoundEvents.THE_WORLD_PUNCH_HEAVY_ENTITY.get(),
						targetLiving.getSoundSource(), 1.2F, 0.8F);
				targetLiving.knockback(0.75F, user.getX() - targetLiving.getX(), user.getZ() - targetLiving.getZ());
				KnockbackCollisionImpact.getHandler(targetLiving)
						.onPunchSetKnockbackImpact(targetLiving.getDeltaMovement(), user);
			}
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
}
