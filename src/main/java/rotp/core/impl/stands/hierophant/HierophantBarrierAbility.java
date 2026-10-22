package rotp.core.impl.stands.hierophant;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.AttributeUtil;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

public class HierophantBarrierAbility extends NoPoseStandEntityAbility {
	public HierophantBarrierAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, BarrierDrop::new);
		partsRequired(StandPart.MAIN_BODY);
	}

	// 1.16 HierophantGreenBarrier.getTargetRequirement: BLOCK
	@Override
	protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
		return target.getType() == TargetType.BLOCK;
	}

	// TargetRequirement.BLOCK: the torch or grass pressed is the block it takes (overrideVanillaMouseTarget left it)
	@Override
	public boolean pressedBlockGivesWayToEntity() {
		return false;
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

	// 1.16: the crosshair block, valid within 10 blocks of the performer (the summoned Stand)
	public static ActionTarget getCurrentBlockTarget(LivingEntity powerUser, LivingEntity performer, Level level) {
		LivingEntity camera = cameraEntity(powerUser, performer);
		if (isPressedAtAnEntity(camera, level)) {
			return ActionTarget.EMPTY;
		}
		LivingEntity rangeFrom = rangeOrigin(powerUser, performer);
		ActionTarget target = getAimTarget(powerUser, level);
		if (isValidBlockTarget(rangeFrom, target, level)) {
			return target;
		}
		// the user's vanilla pick met no block: 1.16 pressed at the Stand's aim, which is a mob when the precision ray
		// found one beside the crosshair
		if (camera != performer && target.getType() != TargetType.BLOCK && isPressedAtAnEntity(performer, level)) {
			return ActionTarget.EMPTY;
		}
		target = getAimTarget(performer, level);
		if (isValidBlockTarget(rangeFrom, target, level)) {
			return target;
		}
		// server-side crosshair: the camera entity's look, capped at the user's reach; the vanilla pick's block, by its outline
		target = raytraceBlockTarget(cameraEntity(powerUser, performer), userReach(powerUser), level);
		return isValidBlockTarget(rangeFrom, target, level) ? target : ActionTarget.EMPTY;
	}

	// 1.16 PowerBaseImpl.checkTarget 295-301: the barrier's TargetRequirement.BLOCK fails for a press at an entity, also
	// one the server no longer has; the target is emptied and the press refused, no block behind the entity is taken
	private static boolean isPressedAtAnEntity(LivingEntity camera, Level level) {
		ActionTargetAim aim = camera != null ? LivingComponentAction.getAim(camera) : null;
		if (aim == null) {
			return false;
		}
		return !level.isClientSide() && aim.isClientEntityMissing() || aim.getTarget().getType() == TargetType.ENTITY;
	}

	private static LivingEntity rangeOrigin(LivingEntity powerUser, LivingEntity performer) {
		return performer != null ? performer : powerUser;
	}

	// the Stand is the camera only while manually controlled
	private static LivingEntity cameraEntity(LivingEntity powerUser, LivingEntity performer) {
		return performer instanceof StandEntity stand && stand.isManuallyControlled() ? performer : powerUser;
	}

	private static double userReach(LivingEntity powerUser) {
		return AttributeUtil.getValueOrDefault(powerUser, Attributes.BLOCK_INTERACTION_RANGE);
	}

	private static ActionTarget getAimTarget(LivingEntity entity, Level level) {
		if (entity == null) {
			return ActionTarget.EMPTY;
		}
		ActionTargetAim aim = LivingComponentAction.getAim(entity);
		return aim != null ? aim.getTarget().resolveEntityId(level) : ActionTarget.EMPTY;
	}

	private static ActionTarget raytraceBlockTarget(LivingEntity entity, double range, Level level) {
		if (entity == null) {
			return ActionTarget.EMPTY;
		}
		ActionTarget target = HitResultUtil.clip(entity.getEyePosition(), entity.getLookAngle(),
				range, range, level, HierophantBarrierAbility::ignoreEntityTarget, entity, 0, ClipContext.Block.OUTLINE);
		return target.getType() == TargetType.BLOCK ? target : ActionTarget.EMPTY;
	}

	// 1.16 Action.checkRangeAndTarget: the distance to the block's shape, as JojoModUtil.getDistance measures it
	private static boolean isValidBlockTarget(LivingEntity entity, ActionTarget target, Level level) {
		if (entity == null || target.getType() != TargetType.BLOCK || target.isEmpty(level)) {
			return false;
		}
		return ActionTargetRange.isBlockWithinRange(entity, level, target.getBlockPos(),
				ActionTargetRange.DEFAULT_BLOCK_RANGE_SQ);
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
				LivingEntity rangeFrom = rangeOrigin(user, performer);
				if (hasOutOfRangeBlockTarget(user, rangeFrom, level) || hasOutOfRangeBlockTarget(performer, rangeFrom, level)) {
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

	private static boolean hasOutOfRangeBlockTarget(LivingEntity aiming, LivingEntity rangeFrom, Level level) {
		ActionTarget target = getAimTarget(aiming, level);
		return target.getType() == TargetType.BLOCK
				&& !target.isEmpty(level)
				&& !isValidBlockTarget(rangeFrom, target, level);
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
			// 1.16 standPerform worked on task.getTarget(), and only the task's target turned the Stand: not a block the
			// task dropped since
			ActionTarget held = getTaskTarget(level);
			if (held.getType() != TargetType.EMPTY) {
				standRotationTarget = held;
			}
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
