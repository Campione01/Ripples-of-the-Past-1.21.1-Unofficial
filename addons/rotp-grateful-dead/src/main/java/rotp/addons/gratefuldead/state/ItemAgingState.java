package rotp.addons.gratefuldead.state;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record ItemAgingState(
        float progress,
        Optional<UUID> owner,
        long ownerGeneration,
        boolean fading) {
    public static final ItemAgingState EMPTY =
            new ItemAgingState(
                    0.0F, Optional.empty(), 0L, false);

    public static final Codec<ItemAgingState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.floatRange(0.0F, 1.0F)
                            .fieldOf("progress")
                            .forGetter(ItemAgingState::progress),
                    UUIDUtil.CODEC.optionalFieldOf("owner")
                            .forGetter(ItemAgingState::owner),
                    Codec.LONG.optionalFieldOf(
                                    "owner_generation", 0L)
                            .forGetter(
                                    ItemAgingState::ownerGeneration),
                    Codec.BOOL.optionalFieldOf("fading", false)
                            .forGetter(ItemAgingState::fading))
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
                    long generation = buffer.readVarLong();
                    boolean fading = buffer.readBoolean();
                    return new ItemAgingState(
                            progress,
                            owner,
                            generation,
                            fading);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        ItemAgingState state) {
                    buffer.writeFloat(state.progress());
                    buffer.writeBoolean(
                            state.owner().isPresent());
                    state.owner().ifPresent(buffer::writeUUID);
                    buffer.writeVarLong(
                            state.ownerGeneration());
                    buffer.writeBoolean(state.fading());
                }
            };

    public ItemAgingState {
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        owner = owner != null ? owner : Optional.empty();
        ownerGeneration = Math.max(0L, ownerGeneration);
    }

    public ItemAgingState age(
            UUID newOwner,
            long generation,
            float amount) {
        return new ItemAgingState(
                progress + Math.max(0.0F, amount),
                Optional.of(newOwner),
                generation,
                false);
    }

    public ItemAgingState startFading() {
        return progress <= 0.0F
                ? EMPTY
                : new ItemAgingState(
                        progress,
                        owner,
                        ownerGeneration,
                        true);
    }

    public ItemAgingState fade(float amount) {
        float next =
                progress - Math.max(0.0F, amount);
        return next <= 0.0F
                ? EMPTY
                : new ItemAgingState(
                        next,
                        owner,
                        ownerGeneration,
                        true);
    }
}
