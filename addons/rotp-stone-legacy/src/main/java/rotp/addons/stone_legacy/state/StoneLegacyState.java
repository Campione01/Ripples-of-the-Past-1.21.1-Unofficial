package rotp.addons.stone_legacy.state;

import rotp.addons.stone_legacy.StoneLegacyAddon;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class StoneLegacyState
        implements INBTSerializable<CompoundTag> {
    public static final StreamCodec<
            RegistryFriendlyByteBuf, StoneLegacyState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public StoneLegacyState decode(
                        RegistryFriendlyByteBuf buffer) {
                    StoneLegacyState state =
                            new StoneLegacyState();
                    state.hamonOrigin = buffer.readBoolean();
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        StoneLegacyState state) {
                    buffer.writeBoolean(state.hamonOrigin);
                }
            };

    private boolean hamonOrigin;

    public boolean hamonOrigin() {
        return hamonOrigin;
    }

    public void setHamonOrigin(
            LivingEntity owner, boolean hamonOrigin) {
        if (this.hamonOrigin == hamonOrigin) {
            return;
        }
        this.hamonOrigin = hamonOrigin;
        if (!owner.level().isClientSide()) {
            owner.syncData(StoneLegacyAddon.PLAYER_STATE);
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HamonOrigin", hamonOrigin);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        hamonOrigin = tag.getBoolean("HamonOrigin");
    }
}
