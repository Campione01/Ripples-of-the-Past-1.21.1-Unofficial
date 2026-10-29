package rotp.addons.frosttraveler.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import rotp.addons.frosttraveler.state.CreamStarterState;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record SprayerData(
        UUID ownerUuid,
        int ammo,
        CreamStarterState.SprayMode mode,
        String standSkin,
        int transformationTicks) {
    public static final Codec<SprayerData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("owner")
                            .forGetter(SprayerData::ownerUuid),
                    Codec.INT.fieldOf("ammo")
                            .forGetter(SprayerData::ammo),
                    Codec.STRING.fieldOf("mode")
                            .xmap(
                                    SprayerData::parseMode,
                                    CreamStarterState.SprayMode::name)
                            .forGetter(SprayerData::mode),
                    Codec.STRING.optionalFieldOf("stand_skin", "")
                            .forGetter(SprayerData::standSkin),
                    Codec.INT.optionalFieldOf(
                                    "transformation_ticks", 0)
                            .forGetter(
                                    SprayerData::transformationTicks))
                    .apply(instance, SprayerData::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, SprayerData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SprayerData decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new SprayerData(
                            buffer.readUUID(),
                            buffer.readVarInt(),
                            buffer.readEnum(
                                    CreamStarterState.SprayMode.class),
                            buffer.readUtf(256),
                            buffer.readVarInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        SprayerData data) {
                    buffer.writeUUID(data.ownerUuid());
                    buffer.writeVarInt(data.ammo());
                    buffer.writeEnum(data.mode());
                    buffer.writeUtf(data.standSkin(), 256);
                    buffer.writeVarInt(data.transformationTicks());
                }
            };

    public SprayerData {
        if (ownerUuid == null) {
            throw new IllegalArgumentException(
                    "Sprayer owner must not be null");
        }
        ammo = Math.max(
                0, Math.min(CreamStarterState.MAX_AMMO, ammo));
        mode = mode != null
                ? mode
                : CreamStarterState.SprayMode.ATTACK;
        standSkin = standSkin != null ? standSkin : "";
        transformationTicks = Math.max(0, transformationTicks);
    }

    public SprayerData withState(CreamStarterState state) {
        return new SprayerData(
                ownerUuid,
                state.ammo(),
                state.mode(),
                state.standSkin(),
                transformationTicks);
    }

    public SprayerData withTransformationTicks(int ticks) {
        return new SprayerData(
                ownerUuid, ammo, mode, standSkin, ticks);
    }

    private static CreamStarterState.SprayMode parseMode(String value) {
        try {
            return CreamStarterState.SprayMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CreamStarterState.SprayMode.ATTACK;
        }
    }
}
