package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeformState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClGELifeformUiPacket(Action action, String lifeformId) implements CustomPacketPayload {
    private static CustomPacketPayload.Type<ClGELifeformUiPacket> type;

    public static ClGELifeformUiPacket addFavorite(String lifeformId) {
        return new ClGELifeformUiPacket(Action.ADD_FAVORITE, lifeformId);
    }

    public static ClGELifeformUiPacket removeFavorite(String lifeformId) {
        return new ClGELifeformUiPacket(Action.REMOVE_FAVORITE, lifeformId);
    }

    public static ClGELifeformUiPacket clearUnseen() {
        return new ClGELifeformUiPacket(Action.CLEAR_UNSEEN, "");
    }

    public static ClGELifeformUiPacket unlockAll() {
        return new ClGELifeformUiPacket(Action.UNLOCK_ALL, "");
    }

    public static class Handler implements PacketsRegister.PacketCodecHandler<ClGELifeformUiPacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<ClGELifeformUiPacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ClGELifeformUiPacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ClGELifeformUiPacket> STREAM_CODEC = StreamCodec.ofMember(
                ClGELifeformUiPacket::write,
                ClGELifeformUiPacket::new);

        @Override
        public void handle(ClGELifeformUiPacket payload, IPayloadContext context) {
            Player player = context.player();
            GoldExperienceLifeformState state = GoldExperienceLifeformState.get(player);
            switch (payload.action) {
                case ADD_FAVORITE -> state.addFavoriteLifeform(payload.lifeformId);
                case REMOVE_FAVORITE -> state.removeFavoriteLifeform(payload.lifeformId);
                case CLEAR_UNSEEN -> state.clearNewUnseenLifeforms();
                case UNLOCK_ALL -> {
                    if (player.getAbilities().instabuild) {
                        state.learnAllValidLifeforms(player.level());
                    }
                }
            }
        }
    }

    public ClGELifeformUiPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readEnum(Action.class), buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(lifeformId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }

    public enum Action {
        ADD_FAVORITE,
        REMOVE_FAVORITE,
        CLEAR_UNSEEN,
        UNLOCK_ALL
    }
}
