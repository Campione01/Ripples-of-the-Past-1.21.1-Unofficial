package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.DebugItem;
import com.github.standobyte.jojo.PacketsRegister;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClDebugCommandPacket implements CustomPacketPayload {
	private static final int MAX_COMMAND_LENGTH = 32;
	private final String command;
	private final int mouseButton;
	
	public ClDebugCommandPacket(String command, int mouseButton) {
		this.command = command;
		this.mouseButton = mouseButton;
	}
	
	
	
	private static CustomPacketPayload.Type<ClDebugCommandPacket> type;
	
	public static class Handler implements PacketsRegister.PacketOGHandler<ClDebugCommandPacket> {
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClDebugCommandPacket> type() {
			return type;
		}

		@Override
		public void encode(ClDebugCommandPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeUtf(packet.command, MAX_COMMAND_LENGTH);
			buf.writeVarInt(packet.mouseButton);
		}

		@Override
		public ClDebugCommandPacket decode(RegistryFriendlyByteBuf buf) {
			String command = buf.readUtf(MAX_COMMAND_LENGTH);
			int mouseButton = buf.readVarInt();
			return new ClDebugCommandPacket(command, mouseButton);
		}

		@Override
		public void handle(ClDebugCommandPacket payload, IPayloadContext context) {
			Player player = context.player();
			if (holdsDebugItem(player.getMainHandItem(), player.getOffhandItem())) {
				DebugItem.handleServer(payload.command, player, payload.mouseButton);
			}
		}

		static boolean holdsDebugItem(ItemStack mainHand, ItemStack offHand) {
			return holdsDebugItemTypes(
					mainHand.getItem().getClass(), offHand.getItem().getClass());
		}

		static boolean holdsDebugItemTypes(Class<?> mainHandType, Class<?> offHandType) {
			return DebugItem.class.isAssignableFrom(mainHandType)
					|| DebugItem.class.isAssignableFrom(offHandType);
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
	
}
