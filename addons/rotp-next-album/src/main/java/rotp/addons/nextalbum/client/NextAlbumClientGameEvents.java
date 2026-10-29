package rotp.addons.nextalbum.client;

import rotp.core.api.client.render.ScopedPlayerModelVisibility;
import rotp.core.api.client.render.ScopedPlayerModelVisibility.Part;
import rotp.addons.nextalbum.NextAlbumAddon;
import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.item.AjaStoneNecklaceItem;
import rotp.addons.nextalbum.item.MistaSuitArmorItem;
import rotp.addons.nextalbum.network.NecklaceBeamUsePayload;
import rotp.addons.nextalbum.network.NecklaceChargeStatePayload;
import rotp.addons.nextalbum.state.NecklaceBeamService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = NextAlbumIds.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class NextAlbumClientGameEvents {
    // Ticks after a Sneak+use press in which the server may have started
    // a charge it has not confirmed yet; keepalives bridge that trip.
    private static int pendingTicks;

    private NextAlbumClientGameEvents() {}

    // 1.16 AjaStoneNecklaceItem.handleRightClick took a use only on the
    // server, and only while a charge ran or the press started one. The
    // use a Sneak press sends reaches the server's use events, which
    // decide; a confirmed charge takes the use here, as 1.16's server did.
    // Vanilla fires no use while an item is in use, so eating or drawing
    // a bow never starts one.
    @SubscribeEvent
    public static void onInteraction(
            InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!event.isUseItem()
                || event.getHand() != InteractionHand.MAIN_HAND
                || player == null
                || !isNecklaceEquipped(player)) {
            return;
        }
        if (NecklaceChargeStatePayload.isChargeConfirmed()) {
            PacketDistributor.sendToServer(
                    NecklaceBeamUsePayload.INSTANCE);
            // 1.16 never cancelled an entity interaction.
            if (!(minecraft.hitResult instanceof EntityHitResult)) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
            return;
        }
        // A bare hand in the air sends the server no use (1.16 took it
        // as RightClickEmpty), so ask; every other start goes through a
        // use event, below.
        HitResult hit = minecraft.hitResult;
        if (player.isShiftKeyDown()
                && !isNecklaceOnCooldown(player)
                && player.getMainHandItem().isEmpty()
                && (hit == null || hit.getType() == HitResult.Type.MISS)) {
            PacketDistributor.sendToServer(
                    NecklaceBeamUsePayload.INSTANCE);
            pendingTicks = NecklaceBeamService.KEEPALIVE_TIMEOUT_TICKS;
        }
    }

    // 1.16 handleRightClick ran on RightClickItem/RightClickBlock, which
    // the server fires for this same use. A use an entity interaction
    // took, or an item on cooldown, fires neither, so it starts nothing
    // and gets no keepalive either.
    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        armPendingForUse(event);
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        armPendingForUse(event);
    }

    // The integrated server fires these too; only the local player's
    // client-side event counts.
    private static void armPendingForUse(PlayerInteractEvent event) {
        if (event.getLevel().isClientSide()
                && event.getEntity() instanceof LocalPlayer player
                && !NecklaceChargeStatePayload.isChargeConfirmed()
                && player.isShiftKeyDown()
                && isNecklaceEquipped(player)
                && !isNecklaceOnCooldown(player)) {
            pendingTicks = NecklaceBeamService.KEEPALIVE_TIMEOUT_TICKS;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            pendingTicks = 0;
            NecklaceChargeStatePayload.clearChargeConfirmed();
            return;
        }
        // A released use keeps the charge for the server's 8-tick
        // timeout, so pressing again in time goes on with it, as in
        // 1.16; the server says when the charge ended. While an item is
        // in use (eating, a drawn bow, a raised shield) vanilla sends no
        // use at all, so 1.16's charge timed out without a beam.
        if (minecraft.screen != null
                || !minecraft.options.keyUse.isDown()
                || player.isUsingItem()
                || !isNecklaceEquipped(player)) {
            pendingTicks = 0;
            return;
        }
        if (NecklaceChargeStatePayload.isChargeConfirmed()
                || pendingTicks > 0) {
            PacketDistributor.sendToServer(
                    NecklaceBeamUsePayload.INSTANCE);
        }
        if (pendingTicks > 0) {
            pendingTicks--;
        }
    }

    @SubscribeEvent
    public static void hideSuitOverlays(
            RenderPlayerEvent.Pre event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)) {
            return;
        }
        ItemStack chest =
                player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs =
                player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet =
                player.getItemBySlot(EquipmentSlot.FEET);
        boolean hideBody = chest.getItem()
                instanceof MistaSuitArmorItem cloth
                && cloth.hidesBodyOverlay();
        boolean hideArms = chest.getItem()
                instanceof MistaSuitArmorItem cloth
                && cloth.hidesArmOverlays();
        boolean hideLegs =
                legs.getItem()
                                instanceof MistaSuitArmorItem pants
                        && pants.hidesLegOverlays()
                    || feet.getItem()
                                instanceof MistaSuitArmorItem boots
                        && boots.hidesLegOverlays();
        if (!hideBody && !hideArms && !hideLegs) {
            return;
        }
        var model = event.getRenderer().getModel();
        if (hideBody) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player, model, Part.JACKET);
        }
        if (hideArms) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    model,
                    Part.LEFT_SLEEVE,
                    Part.RIGHT_SLEEVE);
        }
        if (hideLegs) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    model,
                    Part.LEFT_PANTS,
                    Part.RIGHT_PANTS);
        }
    }

    private static boolean isNecklaceEquipped(
            LocalPlayer player) {
        return AjaStoneNecklaceItem.isEquipped(
                player,
                NextAlbumAddon.AJA_STONE_NECKLACE.get());
    }

    private static boolean isNecklaceOnCooldown(
            LocalPlayer player) {
        return player.getCooldowns().isOnCooldown(
                NextAlbumAddon.AJA_STONE_NECKLACE.get());
    }
}
