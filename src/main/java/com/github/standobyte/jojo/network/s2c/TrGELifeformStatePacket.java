package com.github.standobyte.jojo.network.s2c;

import java.util.List;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
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
    private static final int MAX_LIFEFORM_IDS = 4096;
    private static final int MAX_LIFEFORM_ID_LENGTH = 256;
    private static CustomPacketPayload.Type<TrGELifeformStatePacket> type;

    public TrGELifeformStatePacket {
        selectedLifeformId = NetworkPayloadValidation.requireUtfLength(
                selectedLifeformId, MAX_LIFEFORM_ID_LENGTH, "selected lifeform id");
        metLifeformIds = boundedLifeformIds(metLifeformIds, "met lifeform");
        favoriteLifeformIds = boundedLifeformIds(favoriteLifeformIds, "favorite lifeform");
        newUnseenLifeformIds = boundedLifeformIds(newUnseenLifeformIds, "new lifeform");
    }

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
        this(buf.readInt(), buf.readUtf(MAX_LIFEFORM_ID_LENGTH),
                readLifeformIds(buf), readLifeformIds(buf), readLifeformIds(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(selectedLifeformId, MAX_LIFEFORM_ID_LENGTH);
        writeLifeformIds(buf, metLifeformIds);
        writeLifeformIds(buf, favoriteLifeformIds);
        writeLifeformIds(buf, newUnseenLifeformIds);
    }

    private static List<String> boundedLifeformIds(List<String> ids, String description) {
        NetworkPayloadValidation.requireOutboundCollectionSize(
                ids.size(), MAX_LIFEFORM_IDS, description);
        ids.forEach(id -> NetworkPayloadValidation.requireUtfLength(
                id, MAX_LIFEFORM_ID_LENGTH, description + " id"));
        return List.copyOf(ids);
    }

    private static List<String> readLifeformIds(FriendlyByteBuf buf) {
        return NetworkUtil.readCollection(
                buf, in -> in.readUtf(MAX_LIFEFORM_ID_LENGTH), MAX_LIFEFORM_IDS);
    }

    private static void writeLifeformIds(
            FriendlyByteBuf buf, List<String> lifeformIds) {
        NetworkUtil.writeCollection(buf, lifeformIds,
                (out, id) -> out.writeUtf(id, MAX_LIFEFORM_ID_LENGTH),
                MAX_LIFEFORM_IDS);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
