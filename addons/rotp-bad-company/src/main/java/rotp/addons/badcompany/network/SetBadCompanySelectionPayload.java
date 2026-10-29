package rotp.addons.badcompany.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetBadCompanySelectionPayload(
        int kind,
        int selected) implements CustomPacketPayload {
    public static final Type<SetBadCompanySelectionPayload> TYPE =
            new Type<>(BadCompanySelectionPayloads.id(
                    "set_selection"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SetBadCompanySelectionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    SetBadCompanySelectionPayload::kind,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    SetBadCompanySelectionPayload::selected,
                    SetBadCompanySelectionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
