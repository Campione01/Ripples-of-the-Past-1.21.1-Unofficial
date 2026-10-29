package rotp.addons.whitesnake.disc;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record MemoryDiscData(
        UUID targetId,
        UUID extractionId,
        String targetName) {
    private static final String TARGET_ID_TAG = "target_id";
    private static final String EXTRACTION_ID_TAG = "extraction_id";
    private static final int MAX_NAME_LENGTH = 64;

    public static final Codec<MemoryDiscData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("target")
                            .forGetter(MemoryDiscData::targetId),
                    UUIDUtil.CODEC.fieldOf("extraction")
                            .forGetter(MemoryDiscData::extractionId),
                    Codec.string(0, MAX_NAME_LENGTH)
                            .fieldOf("target_name")
                            .forGetter(MemoryDiscData::targetName))
                    .apply(instance, MemoryDiscData::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, MemoryDiscData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MemoryDiscData decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new MemoryDiscData(
                            buffer.readUUID(),
                            buffer.readUUID(),
                            buffer.readUtf(MAX_NAME_LENGTH));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        MemoryDiscData data) {
                    buffer.writeUUID(data.targetId());
                    buffer.writeUUID(data.extractionId());
                    buffer.writeUtf(
                            data.targetName(), MAX_NAME_LENGTH);
                }
            };

    public MemoryDiscData {
        if (targetId == null || extractionId == null) {
            throw new IllegalArgumentException(
                    "Memory-disc IDs must not be null");
        }
        targetName = targetName != null ? targetName : "";
        if (targetName.length() > MAX_NAME_LENGTH) {
            targetName = targetName.substring(0, MAX_NAME_LENGTH);
        }
    }

    public CompoundTag toPayloadTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TARGET_ID_TAG, targetId);
        tag.putUUID(EXTRACTION_ID_TAG, extractionId);
        return tag;
    }

    public boolean matchesPayload(CompoundTag tag) {
        return tag != null
                && tag.hasUUID(TARGET_ID_TAG)
                && tag.hasUUID(EXTRACTION_ID_TAG)
                && targetId.equals(tag.getUUID(TARGET_ID_TAG))
                && extractionId.equals(
                        tag.getUUID(EXTRACTION_ID_TAG));
    }

    public static Optional<MemoryDiscData> fromPayloadTag(
            CompoundTag tag) {
        if (tag == null
                || !tag.hasUUID(TARGET_ID_TAG)
                || !tag.hasUUID(EXTRACTION_ID_TAG)) {
            return Optional.empty();
        }
        return Optional.of(new MemoryDiscData(
                tag.getUUID(TARGET_ID_TAG),
                tag.getUUID(EXTRACTION_ID_TAG),
                ""));
    }
}
