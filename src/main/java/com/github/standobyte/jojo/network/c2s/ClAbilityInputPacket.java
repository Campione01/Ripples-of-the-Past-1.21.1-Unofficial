package com.github.standobyte.jojo.network.c2s;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.core.JojoMod;
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
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClAbilityInputPacket implements CustomPacketPayload {
	private final short key;
	private final InputEventType inputEvent;
	
	private final Ability baseAbilityEncode;
	private final Ability activeAbilityEncode;
	private final AbilityInputNetwork baseAbilityDecoded;
	private final AbilityInputNetwork activeAbilityDecoded;
	private final float timeTookToResolve;
	private final ActionTarget playerAimTarget;
	private final ActionTarget standAimTarget;

	private final LivingEntity clUser;
	private FriendlyByteBuf extraData;
	
	public static ClAbilityInputPacket keyPress(short key, LivingEntity user, 
			Ability baseAbility, InputEventType inputEvent, float timeTookToResolve) {
		return keyPress(key, user, baseAbility, baseAbility, inputEvent, timeTookToResolve);
	}
	
	public static ClAbilityInputPacket keyPress(short key, LivingEntity user, 
			Ability baseAbility, Ability activeAbility, InputEventType inputEvent, float timeTookToResolve) {
		return keyPress(key, user, baseAbility, activeAbility, inputEvent, timeTookToResolve, ActionTarget.EMPTY, ActionTarget.EMPTY);
	}
	
	public static ClAbilityInputPacket keyPress(short key, LivingEntity user, 
			Ability baseAbility, Ability activeAbility, InputEventType inputEvent, float timeTookToResolve, 
			ActionTarget playerAimTarget, ActionTarget standAimTarget) {
		return new ClAbilityInputPacket(key, inputEvent, user, baseAbility, activeAbility, null, null, timeTookToResolve, 
				playerAimTarget, standAimTarget);
	}
	
	public static ClAbilityInputPacket releaseHold(short key) {
		return new ClAbilityInputPacket(key, InputEventType.RELEASE, null, null, null, null, null, 0, 
				ActionTarget.EMPTY, ActionTarget.EMPTY);
	}
	
	private ClAbilityInputPacket(short key, InputEventType inputEvent, LivingEntity user, 
			@Nullable Ability baseAbilityEncode, @Nullable Ability activeAbilityEncode, 
			@Nullable AbilityInputNetwork baseAbilityDecoded, @Nullable AbilityInputNetwork activeAbilityDecoded, 
			float timeTookToResolve, @Nullable ActionTarget playerAimTarget, @Nullable ActionTarget standAimTarget) {
		this.key = key;
		this.inputEvent = inputEvent;
		this.clUser = user;
		this.baseAbilityEncode = baseAbilityEncode;
		this.activeAbilityEncode = activeAbilityEncode;
		this.baseAbilityDecoded = baseAbilityDecoded;
		this.activeAbilityDecoded = activeAbilityDecoded;
		this.timeTookToResolve = timeTookToResolve;
		this.playerAimTarget = copyTargetOrEmpty(playerAimTarget);
		this.standAimTarget = copyTargetOrEmpty(standAimTarget);
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
			buf.writeEnum(packet.inputEvent);
			if (packet.inputEvent != InputEventType.RELEASE) {
				Ability activeAbility = packet.activeAbilityEncode != null ? packet.activeAbilityEncode : packet.baseAbilityEncode;
				AbilityInputNetwork.encodeInput(buf, packet.clUser, packet.baseAbilityEncode);
				AbilityInputNetwork.encodeInput(buf, packet.clUser, activeAbility);
				buf.writeFloat(packet.timeTookToResolve);
				ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(buf, packet.playerAimTarget);
				ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(buf, packet.standAimTarget);
				if (activeAbility != null) {
					activeAbility.writeExtraInput(buf, packet.clUser, true);
				}	
			}
		}

		@Override
		public ClAbilityInputPacket decode(RegistryFriendlyByteBuf buf) {
			short key = buf.readShort();
			InputEventType inputEvent = buf.readEnum(InputEventType.class);
			return switch (inputEvent) {
				case RELEASE -> ClAbilityInputPacket.releaseHold(key);
				default -> {
					AbilityInputNetwork baseAbility = AbilityInputNetwork.decodeInput(buf);
					AbilityInputNetwork activeAbility = AbilityInputNetwork.decodeInput(buf);
					float timeTookToResolve = buf.readFloat();
					ActionTarget playerAimTarget = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(buf);
					ActionTarget standAimTarget = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(buf);
					
					ClAbilityInputPacket packet = new ClAbilityInputPacket(key, inputEvent, null, null, null, 
							baseAbility, activeAbility, timeTookToResolve, playerAimTarget, standAimTarget);
					packet.extraData = NetworkUtil.extraPacketData(buf);
					yield packet;
				}
			};
		}

		@Override
		public void handle(ClAbilityInputPacket payload, IPayloadContext context) {
			Player player = context.player();
			switch (payload.inputEvent) {
				case PRESS_CLICK, PRESS_HOLD -> {
					if (!canPlayerUseAbilityInput(player)) {
						return;
					}
					applyInputTargets(payload, player);
					Ability baseAbility = resolveAbility(payload.baseAbilityDecoded, payload, player, "base");
					Ability requestedActiveAbility = resolveAbility(payload.activeAbilityDecoded, payload, player, "active");
					if (baseAbility == null || requestedActiveAbility == null) {
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
					AbilityConditionCheck ability = resolveServerInputAbility(payload, player, power, senderAbility, requestedActiveAbility);
					if (ability == null) {
						sendNotUnlockedFeedback(player);
						return;
					}
					if (AbilityInput.withConditionCheck(ability, player, payload.inputEvent.inputMethod)) {
						AbilityInput.keyPress(payload.key, ability.ability, player, payload.extraData, 
								payload.inputEvent.inputMethod, payload.timeTookToResolve, BufferingState.clickCanBuffer(), senderAbility.abilityId);
					}
				}
				case RELEASE -> AbilityInput.keyRelease(payload.key, player);
			}
		}

		private static boolean canPlayerUseAbilityInput(Player player) {
			return player != null && player.isAlive()
					&& JojoModUtil.getGameModeConsiderPossessing(player) != GameType.SPECTATOR;
		}

		private static void sendNotUnlockedFeedback(Player player) {
			ConditionCheck.sendActionFailedMessage(null, ConditionCheck.createNegative("not_unlocked"), player);
		}

		private static void applyInputTargets(ClAbilityInputPacket payload, Player player) {
			ActionTarget playerTarget = payload.playerAimTarget.resolveEntityId(player.level());
			LivingComponentAction playerAction = LivingComponentAction.getComponent(player);
			playerAction.entityAim.setTarget(playerTarget);
			
			StandEntity stand = StandUtil.getSummonedStand(player);
			if (stand != null) {
				ActionTarget standTarget = payload.standAimTarget.resolveEntityId(player.level());
				if (standTarget.getType() == ActionTarget.TargetType.EMPTY && playerTarget.getType() != ActionTarget.TargetType.EMPTY) {
					standTarget = playerTarget;
				}
				LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
				standAction.entityAim.setTarget(standTarget);
				if (standAction.entityAim.checkDirty()) {
					PacketDistributor.sendToPlayersTrackingEntityAndSelf(stand, new TrAimTargetPacket(stand.getId(), standTarget));
				}
			}
		}

		@Nullable
		private static Ability resolveAbility(@Nullable AbilityInputNetwork abilityNetwork, ClAbilityInputPacket payload, Player player, String abilityRole) {
			if (abilityNetwork == null) {
				return null;
			}
			try {
				return abilityNetwork.getAbility(player, null);
			}
			catch (RuntimeException e) {
				JojoMod.getLogger().warn("Ignoring client {} ability input from {} key {} input {}.", 
						abilityRole, player.getName().getString(), payload.key, payload.inputEvent, e);
				return null;
			}
		}

		@Nullable
		private static AbilityConditionCheck resolveServerInputAbility(ClAbilityInputPacket payload, Player player, Power<?> power,
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

		private static Ability getSenderMovesetAbility(Player player, Ability baseAbility) {
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
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
	
}
