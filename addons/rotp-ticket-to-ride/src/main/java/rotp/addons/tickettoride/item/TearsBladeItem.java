package rotp.addons.tickettoride.item;

import java.util.UUID;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.state.TearsWoundService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

public final class TearsBladeItem extends Item {
    public static final int MAX_AMMO = 30;
    // 1.16 GameplayHandler gave the blade with no Ammo tag: empty until refilled
    public static final int INITIAL_AMMO = 0;
    public static final int WOUND_DURATION_TICKS = 6000;

    public TearsBladeItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createOwned(LivingEntity owner) {
        ItemStack stack =
                new ItemStack(TicketToRideAddon.TEARS_BLADE.get());
        stack.set(
                TicketToRideAddon.TEARS_BLADE_DATA.get(),
                new TearsBladeData(
                        owner.getUUID(), INITIAL_AMMO));
        return stack;
    }

    public static int getAmmo(ItemStack stack) {
        TearsBladeData data = data(stack);
        return data != null ? data.ammo() : 0;
    }

    public static boolean isOwnedBy(
            ItemStack stack, LivingEntity owner) {
        TearsBladeData data = data(stack);
        return stack != null
                && stack.is(TicketToRideAddon.TEARS_BLADE.get())
                && data != null
                && data.ownerUuid().equals(owner.getUUID());
    }

    public static boolean isActiveOwned(
            ItemStack stack, LivingEntity owner) {
        return isOwnedBy(stack, owner)
                && TicketToRideAddon.controller(owner) != null;
    }

    public static boolean hasActiveOwnedInHand(Player player) {
        return isActiveOwned(
                        player.getMainHandItem(), player)
                || isActiveOwned(
                        player.getOffhandItem(), player);
    }

    public static boolean hasTearsBladeInHand(Player player) {
        return player.getMainHandItem().is(
                        TicketToRideAddon.TEARS_BLADE.get())
                || player.getOffhandItem().is(
                        TicketToRideAddon.TEARS_BLADE.get());
    }

    public static boolean hasAmmoBelowMaxInHand(Player player) {
        return hasAmmoBelowMax(player.getMainHandItem())
                || hasAmmoBelowMax(player.getOffhandItem());
    }

    public static boolean hasRefillableInHand(Player player) {
        return isRefillable(
                        player.getMainHandItem(), player)
                || isRefillable(
                        player.getOffhandItem(), player);
    }

