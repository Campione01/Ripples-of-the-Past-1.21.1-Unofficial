package rotp.addons.weatherreport.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SelectWeatherPayload(
        UUID sessionId,
        byte choice) implements CustomPacketPayload {
    public static final Type<SelectWeatherPayload> TYPE =
            new Type<>(WeatherReportPayloads.id("select_weather"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SelectWeatherPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SelectWeatherPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new SelectWeatherPayload(
                            buffer.readUUID(), buffer.readByte());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        SelectWeatherPayload payload) {
                    buffer.writeUUID(payload.sessionId());
                    buffer.writeByte(payload.choice());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
