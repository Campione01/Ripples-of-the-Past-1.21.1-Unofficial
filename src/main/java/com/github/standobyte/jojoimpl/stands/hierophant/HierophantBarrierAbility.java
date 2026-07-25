package com.github.standobyte.jojoimpl.stands.hierophant;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.NoPoseStandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTargetAim;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class HierophantBarrierAbility extends NoPoseStandEntityAbility {
	private static final double BLOCK_TARGET_RANGE = 10.0D;

	public HierophantBarrierAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, BarrierDrop::new);
		partsRequired(StandPart.MAIN_BODY);
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (action instanceof BarrierDrop barrierDrop) {
			ActionTarget target = getCurrentBlockTarget(powerUser, performer, level);
			barrierDrop.setActionTarget(target);
			if (!target.isEmpty(level)) {
				action.standRotationTarget = target.copy();
				action.aimAs = AimingEntity.STAND;
			}
		}
	}

	private static ActionTarget getCurrentBlockTarget(LivingEntity powerUser, LivingEntity performer, Level level) {
		ActionTarget target = getAimTarget(powerUser, level);
		if (isValidBlockTarget(powerUser, target, level)) {
			return target;
		}
		target = getAimTarget(performer, level);
		if (isValidBlockTarget(performer, target, level)) {
			return target;
		}
		target = raytraceBlockTarget(performer, level);
		if (isValidBlockTarget(performer, target, level)) {
			return target;
		}
		target = raytraceBlockTarget(powerUser, level);
		return isValidBlockTarget(powerUser, target, level) ? target : ActionTarget.EMPTY;
	}

	private static ActionTarget getAimTarget(LivingEntity entity, Level level) {
		if (entity == null) {
			return ActionTarget.EMPTY;
		}
		ActionTargetAim aim = LivingComponentAction.getAim(entity);
		return aim != null ? aim.getTarget().resolveEntityId(level) : ActionTarget.EMPTY;
	}

	private static ActionTarget raytraceBlockTarget(LivingEntity entity, Level level) {
		if (entity == null) {
			return ActionTarget.EMPTY;
		}
		ActionTarget target = HitResultUtil.clip(entity.getEyePosition(), entity.getLookAngle(),
				BLOCK_TARGET_RANGE, BLOCK_TARGET_RANGE, level, HierophantBarrierAbility::ignoreEntityTarget, entity, 0);
		return target.getType() == TargetType.BLOCK ? target : ActionTarget.EMPTY;
	}

	private static boolean isValidBlockTarget(LivingEntity entity, ActionTarget target, Level level) {
		if (entity == null || target.getType() != TargetType.BLOCK || target.isEmpty(level)) {
			return false;
		}
		return new AABB(target.getBlockPos()).distanceToSqr(entity.getEyePosition()) <= BLOCK_TARGET_RANGE * BLOCK_TARGET_RANGE;
	}

	private static boolean ignoreEntityTarget(Entity entity) {
		return false;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		if (context instanceof StandPower standPower) {
			LivingEntity user = standPower.getUser();
			Level level = user != null ? user.level() : null;
			LivingEntity performer = standPower.getSummonedStandEntity();
			if (level != null) {
				if (hasOutOfRangeBlockTarget(user, level) || hasOutOfRangeBlockTarget(performer, level)) {
					return ConditionCheck.createNegative("target_too_far");
				}
				ActionTarget target = getCurrentBlockTarget(user, performer, level);
				if (target.getType() != TargetType.BLOCK || target.isEmpty(level)) {
					return ConditionCheck.createNegative("block_target");
				}
			}
			if (performer instanceof HierophantGreenEntity hierophant && !hierophant.canPlaceBarrier()) {
				return ConditionCheck.createNegative("barrier");
			}
		}
		return ConditionCheck.POSITIVE;
	}

	private static boolean hasOutOfRangeBlockTarget(LivingEntity entity, Level level) {
		ActionTarget target = getAimTarget(entity, level);
		return target.getType() == TargetType.BLOCK
				&& !target.isEmpty(level)
				&& !isValidBlockTarget(entity, target, level);
	}

	@Override
	protected ConditionCheck checkStandEntityConditions(StandPower standPower, StandEntity standEntity) {
		ConditionCheck check = super.checkStandEntityConditions(standPower, standEntity);
		if (!check.isPositive()) {
			return check;
		}
		if (standEntity instanceof HierophantGreenEntity hierophant && !hierophant.canPlaceBarrier()) {
			return ConditionCheck.createNegative("barrier");
		}
		return ConditionCheck.POSITIVE;
	}

	@Override
	public Component getName(Power<?> context) {
		int barriers = 0;
		int maxBarriers = 15;
		if (context instanceof StandPower standPower) {
			maxBarriers = HierophantGreenEntity.getMaxBarriersPlaceable(standPower);
			if (standPower.getSummonedStandEntity() instanceof HierophantGreenEntity hierophant) {
				barriers = hierophant.getPlacedBarriersCount();
			}
		}
		return abilityName(context, "", barriers, maxBarriers);
	}

	public static class BarrierDrop extends EntityActionInstance {
		private ActionTarget actionTarget = ActionTarget.EMPTY;

		public BarrierDrop(EntityActionType ability) {
			super(ability);
		}

		private void setActionTarget(ActionTarget target) {
			this.actionTarget = target != null ? target.copy() : ActionTarget.EMPTY;
		}

		private ActionTarget getActionTarget(Level level) {
			return actionTarget.resolveEntityId(level);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity performer = getPerformer();
			if (!(performer instanceof HierophantGreenEntity hierophant)) {
				return;
			}
			ActionTarget target = getActionTarget(level);
			LivingEntity user = getPowerUser();
			if (target.getType() != TargetType.BLOCK || target.isEmpty(level)) {
				if (user != null) {
					ConditionCheck.sendActionFailedMessage(null, ConditionCheck.createNegative("block_target"), user);
				}
				startRecovery();
				return;
			}
			if (!hierophant.canPlaceBarrier()) {
				if (user != null) {
					ConditionCheck.sendActionFailedMessage(null, ConditionCheck.createNegative("barrier"), user);
				}
				startRecovery();
				return;
			}
			standRotationTarget = target;
			hierophant.attachBarrier(target.getBlockPos());
		}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(buf, actionTarget);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			actionTarget = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(buf);
		}
	}
}
