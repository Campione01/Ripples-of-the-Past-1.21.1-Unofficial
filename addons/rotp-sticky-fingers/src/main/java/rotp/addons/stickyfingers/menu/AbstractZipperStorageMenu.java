package rotp.addons.stickyfingers.menu;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.storage.ZipperStorageSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundSource;

public abstract class AbstractZipperStorageMenu
        extends AbstractContainerMenu {
    private static final int PLAYER_SLOT_COUNT = 36;

    protected final Container storage;
    private final int storageSlots;
    @Nullable
    private final ZipperStorageSession session;

    protected AbstractZipperStorageMenu(
            MenuType<?> type,
            int containerId,
            Inventory playerInventory,
            Container storage,
            int playerInventoryY,
            int hotbarY,
            int storageSlots,
            int storageColumns,
            int storageX,
            int storageY,
            @Nullable ZipperStorageSession session) {
        super(type, containerId);
        this.storage = storage;
        this.storageSlots = storageSlots;
        this.session = session;
        checkContainerSize(storage, storageSlots);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        8 + column * 18,
                        playerInventoryY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    8 + column * 18,
                    hotbarY));
        }
        for (int slot = 0; slot < storageSlots; slot++) {
            addSlot(new Slot(
                    storage,
                    slot,
                    storageX
                            + slot % storageColumns * 18,
                    storageY
                            + slot / storageColumns * 18));
        }
        storage.startOpen(playerInventory.player);
    }

    @Nullable
    public ZipperStorageSession session() {
        return session;
    }

    @Override
    public boolean stillValid(Player player) {
        return session == null || session.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(
            Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved = index < PLAYER_SLOT_COUNT
                ? moveItemStackTo(
                        source,
                        PLAYER_SLOT_COUNT,
                        PLAYER_SLOT_COUNT + storageSlots,
                        false)
                : moveItemStackTo(
                        source,
                        0,
                        PLAYER_SLOT_COUNT,
                        false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (source.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, source);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        storage.stopOpen(player);
        if (session != null
                && player.level()
                        instanceof ServerLevel level) {
            level.playSound(
                    null,
                    player,
                    StickyFingersAddon.ZIPPER_CLOSE.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
    }
}
