package rotp.addons.thehand;

import rotp.addons.thehand.network.ErasedHealthSyncPayload;
import rotp.addons.thehand.state.ErasedHealthService;
import rotp.addons.thehand.state.ErasedHealthState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TheHandAddon.MOD_ID)
public final class TheHandEvents {
    private TheHandEvents() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                TheHandAddon.THE_HAND_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()
                || event.getAmount() <= 0.0F
                || !target.hasData(TheHandAddon.ERASED_HEALTH)) {
            return;
        }
        float room = ErasedHealthService.healingCeiling(target)
                - target.getHealth();
        if (room <= 0.0F) {
            event.setCanceled(true);
        } else if (event.getAmount() > room) {
            event.setAmount(room);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()
                || event.getNewDamage() <= 0.0F) {
            return;
        }
        if (target.hasEffect(TheHandAddon.SURPRISE)) {
            target.removeEffect(TheHandAddon.SURPRISE);
        }
        // A hit during the erase charge is the core held-action rule
        // (TheHandEraseAbility opts in).
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !living.level().isClientSide()
                && living.hasData(TheHandAddon.ERASED_HEALTH)) {
            ErasedHealthService.validateServer(living);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        ErasedHealthService.clear(entity);
        ErasedHealthService.clearOwnedBy(
                entity.getServer(), entity.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player player = event.getEntity();
        ErasedHealthService.clear(original);
        ErasedHealthService.clear(player);
        ErasedHealthService.clearOwnedBy(
                player.getServer(), original.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        clearPlayerAndOwned(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        clearPlayerAndOwned(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        clearPlayerAndOwned(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayerAndOwned(event.getEntity());
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer trackingPlayer
                && event.getTarget() instanceof LivingEntity target
                && target.hasData(TheHandAddon.ERASED_HEALTH)) {
            ErasedHealthState state =
                    TheHandAddon.erasedHealthState(target);
            PacketDistributor.sendToPlayer(
                    trackingPlayer,
                    new ErasedHealthSyncPayload(
                            target.getId(),
                            state.erasedHealth(),
                            state.expiresAtGameTime()));
        }
    }

    private static void clearPlayerAndOwned(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ErasedHealthService.clear(player);
        MinecraftServer server = serverPlayer.getServer();
        ErasedHealthService.clearOwnedBy(
                server, player.getUUID());
    }
}
