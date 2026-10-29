package rotp.addons.stone_legacy_zombie_orders.state;

import rotp.addons.stone_legacy_zombie_orders.ZombieOrdersAddon;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ZombieOrderState
        implements INBTSerializable<CompoundTag> {
    public static final StreamCodec<
            RegistryFriendlyByteBuf, ZombieOrderState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ZombieOrderState decode(
                        RegistryFriendlyByteBuf buffer) {
                    ZombieOrderState state =
                            new ZombieOrderState();
                    state.orderedToWait =
                            buffer.readBoolean();
                    state.activeWaitingPose =
                            buffer.readBoolean();
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        ZombieOrderState state) {
                    buffer.writeBoolean(
                            state.orderedToWait);
                    buffer.writeBoolean(
                            state.activeWaitingPose);
                }
            };

    private boolean orderedToWait;
    private boolean activeWaitingPose;

    public boolean orderedToWait() {
        return orderedToWait;
    }

    public boolean activeWaitingPose() {
        return activeWaitingPose;
    }

    public void setOrderedToWait(
            LivingEntity zombie, boolean orderedToWait) {
        if (this.orderedToWait == orderedToWait) {
            return;
        }
        this.orderedToWait = orderedToWait;
        if (!orderedToWait) {
            activeWaitingPose = false;
        }
        if (orderedToWait && zombie instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        if (!zombie.level().isClientSide()) {
            zombie.syncData(ZombieOrdersAddon.ORDER_STATE);
        }
    }

    public void setActiveWaitingPose(
            LivingEntity zombie,
            boolean activeWaitingPose) {
        if (this.activeWaitingPose == activeWaitingPose) {
            return;
        }
        this.activeWaitingPose = activeWaitingPose;
        if (!zombie.level().isClientSide()) {
            zombie.syncData(ZombieOrdersAddon.ORDER_STATE);
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("OrderedToWait", orderedToWait);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        orderedToWait = tag.getBoolean("OrderedToWait");
        activeWaitingPose = false;
    }
}
