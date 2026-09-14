package rotp.core.impl.stands.theworld;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.timestop.TimeStopState;

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
