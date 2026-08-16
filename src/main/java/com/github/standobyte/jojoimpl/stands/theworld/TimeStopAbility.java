package com.github.standobyte.jojoimpl.stands.theworld;

import java.util.Locale;
import java.util.Optional;

import com.github.standobyte.jojo.api.timestop.TimeStopAudioContext;
import com.github.standobyte.jojo.api.timestop.TimeStopAudioCue;
import com.github.standobyte.jojo.api.timestop.TimeStopAudioDecision;
import com.github.standobyte.jojo.api.timestop.TimeStopBehaviorPolicies;
import com.github.standobyte.jojo.api.timestop.TimeStopLifecycleEvent;
import com.github.standobyte.jojo.api.timestop.TimeStopStartupCostDecision;
import com.github.standobyte.jojo.client.ui.hud_power.WindupIndicator;
import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.event.ModEventHooks;
import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.EntityActionAbility;
import com.github.standobyte.jojo.powersystem.ability.TrainableAbility;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.HeldInput;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.SyncType;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopCooldowns;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopLearning;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class TimeStopAbility extends StandEntityAbility implements TrainableAbility {
	private static final ActionAnimIdentifier TIME_STOP_ANIM = ActionAnimIdentifier.getOrCreate("time_stop", false);
	private static final int STAR_PLATINUM_HOLD_TO_FIRE_TICKS = 40;
	private static final int THE_WORLD_HOLD_TO_FIRE_TICKS = 30;
	private static final int THE_WORLD_RESOLVE_HOLD_TO_FIRE_TICKS = 20;
	private static final int TIME_STOP_SOUND_MIN_TICKS = 40;
	private static final int TIME_STOP_OPENING_SETTLE_TICKS = 35;
	private static final int STAR_PLATINUM_RESOLVE_LEVEL_TO_UNLOCK = 4;
	private static final int THE_WORLD_RESOLVE_LEVEL_TO_UNLOCK = 2;

	public TimeStopAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, TimeStopAction::new);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, THE_WORLD_HOLD_TO_FIRE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		partsRequired(StandPart.MAIN_BODY);
	}

	@Override
	public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
		return false;
	}

	@Override
	public boolean canBeUsedInStoppedTime(Power<?> context) {
		return true;
	}

	@Override
	public boolean canUserSeeInStoppedTime(LivingEntity user, Power<?> context) {
		return true;
	}

	@Override
	public String getLearningAbilityName() {
		return TimeStopLearning.TIME_STOP;
	}

	@Override
	public float getMaxTrainingPoints(StandPower power) {
		return TimeStopLearning.getMaxTrainingPoints(power);
	}

	@Override
	protected int getRequiredResolveLevelToUnlock(Power<?> context) {
		StandPower power = PowerClass.STAND.cast(context);
		if (power == null) {
			return super.getRequiredResolveLevelToUnlock(context);
		}
		if (power.getPowerType() == ModStands.STAR_PLATINUM.get()) {
			return STAR_PLATINUM_RESOLVE_LEVEL_TO_UNLOCK;
		}
		if (power.getPowerType() == ModStands.THE_WORLD.get()) {
			return THE_WORLD_RESOLVE_LEVEL_TO_UNLOCK;
		}
		return super.getRequiredResolveLevelToUnlock(context);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		StandPower power = PowerClass.STAND.cast(context);
		LivingEntity user = power != null ? power.getUser() : null;
		if (power == null || user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (hasOwnTimeStop(user)) {
			return ConditionCheck.POSITIVE;
		}
		if (!isCurrentlyInStoppedTime(user) && hasUncancellableStandAction(power)) {
			return ConditionCheck.NEGATIVE;
		}
		return hasEnoughTimeStopStamina(power)
				? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative("no_stamina");
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		if (hasOwnTimeStop(user)) {
			bufferingState.isActionSuccess = requestManualResume(level, user);
			return null;
		}
		StandPower power = !level.isClientSide() ? PowerClass.STAND.get(user) : null;
		boolean standAlreadySummoned = power != null && power.getSummonedStandEntity() != null;
		if (power != null && !isCurrentlyInStoppedTime(user)) {
			clearCurrentStandAction(power);
		}

		HeldInput heldInput = power != null
				&& power.getSummonedStandEntity() == null
				&& canStartWithoutStand(power, user)
				? setOrBufferAction(level, user, user, inputMethod, extraClientInput, clickHoldResolveTime, bufferingState)
				: super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
		if (heldInput instanceof TimeStopAction timeStopAction) {
			timeStopAction.setStandAlreadySummoned(standAlreadySummoned);
			if (bufferingState.isActionSuccess && level instanceof ServerLevel serverLevel
					&& power != null && !isCurrentlyInStoppedTime(user)) {
				playTimeStopVoiceLine(serverLevel, user, power, standAlreadySummoned);
			}
		}
		return heldInput;
	}

	private void clearCurrentStandAction(StandPower power) {
		StandEntity standEntity = power.getSummonedStandEntity();
		if (standEntity != null) {
			LivingComponentAction actionComponent = LivingComponentAction.getExistingComponent(standEntity);
			EntityActionInstance curAction = actionComponent != null ? actionComponent.getAction() : null;
			if (curAction != null && canStopCurrentStandActionLikeOriginal(curAction)) {
				actionComponent.setAction(null, SyncType.TRACKING_AND_SELF);
			}
		}
	}

	private boolean hasUncancellableStandAction(StandPower power) {
		StandEntity standEntity = power.getSummonedStandEntity();
		if (standEntity == null) {
			return false;
		}
		LivingComponentAction actionComponent = LivingComponentAction.getExistingComponent(standEntity);
		EntityActionInstance curAction = actionComponent != null ? actionComponent.getAction() : null;
		return curAction != null && !canStopCurrentStandActionLikeOriginal(curAction);
	}

	private boolean canStopCurrentStandActionLikeOriginal(EntityActionInstance curAction) {
		if (curAction.isOver() || curAction.canBeCancelledInto(this)) {
			return true;
		}
		if (curAction.ability instanceof EntityActionAbility entityActionAbility) {
			return entityActionAbility.canBeStoppedByOriginalHoldCancel(curAction);
		}
		return false;
	}

	@Override
	protected AutoSummonMode getAutoSummonMode(StandPower standPower, LivingEntity user) {
		if (standPower.getPowerType() == ModStands.THE_WORLD.get()
				&& ResolveModeEffect.getEffectiveResolveLevel(user, standPower) >= 3) {
			return AutoSummonMode.DISABLED;
		}
		return super.getAutoSummonMode(standPower, user);
	}

	private boolean canStartWithoutStand(StandPower power, LivingEntity user) {
		return power != null
				&& power.getPowerType() == ModStands.THE_WORLD.get()
				&& ResolveModeEffect.getEffectiveResolveLevel(user, power) >= 3;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		action.phasesLength.put(ActionPhase.BUTTON_CHARGE, getHoldToFireTicks(powerUser));
		action.phasesLength.put(ActionPhase.PERFORM, 1);
		action.phasesLength.put(ActionPhase.RECOVERY, 0);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return TIME_STOP_ANIM;
	}

	@Override
	public WindupIndicator cl_windupIndicator(LivingEntity clientPlayer, WindupIndicator indicator, float partialTick) {
		if (isCurrentlyInStoppedTime(clientPlayer)) {
			return null;
		}

		indicator.maxValue = buttonChargePhase.getAsFloat() > 0 ? 1 : 0;
		indicator.value = -1;
		if (indicator.maxValue <= 0) {
			return super.cl_windupIndicator(clientPlayer, indicator, partialTick);
		}

		EntityActionInstance curAction = getClientTimeStopAction(clientPlayer);
		if (curAction != null) {
			if (curAction.getPhase() == ActionPhase.BUTTON_CHARGE) {
				indicator.maxValue = curAction.getAnimPhaseLength();
				if (indicator.maxValue > 0) {
					indicator.value = curAction.getAnimPhaseTick(partialTick);
				}
			}
			else {
				indicator.maxValue = curAction.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
				indicator.value = indicator.maxValue;
			}
		}
		return indicator;
	}

	private EntityActionInstance getClientTimeStopAction(LivingEntity clientPlayer) {
		EntityActionInstance userAction = LivingComponentAction.getCurEntityAction(clientPlayer);
		if (isSameAbilityAction(userAction)) {
			return userAction;
		}
		StandEntity stand = StandUtil.getSummonedStand(clientPlayer);
		EntityActionInstance standAction = stand != null ? LivingComponentAction.getCurEntityAction(stand) : null;
		return isSameAbilityAction(standAction) ? standAction : null;
	}

	@Override
	public Component getName(Power<?> context) {
		StandPower power = PowerClass.STAND.cast(context);
		float seconds = (float) TimeStopLearning.getSavedTimeStopTicks(power) / 20F;
		String secondsString = String.format(Locale.ROOT, "%.2f", seconds);
		boolean creativeTemplate = TimeStopLearning.isCreativeTimeStopTemplate(power);
		if (power != null && power.getPowerType() == ModStands.STAR_PLATINUM.get()) {
			return Component.translatable(creativeTemplate
					? "jojo_ripples.ability.star_platinum_time_stop.creative"
					: "jojo_ripples.ability.star_platinum_time_stop", secondsString);
		}
		if (power != null && power.getPowerType() == ModStands.THE_WORLD.get()) {
			return Component.translatable(creativeTemplate
					? "jojo_ripples.ability.the_world_time_stop.creative"
					: "jojo_ripples.ability.the_world_time_stop", secondsString);
		}
		return Component.translatable(creativeTemplate
				? "jojo_ripples.ability.time_stop.creative"
				: "jojo_ripples.ability.time_stop", secondsString);
	}

	private float getHoldToFireTicks(LivingEntity user) {
		if (isCurrentlyInStoppedTime(user)) {
			return 0;
		}
		StandPower power = PowerClass.STAND.get(user);
		if (power != null && power.getPowerType() == ModStands.STAR_PLATINUM.get()) {
			return STAR_PLATINUM_HOLD_TO_FIRE_TICKS;
		}
		if (power != null && power.getPowerType() == ModStands.THE_WORLD.get()
				&& ResolveModeEffect.getEffectiveResolveLevel(user, power) >= 4) {
			return THE_WORLD_RESOLVE_HOLD_TO_FIRE_TICKS;
		}
		return THE_WORLD_HOLD_TO_FIRE_TICKS;
	}

	private boolean isCurrentlyInStoppedTime(LivingEntity user) {
		if (user.level().isClientSide()) {
			return TimeStopState.getClientDisplayInstance(new ChunkPos(user.blockPosition())).isPresent();
		}
		if (user.level() instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.isTimeStopped(new ChunkPos(user.blockPosition()));
		}
		return false;
	}

	private boolean hasOwnTimeStop(LivingEntity user) {
		if (user.level().isClientSide()) {
			return TimeStopState.getClientInstance(user.getId()).filter(TimeStopState.Instance::isActive).isPresent();
		}
		if (user.level() instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.getInstance(user.getId()).filter(TimeStopState.Instance::isActive).isPresent();
		}
		return false;
	}

	private static boolean requestManualResume(Level level, LivingEntity user) {
		if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
			TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
			return state.requestManualResume(user.getId());
		}
		return false;
	}

	private boolean hasEnoughTimeStopStamina(StandPower power) {
		float defaultCost = TimeStopLearning.getTimeStopStaminaCost(
				power, TimeStopLearning.MIN_RELEASE_TIME_STOP_TICKS);
		TimeStopStartupCostDecision decision =
				TimeStopBehaviorPolicies.resolveStartupCost(
						power, abilityId, null, defaultCost);
		return !decision.isDenied()
				&& (power.isUserCreative()
						|| power.getStamina()
								>= effectiveTimeStopStaminaCost(
										power,
										decision.resolve(defaultCost)));
	}

	private static float effectiveTimeStopStaminaCost(StandPower power, float amount) {
		return amount * PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power);
	}

	private void playTimeStopVoiceLine(ServerLevel serverLevel, LivingEntity user, StandPower power, boolean standAlreadySummoned) {
		TimeStopAudioDecision decision =
				TimeStopBehaviorPolicies.resolveAudio(
						power.getPowerType().getId(),
						new TimeStopAudioContext(
								TimeStopAudioCue.START_VOICE,
								user,
								power,
								abilityId,
								null,
								standAlreadySummoned));
		Holder<SoundEvent> voiceLine = switch (decision.kind()) {
			case PASS -> getTimeStopVoiceLine(
					user, power, standAlreadySummoned);
			case SOUND -> decision.sound();
			case SILENT -> null;
		};
		if (voiceLine != null) {
			JojoModUtil.sayVoiceLine(user, voiceLine);
		}
	}

	private Holder<SoundEvent> getTimeStopVoiceLine(LivingEntity user, StandPower power, boolean standAlreadySummoned) {
		if (power.getPowerType() == ModStands.STAR_PLATINUM.get()) {
			return ModSoundEvents.JOTARO_STAR_PLATINUM_THE_WORLD;
		}
		if (power.getPowerType() == ModStands.THE_WORLD.get()) {
			if (user.getRandom().nextFloat() < 0.05F && isJonathanHamonUser(user)) {
				return ModSoundEvents.JONATHAN_THE_WORLD;
			}
			return standAlreadySummoned ? ModSoundEvents.DIO_TIME_STOP : ModSoundEvents.DIO_THE_WORLD;
		}
		return null;
	}

	private boolean isJonathanHamonUser(LivingEntity user) {
		return PlayerPower.getPowerData(user, ModPlayerPowers.HAMON)
				.map(hamon -> hamon.characterIs(ModHamonSkills.CHARACTER_JONATHAN.get()))
				.orElse(false);
	}

	private static Holder<SoundEvent> getTimeStopSound(StandPower power) {
		return power != null && power.getPowerType() == ModStands.STAR_PLATINUM.get()
				? ModSoundEvents.STAR_PLATINUM_TIME_STOP
				: ModSoundEvents.THE_WORLD_TIME_STOP;
	}

	private boolean startTimeStopAfterHold(LivingEntity user, boolean standAlreadySummoned) {
		StandPower power = PowerClass.STAND.get(user);
		return startTimeStopAfterHold(user, standAlreadySummoned, TimeStopLearning.getTimeStopTicks(power));
	}

	private boolean startTimeStopAfterHold(LivingEntity user, boolean standAlreadySummoned, int requestedTimeStopTicks) {
		Level level = user.level();
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		StandPower power = PowerClass.STAND.get(user);
		if (power == null || power.getSummonedStandEntity() == null && !canStartWithoutStand(power, user)) {
			return false;
		}
		TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
		int instanceId = user.getId();
		if (state.getInstance(instanceId).filter(TimeStopState.Instance::isActive).isPresent()) {
			return false;
		}
		ChunkPos centerPos = new ChunkPos(user.blockPosition());
		int timeStopTicks = Mth.clamp(requestedTimeStopTicks, TimeStopLearning.MIN_RELEASE_TIME_STOP_TICKS,
				TimeStopLearning.getTimeStopTicks(power));
		String visualRoute = power.getPowerType() == ModStands.STAR_PLATINUM.get()
				? "star_platinum_time_stop"
				: "the_world_time_stop";
		TimeStopState.Instance instance = new TimeStopState.Instance(
				instanceId,
				timeStopTicks,
				timeStopTicks,
				centerPos,
				JojoModConfig.getCommonConfigInstance(level.isClientSide()).timeStopChunkRange.get(),
				user.getId(),
				visualRoute,
				instanceId,
				instanceId,
				TimeStopLearning.getTimeStopStaminaCostTick(power))
				.withStandSkin(power)
				.withStartupDelay(TIME_STOP_OPENING_SETTLE_TICKS);
		TimeStopLifecycleEvent.PreStart startEvent =
				ModEventHooks.onTimeStopPreStart(serverLevel, instance);
		if (startEvent.isCanceled()) {
			return false;
		}
		instance = startEvent.getInstance();
		centerPos = instance.centerPos();
		boolean invadingStoppedTime = state.isTimeStopped(centerPos);
		if (invadingStoppedTime) {
			Optional<TimeStopState.Instance> currentMaxInstance =
					state.getLongestInstanceIn(centerPos);
			if (currentMaxInstance.map(TimeStopState.Instance::ticksLeft)
					.orElse(0) > instance.ticksLeft()) {
				instance = instance.withResumeSoundAndVoiceLineUserIds(
						currentMaxInstance.get().resumeSoundUserId(), -1);
				startEvent.setInstance(instance);
			}
		}
		float defaultStaminaCost =
				TimeStopLearning.getTimeStopStaminaCost(
						power, instance.totalTicks());
		TimeStopStartupCostDecision startupCost =
				TimeStopBehaviorPolicies.resolveStartupCost(
						power,
						abilityId,
						instance,
						defaultStaminaCost);
		if (startupCost.isDenied()) {
			return false;
		}
		float timeStopStaminaCost = effectiveTimeStopStaminaCost(
				power, startupCost.resolve(defaultStaminaCost));
		if (!power.consumeStamina(timeStopStaminaCost, false)) {
			ConditionCheck.sendActionFailedMessage(
					this, ConditionCheck.createNegative("no_stamina"), user);
			return false;
		}
		int startupDelay = Math.max(-instance.ticksPassed(), 0);
		int statusDuration = (int) Math.min(
				(long) instance.ticksLeft() + startupDelay,
				Integer.MAX_VALUE);
		user.addEffect(new MobEffectInstance(
				ModStatusEffects.TIME_STOP,
				statusDuration,
				0,
				false,
				false,
				true));
		if (!state.commitPreStart(startEvent)) {
			user.removeEffect(ModStatusEffects.TIME_STOP);
			if (!power.isStaminaInfinite()) {
				power.setStamina(power.getStamina() + timeStopStaminaCost);
			}
			return false;
		}
		if (state.getInstance(instance.id()).orElse(null) != instance) {
			return false;
		}
		TimeStopLearning.markUsedTimeStopToday(power);
		if (!invadingStoppedTime) {
			if (instance.totalTicks() >= TIME_STOP_SOUND_MIN_TICKS) {
				TimeStopAudioDecision soundDecision =
						TimeStopBehaviorPolicies.resolveAudio(
								instance.standTypeId().orElse(null),
								new TimeStopAudioContext(
										TimeStopAudioCue.START_SOUND,
										user,
										power,
										abilityId,
										instance,
										standAlreadySummoned));
				Holder<SoundEvent> startSound =
						switch (soundDecision.kind()) {
							case PASS -> getTimeStopSound(power);
							case SOUND -> soundDecision.sound();
							case SILENT -> null;
						};
				TimeStopState.Instance soundInstance = instance;
				if (startSound != null) {
					StandUtil.broadcastSoundWithCondition(
							serverLevel,
							user.position(),
							startSound,
							false,
							power,
							SoundSource.AMBIENT,
							5.0F,
							1.0F,
							player -> soundInstance.covers(
											new ChunkPos(
													player.blockPosition()))
									&& TimeStopState
											.canPlayerSeeInStoppedTime(player));
				}
			}
		}
		return true;
	}

	public static class TimeStopAction extends EntityActionInstance {
		private boolean standAlreadySummoned;

		public TimeStopAction(EntityActionType ability) {
			super(ability);
		}

		void setStandAlreadySummoned(boolean standAlreadySummoned) {
			this.standAlreadySummoned = standAlreadySummoned;
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			super.onActionSet(prevAction);
			if (performer instanceof StandEntity stand && !stand.level().isClientSide()) {
				stand.summonLockTicks = 0;
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = 1.0F;
		}

		@Override
		public void actionPerformStart() {
			if (level().isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (!(ability instanceof TimeStopAbility timeStop) || user == null
					|| !timeStop.startTimeStopAfterHold(user, standAlreadySummoned)) {
				LivingComponentAction.getComponent(performer).setAction(null, SyncType.TRACKING_AND_SELF);
			}
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE) {
				if (!level().isClientSide()) {
					LivingEntity user = getPowerUser();
					if (ability instanceof TimeStopAbility timeStop && user != null) {
						StandPower power = PowerClass.STAND.get(user);
						int maxTicks = TimeStopLearning.getSavedTimeStopTicks(power);
						int chargeTicks = Math.max(curPhaseTick, 1);
						float chargeRatio = TimeStopLearning.getTimeStopChargeRatio(
								chargeTicks, curPhaseLength);
						int timeStopTicks = TimeStopLearning.getReleasedTimeStopTicks(
								maxTicks, chargeTicks, curPhaseLength);
						float learningPoints = power != null && power.getCurTypeData() != null
								? power.getCurTypeData().getAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP)
								: -1.0F;
						JojoMod.getLogger().info(
								"Time stop release resolved: user={}, stand={}, chargeTicks={}, chargeLength={}, ratio={}, learningPoints={}, maxTicks={}, resolvedTicks={}.",
								user.getScoreboardName(),
								power != null && power.getPowerType() != null ? power.getPowerType().getId() : "<none>",
								chargeTicks, curPhaseLength, chargeRatio, learningPoints, maxTicks, timeStopTicks);
						timeStop.startTimeStopAfterHold(user, standAlreadySummoned, timeStopTicks);
					}
				}
				LivingComponentAction.getComponent(performer).setAction(null, SyncType.TRACKING_AND_SELF);
			}
		}
	}

}
