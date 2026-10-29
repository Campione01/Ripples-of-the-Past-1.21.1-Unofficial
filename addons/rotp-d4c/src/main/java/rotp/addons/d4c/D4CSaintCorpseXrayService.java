package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.addons.d4c.network.SaintCorpseXrayGlowPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * The donor's Saint Corpse X-ray ({@code com.mrlang.rotp_d4c.ak.aa.ab.af} + {@code ah.ao.am}/{@code an}). 1.16
 * toggled it with a private U key; the port toggles it with the {@code saint_corpse_xray} wheel ability
 * ({@link rotp.addons.d4c.ability.D4CSaintCorpseXrayAbility}).
 *
 * <p>The switch is {@code xray_enabled} in the custom data of the carried eyes (or complete corpse), as in 1.16;
 * while it is on, every living entity within 64 blocks is outlined for that player once a second.</p>
 */
public final class D4CSaintCorpseXrayService {
    static final String XRAY_ENABLED = "xray_enabled";
    private static final double XRAY_RADIUS = 64.0D;
    private static final double XRAY_CLEAR_RADIUS = 128.0D;

    private D4CSaintCorpseXrayService() {}

    /** Donor {@code ah.ac.ae}: the eyes or the complete corpse, carried or worn. */
    public static boolean canUse(Player player) {
        return D4CPhase2BService.hasCorpseItem(player, D4CAddon.SAINT_CORPSE_EYES.get())
                || D4CPhase2BService.hasCorpseItem(player, D4CAddon.COMPLETE_SAINT_CORPSE.get());
    }

    public static void toggle(ServerPlayer player) {
        ItemStack reported = inventoryStack(player, D4CAddon.SAINT_CORPSE_EYES.get());
        if (reported == null) {
            reported = inventoryStack(player, D4CAddon.COMPLETE_SAINT_CORPSE.get());
        }
        if (reported == null) {
            reported = curioStack(player, D4CAddon.SAINT_CORPSE_EYES.get());
        }
        if (reported == null) {
            reported = curioStack(player, D4CAddon.COMPLETE_SAINT_CORPSE.get());
        }
        if (reported == null) {
            return;
        }
        flip(player);
        boolean enabled = flag(reported, XRAY_ENABLED);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_BUTTON_CLICK, SoundSource.PLAYERS, 0.5F, 1.0F);
        player.sendSystemMessage(Component.translatable(enabled
                        ? "message.rotp_d4c.xray_toggle_enabled"
                        : "message.rotp_d4c.xray_toggle_disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static void flip(ServerPlayer player) {
        Item item;
        ItemStack current = preferredStack(player, D4CAddon.SAINT_CORPSE_EYES.get());
        if (current != null) {
            item = D4CAddon.SAINT_CORPSE_EYES.get();
        }
        else {
            current = preferredStack(player, D4CAddon.COMPLETE_SAINT_CORPSE.get());
            item = D4CAddon.COMPLETE_SAINT_CORPSE.get();
        }
        if (current == null) {
            return;
        }
        boolean wasEnabled = flag(current, XRAY_ENABLED);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                setFlag(stack, XRAY_ENABLED, !wasEnabled);
            }
        }
        ItemStack curio = curioStack(player, item);
        if (curio != null) {
            setFlag(curio, XRAY_ENABLED, !wasEnabled);
        }
        if (wasEnabled) {
            sendGlow(player, XRAY_CLEAR_RADIUS, false);
        }
    }

    /** Donor {@code ah.ao.am}: once a second, outline living entities within 64 blocks while the X-ray is on. */
    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        ItemStack source = preferredStack(player, D4CAddon.SAINT_CORPSE_EYES.get());
        if (source == null) {
            source = preferredStack(player, D4CAddon.COMPLETE_SAINT_CORPSE.get());
        }
        if (source != null && flag(source, XRAY_ENABLED)) {
            sendGlow(player, XRAY_RADIUS, true);
        }
    }

    private static void sendGlow(ServerPlayer player, double radius, boolean glowing) {
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity entity : player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                entity -> entity != player && (!glowing || entity.isAlive()))) {
            ids.add(entity.getId());
        }
        if (!ids.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new SaintCorpseXrayGlowPayload(ids, glowing));
        }
    }

    /** Donor {@code ah.ao.aa(PlayerEntity, Item)}: the equipped curio wins over the inventory copy. */
    @Nullable
    private static ItemStack preferredStack(ServerPlayer player, Item item) {
        ItemStack curio = curioStack(player, item);
        return curio != null ? curio : inventoryStack(player, item);
    }

    @Nullable
    private static ItemStack inventoryStack(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                return stack;
            }
        }
        return null;
    }

    @Nullable
    private static ItemStack curioStack(ServerPlayer player, Item item) {
        Optional<ItemStack> stack = CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(item))
                .map(SlotResult::stack);
        return stack.filter(found -> !found.isEmpty()).orElse(null);
    }

    static boolean flag(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(key);
    }

    private static void setFlag(ItemStack stack, String key, boolean value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(key, value));
    }

    /** Clears a boolean custom-data flag; returns whether it was set. */
    static boolean removeFlag(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.getBoolean(key)) {
            return false;
        }
        tag.remove(key);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
        else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return true;
    }
}
