package rotp.addons.sexpistols.client;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.accessory.MistaHatItem;
import rotp.addons.sexpistols.network.MistaHatAmmoPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class MistaHatClientEvents {
    private MistaHatClientEvents() {}

    @SubscribeEvent
    public static void onMouseButtonPressed(
            ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1
                || !(event.getScreen()
                        instanceof AbstractContainerScreen<?> screen)
                || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player
                        .getAbilities().instabuild) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null) {
            return;
        }
        ItemStack slotStack = slot.getItem();
        ItemStack carried = screen.getMenu().getCarried();
        if (!canHandle(slotStack, carried)) {
            return;
        }
        int menuSlot = screen.getMenu().slots.indexOf(slot);
        if (menuSlot < 0) {
            return;
        }
        PacketDistributor.sendToServer(
                new MistaHatAmmoPayload(
                        screen.getMenu().containerId,
                        menuSlot));
        event.setCanceled(true);
    }

    private static boolean canHandle(
            ItemStack slotStack, ItemStack carried) {
        if (MistaHatItem.isMistaHat(slotStack)) {
            return carried.isEmpty()
                    ? MistaHatItem.storedAmmo(slotStack) > 0
                    : MistaHatItem.isAmmoItem(carried)
                            && MistaHatItem.remainingCapacity(
                                    slotStack) > 0;
        }
        return MistaHatItem.isMistaHat(carried)
                && MistaHatItem.isAmmoItem(slotStack)
                && MistaHatItem.remainingCapacity(carried) > 0;
    }
}
