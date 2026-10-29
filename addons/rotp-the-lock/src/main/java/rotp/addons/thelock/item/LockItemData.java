package rotp.addons.thelock.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record LockItemData(UUID ownerUuid) {
    public static final Codec<LockItemData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("owner")
                            .forGetter(LockItemData::ownerUuid))
                    .apply(instance, LockItemData::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, LockItemData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public LockItemData decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new LockItemData(buffer.readUUID());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        LockItemData data) {
                    buffer.writeUUID(data.ownerUuid());
                }
            };

    public LockItemData {
        if (ownerUuid == null) {
            throw new IllegalArgumentException(
                    "The Lock item owner must not be null");
        }
    }
}
