package rotp.addons.whitesnake.disc;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record StandDiscAuthorityData(
        UUID ownerId,
        UUID extractionId,
        ResourceLocation standId,
        String ownerName) {
    private static final int MAX_NAME_LENGTH = 64;

    public static final Codec<StandDiscAuthorityData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("owner")
                            .forGetter(
                                    StandDiscAuthorityData::ownerId),
                    UUIDUtil.CODEC.fieldOf("extraction")
                            .forGetter(
                                    StandDiscAuthorityData::extractionId),
                    ResourceLocation.CODEC.fieldOf("stand")
                            .forGetter(
                                    StandDiscAuthorityData::standId),
                    Codec.string(0, MAX_NAME_LENGTH)
                            .fieldOf("owner_name")
                            .forGetter(
                                    StandDiscAuthorityData::ownerName))
                    .apply(instance, StandDiscAuthorityData::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, StandDiscAuthorityData>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public StandDiscAuthorityData decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new StandDiscAuthorityData(
                            buffer.readUUID(),
                            buffer.readUUID(),
                            buffer.readResourceLocation(),
                            buffer.readUtf(MAX_NAME_LENGTH));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        StandDiscAuthorityData data) {
                    buffer.writeUUID(data.ownerId());
                    buffer.writeUUID(data.extractionId());
                    buffer.writeResourceLocation(data.standId());
                    buffer.writeUtf(
                            data.ownerName(), MAX_NAME_LENGTH);
                }
            };

    public StandDiscAuthorityData {
        if (ownerId == null
                || extractionId == null
                || standId == null) {
            throw new IllegalArgumentException(
                    "Stand-disc authority fields must not be null");
        }
        ownerName = ownerName != null ? ownerName : "";
        if (ownerName.length() > MAX_NAME_LENGTH) {
            ownerName = ownerName.substring(0, MAX_NAME_LENGTH);
        }
    }

    public static Optional<StandDiscAuthorityData> get(
            ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                stack.get(WhitesnakeAddon.STAND_DISC_AUTHORITY.get()));
    }
}
