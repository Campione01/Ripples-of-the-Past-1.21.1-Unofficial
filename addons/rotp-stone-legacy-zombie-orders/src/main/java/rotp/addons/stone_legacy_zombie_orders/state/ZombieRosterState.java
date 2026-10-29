package rotp.addons.stone_legacy_zombie_orders.state;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ZombieRosterState
        implements INBTSerializable<CompoundTag> {
    private static final String COUNT_KEY = "Count";
    private static final String ENTRY_PREFIX = "Zombie";

    private final Set<UUID> ownedZombies =
            new LinkedHashSet<>();

    public Set<UUID> ownedZombies() {
        return Set.copyOf(ownedZombies);
    }

    public boolean add(UUID zombieUuid) {
        return ownedZombies.add(zombieUuid);
    }

    public boolean remove(UUID zombieUuid) {
        return ownedZombies.remove(zombieUuid);
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(COUNT_KEY, ownedZombies.size());
        int index = 0;
        for (UUID zombieUuid : ownedZombies) {
            tag.putUUID(
                    ENTRY_PREFIX + index++,
                    zombieUuid);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        ownedZombies.clear();
        int count = Math.max(0, tag.getInt(COUNT_KEY));
        for (int index = 0; index < count; index++) {
            String key = ENTRY_PREFIX + index;
            if (tag.hasUUID(key)) {
                ownedZombies.add(tag.getUUID(key));
            }
        }
    }
}
