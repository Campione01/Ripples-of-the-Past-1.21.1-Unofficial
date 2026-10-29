package rotp.addons.stickyfingers.menu;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.storage.ZipperStorageSession;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

public final class EntityZipperStorageMenu
        extends AbstractZipperStorageMenu {
    public EntityZipperStorageMenu(
            int containerId, Inventory playerInventory) {
        this(
                containerId,
                playerInventory,
                new SimpleContainer(
                        StickyFingersPhase1Spec
                                .ENTITY_STORAGE_SLOTS),
                null);
    }

    public EntityZipperStorageMenu(
            int containerId,
            Inventory playerInventory,
            Container storage,
            @Nullable ZipperStorageSession session) {
        super(
                StickyFingersAddon
                        .STICKY_FINGERS_ENTITY_CONTAINER
                        .get(),
                containerId,
                playerInventory,
                storage,
                84,
                142,
                StickyFingersPhase1Spec.ENTITY_STORAGE_SLOTS,
                3,
                62,
                17,
                session);
    }
}
