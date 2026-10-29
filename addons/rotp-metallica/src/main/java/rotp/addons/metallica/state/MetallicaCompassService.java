package rotp.addons.metallica.state;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.MetallicaPhase1Spec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;

public final class MetallicaCompassService {
    private static final String MARKER_KEY =
            "rotp_metallica_tracked";
    private static final String SOURCE_KEY =
            "rotp_metallica_source";
    private static final String DIMENSION_KEY =
            "rotp_metallica_dimension";
    private static final String POSITION_KEY =
            "rotp_metallica_position";

    private MetallicaCompassService() {}

    public static void tickPlayer(ServerPlayer player) {
        tickPlayer(player, player.containerMenu);
    }

    public static void tickPlayer(
            ServerPlayer player,
            AbstractContainerMenu extraMenu) {
        ServerPlayer source = findNearestSource(player);
        ItemStack candidate = source != null
                ? selectHeldCompass(player)
                : ItemStack.EMPTY;
        sanitizeAccessible(player, extraMenu, candidate);

        if (source == null || candidate.isEmpty()) {
            return;
        }
        if (isTracked(candidate)
                && !ownsCurrentTracker(candidate)) {
            clearMarker(candidate);
            return;
        }
        installTracker(candidate, source);
    }

    public static void restorePlayer(
            ServerPlayer player) {
        restorePlayer(player, player.containerMenu);
    }

    public static void restorePlayer(
            ServerPlayer player,
            AbstractContainerMenu extraMenu) {
        sanitizeAccessible(
                player, extraMenu, ItemStack.EMPTY);
    }

    public static boolean restoreTracked(
            ItemStack stack) {
        if (!isTracked(stack)) {
            return false;
        }
        if (ownsCurrentTracker(stack)) {
            stack.remove(DataComponents.LODESTONE_TRACKER);
        }
        clearMarker(stack);
        return true;
    }

    public static boolean isTracked(
            ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData data =
                stack.get(DataComponents.CUSTOM_DATA);
        return data != null
                && data.copyTag().getBoolean(MARKER_KEY);
    }

    private static ServerPlayer findNearestSource(
            ServerPlayer target) {
        if (!(target.level() instanceof ServerLevel level)
                || !target.isAlive()
                || MetallicaAddon.isMetallicaUser(target)) {
            return null;
        }

        double maxDistanceSqr =
                MetallicaPhase1Spec.MAX_RANGE
                        * MetallicaPhase1Spec.MAX_RANGE;
        ServerPlayer nearest = null;
        double nearestDistanceSqr =
                Double.POSITIVE_INFINITY;
        for (ServerPlayer candidate : level.players()) {
            if (candidate == target
                    || !candidate.isAlive()
                    || MetallicaAddon.controller(candidate)
                            == null) {
                continue;
            }
            double distanceSqr =
                    candidate.distanceToSqr(target);
            if (distanceSqr > maxDistanceSqr) {
                continue;
            }
            if (nearest == null
                    || distanceSqr < nearestDistanceSqr
                    || Double.compare(
                            distanceSqr,
                            nearestDistanceSqr) == 0
                            && candidate.getUUID()
                                    .compareTo(
                                            nearest.getUUID())
                                    < 0) {
                nearest = candidate;
                nearestDistanceSqr = distanceSqr;
            }
        }
        return nearest;
    }

