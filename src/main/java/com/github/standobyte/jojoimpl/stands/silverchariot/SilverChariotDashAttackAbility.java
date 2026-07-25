package com.github.standobyte.jojoimpl.stands.silverchariot;

import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import java.util.HashSet;
import java.util.Set;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SilverChariotDashAttackAbility extends StandEntityAbility {

	private static final int STAMINA_COST = 50;
	private static final float DASH_KNOCKBACK_TO_STRENGTH = 0.5F;
	private static final float ARMORED_DASH_EXTRA_KNOCKBACK = 1.5F;
	private static final float FAST_DASH_EXTRA_KNOCKBACK = 0.25F;
	private static final float FAST_DASH_KNOCKBACK_XROT = -90F;

	public SilverChariotDashAttackAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, DashStrike::new);
		partsRequired(StandPart.MAIN_BODY, StandPart.ARMS);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 2);
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && performer instanceof StandEntity stand) {
			action.phasesLength.put(ActionPhase.PERFORM, Math.max(StandStatFormulas.getHeavyAttackWindup(
					stand.getAttackSpeed(), stand.getFinisherMeter()), 2));
			action.phasesLength.put(ActionPhase.RECOVERY, StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), stand.getFinisherMeter()));
		}
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		if (lacksRapier(context)) {
			return ConditionCheck.createNegative("chariot_rapier");
		}
		return StandAbilityStamina.check(context, STAMINA_COST);
	}

	private static boolean lacksRapier(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower == null) {
			return false;
		}
		LivingEntity user = standPower.getUser();
		if (user == null) {
			return false;
		}
		SilverChariotState state = SilverChariotState.get(user);
		return state != null && !state.hasRapier();
	}

	@Override
	public boolean noAdheringToUserOffset(StandPower standPower, StandEntity standEntity) {
		return true;
	}

	@Override
	public boolean noAdheringToUserOffsetClientFallback(StandEntity standEntity) {
		return true;
	}

	@Override
	public boolean lockStandManualMovement(StandPower standPower, StandEntity standEntity) {
		return true;
	}

	public static class DashStrike extends EntityActionInstance {
		private final Set<Integer> hitEntities = new HashSet<>();
		private Vec3 dashStep = Vec3.ZERO;
		private Vec3 dashForward = Vec3.ZERO;
		private ActionTarget dashTarget = ActionTarget.EMPTY;
		private boolean retractAfterDash;

		public DashStrike(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			if (getPerformer() instanceof StandEntity stand) {
				ActionTarget target = captureActionTargetFromAim(stand);
				keepStandAimedAtTarget(target);
				retractAfterDash = stand.followingUserIsEnabled() && !stand.isManuallyControlled() && !stand.isBeingRetracted();
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (newPhase != ActionPhase.PERFORM || !(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (!level().isClientSide()) {
				if (user == null) {
					startRecovery();
					return;
				}
				SilverChariotState state = SilverChariotState.get(user);
				if (state != null && !state.hasRapier()) {
					startRecovery();
					return;
				}
				StandPower standPower = StandPower.get(user);
				if (!StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST)) {
					startRecovery();
					return;
				}
			}
			else if (ClientGlobals.canHearStand(stand)) {
				level().playLocalSound(stand, ClientsideSoundsHelper.withStandSkin(
						ModSoundEvents.SILVER_CHARIOT_DASH.get(), stand), 
						stand.getSoundSource(), 1.0F, 1.0F);
			}
			dashForward = dashLook(stand, user);
			dashTarget = !level().isClientSide() ? captureDashTarget(level(), stand) : ActionTarget.EMPTY;
			hitEntities.clear();
			if (!level().isClientSide() && retractAfterDash && stand.getAttackSpeed() < 24 && user != null) {
				Vec3 userForward = horizontalLook(user, null, 0.0);
				stand.setPos(user.getX() + userForward.x, stand.getY(), user.getZ() + userForward.z);
			}
			float performTicks = Math.max(phasesLength.getFloat(ActionPhase.PERFORM), 1.0F);
			dashStep = dashForward.scale(10D / performTicks);
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM || !(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			boolean moveForward = getCurPhaseLength() <= 0 || getPhaseRatio() <= 0.5F;
			boolean hadForwardMovement = stand.getDeltaMovement().lengthSqr() > 1.0E-7;

			Level level = level();
			if (!level.isClientSide()) {
				if (moveForward) {
					hitDashTargets(level, stand);
				}
				else if (hadForwardMovement) {
					hitDashTarget(level, stand, dashTarget);
				}
			}
			if (!level.isClientSide() && getPhaseTicksLeft() <= 1 && retractAfterDash) {
				stand.retract();
			}
			stand.setDeltaMovement(moveForward ? dashStep : Vec3.ZERO);
			stand.hurtMarked = true;
		}

		@Override
		public void actionPerformEnd() {
			if (getPerformer() instanceof StandEntity stand) {
				stand.setDeltaMovement(Vec3.ZERO);
				if (!level().isClientSide() && retractAfterDash) {
					stand.retract();
				}
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			if (getPerformer() instanceof StandEntity stand) {
				stand.setDeltaMovement(Vec3.ZERO);
			}
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return getPhase() == ActionPhase.RECOVERY && cancellingAbility == ability;
		}

		private ActionTarget captureDashTarget(Level level, StandEntity stand) {
			ActionTarget target = getActionTargetSnapshot(level);
			double blockReach = stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
			double entityReach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
			if (!target.isEmpty(level)
					&& !HitResultUtil.isTargetWithinRange(target, stand, level, blockReach, entityReach)) {
				target = ActionTarget.EMPTY;
			}
			if (target.isEmpty(level)) {
				var aim = LivingComponentAction.getAim(stand);
				target = aim != null ? aim.getTarget().resolveEntityId(level) : ActionTarget.EMPTY;
			}
			if (!target.isEmpty(level)
					&& !HitResultUtil.isTargetWithinRange(target, stand, level, blockReach, entityReach)) {
				target = ActionTarget.EMPTY;
			}
			if (target.isEmpty(level)) {
				target = HitResultUtil.clip(
						stand.getEyePosition(),
						stand.getLookAngle(),
						blockReach,
						entityReach,
						level,
						entity -> StandEntityPunchAbility.canStandHit(stand, entity),
						stand,
						0);
			}
			return target.isEmpty(level) ? ActionTarget.EMPTY : target.copy().resolveEntityId(level);
		}

		private void hitDashTargets(Level level, StandEntity stand) {
			double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
			for (ActionTarget target : HitResultUtil.clipMultipleTargets(stand, reach,
					entity -> StandEntityPunchAbility.canStandHit(stand, entity), 0.25D, stand.getPrecision())) {
				hitDashTarget(level, stand, target);
			}
		}

		private void hitDashTarget(Level level, StandEntity stand, ActionTarget target) {
			target = target.resolveEntityId(level);
			if (target.isEmpty(level)) {
				return;
			}
			switch (target.getType()) {
				case ENTITY -> {
					if (!(target.getMainEntity() instanceof LivingEntity candidate)) {
						return;
					}
					if (!hitEntities.add(candidate.getId())) {
						return;
					}
					float damage = StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage());
					DamageSource dmgSource = makePunchDamageSource();
					if (dmgSource instanceof DamageSourceModified modified) {
						modified.jojo_ripples$setStandInvulTicks(10);
					}
					if (standEntityAttack(stand, candidate, dmgSource, damage)) {
						knockbackTarget(stand, candidate, dashForward);
						candidate.hurtMarked = true;
					}
				}
				case BLOCK -> StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(target, level, stand, !isUserCreative());
				default -> {
				}
			}
			punchedTarget = target;
		}

		private static Vec3 horizontalLook(LivingEntity entity, LivingEntity fallback, double yScale) {
			Vec3 look = entity.getLookAngle().multiply(1.0, yScale, 1.0);
			if (look.lengthSqr() < 1.0E-6 && fallback != null) {
				look = fallback.getLookAngle().multiply(1.0, yScale, 1.0);
			}
			if (look.lengthSqr() < 1.0E-6) {
				return new Vec3(0.0, 0.0, 1.0);
			}
			return look.normalize();
		}

		private static Vec3 dashLook(StandEntity stand, LivingEntity fallback) {
			Vec3 look = stand.getLookAngle();
			if (look.lengthSqr() < 1.0E-6 && fallback != null) {
				look = fallback.getLookAngle();
			}
			if (look.lengthSqr() < 1.0E-6) {
				return new Vec3(0.0, 0.0, 1.0);
			}
			return look.normalize();
		}

		private static void knockbackTarget(StandEntity stand, LivingEntity target, Vec3 forward) {
			if (stand.getAttackSpeed() < 24) {
				Vec3 toTarget = target.position().subtract(stand.position()).multiply(1.0, 0.0, 1.0);
				double side = forward.x * toTarget.z - forward.z * toTarget.x < 0 ? 1.0 : -1.0;
				double angle = Math.toRadians((60.0F + stand.getRandom().nextFloat() * 30.0F) * side);
				Vec3 away = toTarget.lengthSqr() > 1.0E-6 ? toTarget.normalize() : forward;
				double x = away.x * Math.cos(angle) - away.z * Math.sin(angle);
				double z = away.x * Math.sin(angle) + away.z * Math.cos(angle);
				Vec3 rotated = new Vec3(x, 0.0, z).normalize();
				target.knockback(ARMORED_DASH_EXTRA_KNOCKBACK * DASH_KNOCKBACK_TO_STRENGTH, -rotated.x, -rotated.z);
			}
			else {
				float knockbackStrength = FAST_DASH_EXTRA_KNOCKBACK * DASH_KNOCKBACK_TO_STRENGTH;
				float vertical = -knockbackStrength * Mth.sin(FAST_DASH_KNOCKBACK_XROT * MathUtil.DEG_TO_RAD);
				applyVerticalKnockback(target, vertical);
			}
		}

		private static void applyVerticalKnockback(LivingEntity target, float strength) {
			if (target.level() instanceof ServerLevel serverLevel && serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
				TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
				if (state.shouldFreeze(target)) {
					state.queueOnTimeResume(target, () -> applyVerticalKnockback(target, strength));
					return;
				}
			}
			double kbRes = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			float finalStrength = strength * (1.0F - (float) kbRes);
			if (finalStrength != 0) {
				target.setDeltaMovement(target.getDeltaMovement().add(0.0D, finalStrength, 0.0D));
				target.hurtMarked = true;
			}
			if (target instanceof StandEntity targetStand) {
				LivingEntity user = targetStand.getUser();
				if (user != null && !user.is(target)) {
					applyVerticalKnockback(user, finalStrength);
				}
			}
		}

	}
}
