package rotp.addons.mandom.network;

import rotp.addons.mandom.MandomAddon;
import rotp.addons.mandom.client.MandomRewindPostEffect;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RewindEffectPayload(
        int ticks, int route) implements CustomPacketPayload {
    public static final int DEFAULT_ROUTE = 0;
    public static final int ACID_ROUTE = 1;
    public static final int GRAY_ROUTE = 2;
    public static final int PURPLE_ROUTE = 3;

    public static final Type<RewindEffectPayload> TYPE =
            new Type<>(MandomAddon.id("rewind_effect"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RewindEffectPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RewindEffectPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new RewindEffectPayload(
                            buffer.readVarInt(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        RewindEffectPayload payload) {
                    buffer.writeVarInt(payload.ticks());
                    buffer.writeVarInt(payload.route());
                }
            };

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                TYPE, STREAM_CODEC, RewindEffectPayload::handle);
    }

    private static void handle(
            RewindEffectPayload payload,
            IPayloadContext context) {
        MandomRewindPostEffect.trigger(
                payload.ticks(), payload.route());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