    private static ItemStack selectHeldCompass(
            ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (canOwnTracker(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        return canOwnTracker(offHand)
                ? offHand
                : ItemStack.EMPTY;
    }

    private static boolean canOwnTracker(
            ItemStack stack) {
        if (isTracked(stack)) {
            return stack.is(Items.COMPASS);
        }
        if (!stack.is(Items.COMPASS)) {
            return false;
        }
        ItemStack pristine =
                new ItemStack(Items.COMPASS, stack.getCount());
        return ItemStack.isSameItemSameComponents(
                stack, pristine);
    }

    private static void sanitizeAccessible(
            ServerPlayer player,
            AbstractContainerMenu extraMenu,
            ItemStack keep) {
        Set<ItemStack> visited = Collections.newSetFromMap(
                new IdentityHashMap<>());
        for (int slot = 0;
                slot < player.getInventory()
                        .getContainerSize();
                slot++) {
            sanitize(
                    player.getInventory().getItem(slot),
                    keep,
                    visited);
        }
        sanitizeMenu(
                player.containerMenu,
                keep,
                visited);
        if (extraMenu != player.containerMenu) {
            sanitizeMenu(extraMenu, keep, visited);
        }
    }

    private static void sanitizeMenu(
            AbstractContainerMenu menu,
            ItemStack keep,
            Set<ItemStack> visited) {
        if (menu == null) {
            return;
        }
        for (Slot slot : menu.slots) {
            sanitize(slot.getItem(), keep, visited);
        }
        sanitize(menu.getCarried(), keep, visited);
    }

    private static void sanitize(
            ItemStack stack,
            ItemStack keep,
            Set<ItemStack> visited) {
        if (stack == keep || !visited.add(stack)) {
            return;
        }
        restoreTracked(stack);
    }

    private static void installTracker(
            ItemStack stack,
            ServerPlayer source) {
        BlockPos target = source.blockPosition();
        LodestoneTracker expected =
                new LodestoneTracker(
                        java.util.Optional.of(
                                GlobalPos.of(
                                        source.level()
                                                .dimension(),
                                        target)),
                        false);
        if (expected.equals(stack.get(
                        DataComponents.LODESTONE_TRACKER))
                && markerMatches(
                        stack, source, target)) {
            return;
        }
        stack.set(
                DataComponents.LODESTONE_TRACKER,
                expected);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    tag.putBoolean(MARKER_KEY, true);
                    tag.putUUID(
                            SOURCE_KEY, source.getUUID());
                    tag.putString(
                            DIMENSION_KEY,
                            source.level().dimension()
                                    .location().toString());
                    tag.putLong(
                            POSITION_KEY, target.asLong());
                });
    }

    private static boolean markerMatches(
            ItemStack stack,
            ServerPlayer source,
            BlockPos target) {
        CustomData data =
                stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        CompoundTag tag = data.copyTag();
        return tag.getBoolean(MARKER_KEY)
                && tag.hasUUID(SOURCE_KEY)
                && tag.getUUID(SOURCE_KEY)
                        .equals(source.getUUID())
                && tag.contains(
                        DIMENSION_KEY, Tag.TAG_STRING)
                && tag.getString(DIMENSION_KEY).equals(
                        source.level().dimension()
                                .location().toString())
                && tag.contains(
                        POSITION_KEY, Tag.TAG_LONG)
                && tag.getLong(POSITION_KEY)
                        == target.asLong();
    }

    private static boolean ownsCurrentTracker(
            ItemStack stack) {
        CustomData data =
                stack.get(DataComponents.CUSTOM_DATA);
        LodestoneTracker tracker =
                stack.get(DataComponents.LODESTONE_TRACKER);
        if (data == null || tracker == null
                || tracker.tracked()
                || tracker.target().isEmpty()) {
            return false;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.getBoolean(MARKER_KEY)
                || !tag.contains(
                        DIMENSION_KEY, Tag.TAG_STRING)
                || !tag.contains(
                        POSITION_KEY, Tag.TAG_LONG)) {
            return false;
        }
        GlobalPos target = tracker.target().orElseThrow();
        return target.dimension().location().toString()
                        .equals(tag.getString(DIMENSION_KEY))
                && target.pos().asLong()
                        == tag.getLong(POSITION_KEY);
    }

    private static void clearMarker(
            ItemStack stack) {
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    tag.remove(MARKER_KEY);
                    tag.remove(SOURCE_KEY);
                    tag.remove(DIMENSION_KEY);
                    tag.remove(POSITION_KEY);
                });
    }
}
