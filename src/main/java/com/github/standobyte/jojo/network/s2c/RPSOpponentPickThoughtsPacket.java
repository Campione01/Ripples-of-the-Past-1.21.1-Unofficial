package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.RockPaperScissorsScreen;
import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsGame;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RPSOpponentPickThoughtsPacket(boolean visible, int pickOrdinalPlusOne) implements CustomPacketPayload {
    private static CustomPacketPayload.Type<RPSOpponentPickThoughtsPacket> type;

    public RPSOpponentPickThoughtsPacket(boolean visible) {
        this(visible, 0);
    }

    public RPSOpponentPickThoughtsPacket(boolean visible, RockPaperScissorsGame.Pick pick) {
        this(visible, pick == null ? 0 : pick.ordinal() + 1);
    }

    public RockPaperScissorsGame.Pick pick() {
        if (pickOrdinalPlusOne <= 0) {
            return null;
        }
        return RockPaperScissorsGame.Pick.values()[pickOrdinalPlusOne - 1];
    }

    public static class Handler implements PacketsRegister.PacketCodecHandler<RPSOpponentPickThoughtsPacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<RPSOpponentPickThoughtsPacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, RPSOpponentPickThoughtsPacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, RPSOpponentPickThoughtsPacket> STREAM_CODEC = StreamCodec.ofMember(
                RPSOpponentPickThoughtsPacket::write, RPSOpponentPickThoughtsPacket::new);

        @Override
        public void handle(RPSOpponentPickThoughtsPacket payload, IPayloadContext context) {
            RockPaperScissorsScreen.applyOpponentThoughts(payload.visible(), payload.pick());
        }
    }

    public RPSOpponentPickThoughtsPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readVarInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(visible);
        buf.writeVarInt(pickOrdinalPlusOne);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
