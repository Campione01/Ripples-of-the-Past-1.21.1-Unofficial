package rotp.core.impl.powers.vampirism.abilities;

import rotp.core.init.ModDamageTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.powers.vampirism.VampirismData;

import net.minecraft.world.Difficulty;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
		if (!hasRemainingChargeBlood(context, 0)) {
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

	private boolean hasRemainingChargeBlood(Power<?> context, int heldTicks) {
		LivingEntity user = context != null ? context.getUser() : null;
		VampirismData data = getVampirismData(context);
		float remainingCost = HOLD_BLOOD_COST_PER_TICK * Math.max(HOLD_TO_FIRE_TICKS - heldTicks, 1);
		return user != null && data != null && (isCreative(context) || data.hasBlood(user, remainingCost));
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
			if (user != null && ability instanceof VampirismBloodGiftAbility giftAbility) {
				Power<?> context = giftAbility.getUserPower(user);
				// 1.16 PowerBaseImpl.checkRequirements ran every held tick: a stun ends the hold with its message.
				ConditionCheck check = context != null ? giftAbility.checkMainModLogicConditions(context) : ConditionCheck.POSITIVE;
				int heldTicks = Math.min(Mth.floor(getPhaseTick()) + 1, HOLD_TO_FIRE_TICKS);
				if (check.isPositive() && !giftAbility.hasRemainingChargeBlood(context, heldTicks)) {
					check = ConditionCheck.NEGATIVE;
				}
				if (!check.isPositive()) {
					ConditionCheck.sendActionFailedMessage(giftAbility, check, user);
					forceStop();
					syncPhaseChanges();
					return;
				}
			}
			if (user == null || !canContinueWindup(user)
					|| getPhaseTick() < HOLD_TO_FIRE_TICKS - 1 && !consumeBlood(user, HOLD_BLOOD_COST_PER_TICK)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			if (getPhaseTick() + 1 >= HOLD_TO_FIRE_TICKS) {
				float partialTick = Mth.clamp(Mth.frac(getPhaseTick()), 0.0F, 0.9999F);
				setPhaseStart(ActionPhase.PERFORM);
				setPartialTick(partialTick);
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
			if (user == null || !canContinueWindup(user)
					|| !(ability instanceof VampirismBloodGiftAbility giftAbility)
					|| !giftAbility.hasRemainingChargeBlood(giftAbility.getUserPower(user), HOLD_TO_FIRE_TICKS)) {
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
			if (entity instanceof Player player && isWithinGiftReach(user, player)) {
				return player;
			}
		}
		return null;
	}

	private static boolean isWithinGiftReach(LivingEntity user, LivingEntity target) {
		Vec3 eye = user.getEyePosition(1.0F);
		AABB box = target.getBoundingBox();
		double distance = 0.0D;
		if (!box.contains(eye)) {
			double eyeFraction = user.getBbHeight() == 0.0F ? 0.0D : user.getEyeHeight() / user.getBbHeight();
			Vec3 targetPoint = new Vec3(Mth.lerp(0.5D, box.minX, box.maxX),
					Mth.lerp(eyeFraction, box.minY, box.maxY), Mth.lerp(0.5D, box.minZ, box.maxZ));
			distance = box.clip(eye, targetPoint)
					.map(hit -> eye.distanceTo(hit) - user.getBbWidth() * 0.5D)
					.orElse(Double.POSITIVE_INFINITY);
		}
		double rangeSquared = user.hasLineOfSight(target) ? MAX_RANGE_SQ_ENTITY_TARGET : MAX_RANGE_SQ_ENTITY_TARGET / 4.0D;
		return distance * distance <= rangeSquared;
	}

	private static ActionTarget getAimTarget(Level level, LivingEntity user) {
		var aim = LivingComponentAction.getAim(user);
		return aim != null ? aim.getTarget().resolveEntityId(level) : ActionTarget.EMPTY;
	}
}
