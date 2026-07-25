package com.github.standobyte.jojo.network.s2c;

import java.util.List;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeformState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrGELifeformStatePacket(int entityId, String selectedLifeformId, List<String> metLifeformIds,
        List<String> favoriteLifeformIds, List<String> newUnseenLifeformIds) implements CustomPacketPayload {
    private static CustomPacketPayload.Type<TrGELifeformStatePacket> type;

    public static class Handler implements PacketsRegister.PacketCodecHandler<TrGELifeformStatePacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<TrGELifeformStatePacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, TrGELifeformStatePacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, TrGELifeformStatePacket> STREAM_CODEC = StreamCodec.ofMember(
                TrGELifeformStatePacket::write,
                TrGELifeformStatePacket::new);

        @Override
        public void handle(TrGELifeformStatePacket payload, IPayloadContext context) {
            Entity entity = ClientProxy.getEntityById(payload.entityId);
            if (entity instanceof LivingEntity living) {
                GoldExperienceLifeformState state = GoldExperienceLifeformState.get(living);
                state.setMetLifeformsFromSync(payload.metLifeformIds);
                state.setFavoriteLifeformsFromSync(payload.favoriteLifeformIds);
                state.setNewUnseenLifeformsFromSync(payload.newUnseenLifeformIds);
                state.setSelectedFromSync(payload.selectedLifeformId);
            }
        }
    }

    public TrGELifeformStatePacket(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readUtf(),
                NetworkUtil.readCollection(buf, FriendlyByteBuf::readUtf),
                NetworkUtil.readCollection(buf, FriendlyByteBuf::readUtf),
                NetworkUtil.readCollection(buf, FriendlyByteBuf::readUtf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(selectedLifeformId);
        NetworkUtil.writeCollection(buf, metLifeformIds, FriendlyByteBuf::writeUtf);
        NetworkUtil.writeCollection(buf, favoriteLifeformIds, FriendlyByteBuf::writeUtf);
        NetworkUtil.writeCollection(buf, newUnseenLifeformIds, FriendlyByteBuf::writeUtf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
