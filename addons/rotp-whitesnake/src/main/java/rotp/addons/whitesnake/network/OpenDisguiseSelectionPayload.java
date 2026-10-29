package rotp.addons.whitesnake.network;

import java.util.Objects;
import java.util.UUID;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disguise.DisguiseClientBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenDisguiseSelectionPayload(
        UUID sessionId,
        long expiryGameTick)
        implements CustomPacketPayload {
    public static final Type<OpenDisguiseSelectionPayload> TYPE =
            new Type<>(WhitesnakeAddon.id("open_disguise_selection"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenDisguiseSelectionPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    OpenDisguiseSelectionPayload::write,
                    OpenDisguiseSelectionPayload::new);

    public OpenDisguiseSelectionPayload {
        Objects.requireNonNull(sessionId);
    }

    private OpenDisguiseSelectionPayload(
            RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readVarLong());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeVarLong(expiryGameTick);
    }

    public static void handle(
            OpenDisguiseSelectionPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() ->
                DisguiseClientBridge.openSelection(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
