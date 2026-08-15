package com.github.standobyte.jojo.subsystems.entity_useitem;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.ability.input.InputKeyId;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState;
import com.github.standobyte.jojo.powersystem.entityaction.HeldInput;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.SyncType;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClStandClickPacket(
		HitResultSync target,
		long inputGeneration,
		InteractionHand... hand) implements CustomPacketPayload {
	private static final int MAX_HAND_COUNT = InteractionHand.values().length;
	private static CustomPacketPayload.Type<ClStandClickPacket> type;

	public ClStandClickPacket {
		NetworkPayloadValidation.requireOutboundGeneration(
				inputGeneration, "Stand item input");
	}
	
	public ClStandClickPacket(
			HitResult target,
			long inputGeneration,
			InteractionHand... hand) {
		this(new HitResultSync(target), inputGeneration, hand);
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<ClStandClickPacket> {

		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClStandClickPacket> type() {
			return type;
		}

		@Override
		public void encode(ClStandClickPacket packet, RegistryFriendlyByteBuf buf) {
			encodeBody(packet, buf);
		}
		
		@Override
		public ClStandClickPacket decode(RegistryFriendlyByteBuf buf) {
			return decodeBody(buf);
		}

		@Override
		public void handle(ClStandClickPacket packet, IPayloadContext context) {
			if (!(context.player() instanceof ServerPlayer player)) {
				return;
			}
			StandEntity standEntity = StandUtil.getSummonedStand(player);
			if (!isEligible(
					player.isAlive(),
					player.isSpectator(),
					standEntity != null && standEntity.isAlive(),
					standEntity != null && standEntity.isManuallyControlled(),
					ServerSideLivingClick.isEntityHoldingAnItem(standEntity))) {
				return;
			}
			EntityActionInputState inputHandler = player.getData(
					ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			short internalKeyId = InputKeyId.STAND_ITEM_RMB;
			if (inputHandler == null
					|| hasActiveInputConflict(
							standEntity.isUsingItem(),
							inputHandler.heldKeys.containsKey(internalKeyId))
					|| !inputHandler.acceptNetworkPressGeneration(
							internalKeyId, packet.inputGeneration)) {
				return;
			}

			boolean wasUsingItem = standEntity.isUsingItem();
			HitResult target = packet.target().resolveEntity(player.level());
			ServerSideLivingClick.rightClick(
					standEntity, player, target, packet.hand());

			if (!wasUsingItem && standEntity.isUsingItem()) {
				HeldInput action = LivingComponentAction.getComponent(standEntity).setAction(
						new VanillaItemUseAsAction.ItemUsingInstance(), SyncType.TRACKING_AND_SELF);
				if (action != null) {
					HeldInputEntry heldInput = new HeldInputEntry(
							internalKeyId,
							packet.inputGeneration,
							PowerClass.STAND,
							action);
					inputHandler.heldKeys.put(internalKeyId, heldInput);
				}
				else {
					standEntity.stopUsingItem();
				}
			}
		}

		@ApiStatus.Internal
		static boolean isEligible(
				boolean playerAlive,
				boolean playerSpectator,
				boolean standAlive,
				boolean manuallyControlled,
				boolean holdingItem) {
			return playerAlive
					&& !playerSpectator
					&& standAlive
					&& (manuallyControlled || holdingItem);
		}

		@ApiStatus.Internal
		static boolean hasActiveInputConflict(
				boolean standUsingItem,
				boolean rmbSlotOccupied) {
			return standUsingItem || rmbSlotOccupied;
		}
		
	}

	@ApiStatus.Internal
	static void encodeBody(
			ClStandClickPacket packet, FriendlyByteBuf buf) {
		HitResultSync.STREAM_CODEC.encode(buf, packet.target);
		buf.writeVarLong(
				NetworkPayloadValidation.requireOutboundGeneration(
						packet.inputGeneration, "Stand item input"));
		int handCount = NetworkPayloadValidation.requireOutboundCollectionSize(
				packet.hand.length, MAX_HAND_COUNT, "interaction hand");
		buf.writeVarInt(handCount);
		for (InteractionHand hand : packet.hand) {
			buf.writeEnum(hand);
		}
	}

	@ApiStatus.Internal
	static ClStandClickPacket decodeBody(FriendlyByteBuf buf) {
		HitResultSync target = HitResultSync.STREAM_CODEC.decode(buf);
		long inputGeneration = NetworkPayloadValidation.requireGeneration(
				buf.readVarLong(), "Stand item input");
		int handCount = NetworkPayloadValidation.requireCollectionSize(
				buf.readVarInt(), MAX_HAND_COUNT, "interaction hand");
		InteractionHand[] hand = new InteractionHand[handCount];
		for (int i = 0; i < hand.length; i++) {
			hand[i] = buf.readEnum(InteractionHand.class);
		}
		return new ClStandClickPacket(target, inputGeneration, hand);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
	
}
