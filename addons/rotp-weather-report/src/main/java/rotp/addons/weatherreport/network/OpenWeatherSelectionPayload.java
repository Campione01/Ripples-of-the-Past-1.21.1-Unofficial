package rotp.addons.weatherreport.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenWeatherSelectionPayload(
        UUID sessionId,
        long expiryGameTick,
        byte allowedChoices) implements CustomPacketPayload {
    public static final Type<OpenWeatherSelectionPayload> TYPE =
            new Type<>(WeatherReportPayloads.id(
                    "open_weather_selection"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenWeatherSelectionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenWeatherSelectionPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new OpenWeatherSelectionPayload(
                            buffer.readUUID(),
                            buffer.readVarLong(),
                            buffer.readByte());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        OpenWeatherSelectionPayload payload) {
                    buffer.writeUUID(payload.sessionId());
                    buffer.writeVarLong(payload.expiryGameTick());
                    buffer.writeByte(payload.allowedChoices());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
