package rotp.addons.heavensdoor.network;

import java.util.UUID;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.state.BookCommandSessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BookCommandIntentPayload(
        UUID sessionNonce,
        BookCommand command,
        int playerOptionId) implements CustomPacketPayload {
    public static final Type<BookCommandIntentPayload> TYPE =
            new Type<>(HeavensDoorAddon.id("book_command_intent"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            BookCommandIntentPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BookCommandIntentPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    UUID nonce = buffer.readUUID();
                    BookCommand command = BookCommand
                            .fromNetworkId(buffer.readVarInt())
                            .orElse(BookCommand.INVALID);
                    return new BookCommandIntentPayload(
                            nonce,
                            command,
                            buffer.readVarInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        BookCommandIntentPayload payload) {
                    buffer.writeUUID(payload.sessionNonce());
                    buffer.writeVarInt(payload.command().networkId());
                    buffer.writeVarInt(payload.playerOptionId());
                }
            };

    public BookCommandIntentPayload(
            UUID sessionNonce, BookCommand command) {
        this(
                sessionNonce,
                command,
                BookCommandSessionService.NO_PLAYER_OPTION);
    }

    public static void handle(
            BookCommandIntentPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BookCommandSessionService.consume(
                    player,
                    payload.sessionNonce(),
                    payload.command(),
                    payload.playerOptionId());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
