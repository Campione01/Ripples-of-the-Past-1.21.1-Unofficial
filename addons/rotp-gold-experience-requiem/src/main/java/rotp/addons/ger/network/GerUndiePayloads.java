package rotp.addons.ger.network;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerUndieClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 1.16 SyncUndiePullStateS2CPacket and UndieReviveS2CPacket. */
public final class GerUndiePayloads {
    public static final byte DYING = 1;
    public static final byte PULLING = 2;

    private GerUndiePayloads() {}

    public static void register(
            RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(
                StatePayload.TYPE,
                StatePayload.STREAM_CODEC,
                StatePayload::handle);
        registrar.playToClient(
                RevivePayload.TYPE,
                RevivePayload.STREAM_CODEC,
                RevivePayload::handle);
    }

    public record StatePayload(int entityId, byte phase)
            implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE =
                new Type<>(GoldExperienceRequiemAddon.id(
                        "undie_state"));
        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                StatePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public StatePayload decode(
                            RegistryFriendlyByteBuf buffer) {
                        return new StatePayload(
                                buffer.readVarInt(),
                                buffer.readByte());
                    }

                    @Override
                    public void encode(
                            RegistryFriendlyByteBuf buffer,
                            StatePayload payload) {
                        buffer.writeVarInt(payload.entityId());
                        buffer.writeByte(payload.phase());
                    }
                };

        private static void handle(
                StatePayload payload,
                IPayloadContext context) {
            GerUndieClientState.setState(
                    payload.entityId(), payload.phase());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RevivePayload(int entityId, float health)
            implements CustomPacketPayload {
        public static final Type<RevivePayload> TYPE =
                new Type<>(GoldExperienceRequiemAddon.id(
                        "undie_revive"));
        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                RevivePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public RevivePayload decode(
                            RegistryFriendlyByteBuf buffer) {
                        return new RevivePayload(
                                buffer.readVarInt(),
                                buffer.readFloat());
                    }

                    @Override
                    public void encode(
                            RegistryFriendlyByteBuf buffer,
                            RevivePayload payload) {
                        buffer.writeVarInt(payload.entityId());
                        buffer.writeFloat(payload.health());
                    }
                };

        private static void handle(
                RevivePayload payload,
                IPayloadContext context) {
            GerUndieClientState.revive(
                    payload.entityId(), payload.health());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
