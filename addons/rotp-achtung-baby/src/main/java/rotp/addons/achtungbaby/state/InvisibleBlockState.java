package rotp.addons.achtungbaby.state;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Which block positions in a level were placed from an item the ability had made invisible.
 *
 * <p>This is the only half of 1.16's "invisible blocks" that ever did anything - see
 * {@code rotp.addons.achtungbaby.mixin.BlockItemMixin} for what the rest of it turned out to be. The record exists so
 * that breaking such a block hands the invisible item back instead of a plain one, and it is dropped again the moment
 * the block breaks, so the set only ever holds blocks that are still standing.
 *
 * <p>1.16 kept this in a {@code WorldSavedData} keyed "achtung_baby_invisible_blocks", storing each position as an
 * {@code "x,y,z"} boolean key. A level attachment is the same thing in 1.21 and persists with the level the same way;
 * the positions are stored as packed longs rather than parsed strings.
 */
public final class InvisibleBlockState
        implements INBTSerializable<CompoundTag> {
    private static final String POSITIONS = "Positions";

    private final Set<BlockPos> positions = new HashSet<>();

    public boolean isInvisible(BlockPos pos) {
        return positions.contains(pos);
    }

    public void add(BlockPos pos) {
        positions.add(pos.immutable());
    }

    public boolean remove(BlockPos pos) {
        return positions.remove(pos);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(LongTag.valueOf(pos.asLong()));
        }
        CompoundTag tag = new CompoundTag();
        tag.put(POSITIONS, list);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        positions.clear();
        ListTag list = tag.getList(POSITIONS, Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            positions.add(BlockPos.of(
                    ((LongTag) list.get(i)).getAsLong()));
        }
    }
}
