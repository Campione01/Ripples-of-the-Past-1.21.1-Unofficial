package rotp.addons.tickettoride.item;

import java.util.Objects;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record TearsBladeData(UUID ownerUuid, int ammo) {
    public static final Codec<TearsBladeData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("owner")
                            .forGetter(TearsBladeData::ownerUuid),
                    Codec.INT.fieldOf("ammo")
                            .forGetter(TearsBladeData::ammo))
                    .apply(instance, TearsBladeData::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, TearsBladeData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TearsBladeData decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new TearsBladeData(
                            buffer.readUUID(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        TearsBladeData data) {
                    buffer.writeUUID(data.ownerUuid());
                    buffer.writeVarInt(data.ammo());
                }
            };

    public TearsBladeData {
        Objects.requireNonNull(
                ownerUuid, "Tear Blade owner must not be null");
        ammo = Mth.clamp(
                ammo, 0, TearsBladeItem.MAX_AMMO);
    }

    public TearsBladeData withAmmo(int newAmmo) {
        return new TearsBladeData(ownerUuid, newAmmo);
    }
}
