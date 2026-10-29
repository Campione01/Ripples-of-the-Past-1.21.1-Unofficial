package rotp.addons.tickettoride.state;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class TraceEnchantService {
    public static final int MAX_USES = 3;

    private static final String ROOT = "rotp_ttr.TraceOn";
    private static final String ORIGINAL = "Original";
    private static final String USES = "Uses";

    private TraceEnchantService() {}

    // 1.16 TraceOnAction.FIXED_POOL (:135-171), 35 entries in vanilla order.
    private static final List<Entry> POOL = List.of(
            entry(Enchantments.PROTECTION, 5),
            entry(Enchantments.FIRE_PROTECTION, 5),
            entry(Enchantments.FEATHER_FALLING, 5),
            entry(Enchantments.BLAST_PROTECTION, 5),
            entry(Enchantments.PROJECTILE_PROTECTION, 5),
            entry(Enchantments.RESPIRATION, 5),
            entry(Enchantments.AQUA_AFFINITY, 5),
            entry(Enchantments.THORNS, 5),
            entry(Enchantments.DEPTH_STRIDER, 5),
            entry(Enchantments.FROST_WALKER, 5),
            entry(Enchantments.SOUL_SPEED, 5),
            entry(Enchantments.SHARPNESS, 5),
            entry(Enchantments.SMITE, 5),
            entry(Enchantments.BANE_OF_ARTHROPODS, 5),
            entry(Enchantments.KNOCKBACK, 2),
            entry(Enchantments.FIRE_ASPECT, 5),
            entry(Enchantments.LOOTING, 5),
            entry(Enchantments.SWEEPING_EDGE, 5),
            entry(Enchantments.EFFICIENCY, 9),
            entry(Enchantments.UNBREAKING, 3),
            entry(Enchantments.FORTUNE, 5),
            entry(Enchantments.POWER, 5),
            entry(Enchantments.PUNCH, 5),
            entry(Enchantments.FLAME, 5),
            entry(Enchantments.INFINITY, 5),
            entry(Enchantments.LUCK_OF_THE_SEA, 5),
            entry(Enchantments.LURE, 5),
            entry(Enchantments.LOYALTY, 3),
            entry(Enchantments.IMPALING, 3),
            entry(Enchantments.CHANNELING, 1),
            entry(Enchantments.MULTISHOT, 1),
            entry(Enchantments.QUICK_CHARGE, 10),
            entry(Enchantments.PIERCING, 5),
            entry(Enchantments.MENDING, 1),
            entry(Enchantments.VANISHING_CURSE, 1));

    public static boolean apply(
            ServerLevel level, ItemStack stack) {
        if (!isTraceable(level.registryAccess(), stack)) {
            return false;
        }
        ItemEnchantments.Mutable enchantments =
                new ItemEnchantments.Mutable(
                        EnchantmentHelper
                                .getEnchantmentsForCrafting(stack));
        var registry = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        for (Entry entry : POOL) {
            Holder<Enchantment> enchantment =
                    registry.getOrThrow(entry.key());
            if (canApplyAtEnchantingTable(enchantment, stack)
                    // 1.16 addEnchantment appended an entry and the item's
                    // first entry counted, so an enchantment it had keeps
                    // its level
                    && enchantments.getLevel(enchantment) == 0) {
                enchantments.set(enchantment, entry.level());
            }
        }

        ItemStack original = stack.copy();
        original.remove(DataComponents.CUSTOM_DATA);
        Tag saved = original.save(level.registryAccess());
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    CompoundTag trace = new CompoundTag();
                    trace.put(ORIGINAL, saved);
                    trace.putInt(USES, 0);
                    tag.put(ROOT, trace);
                });
        EnchantmentHelper.setEnchantments(
                stack, enchantments.toImmutable());
        return true;
    }

    public static void recordUse(
            ServerLevel level, ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        CompoundTag root = data.copyTag();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag trace = root.getCompound(ROOT);
        int uses = trace.getInt(USES) + 1;
        if (uses < MAX_USES) {
            trace.putInt(USES, uses);
            root.put(ROOT, trace);
            stack.set(
                    DataComponents.CUSTOM_DATA,
                    CustomData.of(root));
            return;
        }
        restore(level, stack, trace);
    }

    public static boolean isTraced(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null
                && data.copyTag().contains(
                        ROOT, Tag.TAG_COMPOUND);
    }

    private static void restore(
            ServerLevel level,
            ItemStack stack,
            CompoundTag trace) {
        ItemStack original = trace.contains(
                        ORIGINAL, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(
                        level.registryAccess(),
                        trace.getCompound(ORIGINAL))
                : ItemStack.EMPTY;
        ItemEnchantments enchantments = original.getOrDefault(
                DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY);
        EnchantmentHelper.setEnchantments(
                stack, enchantments);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.remove(ROOT));
    }

    // 1.16 TraceOnAction.isValidForTrace (:146-148): a stack with no trace of
    // its own that the table rule lets take at least one pool enchantment.
    public static boolean isTraceable(
            HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty() || isTraced(stack)) {
            return false;
        }
        var registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (Entry entry : POOL) {
            if (canApplyAtEnchantingTable(
                    registry.getOrThrow(entry.key()), stack)) {
                return true;
            }
        }
        return false;
    }

    // 1.16 Enchantment.canApplyAtEnchantingTable (the item's category, so no
    // axe Sharpness and no shears Efficiency, plus the item's own override).
    // 1.21's primary items are that rule except for what is named here:
    // NeoForge lets books take everything, Efficiency's primary items name
    // shears, and the jack-o'-lantern was a wearable CarvedPumpkinBlock.
    public static boolean canApplyAtEnchantingTable(
            Holder<Enchantment> enchantment, ItemStack stack) {
        if (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK)) {
            return false;
        }
        if (stack.is(Items.SHEARS)
                && enchantment.is(Enchantments.EFFICIENCY)) {
            return false;
        }
        if (stack.is(Items.JACK_O_LANTERN)
                && enchantment.is(Enchantments.VANISHING_CURSE)) {
            return true;
        }
        return stack.isPrimaryItemFor(enchantment);
    }

    private static Entry entry(
            ResourceKey<Enchantment> key, int level) {
        return new Entry(key, level);
    }

    private record Entry(ResourceKey<Enchantment> key, int level) {}
}
