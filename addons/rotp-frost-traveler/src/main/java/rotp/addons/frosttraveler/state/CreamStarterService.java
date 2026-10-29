package rotp.addons.frosttraveler.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.item.DisguiseData;
import rotp.addons.frosttraveler.item.SprayerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CreamStarterService {
    public static final int RECONCILE_INTERVAL = 20;

    private CreamStarterService() {}

    public static CreamStarterState state(Player player) {
        return FrostTravelerAddon.state(player);
    }

    public static boolean isOwnedSprayer(
            Player owner, ItemStack stack) {
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        return stack.is(FrostTravelerAddon.CREAM_STARTER_ITEM.get())
                && data != null
                && data.ownerUuid().equals(owner.getUUID());
    }

    public static ItemStack createOwnedSprayer(Player owner) {
        CreamStarterState state = state(owner);
        ItemStack stack = new ItemStack(
                FrostTravelerAddon.CREAM_STARTER_ITEM.get());
        stack.set(
                FrostTravelerAddon.SPRAYER_DATA,
                new SprayerData(
                        owner.getUUID(),
                        state.ammo(),
                        state.mode(),
                        state.standSkin(),
                        0));
        return stack;
    }

    public static void syncStateToStack(
            Player owner, ItemStack stack) {
        SprayerData old = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        if (old != null
                && old.ownerUuid().equals(owner.getUUID())) {
            stack.set(
                    FrostTravelerAddon.SPRAYER_DATA,
                    old.withState(state(owner)));
        }
    }

    public static ItemStack heldOwnedSprayer(Player owner) {
        ItemStack main = owner.getMainHandItem();
        if (isOwnedSprayer(owner, main)) {
            return main;
        }
        ItemStack off = owner.getOffhandItem();
        return isOwnedSprayer(owner, off) ? off : ItemStack.EMPTY;
    }

    public static DisguiseData equippedDisguise(Player wearer) {
        ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(FrostTravelerAddon.MEAT_DGS_ITEM.get())) {
            return null;
        }
        DisguiseData data = head.get(
                FrostTravelerAddon.DISGUISE_DATA);
        return data != null
                && data.ownerUuid().equals(wearer.getUUID())
                && data.isCaptured()
                ? data
                : null;
    }

    public static void reconcile(Player owner) {
        if (!(owner instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!FrostTravelerAddon.isActiveUser(owner)) {
            purgeOwnedSprayers(owner);
            return;
        }
        FrostTravelerAddon.controller(owner)
                .getStandSkin()
                .ifPresentOrElse(
                        skin -> state(owner).setStandSkin(
                                skin.toString()),
                        () -> state(owner).setStandSkin(""));

        MinecraftServer server = serverPlayer.getServer();
        UUID ownerId = owner.getUUID();
        ItemStack keep = ItemStack.EMPTY;

        // an owner off the player list (a fake player) keeps what it holds
        List<ServerPlayer> holders =
                new ArrayList<>(server.getPlayerList().getPlayers());
        if (!holders.contains(serverPlayer)) {
            holders.add(serverPlayer);
        }
        for (ServerPlayer online : holders) {
            for (int slot = 0;
                    slot < online.getInventory().getContainerSize();
                    slot++) {
                ItemStack candidate =
                        online.getInventory().getItem(slot);
                if (!isOwnedSprayer(ownerId, candidate)) {
                    continue;
                }
                if (keep.isEmpty()) {
                    keep = candidate;
                } else if (candidate != keep) {
                    online.getInventory().setItem(
                            slot, ItemStack.EMPTY);
                }
            }
            if (online.containerMenu != null) {
                ItemStack carried =
                        online.containerMenu.getCarried();
                if (isOwnedSprayer(ownerId, carried)) {
                    if (keep.isEmpty()) {
                        keep = carried;
                    } else if (carried != keep) {
                        online.containerMenu.setCarried(
                                ItemStack.EMPTY);
                    }
                }
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity itemEntity)) {
                    continue;
                }
                ItemStack candidate = itemEntity.getItem();
                if (!isOwnedSprayer(ownerId, candidate)) {
                    continue;
                }
                if (keep.isEmpty()) {
                    keep = candidate;
                } else if (candidate != keep) {
                    itemEntity.discard();
                }
            }
        }

        if (keep.isEmpty()) {
            keep = createOwnedSprayer(owner);
            if (!owner.getInventory().add(keep)) {
                owner.spawnAtLocation(keep);
            }
        }
        syncStateToStack(owner, keep);
    }

    public static void purgeOwnedSprayers(Player owner) {
        if (!(owner instanceof ServerPlayer serverPlayer)) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        UUID ownerId = owner.getUUID();
        for (ServerPlayer online :
                server.getPlayerList().getPlayers()) {
            for (int slot = 0;
                    slot < online.getInventory().getContainerSize();
                    slot++) {
                if (isOwnedSprayer(
                        ownerId,
                        online.getInventory().getItem(slot))) {
                    online.getInventory().setItem(
                            slot, ItemStack.EMPTY);
                }
            }
            if (online.containerMenu != null
                    && isOwnedSprayer(
                            ownerId,
                            online.containerMenu.getCarried())) {
                online.containerMenu.setCarried(ItemStack.EMPTY);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity itemEntity
                        && isOwnedSprayer(
                                ownerId, itemEntity.getItem())) {
                    itemEntity.discard();
                }
            }
        }
    }

    private static boolean isOwnedSprayer(
            UUID ownerId, ItemStack stack) {
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        return stack.is(FrostTravelerAddon.CREAM_STARTER_ITEM.get())
                && data != null
                && ownerId.equals(data.ownerUuid());
    }
}
