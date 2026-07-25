package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeformState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClGELifeformButtonPacket(String selectedLifeformId) implements CustomPacketPayload {
    private static CustomPacketPayload.Type<ClGELifeformButtonPacket> type;

    public static class Handler implements PacketsRegister.PacketCodecHandler<ClGELifeformButtonPacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<ClGELifeformButtonPacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ClGELifeformButtonPacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ClGELifeformButtonPacket> STREAM_CODEC = StreamCodec.ofMember(
                ClGELifeformButtonPacket::write,
                ClGELifeformButtonPacket::new);

        @Override
        public void handle(ClGELifeformButtonPacket payload, IPayloadContext context) {
            Player player = context.player();
            GoldExperienceLifeformState.get(player).setSelected(payload.selectedLifeformId());
        }
    }

    public ClGELifeformButtonPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(selectedLifeformId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
