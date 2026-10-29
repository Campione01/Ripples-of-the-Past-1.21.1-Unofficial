package rotp.addons.stickyfingers.network;

import java.util.UUID;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.client.StickyFingersBodyPresentation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DetachedBodyPresentationPayload(
        UUID targetUuid,
        int partMask) implements CustomPacketPayload {
    public static final Type<DetachedBodyPresentationPayload> TYPE =
            new Type<>(StickyFingersAddon.id(
                    "detached_body_presentation"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            DetachedBodyPresentationPayload> STREAM_CODEC =
                    StreamCodec.ofMember(
                            DetachedBodyPresentationPayload::encode,
                            DetachedBodyPresentationPayload::decode);

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                TYPE, STREAM_CODEC,
                DetachedBodyPresentationPayload::handle);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(targetUuid);
        buffer.writeVarInt(partMask);
    }

    private static DetachedBodyPresentationPayload decode(
            RegistryFriendlyByteBuf buffer) {
        return new DetachedBodyPresentationPayload(
                buffer.readUUID(), buffer.readVarInt());
    }

    private static void handle(
            DetachedBodyPresentationPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() ->
                StickyFingersBodyPresentation.applySnapshot(
                        payload.targetUuid(),
                        payload.partMask()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