    public static boolean refillOwnedInHands(Player player) {
        if (player.level().isClientSide()) {
            return false;
        }
        boolean changed = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (isRefillable(stack, player)) {
                setAmmo(stack, MAX_AMMO);
                changed = true;
                // 1.16 RefillAmmoAction.standPerform: per refilled hand
                player.displayClientMessage(Component.translatable(
                                "message.tears_blade.refilled",
                                hand == InteractionHand.MAIN_HAND
                                        ? "\u4e3b\u624b" : "\u526f\u624b"),
                        true);
            }
        }
        if (changed) {
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.RAID_HORN,
                    player.getSoundSource(),
                    1.0F,
                    1.0F);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(
                        ParticleTypes.TOTEM_OF_UNDYING,
                        player.getX(),
                        player.getY() + 1.0D,
                        player.getZ(),
                        10,
                        0.5D,
                        0.5D,
                        0.5D,
                        0.1D);
            }
        }
        return changed;
    }

    @Override
    public boolean hurtEnemy(
            ItemStack stack,
            LivingEntity target,
            LivingEntity attacker) {
        // 1.16 never reached hurtEnemy for a Stand: the core cancelled the
        // hit and dealt it again through its own damage source, so the
        // player's hurt call returned false.
        if (target instanceof StandEntity) {
            return super.hurtEnemy(stack, target, attacker);
        }
        if (!(attacker instanceof Player player)
                || !isActiveOwned(stack, player)
                || getAmmo(stack) <= 0) {
            return super.hurtEnemy(stack, target, attacker);
        }
        if (!attacker.level().isClientSide()
                && consumeAmmo(stack)) {
            TearsWoundService.mark(
                    target,
                    player,
                    WOUND_DURATION_TICKS);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isActiveOwned(stack, player)
                || getAmmo(stack) <= 0) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return getAmmo(stack) > 0
                ? UseAnim.BLOCK
                : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(
            ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean canPerformAction(
            ItemStack stack, ItemAbility itemAbility) {
        return getAmmo(stack) > 0
                && ItemAbilities.DEFAULT_SHIELD_ACTIONS
                        .contains(itemAbility);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return data(stack) != null
                && getAmmo(stack) < MAX_AMMO;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(
                13.0F * getAmmo(stack) / MAX_AMMO);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float fraction =
                (float)getAmmo(stack) / MAX_AMMO;
        return Mth.hsvToRgb(
                Mth.clamp(fraction, 0.0F, 1.0F) / 3.0F,
                1.0F,
                1.0F);
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int itemSlot,
            boolean isSelected) {
        if (!level.isClientSide()
                && (!(entity instanceof LivingEntity living)
                        || !isActiveOwned(stack, living))) {
            stack.setCount(0);
        }
    }

    @Override
    public boolean onEntityItemUpdate(
            ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide()) {
            entity.discard();
        }
        return true;
    }

    public static void enforceOwnedItem(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        if (TicketToRideAddon.controller(player) == null) {
            purgeAll(player);
            return;
        }

        ItemStack keep = ItemStack.EMPTY;
        boolean changed = false;
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack =
                    player.getInventory().getItem(slot);
            if (!stack.is(TicketToRideAddon.TEARS_BLADE.get())) {
                continue;
            }
            if (keep.isEmpty()
                    && isGrantSlot(player, slot)
                    && isOwnedBy(stack, player)) {
                if (stack.getCount() != 1) {
                    stack.setCount(1);
                    changed = true;
                }
                keep = stack;
            } else {
                player.getInventory().setItem(
                        slot, ItemStack.EMPTY);
                changed = true;
            }
        }

        changed |= purgeMenuExcept(player, keep);
        // 1.16 GameplayHandler.onPlayerTick: player.addItem, wherever there
        // is room; the player chooses when to hold it.
        if (keep.isEmpty()
                && player.getInventory().add(createOwned(player))) {
            changed = true;
        }
        if (changed) {
            syncInventory(player);
        }
    }

    public static void purgeAll(LivingEntity holder) {
        if (holder instanceof Player player) {
            boolean changed = false;
            for (int slot = 0;
                    slot < player.getInventory().getContainerSize();
                    slot++) {
                ItemStack stack =
                        player.getInventory().getItem(slot);
                if (stack.is(TicketToRideAddon.TEARS_BLADE.get())) {
                    player.getInventory().setItem(
                            slot, ItemStack.EMPTY);
                    changed = true;
                }
            }
            changed |= purgeMenuExcept(player, ItemStack.EMPTY);
            if (changed) {
                syncInventory(player);
            }
            return;
        }

        if (holder.getMainHandItem().is(
                TicketToRideAddon.TEARS_BLADE.get())) {
            holder.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    ItemStack.EMPTY);
        }
        if (holder.getOffhandItem().is(
                TicketToRideAddon.TEARS_BLADE.get())) {
            holder.setItemInHand(
                    InteractionHand.OFF_HAND,
                    ItemStack.EMPTY);
        }
    }

    public static boolean ownerMatches(
            ItemStack stack, UUID ownerUuid) {
        TearsBladeData data = data(stack);
        return data != null
                && data.ownerUuid().equals(ownerUuid);
    }

    private static boolean isRefillable(
            ItemStack stack, Player player) {
        return isActiveOwned(stack, player)
                && getAmmo(stack) < MAX_AMMO;
    }

    private static boolean hasAmmoBelowMax(ItemStack stack) {
        return stack.is(TicketToRideAddon.TEARS_BLADE.get())
                && getAmmo(stack) < MAX_AMMO;
    }

    private static boolean consumeAmmo(ItemStack stack) {
        int ammo = getAmmo(stack);
        if (ammo <= 0) {
            return false;
        }
        setAmmo(stack, ammo - 1);
        return true;
    }

    private static void setAmmo(ItemStack stack, int ammo) {
        TearsBladeData data = data(stack);
        if (data != null) {
            stack.set(
                    TicketToRideAddon.TEARS_BLADE_DATA.get(),
                    data.withAmmo(ammo));
        }
    }

    private static TearsBladeData data(ItemStack stack) {
        return stack != null
                ? stack.get(
                        TicketToRideAddon.TEARS_BLADE_DATA.get())
                : null;
    }

    private static boolean isGrantSlot(Player player, int slot) {
        return slot < player.getInventory().items.size()
                || slot == Inventory.SLOT_OFFHAND;
    }

    private static void syncInventory(Player player) {
        player.getInventory().setChanged();
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        if (player.inventoryMenu != player.containerMenu) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static boolean purgeMenuExcept(
            Player player, ItemStack keep) {
        if (player.containerMenu == null) {
            return false;
        }
        boolean changed = false;
        for (Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (stack != keep
                    && stack.is(
                            TicketToRideAddon.TEARS_BLADE.get())) {
                slot.set(ItemStack.EMPTY);
                changed = true;
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried != keep
                && carried.is(
                            TicketToRideAddon.TEARS_BLADE.get())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            changed = true;
        }
        return changed;
    }
}
