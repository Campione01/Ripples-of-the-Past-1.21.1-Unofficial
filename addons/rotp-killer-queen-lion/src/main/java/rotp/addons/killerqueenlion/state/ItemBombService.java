package rotp.addons.killerqueenlion.state;

import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class ItemBombService {
    public static final String OWNER_TAG =
            "rotp_zkq8:primary_item_bomb_owner";
    public static final double LEGACY_RANGE = 8.0D;
    public static final float EXPLOSION_RADIUS = 2.0F;

    private ItemBombService() {}

    public static boolean canMark(Player owner) {
        return owner != null
                && owner.getMainHandItem().isEmpty()
                && !owner.getOffhandItem().isEmpty();
    }

    public static boolean markOffhandItem(ServerPlayer owner) {
        if (!canMark(owner)) {
            return false;
        }
        ItemStack source = owner.getOffhandItem();
        ItemStack marked = source.split(1);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                marked,
                tag -> tag.putUUID(OWNER_TAG, owner.getUUID()));
        owner.setItemInHand(InteractionHand.MAIN_HAND, marked);
        owner.getInventory().setChanged();
        return true;
    }

    public static boolean isBomb(
            ItemStack stack, UUID ownerUuid) {
        if (stack == null
                || stack.isEmpty()
                || ownerUuid == null) {
            return false;
        }
        var tag = stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY)
                .copyTag();
        return tag.hasUUID(OWNER_TAG)
                && ownerUuid.equals(tag.getUUID(OWNER_TAG));
    }

    public static boolean hasBombsInRange(
            Player owner, double range) {
        if (owner == null
                || !Double.isFinite(range)
                || range < 0.0D) {
            return false;
        }
        UUID ownerUuid = owner.getUUID();
        AABB search = owner.getBoundingBox().inflate(range);
        if (!owner.level().getEntitiesOfClass(
                        ItemEntity.class,
                        search,
                        item -> isBomb(item.getItem(), ownerUuid))
                .isEmpty()) {
            return true;
        }
        for (LivingEntity living
                : owner.level().getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        LivingEntity::isAlive)) {
            if (containsBomb(living, ownerUuid)) {
                return true;
            }
        }
        return false;
    }

    public static int detonate(
            ServerPlayer owner, double range) {
        if (!(owner.level() instanceof ServerLevel level)
                || !Double.isFinite(range)
                || range < 0.0D) {
            return 0;
        }
        UUID ownerUuid = owner.getUUID();
        AABB search = owner.getBoundingBox().inflate(range);
        int detonated = 0;

        for (ItemEntity item
                : level.getEntitiesOfClass(
                        ItemEntity.class,
                        search,
                        entity -> isBomb(
                                entity.getItem(), ownerUuid))) {
            level.explode(
                    owner,
                    item.getX(),
                    item.getY(),
                    item.getZ(),
                    EXPLOSION_RADIUS,
                    Level.ExplosionInteraction.NONE);
            item.discard();
            detonated++;
        }

        for (LivingEntity carrier
                : level.getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        LivingEntity::isAlive)) {
            int removed = removeBombs(carrier, ownerUuid);
            if (removed <= 0) {
                continue;
            }
            // 1.16 ItemFBombExplode asked no harm check of a carrier
            for (int index = 0; index < removed; index++) {
                level.explode(
                        owner,
                        carrier.getX(),
                        carrier.getY(),
                        carrier.getZ(),
                        EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.NONE);
            }
            detonated += removed;
        }
        return detonated;
    }

    private static boolean containsBomb(
            LivingEntity carrier, UUID ownerUuid) {
        if (carrier instanceof Player player) {
            for (int slot = 0;
                    slot < player.getInventory()
                            .getContainerSize();
                    slot++) {
                if (isBomb(
                        player.getInventory().getItem(slot),
                        ownerUuid)) {
                    return true;
                }
            }
            return false;
        }
        for (ItemStack stack : carrier.getAllSlots()) {
            if (isBomb(stack, ownerUuid)) {
                return true;
            }
        }
        return false;
    }

    private static int removeBombs(
            LivingEntity carrier, UUID ownerUuid) {
        int removed = 0;
        if (carrier instanceof Player player) {
            for (int slot = 0;
                    slot < player.getInventory()
                            .getContainerSize();
                    slot++) {
                ItemStack stack =
                        player.getInventory().getItem(slot);
                if (isBomb(stack, ownerUuid)) {
                    stack.shrink(stack.getCount());
                    removed++;
                }
            }
            if (removed > 0) {
                player.getInventory().setChanged();
            }
            return removed;
        }
        for (ItemStack stack : carrier.getAllSlots()) {
            if (isBomb(stack, ownerUuid)) {
                stack.shrink(stack.getCount());
                removed++;
            }
        }
        return removed;
    }
}
