package rotp.addons.shadowtheworld.state;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ShadowEvolutionState
        implements INBTSerializable<CompoundTag> {
    private static final int STATE_CONVERSION_UNLOCKED = 1;
    private static final int EVOLUTION_UNLOCKED = 1 << 1;
    private static final int EVOLUTION_IN_PROGRESS = 1 << 2;

    public static final StreamCodec<
            RegistryFriendlyByteBuf, ShadowEvolutionState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ShadowEvolutionState decode(
                        RegistryFriendlyByteBuf buffer) {
                    int availability = buffer.readUnsignedByte();
                    ShadowEvolutionState state =
                            new ShadowEvolutionState();
                    state.stateConversionUnlocked =
                            (availability
                                    & STATE_CONVERSION_UNLOCKED) != 0;
                    state.evolutionUnlocked =
                            (availability & EVOLUTION_UNLOCKED) != 0;
                    state.evolutionTicks =
                            (availability
                                    & EVOLUTION_IN_PROGRESS) != 0
                                    ? 1
                                    : 0;
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        ShadowEvolutionState state) {
                    int availability = 0;
                    if (state.stateConversionUnlocked) {
                        availability |= STATE_CONVERSION_UNLOCKED;
                    }
                    if (state.evolutionUnlocked) {
                        availability |= EVOLUTION_UNLOCKED;
                    }
                    if (state.isEvolving()) {
                        availability |= EVOLUTION_IN_PROGRESS;
                    }
                    buffer.writeByte(availability);
                }
            };

    private boolean usedNetherStar;
    private boolean stateConversionUnlocked;
    private boolean evolutionUnlocked;
    private int evolutionCount;
    private int evolutionTicks;
    private int fireProtectionTicks;
    private long counterEndsAt;

    public boolean usedNetherStar() {
        return usedNetherStar;
    }

    public void setUsedNetherStar(boolean value) {
        usedNetherStar = value;
    }

    public boolean stateConversionUnlocked() {
        return stateConversionUnlocked;
    }

    public void unlockStateConversion(Player owner) {
        unlockStateConversion(() -> sync(owner));
    }

    void unlockStateConversion(Runnable syncAction) {
        if (stateConversionUnlocked) {
            return;
        }
        stateConversionUnlocked = true;
        syncAction.run();
    }

    public boolean evolutionUnlocked() {
        return evolutionUnlocked;
    }

    public int evolutionCount() {
        return evolutionCount;
    }

    public boolean isEvolving() {
        return evolutionTicks > 0;
    }

    public int evolutionTicks() {
        return evolutionTicks;
    }

    public void beginEvolution(Player owner, int ticks) {
        beginEvolution(ticks, () -> sync(owner));
    }

    void beginEvolution(int ticks, Runnable syncAction) {
        boolean wasEvolving = isEvolving();
        evolutionTicks = Math.max(0, ticks);
        if (wasEvolving != isEvolving()) {
            syncAction.run();
        }
    }

    public boolean tickEvolution(Player owner) {
        return tickEvolution(() -> sync(owner));
    }

    boolean tickEvolution(Runnable syncAction) {
        if (evolutionTicks <= 0) {
            return false;
        }
        evolutionTicks--;
        if (evolutionTicks == 0) {
            evolutionUnlocked = true;
            evolutionCount++;
            syncAction.run();
            return true;
        }
        return false;
    }

    public void setFireProtectionTicks(int ticks) {
        fireProtectionTicks = Math.max(0, ticks);
    }

    public boolean tickFireProtection() {
        if (fireProtectionTicks <= 0) {
            return false;
        }
        fireProtectionTicks--;
        return true;
    }

    // 1.16 TimeStopCounter.standPerform left a counter that is still armed as it was (no new end time)
    public boolean activateCounter(long gameTime, long endsAt) {
        if (counterActive(gameTime)) {
            return false;
        }
        counterEndsAt = endsAt;
        return true;
    }

    public boolean counterActive(long gameTime) {
        if (counterEndsAt <= gameTime) {
            counterEndsAt = 0L;
            return false;
        }
        return true;
    }

    public void consumeCounter() {
        counterEndsAt = 0L;
    }

    public void copyFrom(
            Player owner, ShadowEvolutionState other) {
        copyFrom(other, () -> sync(owner));
    }

    void copyFrom(
            ShadowEvolutionState other, Runnable syncAction) {
        boolean previousStateConversion =
                stateConversionUnlocked;
        boolean previousEvolutionUnlocked =
                evolutionUnlocked;
        boolean previousEvolving = isEvolving();
        usedNetherStar = other.usedNetherStar;
        stateConversionUnlocked =
                other.stateConversionUnlocked;
        evolutionUnlocked = other.evolutionUnlocked;
        evolutionCount = other.evolutionCount;
        evolutionTicks = other.evolutionTicks;
        fireProtectionTicks = other.fireProtectionTicks;
        // 1.16 kept the armed counter in the user's getPersistentData(), which a Forge player clone (death or the
        // return from the End) did not carry over; only EvolutionStateCap was copied
        counterEndsAt = 0L;
        if (previousStateConversion
                        != stateConversionUnlocked
                || previousEvolutionUnlocked
                        != evolutionUnlocked
                || previousEvolving != isEvolving()) {
            syncAction.run();
        }
    }

    private static void sync(Player owner) {
        syncOnServer(
                owner.level().isClientSide(),
                () -> owner.syncData(
                        ShadowTheWorldAddon.EVOLUTION_STATE));
    }

    static void syncOnServer(
            boolean clientSide, Runnable syncAction) {
        if (!clientSide) {
            syncAction.run();
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("UsedNetherStar", usedNetherStar);
        tag.putBoolean(
                "StateConversionUnlocked",
                stateConversionUnlocked);
        tag.putBoolean("EvolutionUnlocked", evolutionUnlocked);
        tag.putInt("EvolutionCount", evolutionCount);
        tag.putInt("EvolutionTicks", evolutionTicks);
        tag.putInt(
                "FireProtectionTicks",
                fireProtectionTicks);
        tag.putLong("CounterEndsAt", counterEndsAt);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        usedNetherStar = tag.getBoolean("UsedNetherStar");
        stateConversionUnlocked =
                tag.getBoolean("StateConversionUnlocked");
        evolutionUnlocked =
                tag.getBoolean("EvolutionUnlocked");
        evolutionCount = Math.max(
                0, tag.getInt("EvolutionCount"));
        evolutionTicks = Math.max(
                0, tag.getInt("EvolutionTicks"));
        fireProtectionTicks = Math.max(
                0, tag.getInt("FireProtectionTicks"));
        counterEndsAt = Math.max(
                0L, tag.getLong("CounterEndsAt"));
    }
}
