package rotp.addons.stickyfingers.menu;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.storage.ZipperStorageSession;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

public final class BlockZipperStorageMenu
        extends AbstractZipperStorageMenu {
    public BlockZipperStorageMenu(
            int containerId, Inventory playerInventory) {
        this(
                containerId,
                playerInventory,
                new SimpleContainer(
                        StickyFingersPhase1Spec
                                .BLOCK_STORAGE_SLOTS),
                null);
    }

    public BlockZipperStorageMenu(
            int containerId,
            Inventory playerInventory,
            Container storage,
            @Nullable ZipperStorageSession session) {
        super(
                StickyFingersAddon
                        .STICKY_FINGERS_BLOCK_CONTAINER
                        .get(),
                containerId,
                playerInventory,
                storage,
                67,
                125,
                StickyFingersPhase1Spec.BLOCK_STORAGE_SLOTS,
                9,
                8,
                18,
                session);
    }
}
