package rotp.addons.stonefree.state;

import java.util.Optional;
import java.util.UUID;

import rotp.addons.stonefree.StoneFreeAddon;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class StoneFreeState
        implements INBTSerializable<CompoundTag> {
    public static final int ABSOLUTE_STRING_CAP = 100;

    public static final StreamCodec<
            RegistryFriendlyByteBuf, StoneFreeState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public StoneFreeState decode(
                        RegistryFriendlyByteBuf buffer) {
                    StoneFreeState state = new StoneFreeState();
                    state.placedStrings = Mth.clamp(
                            buffer.readVarInt(),
                            0,
                            ABSOLUTE_STRING_CAP);
                    state.removedStrings = Math.max(
                            0, buffer.readVarInt());
                    state.activeUserStrings = Math.max(
                            0, buffer.readVarInt());
                    state.mobiusMode = buffer.readBoolean();
                    state.openBarrier = buffer.readOptional(
                            RegistryFriendlyByteBuf::readUUID);
                    state.userGrapple = buffer.readOptional(
                            RegistryFriendlyByteBuf::readUUID);
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        StoneFreeState state) {
                    buffer.writeVarInt(state.placedStrings);
                    buffer.writeVarInt(state.removedStrings);
                    buffer.writeVarInt(state.activeUserStrings);
                    buffer.writeBoolean(state.mobiusMode);
                    buffer.writeOptional(
                            state.openBarrier,
                            RegistryFriendlyByteBuf::writeUUID);
                    buffer.writeOptional(
                            state.userGrapple,
                            RegistryFriendlyByteBuf::writeUUID);
                }
            };

    private int placedStrings;
    private int removedStrings;
    private int activeUserStrings;
    private boolean mobiusMode;
    private Optional<UUID> openBarrier = Optional.empty();
    private Optional<UUID> userGrapple = Optional.empty();

    public static StoneFreeState get(LivingEntity entity) {
        return entity.getData(StoneFreeAddon.USER_STATE);
    }

    public int placedStrings() {
        return placedStrings;
    }

    public int removedStrings() {
        return removedStrings;
    }

    public int activeUserStrings() {
        return activeUserStrings;
    }

    public boolean mobiusMode() {
        return mobiusMode;
    }

    public Optional<UUID> openBarrier() {
        return openBarrier;
    }

    public Optional<UUID> userGrapple() {
        return userGrapple;
    }

    public boolean investString(
            LivingEntity owner, int amount, int cap) {
        int previous = placedStrings;
        placedStrings = Mth.clamp(
                placedStrings + Math.max(amount, 0),
                0,
                Math.min(cap, ABSOLUTE_STRING_CAP));
        if (previous != placedStrings) {
            sync(owner);
            return true;
        }
        return false;
    }

    public void releaseString(
            LivingEntity owner, boolean countRemoval) {
        if (placedStrings <= 0) {
            return;
        }
        placedStrings--;
        if (countRemoval) {
            removedStrings++;
        }
        sync(owner);
    }

    public void addActiveUserString(LivingEntity owner) {
        activeUserStrings++;
        sync(owner);
    }

    public void removeActiveUserString(LivingEntity owner) {
        if (activeUserStrings > 0) {
            activeUserStrings--;
            sync(owner);
        }
    }

    public void setMobiusMode(
            LivingEntity owner, boolean enabled) {
        if (mobiusMode != enabled) {
            mobiusMode = enabled;
            sync(owner);
        }
    }

    public void setOpenBarrier(
            LivingEntity owner, UUID barrier) {
        if (!openBarrier.equals(Optional.ofNullable(barrier))) {
            openBarrier = Optional.ofNullable(barrier);
            sync(owner);
        }
    }

    public void clearOpenBarrier(
            LivingEntity owner, UUID expected) {
        if (openBarrier.filter(expected::equals).isPresent()) {
            openBarrier = Optional.empty();
            sync(owner);
        }
    }

    public void setUserGrapple(
            LivingEntity owner, UUID grapple) {
        if (!userGrapple.equals(Optional.ofNullable(grapple))) {
            userGrapple = Optional.ofNullable(grapple);
            sync(owner);
        }
    }

    public void clearUserGrapple(
            LivingEntity owner, UUID expected) {
        if (userGrapple.filter(expected::equals).isPresent()) {
            userGrapple = Optional.empty();
            sync(owner);
        }
    }

    public void reconcileActiveStrings(
            LivingEntity owner, int count) {
        int normalized = Math.max(0, count);
        if (activeUserStrings != normalized) {
            activeUserStrings = normalized;
            sync(owner);
        }
    }

    private static void sync(LivingEntity owner) {
        if (!owner.level().isClientSide()) {
            owner.syncData(StoneFreeAddon.USER_STATE);
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("PlacedStrings", placedStrings);
        tag.putInt("RemovedStrings", removedStrings);
        tag.putInt("ActiveUserStrings", activeUserStrings);
        tag.putBoolean("MobiusMode", mobiusMode);
        openBarrier.ifPresent(
                value -> tag.putUUID("OpenBarrier", value));
        userGrapple.ifPresent(
                value -> tag.putUUID("UserGrapple", value));
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        placedStrings = Mth.clamp(
                tag.getInt("PlacedStrings"),
                0,
                ABSOLUTE_STRING_CAP);
        removedStrings = Math.max(
                0, tag.getInt("RemovedStrings"));
        activeUserStrings = Math.max(
                0, tag.getInt("ActiveUserStrings"));
        mobiusMode = tag.getBoolean("MobiusMode");
        openBarrier = tag.hasUUID("OpenBarrier")
                ? Optional.of(tag.getUUID("OpenBarrier"))
                : Optional.empty();
        userGrapple = tag.hasUUID("UserGrapple")
                ? Optional.of(tag.getUUID("UserGrapple"))
                : Optional.empty();
    }
}
