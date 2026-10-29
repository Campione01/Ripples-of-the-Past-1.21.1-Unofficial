package rotp.addons.madeinheaven;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.madeinheaven.state.TimeAccelerationService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = MadeInHeavenAddon.MOD_ID)
public final class MadeInHeavenGameEvents {
    private MadeInHeavenGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                MadeInHeavenAddon
                        .MADE_IN_HEAVEN_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event) {
        TimeAccelerationService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            TimeAccelerationService.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(
            LivingDeathEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            TimeAccelerationService.clearIfOwner(
                    player,
                    TimeAccelerationService
                            .ClearReason.OWNER_DEATH);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            TimeAccelerationService.clearIfOwner(
                    player,
                    TimeAccelerationService
                            .ClearReason.OWNER_LOGOUT);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            TimeAccelerationService.clearIfOwner(
                    player,
                    TimeAccelerationService
                            .ClearReason.DIMENSION_CHANGE);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(
            EntityJoinLevelEvent event) {
        TimeAccelerationService.onEntityJoin(
                event.getEntity(), event.getLevel());
    }

    @SubscribeEvent
    public static void onServerStopping(
            ServerStoppingEvent event) {
        TimeAccelerationService.clear(
                event.getServer().overworld(),
                TimeAccelerationService
                        .ClearReason.SERVER_STOP);
    }
}
