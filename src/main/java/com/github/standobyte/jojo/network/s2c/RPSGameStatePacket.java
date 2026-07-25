package com.github.standobyte.jojo.network.s2c;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.RockPaperScissorsScreen;
import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsGame.Pick;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RPSGameStatePacket implements CustomPacketPayload {
    private static CustomPacketPayload.Type<RPSGameStatePacket> type;

    private final PacketType packetType;
    private final List<Pick> playerPicks;
    private final List<Pick> opponentPicks;
    private final int opponentId;
    private final boolean playerWon;
    @Nullable private final Pick pick;
    private final boolean opponentPick;
    private final int round;

    public static RPSGameStatePacket enteredGame(int opponentId, List<Pick> playerPicks, List<Pick> opponentPicks, int round) {
        return new RPSGameStatePacket(PacketType.ENTER, playerPicks, opponentPicks, opponentId, false, null, false, round);
    }

    public static RPSGameStatePacket stateUpdated(List<Pick> playerPicks, List<Pick> opponentPicks, int round) {
        return new RPSGameStatePacket(PacketType.UPDATE, playerPicks, opponentPicks, -1, false, null, false, round);
    }

    public static RPSGameStatePacket leftGame() {
        return new RPSGameStatePacket(PacketType.LEAVE, List.of(), List.of(), -1, false, null, false, 0);
    }

    public static RPSGameStatePacket gameOver(boolean playerWon) {
        return new RPSGameStatePacket(PacketType.GAME_OVER, List.of(), List.of(), -1, playerWon, null, false, 0);
    }

    public static RPSGameStatePacket setOpponentPick(@Nullable Pick pick, int opponentId) {
        return new RPSGameStatePacket(PacketType.SET_PICK, List.of(), List.of(), opponentId, false, pick, true, 0);
    }

    public static RPSGameStatePacket setOwnPick(@Nullable Pick pick) {
        return new RPSGameStatePacket(PacketType.SET_PICK, List.of(), List.of(), -1, false, pick, false, 0);
    }

    public static RPSGameStatePacket mindRead(int opponentId) {
        return new RPSGameStatePacket(PacketType.MIND_READ, List.of(), List.of(), opponentId, false, null, false, 0);
    }

    private RPSGameStatePacket(PacketType packetType, List<Pick> playerPicks, List<Pick> opponentPicks,
            int opponentId, boolean playerWon, @Nullable Pick pick, boolean opponentPick, int round) {
        this.packetType = packetType;
        this.playerPicks = List.copyOf(playerPicks);
        this.opponentPicks = List.copyOf(opponentPicks);
        this.opponentId = opponentId;
        this.playerWon = playerWon;
        this.pick = pick;
        this.opponentPick = opponentPick;
        this.round = round;
    }

    public static class Handler implements PacketsRegister.PacketCodecHandler<RPSGameStatePacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<RPSGameStatePacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, RPSGameStatePacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, RPSGameStatePacket> STREAM_CODEC = StreamCodec.ofMember(
                RPSGameStatePacket::write, RPSGameStatePacket::new);

        @Override
        public void handle(RPSGameStatePacket payload, IPayloadContext context) {
            switch (payload.packetType) {
                case ENTER -> RockPaperScissorsScreen.open(payload.opponentId, payload.playerPicks, payload.opponentPicks, payload.round);
                case UPDATE -> RockPaperScissorsScreen.updateState(payload.playerPicks, payload.opponentPicks, payload.round);
                case LEAVE -> RockPaperScissorsScreen.closeFromServer();
                case GAME_OVER -> RockPaperScissorsScreen.gameOver(payload.playerWon);
                case SET_PICK -> RockPaperScissorsScreen.setPick(payload.opponentPick, payload.pick);
                case MIND_READ -> RockPaperScissorsScreen.setMindRead(payload.opponentId);
            }
        }
    }

    public RPSGameStatePacket(RegistryFriendlyByteBuf buf) {
        this.packetType = buf.readEnum(PacketType.class);
        switch (packetType) {
            case ENTER -> {
                this.playerPicks = readPickList(buf);
                this.opponentPicks = readPickList(buf);
                this.opponentId = buf.readInt();
                this.round = buf.readVarInt();
                this.playerWon = false;
                this.pick = null;
                this.opponentPick = false;
            }
            case UPDATE -> {
                this.playerPicks = readPickList(buf);
                this.opponentPicks = readPickList(buf);
                this.round = buf.readVarInt();
                this.opponentId = -1;
                this.playerWon = false;
                this.pick = null;
                this.opponentPick = false;
            }
            case LEAVE -> {
                this.playerPicks = List.of();
                this.opponentPicks = List.of();
                this.opponentId = -1;
                this.playerWon = false;
                this.pick = null;
                this.opponentPick = false;
                this.round = 0;
            }
            case GAME_OVER -> {
                this.playerPicks = List.of();
                this.opponentPicks = List.of();
                this.opponentId = -1;
                this.playerWon = buf.readBoolean();
                this.pick = null;
                this.opponentPick = false;
                this.round = 0;
            }
            case SET_PICK -> {
                this.playerPicks = List.of();
                this.opponentPicks = List.of();
                this.opponentPick = buf.readBoolean();
                this.pick = readNullablePick(buf);
                this.opponentId = this.opponentPick ? buf.readInt() : -1;
                this.playerWon = false;
                this.round = 0;
            }
            case MIND_READ -> {
                this.playerPicks = List.of();
                this.opponentPicks = List.of();
                this.opponentId = buf.readInt();
                this.playerWon = false;
                this.pick = null;
                this.opponentPick = false;
                this.round = 0;
            }
            default -> throw new IllegalStateException("Unexpected RPS packet type " + packetType);
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(packetType);
        switch (packetType) {
            case ENTER -> {
                writePickList(buf, playerPicks);
                writePickList(buf, opponentPicks);
                buf.writeInt(opponentId);
                buf.writeVarInt(round);
            }
            case UPDATE -> {
                writePickList(buf, playerPicks);
                writePickList(buf, opponentPicks);
                buf.writeVarInt(round);
            }
            case LEAVE -> {}
            case GAME_OVER -> buf.writeBoolean(playerWon);
            case SET_PICK -> {
                buf.writeBoolean(opponentPick);
                writeNullablePick(buf, pick);
                if (opponentPick) {
                    buf.writeInt(opponentId);
                }
            }
            case MIND_READ -> buf.writeInt(opponentId);
        }
    }

    private static void writePickList(RegistryFriendlyByteBuf buf, List<Pick> picks) {
        buf.writeVarInt(picks.size());
        for (Pick pick : picks) {
            buf.writeEnum(pick);
        }
    }

    private static List<Pick> readPickList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Pick> picks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            picks.add(buf.readEnum(Pick.class));
        }
        return picks;
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

    private enum PacketType {
        ENTER,
        UPDATE,
        LEAVE,
        GAME_OVER,
        SET_PICK,
        MIND_READ
    }
}
