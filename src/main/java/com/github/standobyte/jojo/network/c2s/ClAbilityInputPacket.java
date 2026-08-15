package com.github.standobyte.jojo.network.c2s;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.api.network.AbilityNetworkDiagnostics.Stage;
import com.github.standobyte.jojo.api.network.ServerAbilityNetworkDiagnostics;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.network.s2c.TrAimTargetPacket;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId.AbilityInputNetwork;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.input.AbilityInput;
import com.github.standobyte.jojo.powersystem.ability.input.AbilityInput.InputEventType;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClAbilityInputPacket implements CustomPacketPayload {
	private final short key;
	private final long inputGeneration;
	private final InputEventType inputEvent;
	
	private final Ability baseAbilityEncode;
	private final Ability activeAbilityEncode;
	private final AbilityInputNetwork baseAbilityDecoded;
	private final AbilityInputNetwork activeAbilityDecoded;
	private final float timeTookToResolve;
	private final ActionTarget playerAimTarget;
	private final ActionTarget standAimTarget;

	private final LivingEntity clUser;
	@Nullable private final byte[] extraData;
	
	public static ClAbilityInputPacket keyPress(
			short key, long inputGeneration, LivingEntity user,
			Ability baseAbility, InputEventType inputEvent, float timeTookToResolve) {
		return keyPress(
				key, inputGeneration, user, baseAbility, baseAbility,
				inputEvent, timeTookToResolve);
	}
	
	public static ClAbilityInputPacket keyPress(
			short key, long inputGeneration, LivingEntity user,
			Ability baseAbility, Ability activeAbility, InputEventType inputEvent, float timeTookToResolve) {
		return keyPress(
				key, inputGeneration, user, baseAbility, activeAbility,
				inputEvent, timeTookToResolve,
				ActionTarget.EMPTY, ActionTarget.EMPTY);
	}
	
	public static ClAbilityInputPacket keyPress(
			short key, long inputGeneration, LivingEntity user,
			Ability baseAbility, Ability activeAbility, InputEventType inputEvent, float timeTookToResolve, 
			ActionTarget playerAimTarget, ActionTarget standAimTarget) {
		return new ClAbilityInputPacket(key, inputGeneration, inputEvent, user, baseAbility, activeAbility, null, null, timeTookToResolve,
				playerAimTarget, standAimTarget, null);
	}
	
	public static ClAbilityInputPacket releaseHold(
			short key, long inputGeneration) {
		return new ClAbilityInputPacket(key, inputGeneration, InputEventType.RELEASE, null, null, null, null, null, 0,
				ActionTarget.EMPTY, ActionTarget.EMPTY, null);
	}
	
	private ClAbilityInputPacket(short key, long inputGeneration, InputEventType inputEvent, LivingEntity user,
			@Nullable Ability baseAbilityEncode, @Nullable Ability activeAbilityEncode, 
			@Nullable AbilityInputNetwork baseAbilityDecoded, @Nullable AbilityInputNetwork activeAbilityDecoded, 
			float timeTookToResolve, @Nullable ActionTarget playerAimTarget,
			@Nullable ActionTarget standAimTarget, @Nullable byte[] extraData) {
		this.key = key;
		this.inputGeneration = inputGeneration;
		this.inputEvent = inputEvent;
		this.clUser = user;
		this.baseAbilityEncode = baseAbilityEncode;
		this.activeAbilityEncode = activeAbilityEncode;
		this.baseAbilityDecoded = baseAbilityDecoded;
		this.activeAbilityDecoded = activeAbilityDecoded;
		this.timeTookToResolve = timeTookToResolve;
		this.playerAimTarget = copyTargetOrEmpty(playerAimTarget);
		this.standAimTarget = copyTargetOrEmpty(standAimTarget);
		this.extraData = extraData;
	}

	private static ActionTarget copyTargetOrEmpty(@Nullable ActionTarget target) {
		ActionTarget copy = target != null ? target.copy() : null;
		return copy != null ? copy : ActionTarget.EMPTY;
	}

	
	
	private static CustomPacketPayload.Type<ClAbilityInputPacket> type;
	
	public static class Handler implements PacketsRegister.PacketOGHandler<ClAbilityInputPacket> {
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClAbilityInputPacket> type() {
			return type;
		}

		@Override
		public void encode(ClAbilityInputPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeShort(packet.key);
			buf.writeVarLong(
					NetworkPayloadValidation.requireOutboundGeneration(
							packet.inputGeneration, "ability input"));
			buf.writeEnum(packet.inputEvent);
			if (packet.inputEvent != InputEventType.RELEASE) {
				Ability activeAbility = packet.activeAbilityEncode != null ? packet.activeAbilityEncode : packet.baseAbilityEncode;
				AbilityInputNetwork.encodeInput(buf, packet.clUser, packet.baseAbilityEncode);
				AbilityInputNetwork.encodeInput(buf, packet.clUser, activeAbility);
				buf.writeFloat(packet.timeTookToResolve);
				ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(buf, packet.playerAimTarget);
				ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(buf, packet.standAimTarget);
				if (activeAbility != null) {
					FriendlyByteBuf extra = new FriendlyByteBuf(
							Unpooled.buffer(
									256,
									NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES));
					try {
						activeAbility.writeExtraInput(extra, packet.clUser, true);
						int length = NetworkPayloadValidation
								.requireOutboundByteLength(
										extra.readableBytes(),
										NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES,
										"ability extra input");
						buf.writeBytes(extra, extra.readerIndex(), length);
					}
					finally {
						extra.release();
					}
				}	
			}
		}

		@Override
		public ClAbilityInputPacket decode(RegistryFriendlyByteBuf buf) {
			short key = buf.readShort();
			long inputGeneration = NetworkPayloadValidation.requireGeneration(
					buf.readVarLong(), "ability input");
			InputEventType inputEvent = buf.readEnum(InputEventType.class);
			return switch (inputEvent) {
				case RELEASE -> ClAbilityInputPacket.releaseHold(
						key, inputGeneration);
				default -> {
					AbilityInputNetwork baseAbility = AbilityInputNetwork.decodeInput(buf);
					AbilityInputNetwork activeAbility = AbilityInputNetwork.decodeInput(buf);
					float timeTookToResolve = buf.readFloat();
					ActionTarget playerAimTarget = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(buf);
					ActionTarget standAimTarget = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(buf);
					
					byte[] extraData = NetworkUtil.extraPacketDataBytes(
							buf,
							NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES,
							"ability extra input");
					yield new ClAbilityInputPacket(
							key, inputGeneration, inputEvent, null, null, null,
							baseAbility, activeAbility, timeTookToResolve,
							playerAimTarget, standAimTarget, extraData);
				}
			};
		}

		@Override
		public void handle(ClAbilityInputPacket payload, IPayloadContext context) {
			if (!(context.player() instanceof ServerPlayer player)) {
				return;
			}
			FriendlyByteBuf extraInput = payload.extraData != null
					? new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.extraData))
					: null;
			try {
				switch (payload.inputEvent) {
					case PRESS_CLICK, PRESS_HOLD -> handlePress(
							payload, player, extraInput);
					case RELEASE -> {
						AbilityInput.ReleaseResult release =
								AbilityInput.keyReleaseFromNetwork(
										payload.key, player,
										payload.inputGeneration);
						ServerAbilityNetworkDiagnostics.recordAbility(
								release == AbilityInput.ReleaseResult.STALE
										? Stage.SERVER_RELEASE_REJECTED
										: Stage.SERVER_RELEASE_APPLIED,
								player,
								null,
								payload.key,
								payload.inputEvent.name(),
								0,
								switch (release) {
									case RELEASED -> "held_removed";
									case IDEMPOTENT -> "idempotent_release";
									case STALE -> "stale_release_ignored";
								});
					}
				}
			}
			catch (RuntimeException error) {
				containInputFailure(
						payload, player, null, extraInput,
						"input", error);
			}
			finally {
				if (extraInput != null) {
					extraInput.release();
				}
			}
		}

		private static void handlePress(
				ClAbilityInputPacket payload, ServerPlayer player,
				@Nullable FriendlyByteBuf extraInput) {
			if (!canPlayerUseAbilityInput(player)) {
				return;
			}
			Ability baseAbility = resolveAbility(
					payload.baseAbilityDecoded, payload, player, extraInput,
					"base");
			if (baseAbility == null) {
				sendNotUnlockedFeedback(player);
				return;
			}
			Ability requestedActiveAbility = resolveAbility(
					payload.activeAbilityDecoded, payload, player, extraInput,
					"active");
			if (requestedActiveAbility == null) {
				sendNotUnlockedFeedback(player);
				return;
			}
			Power<?> power = baseAbility.getUserPower(player);
			if (power == null) {
				return;
			}
			Ability senderAbility = getSenderMovesetAbility(player, baseAbility);
			if (senderAbility == null) {
				sendNotUnlockedFeedback(player);
				return;
			}
			if (power.getAbility(requestedActiveAbility.name())
					!= requestedActiveAbility) {
				sendNotUnlockedFeedback(player);
				return;
			}
			EntityActionInputState inputState = player.getData(
					ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			if (inputState == null
					|| !inputState.acceptNetworkPressGeneration(
							payload.key, payload.inputGeneration)) {
				ServerAbilityNetworkDiagnostics.recordAbility(
						Stage.SERVER_INPUT_REJECTED,
						player,
						requestedActiveAbility,
						payload.key,
						payload.inputEvent.name(),
						readableBytes(extraInput),
						"stale_input_generation:" + payload.inputGeneration);
				return;
			}

			AimTargetTransaction targets;
			try {
				targets = AimTargetTransaction.resolve(payload, player);
			}
			catch (RuntimeException error) {
				containInputFailure(
						payload, player, requestedActiveAbility, extraInput,
						"target_resolution", error);
				return;
			}

			RuntimeException failure = null;
			boolean committed = false;
			try {
				targets.apply();
				AbilityConditionCheck ability = resolveServerInputAbility(
						payload, player, power, senderAbility,
						requestedActiveAbility);
				if (ability != null && AbilityInput.withConditionCheck(
						ability, player, payload.inputEvent.inputMethod)) {
					BufferingState bufferingState = BufferingState.clickCanBuffer();
					AbilityInput.keyPress(
							payload.key,
							payload.inputGeneration,
							ability.ability,
							player,
							extraInput,
							payload.inputEvent.inputMethod,
							payload.timeTookToResolve,
							bufferingState,
							senderAbility.abilityId);
					committed = true;
					ServerAbilityNetworkDiagnostics.recordAbility(
							Stage.SERVER_INPUT_APPLIED,
							player,
							ability.ability,
							payload.key,
							payload.inputEvent.name(),
							readableBytes(extraInput),
							bufferingState.isActionSuccess
									? "action_started"
									: bufferingState.shouldBuffer
											? "action_buffered"
											: "input_applied");
				}
			}
			catch (RuntimeException error) {
				failure = error;
			}
			finally {
				if (!committed) {
					try {
						targets.rollback();
					}
					catch (RuntimeException rollbackError) {
						if (failure != null) {
							failure.addSuppressed(rollbackError);
						}
						else {
							failure = rollbackError;
						}
					}
				}
			}
			if (failure != null) {
				containInputFailure(
						payload, player, requestedActiveAbility, extraInput,
						"execution", failure);
			}
		}

		private static void containInputFailure(
				ClAbilityInputPacket payload,
				ServerPlayer player,
				@Nullable Ability ability,
				@Nullable FriendlyByteBuf extraInput,
				String failureScope,
				RuntimeException error) {
			ServerAbilityNetworkDiagnostics.recordAbility(
					payload.inputEvent == InputEventType.RELEASE
							? Stage.SERVER_RELEASE_REJECTED
							: Stage.SERVER_INPUT_REJECTED,
					player,
					ability,
					payload.key,
					payload.inputEvent.name(),
					readableBytes(extraInput),
					error.getClass().getName());
			AbilityInputFailureLogLimiter.Decision decision =
					AbilityInputFailureLogLimiter.acquire(
							player.getUUID(),
							failureAbilityId(ability),
							error.getClass());
			if (!decision.logStackTrace()) {
				return;
			}
			String abilityName = ability != null
					? ability.name() : "unresolved";
			if (decision.suppressedCount() > 0L) {
				JojoMod.getLogger().error(
						"Contained server ability {} failure from {} for {} key {} input {}; {} similar failures were suppressed. The network handler remains active.",
						failureScope,
						player.getName().getString(),
						abilityName,
						payload.key,
						payload.inputEvent,
						decision.suppressedCount(),
						error);
			}
			else {
				JojoMod.getLogger().error(
						"Contained server ability {} failure from {} for {} key {} input {}; the network handler remains active.",
						failureScope,
						player.getName().getString(),
						abilityName,
						payload.key,
						payload.inputEvent,
						error);
			}
		}

		private static String failureAbilityId(@Nullable Ability ability) {
			if (ability == null) {
				return "unresolved";
			}
			ResourceLocation typeId = ability.abilityType != null
					? ability.abilityType.registryKey : null;
			return (typeId != null ? typeId.toString() : "unregistered")
					+ '/' + ability.name();
		}

		private static int readableBytes(@Nullable FriendlyByteBuf buffer) {
			return buffer != null ? buffer.readableBytes() : 0;
		}

		private static boolean canPlayerUseAbilityInput(ServerPlayer player) {
			return player != null && player.isAlive()
					&& JojoModUtil.getGameModeConsiderPossessing(player) != GameType.SPECTATOR;
		}

		private static void sendNotUnlockedFeedback(ServerPlayer player) {
			ConditionCheck.sendActionFailedMessage(null, ConditionCheck.createNegative("not_unlocked"), player);
		}

		@Nullable
		private static Ability resolveAbility(
				@Nullable AbilityInputNetwork abilityNetwork,
				ClAbilityInputPacket payload,
				ServerPlayer player,
				@Nullable FriendlyByteBuf extraInput,
				String abilityRole) {
			if (abilityNetwork == null) {
				return null;
			}
			try {
				return abilityNetwork.getAbility(player, null);
			}
			catch (RuntimeException e) {
				containInputFailure(
						payload,
						player,
						null,
						extraInput,
						abilityRole + "_resolution",
						e);
				return null;
			}
		}

		@Nullable
		private static AbilityConditionCheck resolveServerInputAbility(ClAbilityInputPacket payload, ServerPlayer player, Power<?> power,
				Ability senderAbility, Ability requestedActiveAbility) {
			AbilityConditionCheck resolvedAbility = power.updateAvailableMoves().getContextVariationContainer(senderAbility);
			if (resolvedAbility != null && resolvedAbility.ability == requestedActiveAbility) {
				return resolvedAbility;
			}
			
			Ability requestedMovesetAbility = power.getAbility(requestedActiveAbility.name());
			if (requestedMovesetAbility != requestedActiveAbility) {
				JojoMod.getLogger().warn("Ignoring client ability input from {} key {} input {}: requested active ability {} is not in the server moveset.", 
						player.getName().getString(), payload.key, payload.inputEvent, requestedActiveAbility.name());
				return null;
			}
			
			AbilityConditionCheck directAbility = power.updateAvailableMoves().getContextVariationContainer(requestedActiveAbility);
			if (directAbility != null && directAbility.ability == requestedActiveAbility) {
				return directAbility;
			}
			
			if (requestedActiveAbility.isAbilityAvailable(power)) {
				if (resolvedAbility != null) {
					JojoMod.getLogger().debug("Using client-selected active ability {} for {} key {} input {}; server context currently resolved {}.",
							requestedActiveAbility.name(), player.getName().getString(), payload.key, payload.inputEvent, resolvedAbility.ability.name());
				}
				return power.updateAvailableMoves().getCheckedContainerFor(power, requestedActiveAbility);
			}
			
			JojoMod.getLogger().warn("Ignoring unavailable client ability input from {} key {} input {}: requested active ability {}.", 
					player.getName().getString(), payload.key, payload.inputEvent, requestedActiveAbility.name());
			return null;
		}

		private static Ability getSenderMovesetAbility(ServerPlayer player, Ability baseAbility) {
			Power<?> power = baseAbility.getUserPower(player);
			if (power == null) {
				return null;
			}
			Ability senderAbility = power.getAbility(baseAbility.name());
			if (senderAbility != baseAbility) {
				return null;
			}
			return senderAbility;
		}

		private static final class AimTargetTransaction {
			private final LivingComponentAction playerAction;
			private final ActionTarget playerBefore;
			private final ActionTarget playerTarget;
			@Nullable private final StandEntity stand;
			@Nullable private final LivingComponentAction standAction;
			@Nullable private final ActionTarget standBefore;
			@Nullable private final ActionTarget standTarget;
			private boolean started;

			private AimTargetTransaction(
					LivingComponentAction playerAction,
					ActionTarget playerBefore,
					ActionTarget playerTarget,
					@Nullable StandEntity stand,
					@Nullable LivingComponentAction standAction,
					@Nullable ActionTarget standBefore,
					@Nullable ActionTarget standTarget) {
				this.playerAction = playerAction;
				this.playerBefore = playerBefore;
				this.playerTarget = playerTarget;
				this.stand = stand;
				this.standAction = standAction;
				this.standBefore = standBefore;
				this.standTarget = standTarget;
			}

			private static AimTargetTransaction resolve(
					ClAbilityInputPacket payload, ServerPlayer player) {
				LivingComponentAction playerAction =
						LivingComponentAction.getComponent(player);
				ActionTarget playerBefore = copyTargetOrEmpty(
						playerAction.entityAim.getTarget());
				ActionTarget playerTarget = copyTargetOrEmpty(
						payload.playerAimTarget.resolveEntityId(player.level()));
				StandEntity stand = StandUtil.getSummonedStand(player);
				LivingComponentAction standAction = stand != null
						? LivingComponentAction.getComponent(stand) : null;
				ActionTarget standBefore = standAction != null
						? copyTargetOrEmpty(standAction.entityAim.getTarget()) : null;
				ActionTarget standTarget = standAction != null
						? copyTargetOrEmpty(payload.standAimTarget
								.resolveEntityId(player.level())) : null;
				if (standTarget != null
						&& standTarget.getType() == ActionTarget.TargetType.EMPTY
						&& playerTarget.getType() != ActionTarget.TargetType.EMPTY) {
					standTarget = copyTargetOrEmpty(playerTarget);
				}
				return new AimTargetTransaction(
						playerAction, playerBefore, playerTarget,
						stand, standAction, standBefore, standTarget);
			}

			private void apply() {
				started = true;
				playerAction.entityAim.setTarget(playerTarget);
				setStandTarget(standTarget);
			}

			private void rollback() {
				if (!started) {
					return;
				}
				playerAction.entityAim.setTarget(playerBefore);
				setStandTarget(standBefore);
			}

			private void setStandTarget(@Nullable ActionTarget target) {
				if (stand == null || standAction == null || target == null) {
					return;
				}
				standAction.entityAim.setTarget(target);
				if (standAction.entityAim.checkDirty()) {
					PacketDistributor.sendToPlayersTrackingEntityAndSelf(
							stand,
							new TrAimTargetPacket(stand.getId(), target));
				}
			}
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
	
}
