package com.github.standobyte.jojo.network.s2c;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.api.network.ClientAbilityNetworkDiagnostics;
import com.github.standobyte.jojo.api.network.AbilityNetworkDiagnostics.Stage;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.network.ClientNetworkFailureLogLimiter;
import com.github.standobyte.jojo.network.ClientNetworkFailureLogLimiter.Decision;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId.AbilityInputNetwork;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.input.AbilityInput;
import com.github.standobyte.jojo.powersystem.ability.input.AbilityInput.InputEventType;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TrAbilityUsePacket implements CustomPacketPayload {
	private final int entityId;
	private final short key;
	private final long inputGeneration;
	private final InputEventType inputType;
	private final Ability abilityEncode;
	private final AbilityInputNetwork abilityDecoded;
	private final float timeTookToResolve;
	private final LivingEntity senderUser;
	@Nullable private final byte[] extraData;
	
	public static TrAbilityUsePacket keyPress(
			int entityId, short key, long inputGeneration,
			Ability ability, InputMethod inputMethod, float timeTookToResolve, LivingEntity serverUser) {
		InputEventType inputEvent = switch (inputMethod) {
			case CLICK -> InputEventType.PRESS_CLICK;
			case HOLD -> InputEventType.PRESS_HOLD;
		};
		return new TrAbilityUsePacket(
				entityId, key, inputGeneration, inputEvent, ability, null,
				timeTookToResolve, serverUser, null);
	}
	
	public static TrAbilityUsePacket releaseHold(
			int entityId, short key, long inputGeneration) {
		return new TrAbilityUsePacket(
				entityId, key, inputGeneration, InputEventType.RELEASE, null, null,
				0, null, null);
	}
	
	private TrAbilityUsePacket(int entityId, short key, long inputGeneration, InputEventType inputType,
			@Nullable Ability abilityEncode, @Nullable AbilityInputNetwork abilityDecoded, 
			float timeTookToResolve, @Nullable LivingEntity serverUser,
			@Nullable byte[] extraData) {
		this.entityId = entityId;
		this.key = key;
		this.inputGeneration = inputGeneration;
		this.inputType = inputType;
		this.abilityEncode = abilityEncode;
		this.abilityDecoded = abilityDecoded;
		this.timeTookToResolve = timeTookToResolve;
		this.senderUser = serverUser;
		this.extraData = extraData;
	}

	
	
	private static CustomPacketPayload.Type<TrAbilityUsePacket> type;
	
	public static class Handler implements PacketsRegister.PacketOGHandler<TrAbilityUsePacket> {
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrAbilityUsePacket> type() {
			return type;
		}

		@Override
		public void encode(TrAbilityUsePacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeShort(packet.key);
			buf.writeVarLong(
					NetworkPayloadValidation.requireOutboundGeneration(
							packet.inputGeneration, "ability replay"));
			buf.writeEnum(packet.inputType);
			if (packet.inputType != InputEventType.RELEASE) {
				AbilityInputNetwork.encodeInput(buf, packet.senderUser, packet.abilityEncode);
				buf.writeFloat(packet.timeTookToResolve);
				if (packet.abilityEncode != null) {
					FriendlyByteBuf extra = new FriendlyByteBuf(
							Unpooled.buffer(
									256,
									NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES));
					try {
						packet.abilityEncode.writeExtraInput(
								extra, packet.senderUser, false);
						int length = NetworkPayloadValidation
								.requireOutboundByteLength(
										extra.readableBytes(),
										NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES,
										"ability replay extra input");
						buf.writeBytes(extra, extra.readerIndex(), length);
					}
					finally {
						extra.release();
					}
				}	
			}
		}

		@Override
		public TrAbilityUsePacket decode(RegistryFriendlyByteBuf buf) {
			int entityId = buf.readInt();
			short key = buf.readShort();
			long inputGeneration = NetworkPayloadValidation.requireGeneration(
					buf.readVarLong(), "ability replay");
			InputEventType inputType = buf.readEnum(InputEventType.class);
			return switch (inputType) {
				case RELEASE -> TrAbilityUsePacket.releaseHold(
						entityId, key, inputGeneration);
				default -> {
					AbilityInputNetwork ability = AbilityInputNetwork.decodeInput(buf);
					float timeTookToResolve = buf.readFloat();
					
					byte[] extraData = NetworkUtil.extraPacketDataBytes(
							buf,
							NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES,
							"ability replay extra input");
					yield new TrAbilityUsePacket(
							entityId, key, inputGeneration,
							inputType, null, ability,
							timeTookToResolve, null, extraData);
				}
			};
		}

		@Override
		public void handle(TrAbilityUsePacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity user) {
				ConnectionType connectionType = context.listener() != null
						? context.listener().getConnectionType()
						: ConnectionType.NEOFORGE;
				RegistryFriendlyByteBuf extraInput = payload.extraData != null
						? new RegistryFriendlyByteBuf(
								Unpooled.wrappedBuffer(payload.extraData),
								user.level().registryAccess(), connectionType)
						: null;
				Ability resolvedAbility = null;
				try {
					switch (payload.inputType) {
						case PRESS_CLICK, PRESS_HOLD -> {
							resolvedAbility = resolveAbility(payload, user);
							if (resolvedAbility == null) {
								ClientAbilityNetworkDiagnostics.recordAbility(
										Stage.CLIENT_REPLAY_REJECTED,
										user,
										null,
										payload.key,
										payload.inputType.name(),
										readableBytes(extraInput),
										"ability_unresolved");
								return;
							}
							AbilityInput.keyPress(
									payload.key,
									payload.inputGeneration,
									resolvedAbility,
									user,
									extraInput,
									payload.inputType.inputMethod,
									payload.timeTookToResolve,
									BufferingState.clickOnly(),
									null);
							ClientAbilityNetworkDiagnostics.recordAbility(
									Stage.CLIENT_REPLAY_APPLIED,
									user,
									resolvedAbility,
									payload.key,
									payload.inputType.name(),
									readableBytes(extraInput),
									"replay_applied");
						}
						case RELEASE -> {
							AbilityInput.ReleaseResult release =
									AbilityInput.keyReleaseFromNetwork(
											payload.key, user,
											payload.inputGeneration);
							ClientAbilityNetworkDiagnostics.recordAbility(
									release == AbilityInput.ReleaseResult.STALE
											? Stage.CLIENT_RELEASE_REJECTED
											: Stage.CLIENT_RELEASE_APPLIED,
									user,
									null,
									payload.key,
									payload.inputType.name(),
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
					ClientAbilityNetworkDiagnostics.recordAbility(
							payload.inputType == InputEventType.RELEASE
									? Stage.CLIENT_RELEASE_REJECTED
									: Stage.CLIENT_REPLAY_REJECTED,
							user,
							resolvedAbility,
							payload.key,
							payload.inputType.name(),
							readableBytes(extraInput),
							error.getClass().getName());
					logReplayFailure(
							payload, resolvedAbility, "execution", true, error);
				}
				finally {
					if (extraInput != null) {
						extraInput.release();
					}
				}
			}
		}

		private static void logReplayFailure(
				TrAbilityUsePacket payload,
				@Nullable Ability ability,
				String failureScope,
				boolean errorLevel,
				RuntimeException error) {
			String abilityId = ability != null
					? ability.name() : "unresolved";
			Decision decision = ClientNetworkFailureLogLimiter.acquire(
					"ability_replay_" + failureScope,
					payload.entityId + "/" + abilityId,
					error.getClass());
			if (!decision.logStackTrace()) {
				return;
			}
			if (errorLevel) {
				if (decision.suppressedCount() > 0L) {
					JojoMod.getLogger().error(
							"Contained client ability replay {} failure for entity {} key {} input {}; {} similar failures were suppressed. The network handler remains active.",
							failureScope, payload.entityId, payload.key,
							payload.inputType, decision.suppressedCount(), error);
				}
				else {
					JojoMod.getLogger().error(
							"Contained client ability replay {} failure for entity {} key {} input {}; the network handler remains active.",
							failureScope, payload.entityId, payload.key,
							payload.inputType, error);
				}
			}
			else {
				if (decision.suppressedCount() > 0L) {
					JojoMod.getLogger().warn(
							"Skipping client ability replay {} for entity {} key {} input {}; {} similar failures were suppressed.",
							failureScope, payload.entityId, payload.key,
							payload.inputType, decision.suppressedCount(), error);
				}
				else {
					JojoMod.getLogger().warn(
							"Skipping client ability replay {} for entity {} key {} input {}.",
							failureScope, payload.entityId, payload.key,
							payload.inputType, error);
				}
			}
		}

		private static int readableBytes(
				@Nullable RegistryFriendlyByteBuf buffer) {
			return buffer != null ? buffer.readableBytes() : 0;
		}

		@Nullable
		private static Ability resolveAbility(TrAbilityUsePacket payload, LivingEntity user) {
			if (payload.abilityDecoded == null) {
				return null;
			}
			try {
				return payload.abilityDecoded.getAbility(user, ClientProxy.getClientWorld());
			}
			catch (RuntimeException e) {
				logReplayFailure(payload, null, "resolution", false, e);
				return null;
			}
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
	
}
