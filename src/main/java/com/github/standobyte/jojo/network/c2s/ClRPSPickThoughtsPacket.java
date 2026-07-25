package com.github.standobyte.jojo.network.c2s;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.ServerSavedData;
import com.github.standobyte.jojo.network.s2c.RPSOpponentPickThoughtsPacket;
import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsGame;
import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsGame.Pick;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClRPSPickThoughtsPacket(@Nullable Pick pickThoughts) implements CustomPacketPayload {
    private static CustomPacketPayload.Type<ClRPSPickThoughtsPacket> type;

    public static class Handler implements PacketsRegister.PacketCodecHandler<ClRPSPickThoughtsPacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<ClRPSPickThoughtsPacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ClRPSPickThoughtsPacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ClRPSPickThoughtsPacket> STREAM_CODEC = StreamCodec.ofMember(
                ClRPSPickThoughtsPacket::write, ClRPSPickThoughtsPacket::new);

        @Override
        public void handle(ClRPSPickThoughtsPacket payload, IPayloadContext context) {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            ServerSavedData data = ServerSavedData.get(serverPlayer.getServer());
            RockPaperScissorsGame game = data.rpsPvpGames.get(serverPlayer.getUUID());
            if (game == null || game.opponentIsNpc() || !game.opponentCanReadThoughts()) {
                return;
            }
            ServerPlayer opponentPlayer = serverPlayer.getServer().getPlayerList().getPlayer(game.opponent());
            if (opponentPlayer == null) {
                return;
            }
            RockPaperScissorsGame opponentGame = data.rpsPvpGames.get(opponentPlayer.getUUID());
            if (opponentGame == null) {
                return;
            }
            opponentGame.setOpponentThoughts(payload.pickThoughts());
            PacketDistributor.sendToPlayer(opponentPlayer,
                    new RPSOpponentPickThoughtsPacket(payload.pickThoughts() != null, payload.pickThoughts()));
            data.setDirty();
        }
    }

    public ClRPSPickThoughtsPacket(RegistryFriendlyByteBuf buf) {
        this(readNullablePick(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        writeNullablePick(buf, pickThoughts);
    }

    private static void writeNullablePick(RegistryFriendlyByteBuf buf, @Nullable Pick pick) {
        buf.writeBoolean(pick != null);
        if (pick != null) {
            buf.writeEnum(pick);
        }
    }

    @Nullable
    private static Pick readNullablePick(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readEnum(Pick.class) : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
