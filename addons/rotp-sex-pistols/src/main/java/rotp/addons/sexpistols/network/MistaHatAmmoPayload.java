package rotp.addons.sexpistols.network;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.accessory.MistaHatItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MistaHatAmmoPayload(int containerId, int menuSlot)
        implements CustomPacketPayload {
    public static final Type<MistaHatAmmoPayload> TYPE =
            new Type<>(SexPistolsIds.id("mista_hat_ammo"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            MistaHatAmmoPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    MistaHatAmmoPayload::write,
                    MistaHatAmmoPayload::new);

    private MistaHatAmmoPayload(
            RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(menuSlot);
    }

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(
                TYPE,
                STREAM_CODEC,
                MistaHatAmmoPayload::handle);
    }

    private static void handle(
            MistaHatAmmoPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(
                    player,
                    payload.containerId(),
                    payload.menuSlot());
        }
    }

    private static void apply(
            ServerPlayer player,
            int containerId,
            int menuSlot) {
        if (player.getAbilities().instabuild) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId
                || !menu.stillValid(player)
                || menuSlot < 0
                || menuSlot >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.getSlot(menuSlot);
        if (!slot.isActive()) {
            return;
        }

        ItemStack slotStack = slot.getItem();
        ItemStack carried = menu.getCarried();
        boolean changed = false;
        if (MistaHatItem.isMistaHat(slotStack)) {
            if (!slot.mayPickup(player)) {
                return;
            }
            if (carried.isEmpty()) {
                ItemStack extracted =
                        MistaHatItem.removeAmmoStack(slotStack);
                if (!extracted.isEmpty()) {
                    menu.setCarried(extracted);
                    changed = true;
                }
            }
            else if (MistaHatItem.isAmmoItem(carried)) {
                changed =
                        MistaHatItem.insertAmmo(slotStack, carried) > 0;
                if (carried.isEmpty()) {
                    menu.setCarried(ItemStack.EMPTY);
                }
            }
        }
        else if (MistaHatItem.isMistaHat(carried)
                && MistaHatItem.isAmmoItem(slotStack)
                && slot.mayPickup(player)) {
            changed =
                    MistaHatItem.insertAmmo(carried, slotStack) > 0;
            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            }
        }

        if (!changed) {
            return;
        }
        slot.setChanged();
        menu.broadcastChanges();
        menu.sendAllDataToRemote();
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS,
                0.8F,
                1.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
