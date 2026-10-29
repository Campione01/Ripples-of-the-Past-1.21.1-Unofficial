package rotp.addons.heavensdoor.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/** A separate epoch for the four health-limited commands, including unloaded subjects. */
public final class RestrictedCommandOwners extends SavedData {
    private static final String DATA_NAME = "rotp_hd_restricted_command_owners";
    private static final Factory<RestrictedCommandOwners> FACTORY =
            new Factory<>(RestrictedCommandOwners::new, RestrictedCommandOwners::load, null);
    private final Map<UUID, OwnerEpoch> owners = new HashMap<>();

    public static RestrictedCommandOwners get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static boolean released(LivingEntity owner) {
        return owner == null || BookStateRules.restrictedCommandsReleased(
                owner.isAlive(), owner.getHealth(), owner.getMaxHealth(),
                owner instanceof Player player && player.getAbilities().instabuild);
    }

    public long begin(UUID owner) {
        observe(owner, false);
        return epoch(owner);
    }

    public boolean tracks(UUID owner) {
        return owners.containsKey(owner);
    }

    public long epoch(UUID owner) {
        OwnerEpoch current = owners.get(owner);
        return current != null ? current.epoch() : 0L;
    }

    public boolean matches(UUID owner, long epoch) {
        OwnerEpoch current = owners.get(owner);
        return epoch >= 0L && epoch == epoch(owner) && (current == null || !current.released());
    }

    public boolean observe(UUID owner, boolean released) {
        OwnerEpoch current = owners.get(owner);
        if (current != null && current.released() == released) {
            return false;
        }
        long epoch = current != null ? current.epoch() : 0L;
        if (released) {
            epoch = Math.incrementExact(epoch);
        }
        owners.put(owner, new OwnerEpoch(epoch, released));
        setDirty();
        return released;
    }

    public static RestrictedCommandOwners load(CompoundTag tag, HolderLookup.Provider provider) {
        RestrictedCommandOwners data = new RestrictedCommandOwners();
        ListTag entries = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Owner")) {
                data.owners.put(entry.getUUID("Owner"), new OwnerEpoch(
                        Math.max(0L, entry.getLong("Epoch")), entry.getBoolean("Released")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        owners.forEach((owner, epoch) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putLong("Epoch", epoch.epoch());
            entry.putBoolean("Released", epoch.released());
            entries.add(entry);
        });
        tag.put("Owners", entries);
        return tag;
    }

    private record OwnerEpoch(long epoch, boolean released) {}
}
