package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeformState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClGEMetLifeformPacket(int entityId) implements CustomPacketPayload {
    private static CustomPacketPayload.Type<ClGEMetLifeformPacket> type;
    private static final double MAX_DISTANCE_SQR = 144.0D;

    public static class Handler implements PacketsRegister.PacketCodecHandler<ClGEMetLifeformPacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<ClGEMetLifeformPacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ClGEMetLifeformPacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ClGEMetLifeformPacket> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, ClGEMetLifeformPacket::entityId,
                ClGEMetLifeformPacket::new);

        @Override
        public void handle(ClGEMetLifeformPacket payload, IPayloadContext context) {
            Player player = context.player();
            Entity entity = player.level().getEntity(payload.entityId);
            if (entity != null && entity.distanceToSqr(player) <= MAX_DISTANCE_SQR) {
                GoldExperienceLifeformState.get(player).learnLifeformsForEntity(entity, player.level());
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
