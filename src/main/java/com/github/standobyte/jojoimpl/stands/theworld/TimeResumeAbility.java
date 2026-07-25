package com.github.standobyte.jojoimpl.stands.theworld;

import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.powersystem.entityaction.HeldInput;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class TimeResumeAbility extends Ability {

	public TimeResumeAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		isSubAbility = true;
		spriteName = "time_stop";
	}

	@Override
	public boolean isAbilityUnlocked(Power<?> context) {
		return true;
	}

	@Override
	public boolean canBeUsedInStoppedTime(Power<?> context) {
		return true;
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		if (!isAbilityUnlocked(context)) {
			return false;
		}
		StandPower standPower = PowerClass.STAND.cast(context);
		LivingEntity user = standPower != null ? standPower.getUser() : null;
		if (user != null && TimeStopBlinkAbility.isTimeStopped(user.level(), user)) {
			return hasOwnTimeStop(user.level(), user);
		}
		return false;
	}

	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		StandPower standPower = PowerClass.STAND.cast(context);
		LivingEntity user = standPower != null ? standPower.getUser() : null;
		if (user != null && hasOwnTimeStop(user.level(), user)) {
			return this;
		}
		return this;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		LivingEntity user = standPower != null ? standPower.getUser() : null;
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		return hasOwnTimeStop(user.level(), user) ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		bufferingState.isActionSuccess = requestManualResume(level, user);
		return null;
	}

	private static boolean requestManualResume(Level level, LivingEntity user) {
		if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.requestManualResume(user.getId());
		}
		return false;
	}

	private static boolean hasOwnTimeStop(Level level, LivingEntity user) {
		if (level.isClientSide()) {
			return TimeStopState.getClientInstance(user.getId()).filter(TimeStopState.Instance::isActive).isPresent();
		}
		if (level instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.getInstance(user.getId()).filter(TimeStopState.Instance::isActive).isPresent();
		}
		return false;
	}
}
