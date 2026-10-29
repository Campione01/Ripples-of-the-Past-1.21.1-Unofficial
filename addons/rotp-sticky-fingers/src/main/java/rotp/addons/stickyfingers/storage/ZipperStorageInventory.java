package rotp.addons.stickyfingers.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ZipperStorageInventory implements Container {
    private final NonNullList<ItemStack> items;
    private Runnable changed;

    public ZipperStorageInventory(
            int size, Runnable changed) {
        this.items = NonNullList.withSize(
                size, ItemStack.EMPTY);
        this.changed = changed;
    }

    public static ZipperStorageInventory load(
            CompoundTag tag,
            int size,
            HolderLookup.Provider registries,
            Runnable changed) {
        ZipperStorageInventory inventory =
                new ZipperStorageInventory(size, changed);
        ContainerHelper.loadAllItems(
                tag, inventory.items, registries);
        return inventory;
    }

    public CompoundTag save(
            HolderLookup.Provider registries) {
        return ContainerHelper.saveAllItems(
                new CompoundTag(), items, registries);
    }

    public void onChanged(Runnable callback) {
        changed = callback;
    }

    public List<ItemStack> drain() {
        List<ItemStack> drained = new ArrayList<>();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                drained.add(stack);
                items.set(slot, ItemStack.EMPTY);
            }
        }
        if (!drained.isEmpty()) {
            setChanged();
        }
        return drained;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size()
                ? items.get(slot)
                : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(
                items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(
                items, slot);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            return;
        }
        stack.limitSize(getMaxStackSize(stack));
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        changed.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        boolean changedAny = !isEmpty();
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        if (changedAny) {
            setChanged();
        }
    }
}
