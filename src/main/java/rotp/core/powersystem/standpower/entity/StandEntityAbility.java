package rotp.core.powersystem.standpower.entity;

import java.util.function.Function;

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
import rotp.core.compat.v1_21_4.missingmethods._EntitySelector;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class StandEntityAbility extends EntityActionAbility {
	public boolean noFinisherBarDecay = false;
	protected AutoSummonMode autoSummonMode = AutoSummonMode.FULL;

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

	protected ConditionCheck checkStandEntityConditions(StandPower standPower, StandEntity standEntity) {
		return ConditionCheck.POSITIVE;
	}
	
	
	@Override
	protected LivingEntity getPerformer(LivingEntity user) {
		return StandUtil.getSummonedStand(user);
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
	
	public enum AutoSummonMode {
		FULL,
		ARMS,
		MAIN_ARM,
		OFF_ARM,
		DISABLED
	}

}
