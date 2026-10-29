package rotp.addons.metallica.state;

import java.util.Optional;
import java.util.UUID;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.MetallicaPhase1Spec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class MetallicaIronState
        implements INBTSerializable<CompoundTag> {
    public static final int MAX_SOURCES = 1;

    public static final StreamCodec<
            RegistryFriendlyByteBuf, MetallicaIronState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MetallicaIronState decode(
                        RegistryFriendlyByteBuf buffer) {
                    MetallicaIronState state =
                            new MetallicaIronState();
                    if (buffer.readBoolean()) {
                        state.sourceOwner = buffer.readUUID();
                        state.sourceDimension =
                                buffer.readResourceLocation();
                        state.expiresAt = buffer.readVarLong();
                    }
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        MetallicaIronState state) {
                    buffer.writeBoolean(
                            state.sourceOwner != null);
                    if (state.sourceOwner != null) {
                        buffer.writeUUID(state.sourceOwner);
                        buffer.writeResourceLocation(
                                state.sourceDimension);
                        buffer.writeVarLong(state.expiresAt);
                    }
                }
            };

    private UUID sourceOwner;
    private ResourceLocation sourceDimension;
    private long expiresAt;

    public Optional<UUID> sourceOwner() {
        return Optional.ofNullable(sourceOwner);
    }

    public ResourceLocation sourceDimension() {
        return sourceDimension;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public int sourceCount() {
        return sourceOwner == null ? 0 : 1;
    }

    public boolean activeFor(LivingEntity target) {
        return sourceOwner != null
                && target.hasEffect(MetallicaAddon.LACK_OF_IRON);
    }

    public void mark(
            LivingEntity target, LivingEntity owner) {
        if (target.level().isClientSide()) {
            return;
        }
        ResourceLocation dimension =
                target.level().dimension().location();
        boolean sourceChanged =
                !owner.getUUID().equals(sourceOwner)
                || !dimension.equals(sourceDimension);
        sourceOwner = owner.getUUID();
        sourceDimension = dimension;
        expiresAt = target.level().getGameTime()
                + MetallicaPhase1Spec
                        .LACK_OF_IRON_DURATION_TICKS;
        if (sourceChanged) {
            sync(target);
        }
    }

    public void tickServer(LivingEntity target) {
        if (target.level().isClientSide()
                || sourceOwner == null) {
            return;
        }
        if (!target.isAlive()
                || !target.hasEffect(
                        MetallicaAddon.LACK_OF_IRON)
                || !target.level().dimension().location()
                        .equals(sourceDimension)
                || target.level().getGameTime() > expiresAt) {
            clear(target);
        }
    }

    public Optional<LivingEntity> resolveSource(
            ServerLevel level) {
        if (sourceOwner == null
                || !level.dimension().location()
                        .equals(sourceDimension)) {
            return Optional.empty();
        }
        Entity entity = level.getEntity(sourceOwner);
        return entity instanceof LivingEntity living
                ? Optional.of(living)
                : Optional.empty();
    }

    public void clear(LivingEntity target) {
        if (sourceOwner == null) {
            return;
        }
        sourceOwner = null;
        sourceDimension = null;
        expiresAt = 0L;
        sync(target);
    }

    private static void sync(LivingEntity target) {
        if (!target.level().isClientSide()) {
            target.syncData(MetallicaAddon.IRON_STATE);
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (sourceOwner != null) {
            tag.putUUID("SourceOwner", sourceOwner);
            tag.putString(
                    "SourceDimension",
                    sourceDimension.toString());
            tag.putLong("ExpiresAt", expiresAt);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        sourceOwner = null;
        sourceDimension = null;
        expiresAt = 0L;
        if (!tag.hasUUID("SourceOwner")
                || !tag.contains("SourceDimension")) {
            return;
        }
        try {
            sourceOwner = tag.getUUID("SourceOwner");
            sourceDimension = ResourceLocation.parse(
                    tag.getString("SourceDimension"));
            expiresAt = tag.getLong("ExpiresAt");
        } catch (RuntimeException ignored) {
            sourceOwner = null;
            sourceDimension = null;
            expiresAt = 0L;
        }
    }
}
