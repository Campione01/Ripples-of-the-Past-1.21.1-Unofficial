package rotp.addons.sexpistols.content.revolver.casing;

import java.util.UUID;

import rotp.addons.sexpistols.registry.SexPistolsItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

final class CasingCompensationData extends SavedData {
    private static final String FILE_NAME =
            "rotp_sex_pistols-casing_compensation";
    private static final String CREDITS_TAG = "Credits";
    private static final String PLAYER_TAG = "Player";
    private static final String COUNT_TAG = "Count";
    private static final int MAX_DELIVERY_PER_TICK = 64;
    private static final SavedData.Factory<CasingCompensationData> FACTORY =
            new SavedData.Factory<>(
                    CasingCompensationData::new,
                    CasingCompensationData::load);

    private final CasingCompensationLedger ledger =
            new CasingCompensationLedger();

    static CasingCompensationData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(FACTORY, FILE_NAME);
    }

    static CasingCompensationData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        CasingCompensationData data = new CasingCompensationData();
        for (Tag element : tag.getList(CREDITS_TAG, Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag entry)
                    || !entry.hasUUID(PLAYER_TAG)) {
                continue;
            }
            long count = entry.getLong(COUNT_TAG);
            if (count > 0L) {
                data.ledger.credit(entry.getUUID(PLAYER_TAG), count);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag, HolderLookup.Provider registries) {
        ListTag credits = new ListTag();
        for (CasingCompensationLedger.Credit credit : ledger.snapshot()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(PLAYER_TAG, credit.playerId());
            entry.putLong(COUNT_TAG, credit.count());
            credits.add(entry);
        }
        tag.put(CREDITS_TAG, credits);
        return tag;
    }

    void credit(UUID playerId, long count) {
        long before = ledger.owed(playerId);
        ledger.credit(playerId, count);
        if (ledger.owed(playerId) != before) {
            setDirty();
        }
    }

    void deliver(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long owed = ledger.owed(playerId);
        if (owed <= 0L) {
            return;
        }
        int offered = (int) Math.min(owed, MAX_DELIVERY_PER_TICK);
        int before = player.getInventory().countItem(
                SexPistolsItems.REVOLVER_CASING.get());
        ItemStack stack = new ItemStack(
                SexPistolsItems.REVOLVER_CASING.get(), offered);
        player.getInventory().add(stack);
        int after = player.getInventory().countItem(
                SexPistolsItems.REVOLVER_CASING.get());
        int inserted = CasingCompensationLedger.insertedCount(
                offered, before, after);
        if (inserted > 0) {
            long consumed = ledger.consume(playerId, inserted);
            if (consumed != inserted) {
                throw new IllegalStateException(
                        "Casing compensation ledger underflow");
            }
            setDirty();
        }
    }

    boolean hasCredits() {
        return !ledger.isEmpty();
    }
}
