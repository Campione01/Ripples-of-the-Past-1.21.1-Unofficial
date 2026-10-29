package rotp.addons.nextalbum.content.aging;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record ItemAgingState(float progress, Optional<UUID> owner) {
    public static final ItemAgingState EMPTY =
            new ItemAgingState(0.0F, Optional.empty());

    public static final Codec<ItemAgingState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.floatRange(0.0F, 1.0F)
                            .fieldOf("progress")
                            .forGetter(ItemAgingState::progress),
                    UUIDUtil.CODEC.optionalFieldOf("owner")
                            .forGetter(ItemAgingState::owner))
                    .apply(instance, ItemAgingState::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, ItemAgingState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ItemAgingState decode(
                        RegistryFriendlyByteBuf buffer) {
                    float progress = buffer.readFloat();
                    Optional<UUID> owner = buffer.readBoolean()
                            ? Optional.of(buffer.readUUID())
                            : Optional.empty();
                    return new ItemAgingState(progress, owner);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        ItemAgingState state) {
                    buffer.writeFloat(state.progress());
                    buffer.writeBoolean(state.owner().isPresent());
                    state.owner().ifPresent(buffer::writeUUID);
                }
            };

    public ItemAgingState {
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        owner = owner != null ? owner : Optional.empty();
    }
}
