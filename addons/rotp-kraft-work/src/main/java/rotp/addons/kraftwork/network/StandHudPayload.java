package rotp.addons.kraftwork.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// whether the client's HUD shows the Stand power, which 1.16's crosshair
// target read there (InputHandler.pickMouseTarget :699-704)
public record StandHudPayload(boolean shown)
        implements CustomPacketPayload {
    public static final Type<StandHudPayload> TYPE =
            new Type<>(KraftWorkPayloads.id("stand_hud"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            StandHudPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                     StandHudPayload::write,
                     StandHudPayload::new);

    private StandHudPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(shown);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
