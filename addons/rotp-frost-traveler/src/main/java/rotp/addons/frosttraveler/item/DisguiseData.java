package rotp.addons.frosttraveler.item;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;

public record DisguiseData(
        UUID ownerUuid,
        Optional<ResourceLocation> targetType,
        Optional<UUID> targetUuid,
        String targetName,
        boolean hostile,
        Optional<ResolvableProfile> capturedProfile) {
    public static final Codec<DisguiseData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("owner")
                            .forGetter(DisguiseData::ownerUuid),
                    ResourceLocation.CODEC.optionalFieldOf("target_type")
                            .forGetter(DisguiseData::targetType),
                    UUIDUtil.CODEC.optionalFieldOf("target_uuid")
                            .forGetter(DisguiseData::targetUuid),
                    Codec.STRING.optionalFieldOf("target_name", "")
                            .forGetter(DisguiseData::targetName),
                    Codec.BOOL.optionalFieldOf("hostile", false)
                            .forGetter(DisguiseData::hostile),
                    ResolvableProfile.CODEC.optionalFieldOf("captured_profile")
                            .forGetter(DisguiseData::capturedProfile))
                    .apply(instance, DisguiseData::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, DisguiseData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public DisguiseData decode(
                        RegistryFriendlyByteBuf buffer) {
                    UUID owner = buffer.readUUID();
                    Optional<ResourceLocation> type =
                            buffer.readBoolean()
                                    ? Optional.of(
                                            buffer.readResourceLocation())
                                    : Optional.empty();
                    Optional<UUID> uuid = buffer.readBoolean()
                            ? Optional.of(buffer.readUUID())
                            : Optional.empty();
                    return new DisguiseData(
                            owner,
                            type,
                            uuid,
                            buffer.readUtf(256),
                            buffer.readBoolean(),
                            buffer.readBoolean()
                                    ? Optional.of(ResolvableProfile.STREAM_CODEC.decode(buffer))
                                    : Optional.empty());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        DisguiseData data) {
                    buffer.writeUUID(data.ownerUuid());
                    buffer.writeBoolean(data.targetType().isPresent());
                    data.targetType().ifPresent(
                            buffer::writeResourceLocation);
                    buffer.writeBoolean(data.targetUuid().isPresent());
                    data.targetUuid().ifPresent(buffer::writeUUID);
                    buffer.writeUtf(data.targetName(), 256);
                    buffer.writeBoolean(data.hostile());
                    buffer.writeBoolean(data.capturedProfile().isPresent());
                    data.capturedProfile().ifPresent(
                            profile -> ResolvableProfile.STREAM_CODEC.encode(buffer, profile));
                }
            };

    public DisguiseData {
        if (ownerUuid == null) {
            throw new IllegalArgumentException(
                    "Disguise owner must not be null");
        }
        targetType = targetType != null
                ? targetType
                : Optional.empty();
        targetUuid = targetUuid != null
                ? targetUuid
                : Optional.empty();
        targetName = targetName != null ? targetName : "";
        capturedProfile = capturedProfile != null
                ? capturedProfile
                : Optional.empty();
    }

    public DisguiseData(
            UUID ownerUuid,
            Optional<ResourceLocation> targetType,
            Optional<UUID> targetUuid,
            String targetName,
            boolean hostile) {
        this(ownerUuid, targetType, targetUuid, targetName, hostile, Optional.empty());
    }

    public static DisguiseData blank(UUID ownerUuid) {
        return new DisguiseData(
                ownerUuid,
                Optional.empty(),
                Optional.empty(),
                "",
                false);
    }

    public boolean isCaptured() {
        return targetType.isPresent() || targetUuid.isPresent();
    }
}
