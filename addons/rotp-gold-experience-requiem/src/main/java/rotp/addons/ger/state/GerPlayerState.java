package rotp.addons.ger.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class GerPlayerState
        implements INBTSerializable<CompoundTag> {
    /**
     * Client copy of the evolution counters only (the 1.16 Stand pose was synced to every viewer so the
     * beetle-arrow layer could draw the arrow in Gold Experience's hand).
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, GerPlayerState>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public GerPlayerState decode(RegistryFriendlyByteBuf buffer) {
                    GerPlayerState state = new GerPlayerState();
                    state.evolutionRemaining = buffer.readVarInt();
                    state.evolutionElapsed = buffer.readVarInt();
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer, GerPlayerState state) {
                    buffer.writeVarInt(state.evolutionRemaining);
                    buffer.writeVarInt(state.evolutionElapsed);
                }
            };

    private int evolutionRemaining;
    private int evolutionElapsed;
    private boolean flightGranted;
    private boolean returnToZeroMarkAvailable;
    private boolean returnToZeroPresentationActive;

    public boolean isEvolving() {
        return evolutionRemaining > 0;
    }

    public int evolutionRemaining() {
        return evolutionRemaining;
    }

    public int evolutionElapsed() {
        return evolutionElapsed;
    }

    public void beginEvolution(int ticks) {
        evolutionRemaining = Math.max(0, ticks);
        evolutionElapsed = 0;
    }

    public void advanceEvolution() {
        if (evolutionRemaining > 0) {
            evolutionRemaining--;
            evolutionElapsed++;
        }
    }

    public void clearEvolution() {
        evolutionRemaining = 0;
        evolutionElapsed = 0;
    }

    public boolean flightGranted() {
        return flightGranted;
    }

    public void setFlightGranted(boolean flightGranted) {
        this.flightGranted = flightGranted;
    }

    public boolean returnToZeroMarkAvailable() {
        return returnToZeroMarkAvailable;
    }

    public void setReturnToZeroMarkAvailable(boolean available) {
        returnToZeroMarkAvailable = available;
    }

    public boolean returnToZeroPresentationActive() {
        return returnToZeroPresentationActive;
    }

    public void setReturnToZeroPresentationActive(
            boolean active) {
        returnToZeroPresentationActive = active;
    }

    public void copyFrom(GerPlayerState other) {
        evolutionRemaining = other.evolutionRemaining;
        evolutionElapsed = other.evolutionElapsed;
        flightGranted = other.flightGranted;
        returnToZeroMarkAvailable = false;
        returnToZeroPresentationActive = false;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("EvolutionRemaining", evolutionRemaining);
        tag.putInt("EvolutionElapsed", evolutionElapsed);
        tag.putBoolean("FlightGranted", flightGranted);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        evolutionRemaining = Math.max(
                0, tag.getInt("EvolutionRemaining"));
        evolutionElapsed = Math.max(
                0, tag.getInt("EvolutionElapsed"));
        flightGranted = tag.getBoolean("FlightGranted");
        returnToZeroMarkAvailable = false;
        returnToZeroPresentationActive = false;
    }
}
