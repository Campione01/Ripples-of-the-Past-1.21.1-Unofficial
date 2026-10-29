package rotp.addons.sexpistols.content.revolver.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record RevolverState(
        int chamberBits,
        int selectedChamber,
        boolean reloadMode,
        boolean aiming) {
    public static final int CHAMBER_COUNT = 6;
    public static final int CHAMBER_MASK = 0xFFF;
    public static final RevolverState EMPTY =
            new RevolverState(0, 0, false, false);

    public static final Codec<RevolverState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(0, CHAMBER_MASK)
                            .fieldOf("chambers")
                            .forGetter(RevolverState::chamberBits),
                    Codec.intRange(0, CHAMBER_COUNT - 1)
                            .fieldOf("selected_chamber")
                            .forGetter(RevolverState::selectedChamber),
                    Codec.BOOL.fieldOf("reload_mode")
                            .forGetter(RevolverState::reloadMode),
                    Codec.BOOL.fieldOf("aiming")
                            .forGetter(RevolverState::aiming))
                    .apply(instance, RevolverState::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, RevolverState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RevolverState decode(RegistryFriendlyByteBuf buffer) {
                    return new RevolverState(
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            buffer.readBoolean());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        RevolverState state) {
                    buffer.writeVarInt(state.chamberBits());
                    buffer.writeVarInt(state.selectedChamber());
                    buffer.writeBoolean(state.reloadMode());
                    buffer.writeBoolean(state.aiming());
                }
            };

    public RevolverState {
        chamberBits &= CHAMBER_MASK;
        selectedChamber = Mth.clamp(
                selectedChamber, 0, CHAMBER_COUNT - 1);
        if (reloadMode) {
            aiming = false;
        }
    }

    public Chamber chamber(int chamber) {
        int shift = Mth.clamp(chamber, 0, CHAMBER_COUNT - 1) * 2;
        return Chamber.byId((chamberBits >> shift) & 3);
    }

    public int liveCount() {
        return count(Chamber.LIVE);
    }

    public int spentCount() {
        return count(Chamber.SPENT);
    }

    public int firstChamber(Chamber value) {
        for (int offset = 0; offset < CHAMBER_COUNT; offset++) {
            int chamber = Math.floorMod(
                    selectedChamber + offset, CHAMBER_COUNT);
            if (chamber(chamber) == value) {
                return chamber;
            }
        }
        return -1;
    }

    // 1.16 loaded a round into any chamber without one, over a casing too.
    public int firstUnloadedChamber() {
        for (int offset = 0; offset < CHAMBER_COUNT; offset++) {
            int chamber = Math.floorMod(
                    selectedChamber + offset, CHAMBER_COUNT);
            if (chamber(chamber) != Chamber.LIVE) {
                return chamber;
            }
        }
        return -1;
    }

    public RevolverState withChamber(int chamber, Chamber value) {
        int shift = Mth.clamp(chamber, 0, CHAMBER_COUNT - 1) * 2;
        int nextBits = (chamberBits & ~(3 << shift))
                | (value.id() << shift);
        return new RevolverState(
                nextBits, selectedChamber, reloadMode, aiming);
    }

    public RevolverState withSelectedChamber(int chamber) {
        return new RevolverState(
                chamberBits, chamber, reloadMode, aiming);
    }

    public RevolverState advanceSelected(int direction) {
        int step = direction < 0 ? -1 : 1;
        return withSelectedChamber(
                Math.floorMod(
                        selectedChamber + step,
                        CHAMBER_COUNT));
    }

    public RevolverState withReloadMode(boolean value) {
        return new RevolverState(
                chamberBits, selectedChamber, value, aiming);
    }

    public RevolverState withAiming(boolean value) {
        return new RevolverState(
                chamberBits, selectedChamber, reloadMode, value);
    }

    public RevolverState clearTransient() {
        return new RevolverState(
                chamberBits, selectedChamber, false, false);
    }

    private int count(Chamber expected) {
        int count = 0;
        for (int chamber = 0; chamber < CHAMBER_COUNT; chamber++) {
            if (chamber(chamber) == expected) {
                count++;
            }
        }
        return count;
    }

    public enum Chamber {
        EMPTY(0),
        LIVE(1),
        SPENT(2);

        private final int id;

        Chamber(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Chamber byId(int id) {
            return switch (id) {
                case 1 -> LIVE;
                case 2 -> SPENT;
                default -> EMPTY;
            };
        }
    }
}
