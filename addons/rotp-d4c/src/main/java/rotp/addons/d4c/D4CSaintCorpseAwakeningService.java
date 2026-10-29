package rotp.addons.d4c;

import java.util.List;

import com.mojang.datafixers.util.Either;

import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;

/**
 * Donor Saint Corpse awakening ({@code com.mrlang.rotp_d4c.ah.ao.onItemPickup} + {@code ao.ai}): the first time a
 * player picks up each of the nine parts there is a 20% chance to awaken a random Stand, and the first pickup of
 * the complete corpse always does. The donor ran {@code /stand random <player>} as the server with feedback
 * suppressed, which leaves an existing Stand untouched; this calls the same core path directly. Picking up a part
 * that the Corpse Finder placed ({@code from_finder_system}) also records it for the finder.
 *
 * <p>The per-player record lives in the root persistent data under the donor key {@code rotp_d4c_corpse_parts},
 * so, as in 1.16, it does not survive respawn.</p>
 */
public final class D4CSaintCorpseAwakeningService {
    static final String PICKUP_RECORD = "rotp_d4c_corpse_parts";
    private static final String COMPLETE_RECORD_ID = "complete_saint_corpse";
    private static final float PART_AWAKENING_CHANCE = 0.2F;

    private D4CSaintCorpseAwakeningService() {}

    private static List<Item> parts() {
        return List.of(
                D4CAddon.SAINT_CORPSE_HEART.get(),
                D4CAddon.SAINT_CORPSE_RIGHT_ARM.get(),
                D4CAddon.SAINT_CORPSE_EYES.get(),
                D4CAddon.SAINT_CORPSE_EARS.get(),
                D4CAddon.SAINT_CORPSE_LEGS.get(),
                D4CAddon.SAINT_CORPSE_THORAX.get(),
                D4CAddon.SAINT_CORPSE_HEAD.get(),
                D4CAddon.SAINT_CORPSE_SPINE.get(),
                D4CAddon.SAINT_CORPSE_LEFT_ARM.get());
    }

    /** Called from {@code ItemEntityPickupEvent.Pre}; the donor event fired only once the pickup delay was over. */
    public static void onPickupAttempt(ServerPlayer player, ItemEntity itemEntity) {
        if (itemEntity.hasPickUpDelay()) {
            return;
        }
        Item item = itemEntity.getItem().getItem();
        if (parts().contains(item)) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            // A part the Corpse Finder put into a Devil's Palm chest counts as obtained through the finder.
            if (D4CSaintCorpseXrayService.removeFlag(
                    itemEntity.getItem(), D4CCorpseFinderService.FROM_FINDER_SYSTEM)) {
                D4CCorpseFinderService.recordObtainedThroughFinder(player, id);
            }
            if (!pickedUpBefore(player, id)) {
                recordPickup(player, id);
                if (player.getRandom().nextFloat() < PART_AWAKENING_CHANCE) {
                    awakenRandomStand(player);
                }
            }
        }
        if (item == D4CAddon.COMPLETE_SAINT_CORPSE.get()
                && !pickedUpBefore(player, COMPLETE_RECORD_ID)) {
            recordPickup(player, COMPLETE_RECORD_ID);
            awakenRandomStand(player);
        }
    }

    private static boolean pickedUpBefore(ServerPlayer player, String id) {
        ListTag list = player.getPersistentData().getList(PICKUP_RECORD, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void recordPickup(ServerPlayer player, String id) {
        CompoundTag data = player.getPersistentData();
        ListTag list = data.getList(PICKUP_RECORD, Tag.TAG_STRING);
        list.add(StringTag.valueOf(id));
        data.put(PICKUP_RECORD, list);
    }

    /** Same outcome as the core {@code stand random <player>} without {@code replace}, with no chat feedback. */
    private static void awakenRandomStand(ServerPlayer player) {
        PowerClass.STAND.attachPower(player);
        StandPower power = PowerClass.STAND.get(player);
        if (power == null || power.hasPower()) {
            return;
        }
        Either<StandType, Component> stand =
                StandUtil.randomStandOrError(player, player.getRandom());
        stand.left().ifPresent(power::setStand);
    }
}
