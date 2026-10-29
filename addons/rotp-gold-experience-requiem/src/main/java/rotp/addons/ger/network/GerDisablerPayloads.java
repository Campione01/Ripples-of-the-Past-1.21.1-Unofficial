package rotp.addons.ger.network;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerDisablerClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 1.16 SyncDisablerStateS2CPacket, sent to the disabled player only. */
public final class GerDisablerPayloads {
    private GerDisablerPayloads() {}

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                StatePayload.TYPE,
                StatePayload.STREAM_CODEC,
                StatePayload::handle);
    }

    public record StatePayload(int entityId, boolean disabled)
            implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE =
                new Type<>(GoldExperienceRequiemAddon.id(
                        "disabler_state"));
        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                StatePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public StatePayload decode(
                            RegistryFriendlyByteBuf buffer) {
                        return new StatePayload(
                                buffer.readVarInt(),
                                buffer.readBoolean());
                    }

                    @Override
                    public void encode(
                            RegistryFriendlyByteBuf buffer,
                            StatePayload payload) {
                        buffer.writeVarInt(payload.entityId());
                        buffer.writeBoolean(payload.disabled());
                    }
                };

        private static void handle(
                StatePayload payload,
                IPayloadContext context) {
            GerDisablerClientState.setDisabled(
                    payload.entityId(), payload.disabled());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
