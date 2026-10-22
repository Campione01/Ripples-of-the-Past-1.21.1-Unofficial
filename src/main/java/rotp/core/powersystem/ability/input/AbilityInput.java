package rotp.core.powersystem.ability.input;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;
import rotp.core.event.ModEventHooks;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.network.s2c.TrAbilityUsePacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferedInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.LivingComponentAction.TransactionSnapshot;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class AbilityInput {

	@ApiStatus.Internal
	public static long nextInputGeneration(
			LivingEntity user, short keyId) {
		if (user == null) {
			throw new IllegalArgumentException("Ability input user is required");
		}
		EntityActionInputState inputState = user.getData(
				ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		if (inputState == null) {
			throw new IllegalStateException(
					"Ability input state is unavailable");
		}
		return inputState.nextInputGeneration(keyId);
	}
	
	public static boolean withConditionCheck(Ability ability, LivingEntity user) {
		if (ability == null || user == null) return false;
		
		Power<?> power = ability.abilityId.powerClass().get(user);
		if (power == null) {
			return false;
		}
		
		AvailableAbilities availableAbilities = power.updateAvailableMoves();
		AbilityConditionCheck abilityConditionCheck = availableAbilities.getContextVariationContainer(ability);
		return withConditionCheck(abilityConditionCheck, user);
	}
	
	public static boolean withConditionCheck(AbilityConditionCheck abilityConditionCheck, LivingEntity user) {
		return withConditionCheck(abilityConditionCheck, user, null);
	}
	
	public static boolean withConditionCheck(AbilityConditionCheck abilityConditionCheck, LivingEntity user, @Nullable InputMethod inputMethod) {
		if (abilityConditionCheck == null) {
			if (user != null) {
				ConditionCheck.sendActionFailedMessage(null, ConditionCheck.createNegative("not_unlocked"), user);
			}
			return false;
		}
		Ability ability = abilityConditionCheck.ability;
		ConditionCheck result = abilityConditionCheck.conditionCheck;
		boolean canUse = result.isPositive() || inputMethod == InputMethod.HOLD && result.shouldContinueHold();
		if (!canUse) {
			ConditionCheck.sendActionFailedMessage(ability, result, user);
		}
		return canUse;
	}
	
	
	/* 
	 * FIXME ability inputs that are currently controlled purely by the client
	 * 	barrage can refresh its duration via a client-sent packet
	 */
	@Nullable
	public static HeldInputEntry keyPress(short keyId, Ability ability, 
			LivingEntity user, FriendlyByteBuf extraClientInput, 
			InputMethod inputMethod, float clickHoldResolveTime, 
			BufferingState bufferingState, AbilityId baseAbilityForBuffering) {
		if (ability == null || user == null) return null;
		EntityActionInputState inputState = user.getData(
				ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		if (inputState == null) {
			throw new IllegalStateException(
					"Ability input state is unavailable");
		}
		return keyPress(
				keyId, inputState.nextInputGeneration(keyId), ability,
				user, extraClientInput, inputMethod, clickHoldResolveTime,
				bufferingState, baseAbilityForBuffering);
	}

	@ApiStatus.Internal
	@Nullable
	public static HeldInputEntry keyPress(
			short keyId,
			long inputGeneration,
			Ability ability,
			LivingEntity user,
			FriendlyByteBuf extraClientInput,
			InputMethod inputMethod,
			float clickHoldResolveTime,
			BufferingState bufferingState,
			AbilityId baseAbilityForBuffering) {
		if (ability == null || user == null) return null;
		EntityActionInputState inputState = user.getData(
				ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		if (inputState == null) {
			throw new IllegalStateException(
					"Ability input state is unavailable");
		}
		if (!inputState.adoptInputGeneration(keyId, inputGeneration)) {
			throw new IllegalStateException(
					"Stale ability input generation " + inputGeneration);
		}
		
		Level level = user.level();
		byte[] extraInputForBuffer = ActionInputBuffer.copyRemainingBytes(extraClientInput);
		InputTransaction transaction = new InputTransaction(
				user, keyId, inputGeneration);
		try {
			RipplesAbilityKeyPressEvent event = ModEventHooks.onAbilityKeyPress(
					user, ability, inputMethod, clickHoldResolveTime);
			ability = event.ability;
			if (ability == null) {
				throw new IllegalStateException(
						"Ability key press event returned a null ability");
			}
			transaction.captureCooldown(ability);
			HeldInput action;
			boolean buffered = false;
			if (event.isCanceled()) {
				action = event.newHeldInput;
			}
			else {
				action = ability.onKeyPress(
						level, user, extraClientInput, inputMethod,
						clickHoldResolveTime, bufferingState);
				if (bufferingState.canBuffer() && bufferingState.shouldBuffer) {
					HeldInput heldInputObj = null;
					ActionInputBuffer actionInputBuffer = transaction.inputBuffer(ability);
					if (actionInputBuffer != null) {
						if (baseAbilityForBuffering == null) {
							baseAbilityForBuffering = ability.abilityId;
						}
						switch (inputMethod) {
							case CLICK -> actionInputBuffer.bufferClickInput(
									baseAbilityForBuffering, extraInputForBuffer,
									keyId, inputGeneration);
							case HOLD -> heldInputObj = actionInputBuffer.bufferHeldInput(
									baseAbilityForBuffering, extraInputForBuffer,
									keyId, inputGeneration);
						}
						buffered = true;
					}
					action = heldInputObj;
				}
			}
			transaction.captureBufferedAfter();

			HeldInputEntry heldInput = null;
			if (action != null) {
				if (transaction.inputState == null) {
					throw new IllegalStateException(
							"Ability input state is unavailable");
				}
				heldInput = new HeldInputEntry(
						keyId, inputGeneration,
						ability.abilityId.powerClass(), action);
				transaction.inputState.heldKeys.put(keyId, heldInput);
			}

			EntityActionInstance holdTakingCooldown = null;
			if (!level.isClientSide() && bufferingState.isActionSuccess
					&& ability.abilityId.powerClass() != null) {
				Power<?> power = ability.abilityId.powerClass().get(user);
				if (power != null && ability.shouldSetCooldownOnKeyPress(inputMethod)) {
					holdTakingCooldown = EntityActionAbility.getHoldTakingCooldown(ability, action);
					if (holdTakingCooldown == null) {
						transaction.cooldownAttempted = true;
						ability.setCooldownOnUse(power);
					}
				}
			}
			// a buffered press reaches tracking players once, when ActionInputBuffer replays it and it starts
			if (!level.isClientSide() && !buffered) {
				transaction.replayAttempted = true;
				PacketDistributor.sendToPlayersTrackingEntity(
						user,
						TrAbilityUsePacket.keyPress(
								user.getId(), keyId, inputGeneration,
								ability, inputMethod,
								clickHoldResolveTime, user));
			}
			// last: a press that fails before here is rolled back, and the action it removes owes no cooldown
			if (holdTakingCooldown != null) {
				holdTakingCooldown.takeCooldownAfterHold();
			}
			if (!level.isClientSide() && bufferingState.isActionSuccess && ability.endsOtherHoldOnPress()) {
				endOtherHoldTypeStandActions(transaction.inputState, user, keyId, ability, action);
			}
			return heldInput;
		}
		catch (RuntimeException error) {
			transaction.rollback(error);
			throw error;
		}
	}

	public static void keyRelease(short keyId, LivingEntity user) {
		keyReleaseAndGetGeneration(keyId, user);
	}

	@ApiStatus.Internal
	public static long keyReleaseAndGetGeneration(
			short keyId, @Nullable LivingEntity user) {
		if (user == null) {
			return 0L;
		}
		EntityActionInputState inputState = user.getData(
				ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		HeldInputEntry held = inputState != null
				? inputState.heldKeys.get(keyId) : null;
		long generation = held != null && held.generation > 0L
				? held.generation
				: inputState != null
						? inputState.latestInputGeneration(keyId) : 0L;
		keyRelease(keyId, user, false, generation);
		return generation;
	}

	@ApiStatus.Internal
	public static boolean keyReleaseFromNetwork(
			short keyId, LivingEntity user) {
		if (user == null) {
			return false;
		}
		EntityActionInputState inputState = user.getData(
				ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		HeldInputEntry held = inputState != null
				? inputState.heldKeys.get(keyId) : null;
		long generation = held != null ? held.generation : 0L;
		return generation > 0L
				&& keyReleaseFromNetwork(keyId, user, generation)
						== ReleaseResult.RELEASED;
	}

	@ApiStatus.Internal
	public static ReleaseResult keyReleaseFromNetwork(
			short keyId,
			LivingEntity user,
			long inputGeneration) {
		if (inputGeneration <= 0L) {
			throw new IllegalArgumentException(
					"Ability input generation must be positive");
		}
		return keyRelease(keyId, user, true, inputGeneration);
	}

	private static ReleaseResult keyRelease(
			short keyId, @Nullable LivingEntity user,
			boolean authoritativeNetworkRelease,
			long inputGeneration) {
		if (user == null) {
			return ReleaseResult.IDEMPOTENT;
		}
		EntityActionInputState inputHandler = user.getData(
				ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		HeldInputEntry current = inputHandler != null
				? inputHandler.heldKeys.get(keyId) : null;
		long latestBefore = inputHandler != null
				? inputHandler.latestInputGeneration(keyId) : 0L;
		HeldInputEntry heldAction = null;
		ReleaseResult result = classifyRelease(
				current, latestBefore, inputGeneration);
		if (result == ReleaseResult.RELEASED) {
			inputHandler.heldKeys.remove(keyId);
			heldAction = current;
		}
		if (inputHandler != null && inputGeneration > 0L) {
			inputHandler.observeInputGeneration(keyId, inputGeneration);
		}
		Runnable releaseSync = () -> {};
		if (!user.level().isClientSide() && inputGeneration > 0L
				&& (authoritativeNetworkRelease || heldAction != null)) {
			releaseSync = authoritativeNetworkRelease
					? () -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(
							user,
							TrAbilityUsePacket.releaseHold(
									user.getId(), keyId, inputGeneration))
					: () -> PacketDistributor.sendToPlayersTrackingEntity(
							user,
							TrAbilityUsePacket.releaseHold(
									user.getId(), keyId, inputGeneration));
		}
		releaseHeldInput(heldAction, user, releaseSync);
		return result;
	}

	static ReleaseResult classifyRelease(
			@Nullable HeldInputEntry current,
			long latestGeneration,
			long requestedGeneration) {
		if (current != null
				&& current.generation == requestedGeneration) {
			return ReleaseResult.RELEASED;
		}
		if (requestedGeneration > 0L
				&& requestedGeneration < latestGeneration) {
			return ReleaseResult.STALE;
		}
		return current != null
				? ReleaseResult.STALE : ReleaseResult.IDEMPOTENT;
	}

	static boolean releaseHeldInput(
			@Nullable HeldInputEntry heldAction,
			@Nullable LivingEntity user,
			Runnable releaseSync) {
		RuntimeException failure = null;
		try {
			if (heldAction != null && heldAction.action != null) {
				heldAction.action.onKeyRelease(user);
			}
		}
		catch (RuntimeException error) {
			failure = error;
		}
		try {
			releaseSync.run();
		}
		catch (RuntimeException syncError) {
			if (failure != null) {
				failure.addSuppressed(syncError);
			}
			else {
				failure = syncError;
			}
		}
		if (failure != null) {
			throw failure;
		}
		return heldAction != null;
	}

	private static final class InputTransaction {
		private final LivingEntity user;
		private final short keyId;
		private final long inputGeneration;
		@Nullable private final EntityActionInputState inputState;
		@Nullable private final HeldInputEntry heldBefore;
		@Nullable private final ActionInputBuffer inputBuffer;
		@Nullable private final BufferedInputEntry bufferedBefore;
		@Nullable private BufferedInputEntry bufferedAfter;
		@Nullable private final ActionInputBuffer playerPowerInputBuffer;
		@Nullable private final BufferedInputEntry playerPowerBufferedBefore;
		@Nullable private BufferedInputEntry playerPowerBufferedAfter;
		private final LivingComponentAction userAction;
		private final TransactionSnapshot userActionBefore;
		@Nullable private final LivingComponentAction userStandPowerAction;
		@Nullable private final TransactionSnapshot userStandPowerActionBefore;
		@Nullable private final LivingComponentAction standAction;
		@Nullable private final TransactionSnapshot standActionBefore;
		@Nullable private StandPower cooldownPower;
		@Nullable private String cooldownAbility;
		private int cooldownBefore;
		private int cooldownTotalBefore;
		private boolean cooldownAttempted;
		private boolean replayAttempted;

		private InputTransaction(
				LivingEntity user,
				short keyId,
				long inputGeneration) {
			this.user = user;
			this.keyId = keyId;
			this.inputGeneration = inputGeneration;
			this.inputState = user.getData(
					ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			this.heldBefore = inputState != null
					? inputState.heldKeys.get(keyId) : null;
			this.inputBuffer = inputState != null
					? inputState.inputBuffer : null;
			this.bufferedBefore = inputBuffer != null
					? inputBuffer.buffered : null;
			this.bufferedAfter = bufferedBefore;
			this.playerPowerInputBuffer = inputState != null
					? inputState.playerPowerInputBuffer : null;
			this.playerPowerBufferedBefore = playerPowerInputBuffer != null
					? playerPowerInputBuffer.buffered : null;
			this.playerPowerBufferedAfter = playerPowerBufferedBefore;
			this.userAction = LivingComponentAction.getComponent(user);
			this.userActionBefore = userAction.captureTransactionSnapshot();
			this.userStandPowerAction = user instanceof StandEntity
					? null : LivingComponentAction.getComponent(user, PowerClass.STAND);
			this.userStandPowerActionBefore = userStandPowerAction != null
					? userStandPowerAction.captureTransactionSnapshot() : null;
			StandEntity stand = StandUtil.getSummonedStand(user);
			this.standAction = stand != null
					? LivingComponentAction.getComponent(stand) : null;
			this.standActionBefore = standAction != null
					? standAction.captureTransactionSnapshot() : null;
		}

		private void captureBufferedAfter() {
			bufferedAfter = inputBuffer != null
					? inputBuffer.buffered : null;
			playerPowerBufferedAfter = playerPowerInputBuffer != null
					? playerPowerInputBuffer.buffered : null;
		}

		// the buffer of the pressed ability's power: 1.16 kept a press per power
		@Nullable
		private ActionInputBuffer inputBuffer(Ability pressed) {
			return pressed.abilityId.powerClass() == PowerClass.PLAYER_POWER
					? playerPowerInputBuffer : inputBuffer;
		}

		private static void restoreBuffered(@Nullable ActionInputBuffer inputBuffer,
				@Nullable BufferedInputEntry bufferedBefore, @Nullable BufferedInputEntry bufferedAfter) {
			if (inputBuffer != null
					&& inputBuffer.buffered == bufferedAfter
					&& bufferedAfter != bufferedBefore) {
				inputBuffer.buffered = bufferedBefore;
			}
		}

		private void captureCooldown(Ability ability) {
			if (ability.abilityId.powerClass() == PowerClass.STAND) {
				Power<?> power = PowerClass.STAND.get(user);
				if (power instanceof StandPower standPower) {
					cooldownPower = standPower;
					cooldownAbility = ability.name();
					cooldownBefore = standPower.getAbilityCooldown(
							cooldownAbility);
					cooldownTotalBefore = standPower.getAbilityCooldownTotal(
							cooldownAbility);
				}
			}
		}

		private void rollback(RuntimeException original) {
			rollbackStep(original, () -> {
				if (inputState != null) {
					HeldInputEntry current = inputState.heldKeys.get(keyId);
					if (current != null
							&& current.generation == inputGeneration) {
						if (heldBefore != null) {
							inputState.heldKeys.put(keyId, heldBefore);
						}
						else {
							inputState.heldKeys.remove(keyId);
						}
					}
				}
			});
			rollbackStep(original, () -> restoreBuffered(inputBuffer, bufferedBefore, bufferedAfter));
			rollbackStep(original, () -> restoreBuffered(
					playerPowerInputBuffer, playerPowerBufferedBefore, playerPowerBufferedAfter));
			if (cooldownAttempted && cooldownPower != null
					&& cooldownAbility != null) {
				rollbackStep(original, () -> cooldownPower.setAbilityCooldown(
						cooldownAbility, cooldownBefore, cooldownTotalBefore));
			}
			rollbackStep(original, () ->
					userAction.rollbackFailedInputAction(userActionBefore));
			if (userStandPowerAction != null && userStandPowerActionBefore != null) {
				rollbackStep(original, () -> userStandPowerAction.rollbackFailedInputAction(
						userStandPowerActionBefore));
			}
			if (standAction != null && standActionBefore != null) {
				rollbackStep(original, () -> standAction.rollbackFailedInputAction(
						standActionBefore));
			}
			if (replayAttempted && !user.level().isClientSide()) {
				rollbackStep(original, () -> PacketDistributor
						.sendToPlayersTrackingEntityAndSelf(
								user,
								TrAbilityUsePacket.releaseHold(
										user.getId(), keyId,
										inputGeneration)));
			}
		}

		private static void rollbackStep(
				RuntimeException original, Runnable rollback) {
			try {
				rollback.run();
			}
			catch (RuntimeException rollbackError) {
				original.addSuppressed(rollbackError);
			}
		}
	}

	@ApiStatus.Internal
	public static boolean hasHeldInput(
			LivingEntity user,
			PowerClass<?> powerClass) {
		if (user == null
				|| powerClass == null
				|| user.level().isClientSide()) {
			return false;
		}
		return user.getExistingData(
				ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
				.map(input -> hasHeldInput(
						input.heldKeys, powerClass))
				.orElse(false);
	}

	@ApiStatus.Internal
	public static boolean interruptHeldInputs(
			LivingEntity user,
			PowerClass<?> powerClass) {
		if (user == null
				|| powerClass == null
				|| user.level().isClientSide()) {
			return false;
		}
		EntityActionInputState input = user.getExistingData(
				ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
				.orElse(null);
		if (input == null) {
			return false;
		}
		return interruptHeldInputs(
				input.heldKeys,
				powerClass,
				user,
				(keyId, generation) -> PacketDistributor
						.sendToPlayersTrackingEntityAndSelf(
								user,
								TrAbilityUsePacket.releaseHold(
										user.getId(),
										keyId,
										generation)));
	}

	/*
	 * 1.16 PowerBaseImpl let every action of a power through the power's held hold-type Stand action
	 * (StandEntityAction.heldAllowsOtherAction) and ended that hold with stopHeldAction(false) once the action went off: an
	 * action the Stand performs has already replaced it here (EntityActionInstance#givesWayToOtherAbilities), any other
	 * press ends its hold as letting go of its key does. Server side.
	 */
	private static void endOtherHoldTypeStandActions(
			@Nullable EntityActionInputState input,
			LivingEntity user,
			short keyId,
			Ability pressed,
			@Nullable HeldInput started) {
		PowerClass<?> powerClass = pressed.abilityId.powerClass();
		if (input == null || powerClass == null) {
			return;
		}
		List<HeldInputEntry> holds = new ArrayList<>();
		for (HeldInputEntry entry : input.heldKeys.values()) {
			if (entry.keyId != keyId && entry.powerClass == powerClass
					&& entry.action instanceof EntityActionInstance held && held != started && !held.isOver()
					&& held.ability != pressed && held.ability instanceof EntityActionAbility heldAbility
					&& held.isHoldTypeStandAction() && heldAbility.isHeldSincePress(held)) {
				holds.add(entry);
			}
		}
		for (HeldInputEntry entry : holds) {
			if (input.heldKeys.get(entry.keyId) != entry) {
				continue;
			}
			input.heldKeys.remove(entry.keyId);
			try {
				entry.action.onKeyRelease(user);
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Held {} input {} release failed for {}.",
						powerClass,
						entry.keyId,
						user,
						error);
			}
			if (entry.generation > 0L) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(
						user,
						TrAbilityUsePacket.releaseHold(
								user.getId(), entry.keyId, entry.generation));
			}
		}
	}

	/**
	 * Whether a key (or a mob's pseudo key) still holds this action. 1.16 PowerBaseImpl kept a held action only
	 * while its key was down.
	 */
	@ApiStatus.Internal
	public static boolean isHeldByKey(
			LivingEntity user,
			HeldInput action) {
		if (user == null || action == null) {
			return false;
		}
		EntityActionInputState input = user.getExistingData(
				ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
				.orElse(null);
		return input != null && isHeldByKey(input.heldKeys, action);
	}

	/**
	 * The action of the power that its user holds now, as 1.16's power held one from its press until it fired or was let
	 * go (PowerBaseImpl.getHeldAction): the action of an ability of that power in the user's slot of that power
	 * (LivingComponentAction#getComponent(LivingEntity, PowerClass)) or, for the Stand power, its Stand's, held since its press
	 * (EntityActionAbility#isHeldSincePress) and, on the server, by a key. A client, which does not know the server's
	 * keys, goes by the action's phase alone.
	 */
	@ApiStatus.Internal
	@Nullable
	public static EntityActionInstance getHeldAction(@Nullable LivingEntity user, @Nullable PowerClass<?> powerClass) {
		if (user == null || powerClass == null) {
			return null;
		}
		EntityActionInstance held = heldActionOf(user, user, powerClass);
		StandEntity stand = held == null && powerClass == PowerClass.STAND ? StandUtil.getSummonedStand(user) : null;
		return stand != null && stand != user ? heldActionOf(stand, user, powerClass) : held;
	}

	@Nullable
	private static EntityActionInstance heldActionOf(LivingEntity performer, LivingEntity user, PowerClass<?> powerClass) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(performer, powerClass);
		return action != null && !action.isOver() && action.getPowerUser() == user
				&& action.ability instanceof EntityActionAbility ability && ability.abilityId.powerClass() == powerClass
				&& ability.isHeldSincePress(action) && (user.level().isClientSide() || isHeldByKey(user, action))
				? action : null;
	}

	static boolean isHeldByKey(
			Int2ObjectMap<HeldInputEntry> heldInputs,
			HeldInput action) {
		for (HeldInputEntry entry : heldInputs.values()) {
			if (entry.action == action) {
				return true;
			}
		}
		return false;
	}

	static boolean hasHeldInput(
			Int2ObjectMap<HeldInputEntry> heldInputs,
			PowerClass<?> powerClass) {
		for (HeldInputEntry entry : heldInputs.values()) {
			if (entry.powerClass == powerClass) {
				return true;
			}
		}
		return false;
	}

	static boolean interruptHeldInputs(
			Int2ObjectMap<HeldInputEntry> heldInputs,
			PowerClass<?> powerClass,
			@Nullable LivingEntity user,
			IntConsumer releaseSync) {
		return interruptHeldInputs(
				heldInputs, powerClass, user,
				(keyId, generation) -> releaseSync.accept(keyId));
	}

	private static boolean interruptHeldInputs(
			Int2ObjectMap<HeldInputEntry> heldInputs,
			PowerClass<?> powerClass,
			@Nullable LivingEntity user,
			BiConsumer<Short, Long> releaseSync) {
		List<HeldInputEntry> interrupted = new ArrayList<>();
		for (HeldInputEntry entry : heldInputs.values()) {
			if (entry.powerClass == powerClass) {
				interrupted.add(entry);
			}
		}
		for (HeldInputEntry entry : interrupted) {
			if (heldInputs.get(entry.keyId) != entry) {
				continue;
			}
			heldInputs.remove(entry.keyId);
			try {
				if (entry.action != null) {
					entry.action.onKeyRelease(user);
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Held {} input {} release failed for {}.",
						powerClass,
						entry.keyId,
						user,
						error);
			}
			releaseSync.accept(entry.keyId, entry.generation);
		}
		return !interrupted.isEmpty();
	}

	@Nullable
	public static HeldInputEntry keyPressMob(Ability ability, LivingEntity user, FriendlyByteBuf extraData, InputMethod inputMethod) {
		short keyId = (short) pseudoKey.incrementAndGet();
		return keyPress(keyId, ability, user, extraData, inputMethod, 0, BufferingState.clickOnly(), null);
	}
	private static final AtomicInteger pseudoKey = new AtomicInteger();
	
	public enum ReleaseResult {
		RELEASED,
		IDEMPOTENT,
		STALE
	}

	public enum InputEventType {
		PRESS_CLICK(InputMethod.CLICK),
		PRESS_HOLD(InputMethod.HOLD),
		RELEASE(InputMethod.HOLD);
		
		public final InputMethod inputMethod;

		InputEventType(InputMethod inputMethod) {
			this.inputMethod = inputMethod;
		}
	}
	
}
