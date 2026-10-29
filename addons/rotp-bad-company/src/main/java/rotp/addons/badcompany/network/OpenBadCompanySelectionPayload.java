package rotp.addons.badcompany.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenBadCompanySelectionPayload(
        int kind,
        int selected,
        int optionCount) implements CustomPacketPayload {
    public static final Type<OpenBadCompanySelectionPayload> TYPE =
            new Type<>(BadCompanySelectionPayloads.id(
                    "open_selection"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenBadCompanySelectionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    OpenBadCompanySelectionPayload::kind,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    OpenBadCompanySelectionPayload::selected,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    OpenBadCompanySelectionPayload::optionCount,
                    OpenBadCompanySelectionPayload::new);

    public OpenBadCompanySelectionPayload {
        if (BadCompanySelectionKind.fromNetworkId(kind)
                        .isEmpty()
                || optionCount < 1
                || optionCount > 4
                || selected < 0
                || selected >= optionCount) {
            throw new IllegalArgumentException(
                    "Invalid Bad Company selection payload");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
