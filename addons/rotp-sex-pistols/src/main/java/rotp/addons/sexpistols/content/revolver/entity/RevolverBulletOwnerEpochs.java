package rotp.addons.sexpistols.content.revolver.entity;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

public final class RevolverBulletOwnerEpochs extends SavedData {
    private static final String FILE_NAME =
            SexPistolsIds.MOD_ID + "-revolver_bullet_owner_epochs";
    private static final String EPOCHS_KEY = "OwnerEpochs";
    private static final String OWNER_KEY = "Owner";
    private static final String EPOCH_KEY = "Epoch";
    private static final BigInteger INITIAL_EPOCH = BigInteger.ZERO;
    private static final SavedData.Factory<RevolverBulletOwnerEpochs>
            FACTORY = new SavedData.Factory<>(
                    RevolverBulletOwnerEpochs::new,
                    RevolverBulletOwnerEpochs::load);

    private final Map<UUID, BigInteger> epochs = new HashMap<>();

    static RevolverBulletOwnerEpochs get(MinecraftServer server) {
        DimensionDataStorage storage =
                server.overworld().getDataStorage();
        return storage.computeIfAbsent(FACTORY, FILE_NAME);
    }

    synchronized BigInteger current(UUID ownerUuid) {
        return epochs.getOrDefault(ownerUuid, INITIAL_EPOCH);
    }

    synchronized BigInteger advance(UUID ownerUuid) {
        BigInteger next = current(ownerUuid).add(BigInteger.ONE);
        epochs.put(ownerUuid, next);
        setDirty();
        return next;
    }

    synchronized boolean isCurrent(
            UUID ownerUuid, BigInteger bulletEpoch) {
        return bulletEpoch != null
                && bulletEpoch.signum() >= 0
                && current(ownerUuid).equals(bulletEpoch);
    }

    @Override
    public synchronized CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        ListTag savedEpochs = new ListTag();
        epochs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag savedEpoch = new CompoundTag();
                    savedEpoch.putUUID(OWNER_KEY, entry.getKey());
                    savedEpoch.putString(
                            EPOCH_KEY, entry.getValue().toString());
                    savedEpochs.add(savedEpoch);
                });
        tag.put(EPOCHS_KEY, savedEpochs);
        return tag;
    }

    static RevolverBulletOwnerEpochs load(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        RevolverBulletOwnerEpochs data =
                new RevolverBulletOwnerEpochs();
        ListTag savedEpochs = tag.getList(
                EPOCHS_KEY, Tag.TAG_COMPOUND);
        for (Tag value : savedEpochs) {
            if (!(value instanceof CompoundTag savedEpoch)
                    || !savedEpoch.hasUUID(OWNER_KEY)
                    || !savedEpoch.contains(
                            EPOCH_KEY, Tag.TAG_STRING)) {
                continue;
            }
            try {
                BigInteger epoch = new BigInteger(
                        savedEpoch.getString(EPOCH_KEY));
                if (epoch.signum() >= 0) {
                    data.epochs.put(
                            savedEpoch.getUUID(OWNER_KEY), epoch);
                }
            }
            catch (NumberFormatException ignored) {
                // Ignore malformed entries instead of blocking world load.
            }
        }
        return data;
    }
}
