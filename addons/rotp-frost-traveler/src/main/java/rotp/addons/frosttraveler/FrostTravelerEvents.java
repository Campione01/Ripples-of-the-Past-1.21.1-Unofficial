package rotp.addons.frosttraveler;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.frosttraveler.ability.CreamStandActionAbility;
import rotp.addons.frosttraveler.item.DisguiseData;
import rotp.addons.frosttraveler.item.MeatMaskItem;
import rotp.addons.frosttraveler.state.CreamStarterService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingVisibilityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = FrostTravelerAddon.MOD_ID)
public final class FrostTravelerEvents {
    private FrostTravelerEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                FrostTravelerAddon.CREAM_STARTER_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (player.tickCount
                % CreamStarterService.RECONCILE_INTERVAL == 0) {
            CreamStarterService.reconcile(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        if (event.getAmount() > 10.0F) {
            if (player.getUseItem().is(
                    FrostTravelerAddon.CREAM_STARTER_ITEM.get())) {
                player.stopUsingItem();
            }
            // 1.16 stopHeldAction(false): the heal's cooldown starts here too.
            CreamStandActionAbility.stopHeldHealOnHit(player);
        }
        ItemStack head = player.getInventory().armor.get(3);
        DisguiseData data = head.get(
                FrostTravelerAddon.DISGUISE_DATA);
        LivingEntity attacker =
                resolveLiving(
                        event.getSource().getDirectEntity());
        if (head.is(FrostTravelerAddon.MEAT_DGS_ITEM.get())
                && data != null
                && data.ownerUuid().equals(player.getUUID())
                && !data.isCaptured()
                && attacker != null
                && !(attacker instanceof StandEntity)
                && attacker != player) {
            MeatMaskItem.capture(
                    head, attacker, player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLivingVisibility(
            LivingVisibilityEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        DisguiseData disguise =
                CreamStarterService.equippedDisguise(player);
        if (disguise != null && disguise.hostile()) {
            event.modifyVisibility(0.05D);
        }
    }

    @SubscribeEvent
    public static void onLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        CreamStarterService.purgeOwnedSprayers(
                event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player
                && !player.level().isClientSide()) {
            CreamStarterService.purgeOwnedSprayers(player);
        }
    }

    private static LivingEntity resolveLiving(
            net.minecraft.world.entity.Entity entity) {
        return entity instanceof LivingEntity living
                ? living
                : null;
    }
}
