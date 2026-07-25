package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientTimeStopHandler;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrTimeStopPlayerStatePacket(boolean canSee, boolean canMove) implements CustomPacketPayload {
    private static CustomPacketPayload.Type<TrTimeStopPlayerStatePacket> type;

    public TrTimeStopPlayerStatePacket {
        canMove = canSee && canMove;
    }

    public static class Handler implements PacketsRegister.PacketCodecHandler<TrTimeStopPlayerStatePacket> {

        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<TrTimeStopPlayerStatePacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, TrTimeStopPlayerStatePacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, TrTimeStopPlayerStatePacket> STREAM_CODEC = StreamCodec.ofMember(
                TrTimeStopPlayerStatePacket::write, TrTimeStopPlayerStatePacket::new);

        @Override
        public void handle(TrTimeStopPlayerStatePacket payload, IPayloadContext context) {
            ClientTimeStopHandler.setTimeStopClientState(payload.canSee, payload.canMove);
        }
    }

    public TrTimeStopPlayerStatePacket(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(canSee);
        buf.writeBoolean(canMove);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
