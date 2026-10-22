package rotp.core.powersystem.standpower.entity;

import java.util.function.Function;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.compat.v1_21_4.missingmethods._EntitySelector;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class StandEntityAbility extends EntityActionAbility {
	public boolean noFinisherBarDecay = false;
	protected AutoSummonMode autoSummonMode = AutoSummonMode.FULL;
	private float standUserWalkSpeed = 1.0F;

	/**
	 * @deprecated You can use the other constructor, so that you don't have to override {@link EntityActionAbility#createActionObj()}
	 */
	@Deprecated
	public StandEntityAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
	}
	
	public StandEntityAbility(AbilityType<?> abilityType, AbilityId abilityId, 
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		super(abilityType, abilityId, createActionObj);
	}
	
	
	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput, 
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		if (level.isClientSide()) return null;
		
		StandPower power = PowerClass.STAND.get(user); if (power == null) return null;
		StandEntity standEntity = power.getSummonedStandEntity();
		boolean autoSummoned = false;
		if (standEntity == null) {
			standEntity = tryAutoSummonStand(power, user);
			autoSummoned = standEntity != null;
		}
		else if (standEntity.isArmsOnlyMode()) {
			updateArmsOnlyStandForAction(power, user, standEntity);
		}
		if (standEntity == null) return null;
		ConditionCheck standCheck = checkStandEntityConditions(power, standEntity);
		if (!standCheck.isPositive()) {
			ConditionCheck.sendActionFailedMessage(this, standCheck, user);
			return null;
		}
		HeldInput heldInput = setOrBufferAction(level, user, standEntity, inputMethod, extraClientInput, clickHoldResolveTime, bufferingState);
		if (autoSummoned && !standEntity.isAddedToLevel() && power.getPowerType() instanceof EntityStandType entityStandType) {
			entityStandType.finalizeStandSummonFromAction(user, power, standEntity, bufferingState.isActionSuccess);
		}
		return heldInput;
	}
	
	private StandEntity tryAutoSummonStand(StandPower power, LivingEntity user) {
		if (!(power.getPowerType() instanceof EntityStandType entityStandType)) {
			return null;
		}
		
		switch (getAutoSummonMode(power, user)) {
		case FULL:
			entityStandType.summon(user, power, entity -> {}, false);
			break;
		case ARMS:
			entityStandType.summon(user, power, entity -> entity.setArmsOnlyMode(), false);
			break;
		case MAIN_ARM:
			entityStandType.summon(user, power, entity -> entity.setArmsOnlyMode(true, false), false);
			break;
		case OFF_ARM:
			entityStandType.summon(user, power, entity -> entity.setArmsOnlyMode(false, true), false);
			break;
		case DISABLED:
			return null;
		}
		return power.getSummonedStandEntity();
	}
	
	private void updateArmsOnlyStandForAction(StandPower power, LivingEntity user, StandEntity standEntity) {
		switch (getAutoSummonMode(power, user)) {
		case ARMS:
			standEntity.setArmsOnlyMode();
			break;
		case MAIN_ARM:
			standEntity.addToArmsOnly(InteractionHand.MAIN_HAND);
			break;
		case OFF_ARM:
			standEntity.addToArmsOnly(InteractionHand.OFF_HAND);
			break;
		case FULL:
			standEntity.fullSummonFromArms();
			if (power.getPowerType() instanceof EntityStandType entityStandType) {
				entityStandType.triggerFullSummonAdvancement(user, standEntity);
			}
			break;
		case DISABLED:
			break;
		}
	}
	
	public StandEntityAbility standAutoSummonMode(AutoSummonMode mode) {
		if (mode != null) {
			this.autoSummonMode = mode;
		}
		return this;
	}
	
	protected AutoSummonMode getAutoSummonMode(StandPower standPower, LivingEntity user) {
		return autoSummonMode;
	}

	/**
	 * The user's walk speed while the action runs, in every phase but RECOVERY (full speed there), as 1.16
	 * StandEntityAction.Builder#standUserWalkSpeed. Owner rule 2026-10-15: only the Stand guard slows its user (0.5);
	 * every other Stand action keeps full speed, so the default is 1.0 instead of the 1.16 default 0.5.
	 * Values written in an action's constructor or initActionFromConfig are replaced by the registration value; only
	 * onSetPhase or tick writes override it.
	 */
	public StandEntityAbility standUserWalkSpeed(float factor) {
		this.standUserWalkSpeed = Mth.clamp(factor, 0.0F, 1.0F);
		return this;
	}

	public float getStandUserWalkSpeed() {
		return standUserWalkSpeed;
	}

	public static boolean canPickEntityForAiming(Entity target) {
		return _EntitySelector.CAN_BE_PICKED.test(target);
	}

	public static boolean canDefaultTargetEntityForAiming(StandEntity standEntity, Entity target) {
		return target.isAlive() && canPickEntityForAiming(target)
				&& standEntity.canAttackEntity(target);
	}

	public boolean canTargetEntityForAiming(StandEntity standEntity, Entity target) {
		return canDefaultTargetEntityForAiming(standEntity, target);
	}

	/**
	 * 1.16 StandEntityAction.checkStandConditions: what the Stand must be able to do for the action, asked at the press
	 * and, as 1.16 PowerBaseImpl.tickHeldAction asked it, on every tick a key holds the action and before such a hold fires.
	 */
	protected ConditionCheck checkStandEntityConditions(StandPower standPower, StandEntity standEntity) {
		return ConditionCheck.POSITIVE;
	}

	@Override
	protected ConditionCheck checkHeldPerformerConditions(EntityActionInstance action, Power<?> context) {
		if (action.getPerformer() instanceof StandEntity standEntity && context instanceof StandPower standPower) {
			return checkStandEntityConditions(standPower, standEntity);
		}
		return ConditionCheck.POSITIVE;
	}

	
	@Override
	protected LivingEntity getPerformer(LivingEntity user) {
		return StandUtil.getSummonedStand(user);
	}

	/**
	 * 1.16 StandEntityAction: the Stand ticked its task in its own tick (StandEntity.tick, the level's entity loop) and
	 * its user's power fired the hold later in the tick (PowerBaseImpl.tickHeldAction in the player tick, a mob's in its
	 * own tick before its Stand's), which only moved the task on (StandEntityTask.moveToPhase): a charge the Stand
	 * performs runs its N ticks and its next phase's first tick comes in the Stand's next tick, Crossfire Hurricane's shot
	 * in the 21st tick from a player's press. A port of a 1.16 StandAction its user's power performed (the time stops, any
	 * that takes its user's Stand-power slot: {@link #performsInUsersStandPowerSlot}) returns true.
	 */
	@Override
	public boolean chargeFiresAsItsLengthEnds(EntityActionInstance action) {
		return performsInUsersStandPowerSlot() || !(action.getPerformer() instanceof StandEntity);
	}
	
	public boolean noAdheringToUserOffset(StandPower standPower, StandEntity standEntity) {
		return false;
	}

	public boolean noAdheringToUserOffsetClientFallback(StandEntity standEntity) {
		return false;
	}

	public boolean lockStandManualMovement(StandPower standPower, StandEntity standEntity) {
		return false;
	}

	public boolean retractsStandAfterAction(StandPower standPower, StandEntity standEntity, EntityActionInstance action) {
		return true;
	}

	/**
	 * 1.16 StandEntityAction.lockOnTargetPosition: while the action's task holds a target with a position
	 * ({@link EntityActionInstance#getTaskTarget}), every offset of the action, the default one included, turns by the
	 * yaw and pitch from the user's eyes to that target (StandEntity.taskOffset); the pitch reaches only an offset turned
	 * by the pitch ({@link StandOffsetFromUser.Rotations#HEAD_XY}). False turns them by {@link #yRotForOffset} and the
	 * user's pitch whatever the task holds, as 1.16's punches and Previous State did.
	 * @param standPower null on a client that does not have the user's power
	 */
	public boolean lockOnTargetPosition(@Nullable StandPower standPower, StandEntity standEntity, EntityActionInstance action) {
		return true;
	}

	/**
	 * 1.16 StandEntityAction.yRotForOffset: the yaw that turns the action's offsets while they are not locked on its
	 * task's target, the user's view yaw. An offset given {@link StandOffsetFromUser.Rotations#BODY} takes the body yaw.
	 */
	public float yRotForOffset(LivingEntity user, EntityActionInstance action) {
		return user.getYRot();
	}

	/**
	 * 1.16 StandEntityAction.rotateStandTowardsTarget, which StandEntityTask.rotateStand called on every tick the action's
	 * task held a target and the Stand was not manually controlled, following its user or not
	 * ({@link StandEntity#lookAtTaskTarget}): the Stand faces the target at once (MCUtil.rotateTowards by 360 degrees), at
	 * the point the action looks at ({@link EntityActionInstance#getStandLookTargetPosition}, as Gold Experience's and
	 * Crazy Diamond's heavy punches give) or else at ActionTarget.getTargetPos(true): the eyes of any entity, a boat, a
	 * minecart or primed TNT too, a block's centre. The Stand's head keeps the yaw this leaves it (yHeadRot)
	 * for the tick, so an override that turns only the Stand's yaw and body afterwards leaves the head on the target.
	 */
	public void rotateStandTowardsTarget(StandEntity standEntity, EntityActionInstance action, ActionTarget target) {
		Vec3 actionPos = action.getStandLookTargetPosition(standEntity, target);
		standEntity.lookAtPos(actionPos != null ? actionPos : StandOffsetFromUser.taskTargetPos(target), true);
	}

	/**
	 * The kinds of target the action's task keeps, 1.16's getTargetRequirement, standKeepsTarget and checkTarget: none,
	 * as for a 1.16 action with neither. See {@link #keepsTaskTarget}.
	 */
	protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
		return false;
	}

	/**
	 * Whether the action's task keeps targets of this one's kind ({@link #keepsTaskTargetType}), whether or not it keeps
	 * this one ({@link #keepsTaskTarget}). Server side.
	 */
	public final boolean keepsTaskTargetKind(StandEntity standEntity, @Nullable ActionTarget target) {
		Level level = standEntity.level();
		ActionTarget resolved = target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
		return !resolved.isEmpty(level) && keepsTaskTargetType(standEntity, resolved);
	}

	/**
	 * 1.16 StandEntityAction.checkStandTarget and canStandTargetEntity: the entities the action's task keeps are the living
	 * ones the Stand may attack. See {@link #keepsTaskTarget}.
	 */
	protected boolean keepsTaskTargetEntity(StandEntity standEntity, Entity entity) {
		return entity instanceof LivingEntity living && standEntity.canAttack(living);
	}

	/**
	 * 1.16 StandEntityAction.checkStandTarget as PowerBaseImpl.checkTarget asked it of the target an action was pressed
	 * at: an entity is the action's target only if its task keeps that entity ({@link #keepsTaskTargetEntity}: a living one
	 * the Stand may attack, any one an action with friendly fire takes); a refused one left the action without a target, so
	 * its Stand did not turn to it. Other targets pass. Asked where a Stand action takes the synced aim at its press
	 * (EntityActionInstance#captureActionTargetFromAim, #keepStandAimedAtTarget).
	 */
	public boolean takesPressedTarget(StandEntity standEntity, ActionTarget target) {
		if (target.getType() != ActionTarget.TargetType.ENTITY) {
			return true;
		}
		Entity entity = target.getEntity();
		return entity != null && keepsTaskTargetEntity(standEntity, entity);
	}

	/**
	 * 1.16 StandEntityTask.keepTarget, which the server asked as the task started (makeServerSideTask, setTask taking the
	 * previous task's target) and on every tick of it: no target is kept from nothing; an entity is kept until it is
	 * removed or has lain dead for 20 ticks, never the Stand itself, only one the action keeps
	 * ({@link #keepsTaskTargetEntity}); the action keeps that kind of target ({@link #keepsTaskTargetType}); and it is
	 * within the action's range of the Stand (Action.checkRangeAndTarget: 8 blocks to an entity, 4 without a line of
	 * sight, 10 to a block that is not air). Server side.
	 */
	public boolean keepsTaskTarget(StandEntity standEntity, @Nullable ActionTarget target) {
		Level level = standEntity.level();
		ActionTarget kept = target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
		if (kept.isEmpty(level) || !keepsTaskTargetType(standEntity, kept)) {
			return false;
		}
		if (kept.getType() == ActionTarget.TargetType.ENTITY) {
			Entity entity = kept.getEntity();
			if (entity == standEntity || entity instanceof LivingEntity living && living.deathTime >= 20
					|| !keepsTaskTargetEntity(standEntity, entity)) {
				return false;
			}
		}
		return ActionTargetRange.isTargetWithinRange(standEntity, kept, level);
	}

	public enum AutoSummonMode {
		FULL,
		ARMS,
		MAIN_ARM,
		OFF_ARM,
		DISABLED
	}

}
