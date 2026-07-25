package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojoimpl.stands.goldexperience.client.ControllerConsciousness;
import com.github.standobyte.v1_21_4_stuff.missingmethods._Vec3;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrGESplitConsciousnessPacket(Vec3 deltaMovement) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrGESplitConsciousnessPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrGESplitConsciousnessPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrGESplitConsciousnessPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrGESplitConsciousnessPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrGESplitConsciousnessPacket> STREAM_CODEC = StreamCodec.composite(
				_Vec3.STREAM_CODEC, TrGESplitConsciousnessPacket::deltaMovement,
				TrGESplitConsciousnessPacket::new);

		@Override
		public void handle(TrGESplitConsciousnessPacket payload, IPayloadContext context) {
			ControllerConsciousness.onSplitPacket(payload.deltaMovement());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}
