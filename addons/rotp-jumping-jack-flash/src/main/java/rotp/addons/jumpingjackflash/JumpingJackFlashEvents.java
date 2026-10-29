package rotp.addons.jumpingjackflash;

import rotp.addons.jumpingjackflash.entity.JumpingJackFlashStandEntity;
import rotp.addons.jumpingjackflash.state.GravityLeaseService;
import rotp.addons.jumpingjackflash.state.JumpingJackFlashService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = JumpingJackFlashAddon.MOD_ID)
public final class JumpingJackFlashEvents {
    private JumpingJackFlashEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                JumpingJackFlashAddon.STAND_ENTITY.get(),
                JumpingJackFlashStandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            GravityLeaseService.reconstructOwnerMarkers(
                    event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !GravityLeaseService.hasActiveOwner(
                        player.getUUID())) {
            return;
        }
        if (!JumpingJackFlashAddon.isUser(player)) {
            JumpingJackFlashService.cleanupOwner(player);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (GravityLeaseService.isAffected(event.getEntity())) {
            event.getEntity().fallDistance = 0.0F;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectCured(MobEffectEvent.Remove event) {
        if (!event.isCanceled() && event.getCure() != null) {
            GravityLeaseService.onEffectCured(event.getEntity(), event.getEffect());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        GravityLeaseService.releaseTarget(entity);
        if (entity instanceof ServerPlayer player) {
            JumpingJackFlashService.cleanupOwner(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        GravityLeaseService.releaseTarget(event.getOriginal());
        GravityLeaseService.releaseTarget(event.getEntity());
        if (event.getOriginal() instanceof ServerPlayer original) {
            JumpingJackFlashService.cleanupOwner(original);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            JumpingJackFlashService.cleanupOwner(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        GravityLeaseService.releaseTarget(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            JumpingJackFlashService.cleanupOwner(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        GravityLeaseService.releaseTarget(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            JumpingJackFlashService.cleanupOwner(player);
        }
    }
}
