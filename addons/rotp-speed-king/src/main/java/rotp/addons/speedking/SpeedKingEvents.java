package rotp.addons.speedking;

import rotp.addons.speedking.entity.SpeedKingEntity;
import rotp.addons.speedking.network.SpeedKingStatePayload;
import rotp.addons.speedking.state.SpeedKingState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SpeedKingAddon.MOD_ID)
public final class SpeedKingEvents {
    private SpeedKingEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                SpeedKingAddon.SPEED_KING_ENTITY.get(),
                SpeedKingEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SpeedKingState state = SpeedKingAddon.state(player);
        if (!SpeedKingAddon.isUser(player)) {
            SpeedKingService.cleanupTransient(player);
            if (state.clearAll()) {
                SpeedKingService.sync(player);
            }
            return;
        }
        if (state.heatVisionLeaseId() != null
                && SpeedKingService.activeStand(player) == null) {
            SpeedKingService.cleanupHeatVision(player);
        }
        if (state.heatVisionExpiresAt() > 0L
                && player.serverLevel().getGameTime()
                        >= state.heatVisionExpiresAt()) {
            SpeedKingService.cleanupHeatVision(player);
        }
        if (player.tickCount % 40 == 0) {
            SpeedKingService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !living.level().isClientSide()) {
            SpeedKingService.tickHeatVisionTarget(living);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && !event.getLevel().isClientSide()) {
            SpeedKingService.rejoinHeatVisionTarget(living);
        }
    }

    // example_stand_block is the core guard (donor 099: a plain
    // StandEntityBlock); StandEntity blocks the hit and plays its block sound.

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        SpeedKingService.clearHeatVisionTarget(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            SpeedKingService.cleanupTransient(player);
            SpeedKingAddon.state(player).clearAll();
            SpeedKingService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            SpeedKingService.cleanupTransient(original);
            SpeedKingAddon.state(original).clearAll();
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            SpeedKingAddon.state(player).clearAll();
            SpeedKingService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpeedKingAddon.state(player).clearTransient();
            SpeedKingService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpeedKingService.cleanupTransient(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpeedKingService.cleanupTransient(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(
                    observer, SpeedKingStatePayload.from(target));
        }
    }
}
