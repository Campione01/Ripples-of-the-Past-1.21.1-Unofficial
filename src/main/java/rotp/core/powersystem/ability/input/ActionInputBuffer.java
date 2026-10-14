package rotp.core.powersystem.ability.input;

import java.util.Iterator;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.event.ModEventHooks;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.network.NetworkPayloadValidation;
import rotp.core.network.s2c.TrAbilityUsePacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;

import io.netty.buffer.Unpooled;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class ActionInputBuffer {
	protected BufferedInputEntry buffered;

	public void bufferClickInput(AbilityId abilityToBuffer) {
		bufferClickInput(abilityToBuffer, null);
	}

	public void bufferClickInput(AbilityId abilityToBuffer, @Nullable byte[] extraInput) {
		bufferClickInput(abilityToBuffer, extraInput, (short) 0, 0L);
	}

	/** The press's key and input generation let its replay reach tracking players as the press itself did. */
	@ApiStatus.Internal
	public void bufferClickInput(AbilityId abilityToBuffer, @Nullable byte[] extraInput, short keyId, long inputGeneration) {
		this.buffered = new BufferedInputEntry(abilityToBuffer, InputMethod.CLICK, extraInput, keyId, inputGeneration);
	}

	public HeldInput bufferHeldInput(AbilityId abilityToBuffer) {
		return bufferHeldInput(abilityToBuffer, null);
	}

	public HeldInput bufferHeldInput(AbilityId abilityToBuffer, @Nullable byte[] extraInput) {
		return bufferHeldInput(abilityToBuffer, extraInput, (short) 0, 0L);
	}

	@ApiStatus.Internal
	public HeldInput bufferHeldInput(AbilityId abilityToBuffer, @Nullable byte[] extraInput, short keyId, long inputGeneration) {
		this.buffered = new BufferedInputEntry(abilityToBuffer, InputMethod.HOLD, extraInput, keyId, inputGeneration);
		return buffered;
	}
	
	public static class BufferingState {
		protected boolean canBuffer;
		protected boolean isAlreadyBuffered;
		public boolean shouldBuffer;
		public boolean isActionSuccess;
		
		public static BufferingState clickCanBuffer() {
			BufferingState obj = new BufferingState();
			obj.canBuffer = true;
			return obj;
		}
		
		public static BufferingState clickOnly() {
			return new BufferingState();
		}
		
		public static BufferingState buffered() {
			BufferingState obj = new BufferingState();
			obj.isAlreadyBuffered = true;
			return obj;
		}
		
		public boolean canBuffer() {
			return canBuffer;
		}
		
		public boolean isBuffered() {
			return isAlreadyBuffered;
		}
		
		public void setToBuffer() {
			this.shouldBuffer = true;
		}
		
		public void setActionSuccess() {
			this.isActionSuccess = true;
		}
	}

	public void tickInputBuffer(EntityActionInputState userInput) {
		LivingEntity user = userInput.user;
		if (user.level().isClientSide()) return;

		if (buffered != null) {
			BufferedInputEntry replaying = buffered;
			AbilityId baseAbilityId = replaying.baseAbilityId;
			Power<?> power = baseAbilityId.powerClass().get(user);
			if (power != null && power.hasPower() && baseAbilityId.powerTypeId().equals(power.getPowerType().getId())) {
				AvailableAbilities abilities = power.updateAvailableMoves();
				Ability ability = abilities.inMovesetAndCanBeUsed.get(baseAbilityId.nameInMoveset());
				// 1.16 StandEntity.checkInputBuffer clicked a queued action again only once its Stand could take it.
				if (ability != null && !(ability instanceof EntityActionAbility entityAction
						&& entityAction.wouldBufferInput(user))) {
					replay(userInput, replaying, ability);
				}
			}
			else {
				buffered = null;
			}
		}
	}

	/**
	 * 1.16 replayed a queued action through clickAction, as a new click: its conditions, its cooldown on perform
	 * (PowerBaseImpl.performAction) and its sync. Here the replay gets what AbilityInput.keyPress gives a press that
	 * starts: the key press event (which may cancel or replace it), the cooldown and the press sent to tracking players.
	 */
	private void replay(EntityActionInputState userInput, BufferedInputEntry replaying, Ability ability) {
		LivingEntity user = userInput.user;
		BufferingState bufferingState = BufferingState.buffered();
		FriendlyByteBuf replayInput = null;
		try {
			RipplesAbilityKeyPressEvent event = ModEventHooks.onAbilityKeyPress(
					user, ability, replaying.inputMethod, 0);
			ability = event.ability;
			if (ability == null) {
				throw new IllegalStateException(
						"Ability key press event returned a null ability");
			}
			if (event.isCanceled()) {
				rebindHeldKey(userInput, replaying, event.newHeldInput);
				return;
			}
			byte[] extraInput = replaying.extraInput;
			replayInput = new FriendlyByteBuf(extraInput != null
					? Unpooled.wrappedBuffer(extraInput)
					: Unpooled.buffer(
							256,
							NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES));
			if (extraInput == null) {
				ability.writeExtraInput(replayInput, user, false);
				NetworkPayloadValidation.requireOutboundByteLength(
						replayInput.readableBytes(),
						NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES,
						"buffered ability replay input");
			}
			HeldInput newAction = ability.onKeyPress(
					user.level(), user, replayInput,
					replaying.inputMethod, 0, bufferingState);
			if (bufferingState.isActionSuccess) {
				Power<?> abilityPower = ability.abilityId.powerClass() != null
						? ability.abilityId.powerClass().get(user) : null;
				if (abilityPower != null && ability.shouldSetCooldownOnKeyPress(replaying.inputMethod)) {
					ability.setCooldownOnUse(abilityPower);
				}
				rebindHeldKey(userInput, replaying, newAction);
				sendReplayToTracking(userInput, replaying, ability);
			}
		}
		catch (RuntimeException error) {
			clearFailedBufferedInput(userInput, replaying);
			throw error;
		}
		finally {
			if (replayInput != null) {
				replayInput.release();
			}
		}
	}

	private void rebindHeldKey(EntityActionInputState userInput, BufferedInputEntry replayed, @Nullable HeldInput newAction) {
		for (HeldInputEntry heldKeyAction : userInput.heldKeys.values()) {
			if (heldKeyAction.action == replayed) {
				heldKeyAction.action = newAction;
				break;
			}
		}
		if (buffered == replayed) {
			buffered = null;
		}
	}

	// A later press of the same key has already reached tracking players; a replay of the older one would be stale there.
	private static void sendReplayToTracking(EntityActionInputState userInput, BufferedInputEntry replayed, Ability ability) {
		if (replayed.inputGeneration <= 0L
				|| replayed.inputGeneration < userInput.latestInputGeneration(replayed.keyId)) {
			return;
		}
		LivingEntity user = userInput.user;
		PacketDistributor.sendToPlayersTrackingEntity(
				user,
				TrAbilityUsePacket.keyPress(
						user.getId(), replayed.keyId, replayed.inputGeneration,
						ability, replayed.inputMethod, 0, user));
	}

	private void clearFailedBufferedInput(
			EntityActionInputState userInput,
			BufferedInputEntry failed) {
		if (buffered == failed) {
			buffered = null;
		}
		Iterator<HeldInputEntry> iterator =
				userInput.heldKeys.values().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().action == failed) {
				iterator.remove();
			}
		}
	}

	@Nullable
	public static byte[] copyRemainingBytes(@Nullable FriendlyByteBuf source) {
		if (source == null || source.readableBytes() <= 0) {
			return null;
		}
		int length = NetworkPayloadValidation.requireOutboundByteLength(
				source.readableBytes(),
				NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES,
				"buffered ability input");
		byte[] bytes = new byte[length];
		source.getBytes(source.readerIndex(), bytes);
		return bytes;
	}
	
	public static ActionInputBuffer get(LivingEntity user) {
		EntityActionInputState inputState = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		return inputState != null ? inputState.inputBuffer : null;
	}


	public static record BufferedInputEntry(AbilityId baseAbilityId, InputMethod inputMethod, @Nullable byte[] extraInput,
			short keyId, long inputGeneration) implements HeldInput {

		public BufferedInputEntry(AbilityId baseAbilityId, InputMethod inputMethod, @Nullable byte[] extraInput) {
			this(baseAbilityId, inputMethod, extraInput, (short) 0, 0L);
		}

		@Override
		public void onKeyRelease(LivingEntity user) {
			ActionInputBuffer inputBuffer = ActionInputBuffer.get(user);
			if (inputBuffer == null) return;
			
			// Remove itself from the input buffer, if the key was released before the queued action could start

			if (inputMethod == InputMethod.HOLD) {
				EntityActionInputState inputState = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
				if (inputState != null) {
					if (inputState.inputBuffer.buffered == this) {
						inputState.inputBuffer.buffered = null;
					}
				}
			}
		}

	}
}
