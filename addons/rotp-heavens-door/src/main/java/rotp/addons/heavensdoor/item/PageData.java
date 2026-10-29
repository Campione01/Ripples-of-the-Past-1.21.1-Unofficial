package rotp.addons.heavensdoor.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record PageData(
        UUID targetUuid,
        UUID creatorUuid,
        ResourceLocation dimension,
        long generation,
        String targetName) {
    public static final int MAX_TARGET_NAME_LENGTH = 128;

    public static final Codec<PageData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("target")
                            .forGetter(PageData::targetUuid),
                    UUIDUtil.CODEC.fieldOf("creator")
                            .forGetter(PageData::creatorUuid),
                    ResourceLocation.CODEC.fieldOf("dimension")
                            .forGetter(PageData::dimension),
                    Codec.LONG.validate(value -> value > 0L
                                    ? DataResult.success(value)
                                    : DataResult.error(() ->
                                            "generation must be positive"))
                            .fieldOf("generation")
                            .forGetter(PageData::generation),
                    Codec.string(0, MAX_TARGET_NAME_LENGTH)
                            .fieldOf("target_name")
                            .forGetter(PageData::targetName))
                    .apply(instance, PageData::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, PageData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PageData decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new PageData(
                            buffer.readUUID(),
                            buffer.readUUID(),
                            buffer.readResourceLocation(),
                            buffer.readVarLong(),
                            buffer.readUtf(
                                    MAX_TARGET_NAME_LENGTH));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        PageData data) {
                    buffer.writeUUID(data.targetUuid());
                    buffer.writeUUID(data.creatorUuid());
                    buffer.writeResourceLocation(data.dimension());
                    buffer.writeVarLong(data.generation());
                    buffer.writeUtf(
                            data.targetName(),
                            MAX_TARGET_NAME_LENGTH);
                }
            };

    public PageData {
        if (targetUuid == null
                || creatorUuid == null
                || dimension == null
                || generation <= 0L) {
            throw new IllegalArgumentException(
                    "Heaven's Door page data is incomplete");
        }
        targetName = targetName != null ? targetName : "";
        if (targetName.length() > MAX_TARGET_NAME_LENGTH) {
            targetName = targetName.substring(
                    0, MAX_TARGET_NAME_LENGTH);
        }
    }
}
