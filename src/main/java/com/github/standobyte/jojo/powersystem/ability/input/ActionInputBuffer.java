package com.github.standobyte.jojo.powersystem.ability.input;

import java.util.Iterator;

import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState;
import com.github.standobyte.jojo.powersystem.entityaction.HeldInput;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState.HeldInputEntry;

import io.netty.buffer.Unpooled;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;

public class ActionInputBuffer {
	protected BufferedInputEntry buffered;

	public void bufferClickInput(AbilityId abilityToBuffer) {
		bufferClickInput(abilityToBuffer, null);
	}

	public void bufferClickInput(AbilityId abilityToBuffer, @Nullable byte[] extraInput) {
		this.buffered = new BufferedInputEntry(abilityToBuffer, InputMethod.CLICK, extraInput);
	}

	public HeldInput bufferHeldInput(AbilityId abilityToBuffer) {
		return bufferHeldInput(abilityToBuffer, null);
	}

	public HeldInput bufferHeldInput(AbilityId abilityToBuffer, @Nullable byte[] extraInput) {
		this.buffered = new BufferedInputEntry(abilityToBuffer, InputMethod.HOLD, extraInput);
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
				if (ability != null) {
					BufferingState bufferingState = BufferingState.buffered();
					FriendlyByteBuf replayInput = null;
					try {
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
							for (HeldInputEntry heldKeyAction : userInput.heldKeys.values()) {
								if (heldKeyAction.action == replaying) {
									heldKeyAction.action = newAction;
									break;
								}
							}
							if (buffered == replaying) {
								buffered = null;
							}
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
			}
			else {
				buffered = null;
			}
		}
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


	public static record BufferedInputEntry(AbilityId baseAbilityId, InputMethod inputMethod, @Nullable byte[] extraInput) implements HeldInput {

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
